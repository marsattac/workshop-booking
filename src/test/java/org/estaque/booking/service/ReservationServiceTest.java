package org.estaque.booking.service;

import org.estaque.booking.model.Reservation;
import org.estaque.booking.model.User;
import org.estaque.booking.model.Workshop;
import org.estaque.booking.repository.Latency;
import org.estaque.booking.repository.ReservationRepository;
import org.estaque.booking.repository.UserRepository;
import org.estaque.booking.repository.WorkshopRepository;
import org.estaque.booking.service.result.Confirmed;
import org.estaque.booking.service.result.Reason;
import org.estaque.booking.service.result.Rejected;
import org.estaque.booking.service.result.ReservationResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ReservationServiceTest {

    private static final Duration DB_LATENCY = Duration.ofMillis(200);

    private UserRepository users;
    private WorkshopRepository workshops;
    private ReservationRepository reservations;
    private final List<Reservation> published = new CopyOnWriteArrayList<>();
    private ReservationService service;

    @BeforeEach
    void setUp() {
        var latency = Latency.of(DB_LATENCY);
        users = new UserRepository(latency);
        workshops = new WorkshopRepository(latency);
        reservations = new ReservationRepository(Latency.NONE);
        service = new ReservationService(users, workshops, reservations, published::add);

        users.save(new User("u1", "Alice"));
        workshops.save(new Workshop("w1", "Java 25", 3));
        workshops.save(new Workshop("w-full", "Complet", 0));
    }

    @Test
    @DisplayName("cas nominal : la réservation est confirmée, enregistrée et publiée")
    void confirmsReservation() throws InterruptedException {
        var result = service.reserve("u1", "w1");

        var confirmed = assertInstanceOf(Confirmed.class, result);
        var reservation = confirmed.reservation();
        assertEquals("u1", reservation.userId());
        assertEquals("w1", reservation.workshopId());
        assertNotNull(reservation.id());
        assertEquals(List.of(reservation), reservations.findAll());
        assertEquals(List.of(reservation), published);
    }

    @Test
    @DisplayName("utilisateur inconnu : USER_NOT_FOUND")
    void rejectsUnknownUser() throws InterruptedException {
        assertRejected(Reason.USER_NOT_FOUND, service.reserve("unknown", "w1"));
    }

    @Test
    @DisplayName("atelier inconnu : WORKSHOP_NOT_FOUND")
    void rejectsUnknownWorkshop() throws InterruptedException {
        assertRejected(Reason.WORKSHOP_NOT_FOUND, service.reserve("u1", "unknown"));
    }

    @Test
    @DisplayName("utilisateur et atelier inconnus : l'utilisateur est vérifié en premier")
    void userCheckTakesPrecedence() throws InterruptedException {
        assertRejected(Reason.USER_NOT_FOUND, service.reserve("unknown", "unknown"));
    }

    @Test
    @DisplayName("atelier complet : FULL")
    void rejectsFullWorkshop() throws InterruptedException {
        assertRejected(Reason.FULL, service.reserve("u1", "w-full"));
    }

    @Test
    @DisplayName("utilisateur et atelier sont chargés en parallèle")
    void loadsUserAndWorkshopInParallel() throws InterruptedException {
        long start = System.nanoTime();
        service.reserve("u1", "w1");
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        // En séquentiel on attendrait au moins 2 x 200 ms.
        assertTrue(elapsed.compareTo(DB_LATENCY.multipliedBy(2)) < 0,
                () -> "chargement non parallèle : " + elapsed.toMillis() + " ms");
    }

    @Test
    @DisplayName("les lectures s'exécutent sur des virtual threads")
    void loadsOnVirtualThreads() throws InterruptedException {
        var threads = new CopyOnWriteArrayList<Thread>();
        Latency recordThread = () -> threads.add(Thread.currentThread());
        var spiedUsers = new UserRepository(recordThread);
        var spiedWorkshops = new WorkshopRepository(recordThread);
        spiedUsers.save(new User("u1", "Alice"));
        spiedWorkshops.save(new Workshop("w1", "Java 25", 3));

        new ReservationService(spiedUsers, spiedWorkshops, reservations, published::add).reserve("u1", "w1");

        assertEquals(2, threads.size());
        assertTrue(threads.stream().allMatch(Thread::isVirtual));
        assertNotEquals(threads.get(0), threads.get(1));
    }

    private void assertRejected(Reason expected, ReservationResult result) {
        var rejected = assertInstanceOf(Rejected.class, result);
        assertEquals(expected, rejected.reason());
        assertTrue(reservations.findAll().isEmpty(), "aucune réservation ne doit être créée");
        assertTrue(published.isEmpty(), "aucun événement ne doit être publié");
    }
}
