package org.estaque.booking.service;

import org.estaque.booking.event.ReservationEvents;
import org.estaque.booking.model.Reservation;
import org.estaque.booking.repository.ReservationRepository;
import org.estaque.booking.repository.UserRepository;
import org.estaque.booking.repository.WorkshopRepository;
import org.estaque.booking.service.result.Confirmed;
import org.estaque.booking.service.result.Reason;
import org.estaque.booking.service.result.Rejected;
import org.estaque.booking.service.result.ReservationResult;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionOperations;

import java.util.Optional;

@Service
public class ReservationService {

    private final UserRepository users;
    private final WorkshopRepository workshops;
    private final ReservationRepository reservations;
    private final ReservationEvents events;
    private final TransactionOperations transactions;

    public ReservationService(UserRepository users,
                              WorkshopRepository workshops,
                              ReservationRepository reservations,
                              ReservationEvents events,
                              TransactionOperations transactions) {
        this.users = users;
        this.workshops = workshops;
        this.reservations = reservations;
        this.events = events;
        this.transactions = transactions;
    }

    public ReservationResult reserve(String userId, String workshopId) throws InterruptedException {
        if (users.findById(userId).isEmpty()) {
            return new Rejected(Reason.USER_NOT_FOUND);
        }
        if (workshops.findById(workshopId).isEmpty()) {
            return new Rejected(Reason.WORKSHOP_NOT_FOUND);
        }
        // No check on seatsLeft here: the value read may already be stale. The "a seat is left"
        // rule is enforced by the SQL in WorkshopRepository.takeSeat.

        var result = book(userId, workshopId);
        if (result instanceof Confirmed(var reservation)) {
            // Published once the transaction has committed, never for a rolled-back reservation.
            events.created(reservation);
        }
        return result;
    }

    /**
     * Takes the seat, then saves the reservation, in a single transaction: one never happens without the other.
     *
     * <p>The transaction only covers these two writes, not the reads that come before them:
     * it holds a connection and the workshop row lock for its whole duration, so it is kept short.
     */
    private ReservationResult book(String userId, String workshopId) {
        return transactions.execute(status -> switch (workshops.takeSeat(workshopId)) {
            case TAKEN -> insert(userId, workshopId, status);
            case FULL -> new Rejected(Reason.FULL);
            // Deleted between its read and takeSeat(): the workshop is missing, not full.
            case WORKSHOP_NOT_FOUND -> new Rejected(Reason.WORKSHOP_NOT_FOUND);
        });
    }

    private ReservationResult insert(String userId, String workshopId, TransactionStatus status) {
        try {
            return new Confirmed(reservations.insert(userId, workshopId));
        } catch (DuplicateKeyException e) {
            // Unique constraint (user_id, workshop_id): the user has already booked this workshop.
            // Give back the seat taken just before.
            status.setRollbackOnly();
            return new Rejected(Reason.ALREADY_RESERVED);
        }
    }

    /** Reservation {@code reservationId}, if it exists and belongs to workshop {@code workshopId}. */
    public Optional<Reservation> find(String workshopId, String reservationId) {
        return reservations.findById(reservationId)
                .filter(reservation -> reservation.workshopId().equals(workshopId));
    }
}
