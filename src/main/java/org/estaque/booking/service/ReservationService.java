package org.estaque.booking.service;

import org.estaque.booking.event.ReservationEvents;
import org.estaque.booking.model.Reservation;
import org.estaque.booking.model.User;
import org.estaque.booking.model.Workshop;
import org.estaque.booking.repository.ReservationRepository;
import org.estaque.booking.repository.UserRepository;
import org.estaque.booking.repository.WorkshopRepository;
import org.estaque.booking.service.result.Confirmed;
import org.estaque.booking.service.result.Reason;
import org.estaque.booking.service.result.Rejected;
import org.estaque.booking.service.result.ReservationResult;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.StructuredTaskScope;

@Service
public class ReservationService {

    private final UserRepository users;
    private final WorkshopRepository workshops;
    private final ReservationRepository reservations;
    private final ReservationEvents events;

    private record Loaded(Optional<User> user, Optional<Workshop> workshop) {}

    public ReservationService(UserRepository users,
                              WorkshopRepository workshops,
                              ReservationRepository reservations,
                              ReservationEvents events) {
        this.users = users;
        this.workshops = workshops;
        this.reservations = reservations;
        this.events = events;
    }

    public ReservationResult reserve(String userId, String workshopId) throws InterruptedException {
        var loaded = load(userId, workshopId);

        if (loaded.user().isEmpty()) {
            return new Rejected(Reason.USER_NOT_FOUND);
        }
        var foundWorkshop = loaded.workshop();
        if (foundWorkshop.isEmpty()) {
            return new Rejected(Reason.WORKSHOP_NOT_FOUND);
        }
        if (foundWorkshop.get().seatsLeft() <= 0) {
            return new Rejected(Reason.FULL);
        }

        Reservation reservation;
        try {
            reservation = reservations.insert(userId, workshopId);
        } catch (DuplicateKeyException e) {
            // Unique constraint (user_id, workshop_id): the user has already booked this workshop.
            return new Rejected(Reason.ALREADY_RESERVED);
        }
        events.created(reservation);
        return new Confirmed(reservation);
    }

    /** Reservation {@code reservationId}, if it exists and belongs to workshop {@code workshopId}. */
    public Optional<Reservation> find(String workshopId, String reservationId) throws InterruptedException {
        return reservations.findById(reservationId)
                .filter(reservation -> reservation.workshopId().equals(workshopId));
    }

    private Loaded load(String userId, String workshopId) throws InterruptedException {
        // Both reads run in parallel, each on its own virtual thread;
        // join() waits for both and, if one fails, cancels the other.
        try (var scope = StructuredTaskScope.open()) {
            var user = scope.fork(() -> users.findById(userId));
            var workshop = scope.fork(() -> workshops.findById(workshopId));
            scope.join();
            return new Loaded(user.get(), workshop.get());
        } catch (StructuredTaskScope.FailedException e) {
            switch (e.getCause()) {
                case RuntimeException re -> throw re;
                case Error err -> throw err;
                case InterruptedException ie -> throw ie;
                case Throwable t -> throw new IllegalStateException(t);
            }
        }
    }
}
