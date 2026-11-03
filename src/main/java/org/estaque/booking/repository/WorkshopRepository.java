package org.estaque.booking.repository;

import org.estaque.booking.context.RequestContext;
import org.estaque.booking.model.Workshop;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class WorkshopRepository {

    private final Map<String, Workshop> workshops = new ConcurrentHashMap<>();
    private final Latency latency;

    public WorkshopRepository(Latency latency) {
        this.latency = latency;
    }

    public void save(Workshop workshop) {
        workshops.put(workshop.id(), workshop);
    }

    public Optional<Workshop> findById(String id) throws InterruptedException {
        RequestContext.log("findById " + id);
        latency.simulate();
        return Optional.ofNullable(workshops.get(id));
    }
}
