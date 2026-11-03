# workshop-booking
Training repository for agentic coding

Module 1 : Modern Java & AI tools
Agent succeeded in :
- refactoring a legacy reactive vertx implementation into modern sequential java 25 with virtual threads
- detecting a potential concurrency issue
- covering business with unit tests

Agent failed at :
- organising the produced code into a readable package tree
- detecting or notifying about the lack of decrement about the number of remaining seats
- making tests robust and fully representative of production issues

## Experiment: ScopedValue with an executor instead of StructuredTaskScope

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

Module 2: SpringBoot & Postgres

Agent globally succeeded in migrating the project to the adequate SpringBoot version, covering business correctly and handling errors.
It just failed at activating virtual threads in the config file.

Agent has been very relevant with the overload of test configuration by suggesting a more refined src/test/resources/config/application.yaml path.