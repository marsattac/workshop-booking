package org.estaque.booking.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class LatencyConfiguration {

    /** Aucune latence par défaut ; booking.db-latency permet d'en simuler une. */
    @Bean
    Latency latency(@Value("${booking.db-latency:0ms}") Duration dbLatency) {
        return dbLatency.isZero() ? Latency.NONE : Latency.of(dbLatency);
    }
}
