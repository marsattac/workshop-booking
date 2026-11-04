package org.estaque.booking.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class LatencyConfiguration {

    /** No latency by default; set booking.db-latency to simulate some. */
    @Bean
    Latency latency(@Value("${booking.db-latency:0ms}") Duration dbLatency) {
        return dbLatency.isZero() ? Latency.NONE : Latency.of(dbLatency);
    }
}
