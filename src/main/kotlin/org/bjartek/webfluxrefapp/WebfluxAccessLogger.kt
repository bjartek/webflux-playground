package org.bjartek.webfluxrefapp

import mu.KotlinLogging
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.security.Principal
import java.time.Duration
import java.time.Instant
import kotlin.reflect.jvm.internal.impl.load.kotlin.JvmType

private val logger = KotlinLogging.logger{}

/**
 * You need a appender and a logger something like below to make the access log go into its own file
 *
 * <appender name="WebfluxAccessLogger" class="ch.qos.logback.core.FileAppender">
 *   <file>logs/access.log</file>
 *   <encoder>
 *      <pattern>%msg %X %n</pattern>
 *    </encoder>
 *  </appender>
 *
 *  <logger name="WebFluxAccessLogger" level="INFO" additivity="false">
 *    <appender-ref ref="WebfluxAccessLogger"/>
 *  </logger>
 *
 *  TODO: parameterize name of logger, parameterize a funcction on what request to skip logging for
 */
@Component
class AuroraAccessLogFilter() : WebFilter, Ordered {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        return  exchange.getPrincipal<Principal>().cast(Object::class.java).switchIfEmpty(Mono.just(Object()))
           .flatMap {
               val p: Principal? = if(it is Principal) it  else null
            filterWithPrincipal(exchange, chain, p)
        }


    }

    private fun filterWithPrincipal(exchange: ServerWebExchange, chain: WebFilterChain, principal: Principal?): Mono<Void> {

        //parameterize later
        if(exchange.request.localAddress?.port == 8081) {
            return chain.filter(exchange)
        }

        val started = Instant.now()

        exchange.response.beforeCommit {

            val request = exchange.request
            val response = exchange.response

            val accessLog = KotlinLogging.logger("WebfluxAccessLogger")
            val host= request.remoteAddress?.hostString ?: "-"

            val duration = Duration.between(started, Instant.now())

            val contentLength = 0
          //  val contentLength = response.headers.contentLength
            accessLog.info("""$host - ${principal?.name ?: "-"} [${started}] "${request.method} ${request.path} HTTP/1.1" ${response.statusCode?.value()} $contentLength duration=${duration.toMillis()}ms """)

            Mono.empty()

        }

        return chain.filter(exchange)
    }

    override fun getOrder() = Ordered.LOWEST_PRECEDENCE - 1
}
