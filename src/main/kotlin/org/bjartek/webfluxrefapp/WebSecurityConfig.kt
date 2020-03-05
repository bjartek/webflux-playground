package org.bjartek.webfluxrefapp


import brave.propagation.ExtraFieldPropagation
import org.slf4j.MDC
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.context.ServerSecurityContextRepository
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
class WebSecurityConfig(
    val authenticationManager: AuthManager,
    val securityContextRepo: SecurityContextRepository
) {

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf().disable()
            .formLogin().disable()
            .httpBasic().disable()
            .authenticationManager(authenticationManager)
            .securityContextRepository(securityContextRepo)
            .authorizeExchange()
            .pathMatchers("/auth/**").authenticated()
            .anyExchange().permitAll()
            .and().build();
    }
}


@Component
class SecurityContextRepository(
    val authenticationManager: ReactiveAuthenticationManager
) : ServerSecurityContextRepository {

    override fun save(exchange: ServerWebExchange?, context: SecurityContext?): Mono<Void> {
        throw UnsupportedOperationException("Not supported yet.");
    }

    override fun load(exchange: ServerWebExchange?): Mono<SecurityContext> {
        MDC.remove("User");
        MDC.remove("User-Agent");

        val headers = exchange
            ?.request
            ?.headers

        val auth = UsernamePasswordAuthenticationToken("Bearer", headers
            ?.getFirst(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.startsWith("Bearer ") }
            ?.substring(7))

        return authenticationManager.authenticate(auth).map {
            ExtraFieldPropagation.set("User", it.principal.toString())
            MDC.put("User", it.principal.toString())
            headers?.getFirst("User-Agent")?.let { agent ->
                ExtraFieldPropagation.set("User-Agent", agent)
                MDC.put("User-Agent", agent)
            }
            SecurityContextImpl(it)
        }
    }
}

@Component
class AuthManager : ReactiveAuthenticationManager {
    override fun authenticate(authentication: Authentication?): Mono<Authentication> {

        val res = authentication?.credentials?.let {
            UsernamePasswordAuthenticationToken(
                it, it, listOf(
                    SimpleGrantedAuthority("USER")
                )
            )
        }

        return Mono.justOrEmpty(res)
    }
}

