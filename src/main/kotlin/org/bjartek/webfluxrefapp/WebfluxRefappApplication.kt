package org.bjartek.webfluxrefapp

import brave.Tracing
import brave.propagation.CurrentTraceContext
import brave.propagation.TraceContext
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.core.io.support.DefaultPropertySourceFactory
import org.springframework.core.io.support.EncodedResource
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

val logger = KotlinLogging.logger {}

// start with -Dreactor.netty.http.server.accessLogEnabled=true for access log
fun main(args: Array<String>) {
    runApplication<WebfluxRefappApplication>(*args)
}

@SpringBootApplication
@Configuration
@PropertySource("classpath:baseproperties.yml", factory = YamlPropertyLoaderFactory::class)
class WebfluxRefappApplication {

    @Bean
    fun webclient(
        @Value("\${spring.application.name}") name: String,
        builder: WebClient.Builder
    ): WebClient {

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

@Service
@RestController
@RequestMapping("/")
class Controller(
    val client: WebClient,
    val ctx: CurrentTraceContext
) {

    @GetMapping("auth/foo")
    suspend fun authFoo(): Map<String, String> {
        return withContext(TracingContextElement(ctx) + MDCContext()) {
            mapOf("authfoo" to "bar").also {
                logger.info { it }
            }
        }
    }

    @GetMapping("auth/bar")
    suspend fun authBar(): JsonNode? {
        return withContext(TracingContextElement(ctx) + MDCContext()) {
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
            result
        }
    }

    @GetMapping("foo")
    suspend fun foo(): Map<String, String> {
        return withContext(TracingContextElement(ctx) + MDCContext()) {
            mapOf("foo" to "bar").also {
                logger.info { it }
            }
        }
    }

    @GetMapping("/bar")
    suspend fun bar(): JsonNode? {
        return withContext(TracingContextElement(ctx) + MDCContext()) {
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
            result
        }
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

typealias TraceContextHolder = CurrentTraceContext
typealias TraceContextState = TraceContext?

class TracingContextElement(
    private val traceContextHolder: TraceContextHolder = Tracing.current().currentTraceContext(),
    private var context: TraceContextState = traceContextHolder.get()
) : ThreadContextElement<CurrentTraceContext.Scope>, AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TracingContextElement>

    override fun updateThreadContext(context: CoroutineContext): CurrentTraceContext.Scope {
        return traceContextHolder.maybeScope(this.context)
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: CurrentTraceContext.Scope) {
        // check to see if, during coroutine execution, the context was updated
        traceContextHolder.get()?.let {
            if (it != this.context) {
                this.context = it
            }
        }

        oldState.close()
    }
}
