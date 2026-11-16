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
                        INSERT INTO workshops (id, title, capacity, seats_left)
                        VALUES (:id, :title, :capacity, :seatsLeft)
                        ON CONFLICT (id) DO UPDATE
                        SET title = EXCLUDED.title, capacity = EXCLUDED.capacity, seats_left = EXCLUDED.seats_left
                        """)
                .param("id", workshop.id())
                .param("title", workshop.title())
                .param("capacity", workshop.capacity())
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
     * The schema's CHECK (seats_left BETWEEN 0 AND capacity) constraint remains the last line of defense.
     *
     * <p>When no row is updated, a second query tells a full workshop from a missing one: the workshop
     * may have been deleted since it was read. That query runs after the {@code UPDATE}, which waits
     * for any concurrent deletion of the row to end, so it sees that deletion once committed.
     *
     * @return {@link SeatTaking#TAKEN} if a seat was taken, {@link SeatTaking#FULL} if the workshop
     *         has no seat left, {@link SeatTaking#WORKSHOP_NOT_FOUND} if it does not exist
     */
    public SeatTaking takeSeat(String id) {
        var updated = jdbc.sql("""
                        UPDATE workshops SET seats_left = seats_left - 1
                        WHERE id = :id AND seats_left > 0
                        """)
                .param("id", id)
                .update();
        if (updated == 1) {
            return SeatTaking.TAKEN;
        }
        return exists(id) ? SeatTaking.FULL : SeatTaking.WORKSHOP_NOT_FOUND;
    }

    private boolean exists(String id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM workshops WHERE id = :id)")
                .param("id", id)
                .query(Boolean.class)
                .single();
    }

    public Optional<Workshop> findById(String id) throws InterruptedException {
        RequestContext.log("findById " + id);
        latency.simulate();
        return jdbc.sql("SELECT id, title, capacity, seats_left FROM workshops WHERE id = :id")
                .param("id", id)
                .query(Workshop.class)
                .optional();
    }
}
