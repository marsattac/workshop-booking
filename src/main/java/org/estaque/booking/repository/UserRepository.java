package org.estaque.booking.repository;

import org.estaque.booking.context.RequestContext;
import org.estaque.booking.model.User;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcClient jdbc;
    private final Latency latency;

    public UserRepository(JdbcClient jdbc, Latency latency) {
        this.jdbc = jdbc;
        this.latency = latency;
    }

    /** Crée l'utilisateur, ou le remplace s'il existe déjà. */
    public void save(User user) {
        jdbc.sql("""
                        INSERT INTO users (id, name) VALUES (:id, :name)
                        ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name
                        """)
                .param("id", user.id())
                .param("name", user.name())
                .update();
    }

    public Optional<User> findById(String id) throws InterruptedException {
        RequestContext.log("findById " + id);
        latency.simulate();
        return jdbc.sql("SELECT id, name FROM users WHERE id = :id")
                .param("id", id)
                .query(User.class)
                .optional();
    }
}
