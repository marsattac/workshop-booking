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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
        reservations = new ReservationRepository(latency);
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

        // En séquentiel on attendrait au moins 3 x 200 ms (2 lectures de user et de workshop et 1 insertion de la réservation).
        assertTrue(elapsed.compareTo(DB_LATENCY.multipliedBy(3)) < 0,
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

    @Test
    @DisplayName("1000 réservations simultanées pour 100 places : 100 confirmées, 900 refusées (FULL)")
    void handlesThousandConcurrentReservations() throws Exception {
        int count = 1000;
        int seats = 100;
        workshops.save(new Workshop("w-big", "Grand amphi", seats));

        long start = System.nanoTime();
        List<Future<ReservationResult>> results = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < count; i++) {
                results.add(executor.submit(() -> service.reserve("u1", "w-big")));
            }
        }
        var elapsed = Duration.ofNanos(System.nanoTime() - start);
        System.out.printf("%d réservations simultanées en %d ms%n", count, elapsed.toMillis());

        int confirmed = 0;
        int full = 0;
        for (var result : results) {
            switch (result.get()) {
                case Confirmed _ -> confirmed++;
                case Rejected(var reason) when reason == Reason.FULL -> full++;
                case Rejected rejected -> fail("refus inattendu : " + rejected);
            }
        }
        assertEquals(seats, confirmed, "réservations confirmées");
        assertEquals(count - seats, full, "réservations refusées (FULL)");
        assertEquals(seats, reservations.findAll().size());
        assertEquals(seats, published.size());
        // Toutes les réservations se chevauchent : le temps total reste proche de celui d'une seule
        // réservation confirmée, soit 2 x DB_LATENCY (lectures parallèles, puis insertion).
        var singleReservation = DB_LATENCY.multipliedBy(2);
        var limit = singleReservation.multipliedBy(2);
        assertTrue(elapsed.compareTo(limit) < 0,
                () -> "trop lent : " + elapsed.toMillis() + " ms (limite : " + limit.toMillis() + " ms)");
    }

    @Test
    @DisplayName("dernière place : sur deux réservations simultanées, une seule est confirmée")
    void confirmsOnlyOneReservationForLastSeat() throws Exception {
        users.save(new User("u2", "Bob"));
        workshops.save(new Workshop("w-last", "Dernière place", 1));

        Future<ReservationResult> first;
        Future<ReservationResult> second;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            first = executor.submit(() -> service.reserve("u1", "w-last"));
            second = executor.submit(() -> service.reserve("u2", "w-last"));
        }

        var results = List.of(first.get(), second.get());
        assertEquals(1, results.stream().filter(Confirmed.class::isInstance).count(),
                () -> "une seule réservation doit être confirmée : " + results);
        assertTrue(results.contains(new Rejected(Reason.FULL)), () -> "l'autre doit être refusée (FULL) : " + results);
        assertEquals(1, reservations.findAll().size());
        assertEquals(1, published.size());
    }

    private void assertRejected(Reason expected, ReservationResult result) {
        var rejected = assertInstanceOf(Rejected.class, result);
        assertEquals(expected, rejected.reason());
        assertTrue(reservations.findAll().isEmpty(), "aucune réservation ne doit être créée");
        assertTrue(published.isEmpty(), "aucun événement ne doit être publié");
    }
}
