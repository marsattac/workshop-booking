package org.estaque.booking.service;

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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ReservationService {

    private final UserRepository users;
    private final WorkshopRepository workshops;
    private final ReservationRepository reservations;
    private final ReservationEvents events;

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
        Future<Optional<User>> user;
        Future<Optional<Workshop>> workshop;
        // Les deux lectures partent en parallèle, chacune sur son virtual thread ;
        // la fermeture de l'executor attend la fin des deux.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            user = executor.submit(() -> users.findById(userId));
            workshop = executor.submit(() -> workshops.findById(workshopId));
        }

        if (await(user).isEmpty()) {
            return new Rejected(Reason.USER_NOT_FOUND);
        }
        var foundWorkshop = await(workshop);
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

    private static <T> T await(Future<T> future) throws InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            switch (e.getCause()) {
                case RuntimeException re -> throw re;
                case Error err -> throw err;
                case InterruptedException ie -> throw ie;
                case Throwable t -> throw new IllegalStateException(t);
            }
        }
    }
}
