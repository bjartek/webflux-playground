package org.bjartek.webfluxrefapp

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.reactive.awaitFirst
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono


@SpringBootApplication
@Configuration
class WebfluxRefappApplication {

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

    @GetMapping("foo")
    suspend fun foo(): Map<String, String> {
        return mapOf("foo" to "bar").also {
            logger.info { it }
        }
    }

    @GetMapping("/bar")
    suspend fun bar(): JsonNode? {
        logger.info { "bar" }
        val result = client
                .get()
                .uri("/foo")
                .retrieve()
                .bodyToMono<JsonNode>()
                .log()
                .awaitFirst()

        logger.info { "bar" }
        return result
    }
}

