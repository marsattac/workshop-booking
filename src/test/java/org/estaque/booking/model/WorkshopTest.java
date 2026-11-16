package org.estaque.booking.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkshopTest {

    @Test
    @DisplayName("nouvel atelier : toutes les places sont libres")
    void newWorkshopHasAllSeatsLeft() {
        assertEquals(new Workshop("w1", "Java 25", 20, 20), new Workshop("w1", "Java 25", 20));
    }

    @Test
    @DisplayName("seatsLeft doit rester entre 0 et la capacité")
    void rejectsSeatsLeftOutsideCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new Workshop("w1", "Java 25", 20, -1));
        assertThrows(IllegalArgumentException.class, () -> new Workshop("w1", "Java 25", 20, 21));
    }
}
