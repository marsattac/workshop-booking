package org.estaque.booking.repository;

import org.estaque.booking.context.RequestContext;
import org.estaque.booking.model.Workshop;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class WorkshopRepository {

    private final JdbcClient jdbc;
    private final Latency latency;

    public WorkshopRepository(JdbcClient jdbc, Latency latency) {
        this.jdbc = jdbc;
        this.latency = latency;
    }

    /** Creates the workshop, or replaces it if it already exists. */
    public void save(Workshop workshop) {
        jdbc.sql("""
                        INSERT INTO workshops (id, title, seats_left) VALUES (:id, :title, :seatsLeft)
                        ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, seats_left = EXCLUDED.seats_left
                        """)
                .param("id", workshop.id())
                .param("title", workshop.title())
                .param("seatsLeft", workshop.seatsLeft())
                .update();
    }

    public Optional<Workshop> findById(String id) throws InterruptedException {
        RequestContext.log("findById " + id);
        latency.simulate();
        return jdbc.sql("SELECT id, title, seats_left FROM workshops WHERE id = :id")
                .param("id", id)
                .query(Workshop.class)
                .optional();
    }
}
