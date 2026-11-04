package org.estaque.booking.event;

import org.estaque.booking.model.Reservation;

/** Stands in for publishing to the event bus ("reservations.created"). */
@FunctionalInterface
public interface ReservationEvents {

    void created(Reservation reservation);
}
