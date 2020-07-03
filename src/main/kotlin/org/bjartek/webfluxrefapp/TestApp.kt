package org.bjartek.webfluxrefapp

import brave.baggage.BaggageField
import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import no.skatteetaten.aurora.webflux.AuroraHeaderWebFilter.KLIENTID_FIELD
import no.skatteetaten.aurora.webflux.AuroraHeaderWebFilter.KORRELASJONSID_FIELD
import no.skatteetaten.aurora.webflux.AuroraHeaderWebFilter.MELDINGID_FIELD
import org.slf4j.MDC
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpHeaders.USER_AGENT
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

// Default profile will connect to zipkin (run docker-compose to start local zipkin)
// Start with no-zipkin profile to disable zipkin integration
@SpringBootApplication
class TestMain

fun main(args: Array<String>) {
    SpringApplication.run(TestMain::class.java, *args)
}

private val logger = KotlinLogging.logger {}

@RestController
class TestController(private val webClient: WebClient) {

    @GetMapping()
    fun get(): Mono<Map<String, Any>> {
        val korrelasjonsid = BaggageField.getByName(KORRELASJONSID_FIELD).value
        checkNotNull(korrelasjonsid)
        check(korrelasjonsid == MDC.get(KORRELASJONSID_FIELD))
        logger.info("Get request received")

        return webClient.get().uri("/headers").retrieve().bodyToMono<Map<String, String>>().map {
            mapOf(
                "Korrelasjonsid fra WebFilter" to korrelasjonsid,
                "Request headers fra WebClient" to it
            )
        }
    }

    @GetMapping("/auth/suspended")
    suspend fun getSuspended() = get().awaitSingle()

    @GetMapping("/headers")
    fun getHeaders(@RequestHeader headers: HttpHeaders): Map<String, String> {
        logger.info("Get headers request")
        checkNotNull(headers[KORRELASJONSID_FIELD])
        checkNotNull(headers[MELDINGID_FIELD])
        checkNotNull(headers[KLIENTID_FIELD])
        checkNotNull(headers[USER_AGENT])
        return headers.toSingleValueMap()
    }
}