# workshop-booking
Training repository for agentic coding

## Module 1 : Modern Java & AI tools
Agent succeeded in :
- refactoring a legacy reactive vertx implementation into modern sequential java 25 with virtual threads
- detecting a potential concurrency issue
- covering business with unit tests

Agent failed at :
- organising the produced code into a readable package tree
- detecting or notifying about the lack of decrement about the number of remaining seats
- making tests robust and fully representative of production issues

### Experiment: ScopedValue with an executor instead of StructuredTaskScope

The caller of `ReservationService.reserve()` binds `REQUEST_ID` with a `ScopedValue`, and `reserve()` loads the user and the workshop in parallel.
At the time of the experiment, `reserve()` created the binding itself, which is why the stack trace below mentions `doReserve`.
For this experiment, `StructuredTaskScope` was temporarily replaced with `Executors.newVirtualThreadPerTaskExecutor()`, and `propagatesRequestIdToParallelReads` was run unchanged.

The test fails on the first read:

```
java.util.NoSuchElementException: ScopedValue not bound
	at java.base/java.lang.ScopedValue.slowGet(ScopedValue.java:571)
	at java.base/java.lang.ScopedValue.get(ScopedValue.java:564)
	at org.estaque.booking.service.ReservationServiceTest.lambda$propagatesRequestIdToParallelReads$0(ReservationServiceTest.java:123)
	at org.estaque.booking.repository.UserRepository.findById(UserRepository.java:25)
	at org.estaque.booking.service.ReservationService.lambda$doReserve$0(ReservationService.java:49)
```

The repositories also log the fallback value instead of the request id:

```
[no-request] findById u1
[no-request] findById w1
```

Threads started by an executor do not inherit scoped values, whereas subtasks forked in a `StructuredTaskScope` opened inside the binding do. The code was restored to `StructuredTaskScope` after the experiment.

## Module 2: SpringBoot & PostgreSQL

### SpringBoot
Agent globally succeeded in migrating the project to the adequate SpringBoot version, covering business correctly and handling errors.
It just failed at activating virtual threads in the config file.

Agent has been very relevant with the overload of test configuration by suggesting a more refined src/test/resources/config/application.yaml path.

### PostgreSQL
Agent suggested to handle properly the case of an already booked workshop.

After every iteration of coding, the agent reported the attention point for which I had to decide.

I asked the agent to write the Javadoc in English (it was written in French)

The agent suggested using TransactionTemplate for a book() subtask to target only the section facing concurrency issue over a global @Transactional annotation on reserve() that would have englobed and blocked the paralleled load() tasks.

### Concurrency strategies: benchmark

Three ways to stop two reservations from taking the same last seat, each on its own branch:

| Strategy | Branch | How a seat is taken | Where the "a seat is left" rule lives |
|---|---|---|---|
| Conditional update | `feature/postgres-setup` | `UPDATE workshops SET seats_left = seats_left - 1 WHERE id = :id AND seats_left > 0`; no row updated means full | SQL |
| Pessimistic lock | `feature/pessimistic-lock` | `SELECT seats_left ... FOR UPDATE`, check in Java, then `UPDATE` under the lock | Java, under the row lock |
| Optimistic lock | `feature/optimistic-lock` | Read `seats_left` and `version`, check in Java, then `UPDATE ... WHERE id = :id AND version = :read`; no row updated means retry | Java, guarded by the version |

In every case, taking the seat and inserting the reservation run in one transaction, and the schema's `CHECK (seats_left >= 0)` stays as the last line of defense.

Measured with `ReservationServiceTest#handlesThousandConcurrentReservations`: 1000 users book a 100-seat workshop at the same time, on virtual threads. Each run gave 100 confirmed reservations, 900 rejected as full and 0 seats left. The times include the 200 ms simulated latency of the parallel reads of the user and the workshop, which is the same for all three. 5 runs per strategy, interleaved, on a local PostgreSQL 18 (Testcontainers on OrbStack) with the default pool of 10 connections:

| Strategy | Median total time | Min – max | Retries (median, min – max) | Statements per confirmed reservation |
|---|---|---|---|---|
| Conditional update | **1090 ms** | 1063 – 1130 ms | none | 2 (update, insert) |
| Pessimistic lock | 1704 ms | 1674 – 1768 ms | none | 3 (select for update, update, insert) |
| Optimistic lock | 1270 ms | 1262 – 1501 ms | 130 (128 – 133) | 3 (select, update, insert), plus 2 per retry |

Reading the results:
- **Conditional update** is the fastest: one statement both checks and takes the seat. Once the workshop is full, the remaining requests update no row and do not wait for anything.
- **Pessimistic lock** is the slowest: all 1000 transactions queue on the workshop row, including the 900 that only find out it is full. The lock is held while the seats are read and updated, and while the reservation is inserted.
- **Optimistic lock** sits in between. Requests that read 0 seats are rejected without locking anything. Retries stay low (about 130 for 1000 requests): in PostgreSQL, an `UPDATE` on a row being modified still waits for the other transaction, then finds that the version changed. Conflicts therefore only happen while seats are left.
