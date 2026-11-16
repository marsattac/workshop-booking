package org.estaque.booking;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Throwaway PostgreSQL for tests that start a Spring context.
 *
 * <p>Test classes with different configurations (mocked beans, {@code @JdbcTest}, MockMvc...) get
 * different Spring contexts. Each context would create its own container from a plain {@code @Bean};
 * the static field makes all of them share one: Spring starts it once ({@code start()} does nothing
 * on a running container) and every context connects to it.
 *
 * <p>Spring stops the container when the first context using it closes. Cached test contexts only
 * close when the JVM shuts down, so the container lives for the whole test run.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Same image as compose.yaml.
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return POSTGRES;
    }
}
