package org.bjartek.webfluxrefapp;
/*
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.NamedContributors;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
public class ActuatorHealthPrometheusMetrics{

    private final List<String> statuses;
    private final Map<String, Double> statusMap;

    public ActuatorHealthPrometheusMetrics(
     @Value("${management.endpoint.health.status.order:DOWN, OUT_OF_SERVICE, FATAL, UNKNOWN, UP}") String order
    ) {
        this.statuses = Arrays.asList(order.split(", "));

        //    val statuses: Map<String, Double> = order.split(", ").reversed().mapIndexed { index, s ->
        //        s to index.toDouble()
        //    }.toMap()
        int max = statuses.size();
        this.statusMap = IntStream.range(0, max)
            .boxed().collect(Collectors.toMap(this.statuses::get, i -> (double) (max - i)));

    }
    /*
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
     */

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> healthRegistrySinelHealthCustomizer(HealthContributorRegistry healthContributorRegistry) {

        return (MeterRegistryCustomizer) registry -> {
            Gauge.builder("health", healthContributorRegistry, health ->


            ).description("Aggregated status for all health checks. Value is modeled after unix exit codes. 0=OK, the higher the more servere.")
                    .register(registry);

        };
    }


    private Set<Status> findAllStatuses(NamedContributors<HealthContributor> registry) {

        registry.stream().flatMap( healthContributor -> {
            var c = healthContributor.getContributor();

            if(c instanceof HealthIndicator) {
                Set.of(((HealthIndicator) c).health().getStatus());
            }


            }
        )
    }

    /*
       Find all health indicator statuses, join on "." for composite contributors
    fun NamedContributors<HealthContributor>.findAllStatuses() : Set<Status> {
        return this.flatMap {
            when(val c = it.contributor) {
                is HealthIndicator -> setOf(c.health().status)
                is CompositeHealthContributor -> c.findAllStatuses()
                else -> emptySet()
            }
        }.toSet()
    }
      */



}
*/
