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
     * Reads the seats left and locks the workshop row until the end of the current transaction
     * (pessimistic lock, {@code SELECT ... FOR UPDATE}).
     *
     * <p>Must be called inside a transaction: concurrent reservations of the same workshop wait here
     * for the lock, so the value returned cannot change before {@link #updateSeatsLeft} is called.
     *
     * @return the seats left, or empty if the workshop does not exist
     */
    public Optional<Integer> lockSeatsLeft(String id) {
        return jdbc.sql("SELECT seats_left FROM workshops WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(Integer.class)
                .optional();
    }

    /** Sets the seats left; call it under the lock taken by {@link #lockSeatsLeft}. */
    public void updateSeatsLeft(String id, int seatsLeft) {
        jdbc.sql("UPDATE workshops SET seats_left = :seatsLeft WHERE id = :id")
                .param("id", id)
                .param("seatsLeft", seatsLeft)
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
