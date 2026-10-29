package org.estaque.booking.service.result;

public sealed interface ReservationResult permits Confirmed, Rejected {}
