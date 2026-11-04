package org.estaque.booking.service;

import org.estaque.booking.TestcontainersConfiguration;
import org.estaque.booking.context.RequestContext;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
// No test transaction: concurrent reservations use other connections
// and must see the data inserted in setUp().
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReservationServiceTest {

    private static final Duration DB_LATENCY = Duration.ofMillis(200);

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions;

    private UserRepository users;
    private WorkshopRepository workshops;
    private ReservationRepository reservations;
    private final List<Reservation> published = new CopyOnWriteArrayList<>();
    private ReservationService service;

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE reservations, workshops, users").update();

        transactions = new TransactionTemplate(transactionManager);
        var latency = Latency.of(DB_LATENCY);
        users = new UserRepository(jdbc, latency);
        workshops = new WorkshopRepository(jdbc, latency);
        reservations = new ReservationRepository(jdbc);
        service = new ReservationService(users, workshops, reservations, published::add, transactions);

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
        assertEquals(2, seatsLeft("w1"), "une place doit être prise");
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
    @DisplayName("atelier déjà réservé par l'utilisateur : ALREADY_RESERVED, sans nouvel événement")
    void rejectsDuplicateReservation() throws InterruptedException {
        var first = assertInstanceOf(Confirmed.class, service.reserve("u1", "w1"));

        assertEquals(new Rejected(Reason.ALREADY_RESERVED), service.reserve("u1", "w1"));
        assertEquals(List.of(first.reservation()), reservations.findAll());
        assertEquals(List.of(first.reservation()), published);
        assertEquals(2, seatsLeft("w1"), "le doublon ne doit pas prendre de place");
    }

    @Test
    @DisplayName("utilisateur et atelier sont chargés en parallèle")
    void loadsUserAndWorkshopInParallel() throws InterruptedException {
        long start = System.nanoTime();
        service.reserve("u1", "w1");
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        // Sequentially it would take at least 2 x 200 ms (read the user, then the workshop).
        assertTrue(elapsed.compareTo(DB_LATENCY.multipliedBy(2)) < 0,
                () -> "chargement non parallèle : " + elapsed.toMillis() + " ms");
    }

    @Test
    @DisplayName("les lectures s'exécutent sur des virtual threads")
    void loadsOnVirtualThreads() throws InterruptedException {
        var threads = new CopyOnWriteArrayList<Thread>();
        Latency recordThread = () -> threads.add(Thread.currentThread());
        var spiedUsers = new UserRepository(jdbc, recordThread);
        var spiedWorkshops = new WorkshopRepository(jdbc, recordThread);
        spiedUsers.save(new User("u1", "Alice"));
        spiedWorkshops.save(new Workshop("w1", "Java 25", 3));

        new ReservationService(spiedUsers, spiedWorkshops, reservations, published::add, transactions).reserve("u1", "w1");

        assertEquals(2, threads.size());
        assertTrue(threads.stream().allMatch(Thread::isVirtual));
        assertNotEquals(threads.get(0), threads.get(1));
    }

    @Test
    @DisplayName("le REQUEST_ID lié par l'appelant est propagé aux deux lectures")
    void propagatesRequestIdToParallelReads() throws InterruptedException {
        var requestIds = new CopyOnWriteArrayList<String>();
        Latency recordRequestId = () -> requestIds.add(RequestContext.REQUEST_ID.get());
        var spiedUsers = new UserRepository(jdbc, recordRequestId);
        var spiedWorkshops = new WorkshopRepository(jdbc, recordRequestId);
        spiedUsers.save(new User("u1", "Alice"));
        spiedWorkshops.save(new Workshop("w1", "Java 25", 3));
        var spiedService = new ReservationService(spiedUsers, spiedWorkshops, reservations, published::add, transactions);

        ScopedValue.where(RequestContext.REQUEST_ID, "req-42").call(() -> spiedService.reserve("u1", "w1"));

        assertEquals(List.of("req-42", "req-42"), requestIds);
    }

    @Test
    @DisplayName("1000 réservations simultanées pour 100 places : 100 confirmées, 900 refusées (FULL)")
    void handlesThousandConcurrentReservations() throws Exception {
        int count = 1000;
        int seats = 100;
        workshops.save(new Workshop("w-big", "Grand amphi", seats));
        // A user can book a workshop only once: one reservation per user.
        for (int i = 0; i < count; i++) {
            users.save(new User("u-big-" + i, "Participant " + i));
        }

        long start = System.nanoTime();
        List<Future<ReservationResult>> results = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < count; i++) {
                var userId = "u-big-" + i;
                results.add(executor.submit(() -> service.reserve(userId, "w-big")));
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
        assertEquals(0, seatsLeft("w-big"));
        // Reservations overlap: sequentially, the reads alone would take 1000 x DB_LATENCY.
        // Writes, however, go through one at a time: the conditional update locks the workshop row
        // until each transaction ends. Hence a generous limit (about 1 s measured locally).
        var limit = DB_LATENCY.multipliedBy(10);
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
        assertEquals(0, seatsLeft("w-last"));
    }

    @Test
    @DisplayName("find : renvoie une réservation existante de l'atelier")
    void findsExistingReservation() throws Exception {
        var confirmed = (Confirmed) service.reserve("u1", "w1");

        assertEquals(Optional.of(confirmed.reservation()), service.find("w1", confirmed.reservation().id()));
    }

    @Test
    @DisplayName("find : vide pour un identifiant inconnu ou une réservation d'un autre atelier")
    void findsNothingForUnknownOrForeignReservation() throws Exception {
        var confirmed = (Confirmed) service.reserve("u1", "w1");

        assertTrue(service.find("w1", "unknown").isEmpty());
        assertTrue(service.find("w2", confirmed.reservation().id()).isEmpty());
    }

    private int seatsLeft(String workshopId) {
        return jdbc.sql("SELECT seats_left FROM workshops WHERE id = :id")
                .param("id", workshopId)
                .query(Integer.class)
                .single();
    }

    private void assertRejected(Reason expected, ReservationResult result) {
        var rejected = assertInstanceOf(Rejected.class, result);
        assertEquals(expected, rejected.reason());
        assertTrue(reservations.findAll().isEmpty(), "aucune réservation ne doit être créée");
        assertTrue(published.isEmpty(), "aucun événement ne doit être publié");
    }
}
