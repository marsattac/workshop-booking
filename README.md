# workshop-booking
Training repository for agentic coding

Agent succeeded in :
- refactoring a legacy reactive vertx implementation into modern sequential java 25 with virtual threads
- detecting a potential concurrency issue
- covering business with unit tests

Agent failed at :
- organising the produced code into a readable package tree
- detecting or notifying about the lack of decrement about the number of remaining seats
- making tests robust and fully representative of production issues

## Experiment: ScopedValue with an executor instead of StructuredTaskScope

`ReservationService.reserve()` binds `REQUEST_ID` with a `ScopedValue`, then loads the user and the workshop in parallel.
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