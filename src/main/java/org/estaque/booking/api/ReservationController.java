package org.estaque.booking.api;

import jakarta.validation.Valid;
import org.estaque.booking.model.Reservation;
import org.estaque.booking.service.ReservationService;
import org.estaque.booking.service.result.Confirmed;
import org.estaque.booking.service.result.Reason;
import org.estaque.booking.service.result.Rejected;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/workshops/{workshopId}/reservations")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> reserve(@PathVariable String workshopId,
                                     @Valid @RequestBody ReservationRequest request) throws InterruptedException {
        return switch (service.reserve(request.userId(), workshopId)) {
            case Confirmed(var reservation) -> created(reservation);
            case Rejected(var reason) -> ResponseEntity.of(problem(reason, request.userId(), workshopId)).build();
        };
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<?> get(@PathVariable String workshopId,
                                 @PathVariable String reservationId) throws InterruptedException {
        return service.find(workshopId, reservationId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.of(reservationNotFound(reservationId)).build());
    }

    private static ResponseEntity<Reservation> created(Reservation reservation) {
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{reservationId}")
                .buildAndExpand(reservation.id())
                .toUri();
        return ResponseEntity.created(location).body(reservation);
    }

    private static ProblemDetail problem(Reason reason, String userId, String workshopId) {
        var problem = switch (reason) {
            case USER_NOT_FOUND -> ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND, "Utilisateur introuvable : " + userId);
            case WORKSHOP_NOT_FOUND -> ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND, "Atelier introuvable : " + workshopId);
            case FULL -> ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT, "Atelier complet : " + workshopId);
        };
        problem.setProperty("code", reason.name());
        return problem;
    }

    private static ProblemDetail reservationNotFound(String reservationId) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "Réservation introuvable : " + reservationId);
        problem.setProperty("code", "RESERVATION_NOT_FOUND");
        return problem;
    }
}
