package org.estaque.booking.repository;

import org.estaque.booking.model.Workshop;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class WorkshopRepository {

    private final Map<String, Workshop> workshops = new ConcurrentHashMap<>();
    private final Latency latency;

    public WorkshopRepository(Latency latency) {
        this.latency = latency;
    }

    public void save(Workshop workshop) {
        workshops.put(workshop.id(), workshop);
    }

    public Optional<Workshop> findById(String id) throws InterruptedException {
        latency.simulate();
        return Optional.ofNullable(workshops.get(id));
    }
}
