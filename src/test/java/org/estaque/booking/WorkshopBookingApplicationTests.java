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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WorkshopBookingApplicationTests {

    @Autowired
    private ReservationService service;

    @Autowired
    private UserRepository users;

    @Autowired
    private WorkshopRepository workshops;

    @Autowired
    private ReservationRepository reservations;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void contextLoads() {
    }

    @Test
    void queriesPostgresThroughJdbcClient() {
        var version = jdbc.sql("SHOW server_version_num").query(Integer.class).single();

        assertEquals(18, version / 10_000);
    }

    @Test
    void reservesThroughSpringBeans() throws InterruptedException {
        users.save(new User("u-spring", "Alice"));
        workshops.save(new Workshop("w-spring", "Spring Boot", 1));

        var confirmed = assertInstanceOf(Confirmed.class, service.reserve("u-spring", "w-spring"));

        assertTrue(reservations.findAll().contains(confirmed.reservation()));
    }
}
