package org.estaque.booking.event;

import org.estaque.booking.model.Reservation;

/** Remplace la publication sur l'event bus ("reservations.created"). */
@FunctionalInterface
public interface ReservationEvents {

    void created(Reservation reservation);
}
