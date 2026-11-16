package org.estaque.booking.repository;

/** Outcome of {@link WorkshopRepository#takeSeat}. */
public enum SeatTaking {
    /** A seat was taken. */
    TAKEN,
    /** The workshop exists but has no seat left. */
    FULL,
    /** The workshop does not exist, for instance because it was deleted after being read. */
    WORKSHOP_NOT_FOUND
}
