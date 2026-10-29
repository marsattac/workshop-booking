package org.estaque.booking.repository;

import org.estaque.booking.model.Reservation;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReservationRepository {

    private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();
    private final Latency latency;

    public ReservationRepository(Latency latency) {
        this.latency = latency;
    }

    public Reservation insert(String userId, String workshopId) throws InterruptedException {
        latency.simulate();
        var reservation = new Reservation(UUID.randomUUID().toString(), userId, workshopId);
        reservations.put(reservation.id(), reservation);
        return reservation;
    }

    public List<Reservation> findAll() {
        return List.copyOf(reservations.values());
    }
}
