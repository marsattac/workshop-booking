package org.estaque.booking.repository;

import org.estaque.booking.context.RequestContext;
import org.estaque.booking.model.User;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class UserRepository {

    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Latency latency;

    public UserRepository(Latency latency) {
        this.latency = latency;
    }

    public void save(User user) {
        users.put(user.id(), user);
    }

    public Optional<User> findById(String id) throws InterruptedException {
        RequestContext.log("findById " + id);
        latency.simulate();
        return Optional.ofNullable(users.get(id));
    }
}
