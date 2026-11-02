package org.estaque.booking.api;

import jakarta.validation.constraints.NotBlank;

public record ReservationRequest(@NotBlank String userId) {}
