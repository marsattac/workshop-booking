package org.estaque.booking.service.result;

public record Rejected(Reason reason) implements ReservationResult {}
