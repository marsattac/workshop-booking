package org.estaque.booking;

import org.estaque.booking.model.User;
import org.estaque.booking.model.Workshop;
import org.estaque.booking.repository.ReservationRepository;
import org.estaque.booking.repository.UserRepository;
import org.estaque.booking.repository.WorkshopRepository;
import org.estaque.booking.service.ReservationService;
import org.estaque.booking.service.result.Confirmed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class WorkshopBookingApplicationTests {

    @Autowired
    private ReservationService service;

    @Autowired
    private UserRepository users;

    @Autowired
    private WorkshopRepository workshops;

    @Autowired
    private ReservationRepository reservations;

    @Test
    void contextLoads() {
    }

    @Test
    void reservesThroughSpringBeans() throws InterruptedException {
        users.save(new User("u-spring", "Alice"));
        workshops.save(new Workshop("w-spring", "Spring Boot", 1));

        var confirmed = assertInstanceOf(Confirmed.class, service.reserve("u-spring", "w-spring"));

        assertTrue(reservations.findAll().contains(confirmed.reservation()));
    }
}
