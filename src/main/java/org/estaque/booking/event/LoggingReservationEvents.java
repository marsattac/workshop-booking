package org.estaque.booking.event;

import org.estaque.booking.context.RequestContext;
import org.estaque.booking.model.Reservation;
import org.springframework.stereotype.Component;

/** Logs created reservations until a real event bus is in place. */
@Component
public class LoggingReservationEvents implements ReservationEvents {

    @Override
    public void created(Reservation reservation) {
        RequestContext.log("reservations.created " + reservation.id());
    }
}
