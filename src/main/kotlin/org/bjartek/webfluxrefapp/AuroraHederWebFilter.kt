package org.bjartek.webfluxrefapp

import brave.propagation.ExtraFieldPropagation
import mu.KotlinLogging
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.security.Principal
import java.time.Duration
import java.time.Instant
import java.util.*

const val USER_AGENT_FIELD = "User-Agent"
const val KORRELASJONSID_FIELD = "Korrelasjonsid"
const val MELDINGID_FIELD = "Meldingsid"
const val KLIENTID_FIELD = "Klientid"

@Component
class AuroraHeaderWebFilter() : WebFilter, Ordered {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        MDC.remove(USER_AGENT_FIELD);
        MDC.remove(KLIENTID_FIELD)
        MDC.remove(MELDINGID_FIELD)
        MDC.remove(KORRELASJONSID_FIELD)

        val headers = exchange.request.headers
        headers.setMDCAndExtraFieldOrGenerate(KORRELASJONSID_FIELD)

        // We use either KlientID or user-agent as klientid.
        (headers.getFirst(KLIENTID_FIELD) ?: headers.getFirst(USER_AGENT_FIELD))?.let {
            ExtraFieldPropagation.set(KLIENTID_FIELD, it)
            MDC.put(KLIENTID_FIELD, it)
        }

        headers.getFirst(USER_AGENT_FIELD)?.let {
            ExtraFieldPropagation.set(USER_AGENT_FIELD, it)
            MDC.put(USER_AGENT_FIELD, it)
        }
        headers.setMDCAndExtraFieldOrGenerate(MELDINGID_FIELD)

        return chain.filter(exchange)
    }

    override fun getOrder() = Ordered.LOWEST_PRECEDENCE - 2
}

fun HttpHeaders.setMDCAndExtraFieldOrGenerate(field:String) {
    val value = this.getFirst(field) ?: UUID.randomUUID().toString()
    ExtraFieldPropagation.set(field, value)
    MDC.put(field, value)
}
