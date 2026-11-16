package org.estaque.booking.model;

/**
 * A workshop with {@code capacity} seats in total, {@code seatsLeft} of which are still free.
 */
public record Workshop(String id, String title, int capacity, int seatsLeft) {

    public Workshop {
        if (seatsLeft < 0 || seatsLeft > capacity) {
            throw new IllegalArgumentException(
                    "seatsLeft must be between 0 and capacity (" + capacity + "): " + seatsLeft);
        }
    }

    /** A workshop that nobody has booked yet: all its seats are free. */
    public Workshop(String id, String title, int capacity) {
        this(id, title, capacity, capacity);
    }
}
