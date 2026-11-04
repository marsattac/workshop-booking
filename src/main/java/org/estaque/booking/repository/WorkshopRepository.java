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

    /** Seats left in a workshop, with the version they were read at. */
    public record Seats(int seatsLeft, long version) {}

    /** Reads the seats left and their version, without locking anything. */
    public Optional<Seats> findSeats(String id) {
        return jdbc.sql("SELECT seats_left, version FROM workshops WHERE id = :id")
                .param("id", id)
                .query(Seats.class)
                .optional();
    }

    /**
     * Takes a seat if nobody updated the workshop since {@code seats} was read (optimistic lock).
     *
     * <p>The "a seat is left" rule is checked in Java by the caller on {@code seats}; the version in
     * the WHERE clause guarantees that this value is still current when the update applies.
     * The schema's CHECK (seats_left >= 0) constraint remains the last line of defense.
     *
     * @return {@code true} if a seat was taken, {@code false} if the version changed in the meantime
     */
    public boolean takeSeat(String id, Seats seats) {
        return jdbc.sql("""
                        UPDATE workshops SET seats_left = seats_left - 1, version = version + 1
                        WHERE id = :id AND version = :version
                        """)
                .param("id", id)
                .param("version", seats.version())
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
