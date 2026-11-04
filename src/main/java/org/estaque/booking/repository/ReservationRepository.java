package org.estaque.booking.repository;

import org.estaque.booking.model.Reservation;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReservationRepository {

    // La colonne id est un UUID ; le modèle le manipule en String.
    private static final RowMapper<Reservation> RESERVATION = (rs, _) ->
            new Reservation(rs.getString("id"), rs.getString("user_id"), rs.getString("workshop_id"));

    private final JdbcClient jdbc;
    private final Latency latency;

    public ReservationRepository(JdbcClient jdbc, Latency latency) {
        this.jdbc = jdbc;
        this.latency = latency;
    }

    /** Enregistre la réservation ; son identifiant est généré par la base. */
    public Reservation insert(String userId, String workshopId) throws InterruptedException {
        latency.simulate();
        return jdbc.sql("""
                        INSERT INTO reservations (user_id, workshop_id) VALUES (:userId, :workshopId)
                        RETURNING id, user_id, workshop_id
                        """)
                .param("userId", userId)
                .param("workshopId", workshopId)
                .query(RESERVATION)
                .single();
    }

    public Optional<Reservation> findById(String id) throws InterruptedException {
        // Un identifiant qui n'est pas un UUID ne peut désigner aucune réservation.
        var uuid = parseUuid(id);
        if (uuid.isEmpty()) {
            return Optional.empty();
        }
        latency.simulate();
        return jdbc.sql("SELECT id, user_id, workshop_id FROM reservations WHERE id = :id")
                .param("id", uuid.get())
                .query(RESERVATION)
                .optional();
    }

    public List<Reservation> findAll() {
        return jdbc.sql("SELECT id, user_id, workshop_id FROM reservations ORDER BY created_at")
                .query(RESERVATION)
                .list();
    }

    private static Optional<UUID> parseUuid(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
