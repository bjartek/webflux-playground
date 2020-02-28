package org.bjartek.webfluxrefapp

import com.fasterxml.jackson.databind.JsonNode
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import kotlinx.coroutines.reactive.awaitFirst
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.boot.actuate.health.CompositeHealthContributor
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthContributor
import org.springframework.boot.actuate.health.HealthContributorRegistry
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.health.NamedContributor
import org.springframework.boot.actuate.health.NamedContributors
import org.springframework.boot.actuate.health.SimpleStatusAggregator
import org.springframework.boot.actuate.health.Status
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.core.io.support.DefaultPropertySourceFactory
import org.springframework.core.io.support.EncodedResource
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@Component
class Test2: HealthIndicator {
    override fun health(): Health {
        return Health.up().withDetail("Foo", "bar").build()
    }
}

@Component
class Test: HealthIndicator {
    override fun health(): Health {
        return Health.status("OBSERVE").withDetail("Foo", "bar").build()
    }
}


@Component
class Foo(val t:Test, val t2:Test2): CompositeHealthContributor {
    val c = mapOf("testing" to t, "testing2" to t2)
    override fun iterator(): MutableIterator<NamedContributor<HealthContributor>> {
        val nc =c.map {
            object : NamedContributor<HealthContributor> {
                override fun getName() = it.key
                override fun getContributor() = it.value
            }
        }

        return nc.iterator() as MutableIterator<NamedContributor<HealthContributor>>
    }

    override fun getContributor(name: String?): HealthContributor? {
        if(name == null) return null

        return c[name]
    }
}


@SpringBootApplication
@Configuration
@PropertySource("classpath:baseproperties.yml", factory = YamlPropertyLoaderFactory::class)
class WebfluxRefappApplication(
    @Value("\${management.endpoint.health.status.order:DOWN, OUT_OF_SERVICE, FATAL, UNKNOWN, UP}") val order:String
) {

    val statuses: Map<String, Double> = order.split(", ").reversed().mapIndexed { index, s ->
        s to index.toDouble()
    }.toMap()

    @Bean
    fun healthRegistrySingleHealthCustomizer(healthRegistry: HealthContributorRegistry): MeterRegistryCustomizer<MeterRegistry>? {
        return MeterRegistryCustomizer { registry: MeterRegistry ->
            Gauge.builder("health",healthRegistry, { health ->
                    val status = health.findAllStatuses().aggregateStatus()
                    statuses[status.code] ?: 4.0 })
                .description("Aggregated status for all health checks. Value is modeled after unix exit codes. 0=OK, the higher the more servere.")
                .register(registry)
        }
    }

    @Bean
    fun healthRegistryActuatorHealthForEachHealthCheckCustomizer(healthRegistry: HealthContributorRegistry): MeterRegistryCustomizer<MeterRegistry>? {
        return MeterRegistryCustomizer { registry: MeterRegistry ->

            val healIndicators = healthRegistry.findAllHealthIndicatorNames()

            val healthGauge = MultiGauge.builder("health_indicator")
                .description("Status of a individual health check. Value is modeled after unix exit codes. 0=ok, the higher the number the more severe")
                .register(registry)

            healthGauge.register(healIndicators.map {
                val t: Tags = Tags.of("name", it)
                MultiGauge.Row.of(t, it, {name ->
                    val healthIndicator = healthRegistry.findHealthIndicator(name)
                    val status = healthIndicator.health().status
                    statuses[status.code] ?: 4.0
                })
            })

        }
    }

    /*
      Find a given health indicator in  NamedContributors

      @param name : Name of healthIndicator spearated with "." if part of a composite health contributor
     */
    fun NamedContributors<HealthContributor>.findHealthIndicator(name:String) : HealthIndicator {
        if(!name.contains(".")) {
           return this.getContributor(name) as HealthIndicator
        }

        val composite = this.getContributor(name.substringBefore(".")) as CompositeHealthContributor
        return composite.findHealthIndicator(name.substringAfter("."))

    }

    fun Set<Status>.aggregateStatus(): Status = SimpleStatusAggregator(order.split(", ")).getAggregateStatus(this)

    /*
      Find all health indicator statuses, join on "." for composite contributors
     */
    fun NamedContributors<HealthContributor>.findAllStatuses() : Set<Status> {
        return this.flatMap {
            when(val c = it.contributor) {
                is HealthIndicator -> setOf(c.health().status)
                is CompositeHealthContributor -> c.findAllStatuses()
                else -> emptySet()
            }
        }.toSet()
    }


    /*
      Find all health indicators names, join on "." for composite contributors
     */
    fun NamedContributors<HealthContributor>.findAllHealthIndicatorNames(suffix:String="") : Set<String> {
        return this.flatMap {
            val fullName="$suffix${it.name}"
            when(val c = it.contributor) {
                is HealthIndicator -> setOf(fullName)
                is CompositeHealthContributor -> c.findAllHealthIndicatorNames("${fullName}.")
                else -> emptySet()
            }
        }.toSet()
    }

    @Bean
    fun webclient(
            @Value("\${spring.application.name}") name: String,
            builder: WebClient.Builder): WebClient {

        return builder
                .baseUrl("http://127.0.0.1:8080")
                .filter(ExchangeFilterFunction.ofRequestProcessor { req ->
                    val cr = ClientRequest.from(req).headers {
                        it["User-Agent"] = name
                    }.build()
                    Mono.just(cr)
                }).build()

    }

}

// start with -Dreactor.netty.http.server.accessLogEnabled=true for access log
fun main(args: Array<String>) {
    runApplication<WebfluxRefappApplication>(*args)
}

val logger = KotlinLogging.logger {}

@Service
@RestController
@RequestMapping("/")
class Controller(
        val client: WebClient
) {


    @GetMapping("auth/foo")
    suspend fun authFoo(): Map<String, String> {
        return mapOf("authfoo" to "bar").also {
            logger.info { it }
        }
    }

    @GetMapping("auth/bar")
    suspend fun authBar(): JsonNode? {
        //User is set here
        logger.info { "Auth bar begin" }
        val result = client
            .get()
            .uri("/auth/foo")
            .header(HttpHeaders.AUTHORIZATION, "Bearer token")
            .retrieve()
            .bodyToMono<JsonNode>()
            .log()
            .awaitFirst()

        //User is not set here
        logger.info { "Auth bar ends" }
        return result
    }

    @GetMapping("foo")
    suspend fun foo(): Map<String, String> {
        return mapOf("foo" to "bar").also {
            logger.info { it }
        }
    }

    @GetMapping("/bar")
    suspend fun bar(): JsonNode? {
        logger.info { "\n\nbar" }
        val result = client
                .get()
                .uri("/foo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .retrieve()
                .bodyToMono<JsonNode>()
                .log()
                .awaitFirst()

        logger.info { "bar\n\n" }
        return result
    }
}


class YamlPropertyLoaderFactory : DefaultPropertySourceFactory() {
    override fun createPropertySource(
        name: String?,
        resource: EncodedResource
    ): org.springframework.core.env.PropertySource<*> {

        return YamlPropertySourceLoader().load(resource.resource.filename, resource.resource).first()
    }
}
