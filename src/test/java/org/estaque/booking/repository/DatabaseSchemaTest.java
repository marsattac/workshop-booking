package org.estaque.booking.repository;

import org.estaque.booking.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the schema created by the Flyway migrations against a real PostgreSQL. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DatabaseSchemaTest {

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        jdbc.sql("INSERT INTO users (id, name) VALUES ('u1', 'Alice')").update();
        jdbc.sql("INSERT INTO workshops (id, title, seats_left) VALUES ('w1', 'Java 25', 1)").update();
    }

    @Test
    @DisplayName("sans profil dev : aucune donnée de démo")
    void loadsNoDemoData() {
        assertEquals(1, count("users"));
        assertEquals(1, count("workshops"));
        assertEquals(0, count("reservations"));
    }

    @Test
    @DisplayName("réservation : identifiant et date de création générés par la base")
    void generatesReservationIdAndCreationDate() {
        jdbc.sql("INSERT INTO reservations (user_id, workshop_id) VALUES ('u1', 'w1')").update();

        var generated = jdbc.sql("SELECT id IS NOT NULL AND created_at IS NOT NULL FROM reservations")
                .query(Boolean.class).single();
        assertTrue(generated);
    }

    @Test
    @DisplayName("seats_left ne peut pas devenir négatif")
    void rejectsNegativeSeatsLeft() {
        var update = jdbc.sql("UPDATE workshops SET seats_left = seats_left - 2 WHERE id = 'w1'");

        var error = assertThrows(DataIntegrityViolationException.class, update::update);
        assertTrue(error.getMessage().contains("workshops_seats_left_not_negative"));
    }

    @Test
    @DisplayName("un utilisateur ne réserve qu'une fois le même atelier")
    void rejectsDuplicateReservation() {
        var insert = "INSERT INTO reservations (user_id, workshop_id) VALUES ('u1', 'w1')";
        jdbc.sql(insert).update();

        var error = assertThrows(DuplicateKeyException.class, () -> jdbc.sql(insert).update());
        assertTrue(error.getMessage().contains("reservations_user_workshop_unique"));
    }

    @Test
    @DisplayName("une réservation référence un utilisateur existant")
    void rejectsReservationForUnknownUser() {
        var insert = jdbc.sql("INSERT INTO reservations (user_id, workshop_id) VALUES ('unknown', 'w1')");

        var error = assertThrows(DataIntegrityViolationException.class, insert::update);
        assertTrue(error.getMessage().contains("reservations_user_id_fkey"));
    }

    @Test
    @DisplayName("une réservation référence un atelier existant")
    void rejectsReservationForUnknownWorkshop() {
        var insert = jdbc.sql("INSERT INTO reservations (user_id, workshop_id) VALUES ('u1', 'unknown')");

        var error = assertThrows(DataIntegrityViolationException.class, insert::update);
        assertTrue(error.getMessage().contains("reservations_workshop_id_fkey"));
    }

    @Test
    @DisplayName("un utilisateur ayant des réservations ne peut pas être supprimé")
    void rejectsDeletingUserWithReservations() {
        jdbc.sql("INSERT INTO reservations (user_id, workshop_id) VALUES ('u1', 'w1')").update();

        var delete = jdbc.sql("DELETE FROM users WHERE id = 'u1'");

        assertThrows(DataIntegrityViolationException.class, delete::update);
    }

    private int count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }
}
