package org.estaque.booking.service;

import org.estaque.booking.context.RequestContext;
import org.estaque.booking.event.ReservationEvents;
import org.estaque.booking.model.User;
import org.estaque.booking.model.Workshop;
import org.estaque.booking.repository.ReservationRepository;
import org.estaque.booking.repository.UserRepository;
import org.estaque.booking.repository.WorkshopRepository;
import org.estaque.booking.service.result.Confirmed;
import org.estaque.booking.service.result.Reason;
import org.estaque.booking.service.result.Rejected;
import org.estaque.booking.service.result.ReservationResult;

import java.util.Optional;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.atomic.AtomicLong;

public final class ReservationService {

    private final UserRepository users;
    private final WorkshopRepository workshops;
    private final ReservationRepository reservations;
    private final ReservationEvents events;
    private final AtomicLong requestSequence = new AtomicLong();

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
        var requestId = "req-" + requestSequence.incrementAndGet();
        return ScopedValue.where(RequestContext.REQUEST_ID, requestId)
                .call(() -> doReserve(userId, workshopId));
    }

    private ReservationResult doReserve(String userId, String workshopId) throws InterruptedException {
        Subtask<Optional<User>> user;
        Subtask<Optional<Workshop>> workshop;
        // Les deux lectures partent en parallèle, chacune sur son virtual thread ;
        // join() attend la fin des deux et, si l'une échoue, annule l'autre.
        try (var scope = StructuredTaskScope.open()) {
            user = scope.fork(() -> users.findById(userId));
            workshop = scope.fork(() -> workshops.findById(workshopId));
            scope.join();
        } catch (StructuredTaskScope.FailedException e) {
            switch (e.getCause()) {
                case RuntimeException re -> throw re;
                case Error err -> throw err;
                case InterruptedException ie -> throw ie;
                case Throwable t -> throw new IllegalStateException(t);
            }
        }

        if (user.get().isEmpty()) {
            return new Rejected(Reason.USER_NOT_FOUND);
        }
        var foundWorkshop = workshop.get();
        if (foundWorkshop.isEmpty()) {
            return new Rejected(Reason.WORKSHOP_NOT_FOUND);
        }
        if (foundWorkshop.get().seatsLeft() <= 0) {
            return new Rejected(Reason.FULL);
        }

        var reservation = reservations.insert(userId, workshopId);
        events.created(reservation);
        return new Confirmed(reservation);
    }
}
