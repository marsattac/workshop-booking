package org.estaque.booking.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class LatencyConfiguration {

    @Bean
    Latency latency(@Value("${booking.db-latency}") Duration dbLatency) {
        return Latency.of(dbLatency);
    }
}
