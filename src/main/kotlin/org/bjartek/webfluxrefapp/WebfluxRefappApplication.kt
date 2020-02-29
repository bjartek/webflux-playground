package org.bjartek.webfluxrefapp

import brave.Tracing
import brave.propagation.CurrentTraceContext
import brave.propagation.TraceContext
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.slf4j.MDCContextMap
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.jboss.logging.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

val logger = KotlinLogging.logger {}

// start with -Dreactor.netty.http.server.accessLogEnabled=true for access log
fun main(args: Array<String>) {
    runApplication<WebfluxRefappApplication>(*args)
}

@SpringBootApplication
@Configuration
class WebfluxRefappApplication {

    @Bean
    fun userAgentWebClientCustomizer(@Value("\${spring.application.name}") name: String) =
        WebClientCustomizer {
            it.defaultHeader("User-Agent", name)
        }

    @Bean
    fun webclient(
        builder: WebClient.Builder
    ): WebClient {
        return builder
            .baseUrl("http://127.0.0.1:8080")
            .build()
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
        //return withContext(MDCContext()) {
            return withContext(TracingContextElement(ctx) + MDCContext()) {
            mapOf("authfoo" to "bar").also {
                logger.info { it }
            }
        }
    }

    @GetMapping("auth/bar")
    suspend fun authBar(): JsonNode? {

        // I do not
        return withContext(TracingContextElement(ctx) + MDCContext()) {
      //  return withContext(MDCContext()) {
            //User is set here
            logger.info { "Auth bar begin" }
            val result = client
                .get()
                .uri("/auth/foo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .retrieve()
                .bodyToMono<JsonNode>()
                .doOnNext { logger.info("Next") } // User is not here
                .awaitFirst()

            logger.info { "Auth bar ends" } // User is here
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
