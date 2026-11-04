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

    /**
     * Takes a seat if the workshop still has one.
     *
     * <p>Business rule enforced in SQL: a workshop can only be booked while at least one seat is left.
     * PostgreSQL evaluates {@code seats_left > 0} while it holds the row lock, so two concurrent
     * reservations cannot both take the last seat. Checking {@link Workshop#seatsLeft()} in Java
     * and then decrementing would let both through.
     * The schema's CHECK (seats_left >= 0) constraint remains the last line of defense.
     *
     * @return {@code true} if a seat was taken, {@code false} if the workshop is full or does not exist
     */
    public boolean takeSeat(String id) {
        return jdbc.sql("""
                        UPDATE workshops SET seats_left = seats_left - 1
                        WHERE id = :id AND seats_left > 0
                        """)
                .param("id", id)
                .update() == 1;
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
