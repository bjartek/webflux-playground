package org.bjartek.webfluxrefapp

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
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
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Configuration
class MeterRegistryHealthCheckConfiguration(
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

}

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

