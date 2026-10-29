package org.estaque.booking.service.result;

import org.estaque.booking.model.Reservation;

public record Confirmed(Reservation reservation) implements ReservationResult {}
