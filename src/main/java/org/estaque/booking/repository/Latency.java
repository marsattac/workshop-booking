package org.estaque.booking.repository;

import java.time.Duration;

/** Simule le temps d'accès à la base de données. */
@FunctionalInterface
public interface Latency {

    Latency NONE = () -> {};

    void simulate() throws InterruptedException;

    static Latency of(Duration duration) {
        return () -> Thread.sleep(duration);
    }
}
