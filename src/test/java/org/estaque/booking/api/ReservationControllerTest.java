package org.estaque.booking.api;

import org.estaque.booking.context.RequestContext;
import org.estaque.booking.model.Reservation;
import org.estaque.booking.service.ReservationService;
import org.estaque.booking.service.result.Confirmed;
import org.estaque.booking.service.result.Reason;
import org.estaque.booking.service.result.Rejected;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

    private static final String URL = "/workshops/w1/reservations";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ReservationService service;

    @Test
    @DisplayName("réservation confirmée : 201, en-tête Location et réservation dans le corps")
    void returnsCreatedWhenConfirmed() throws Exception {
        given(service.reserve("u1", "w1")).willReturn(new Confirmed(new Reservation("r1", "u1", "w1")));

        postReservation("{\"userId\":\"u1\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/workshops/w1/reservations/r1"))
                .andExpect(jsonPath("$.id").value("r1"))
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(jsonPath("$.workshopId").value("w1"));
    }

    @Test
    @DisplayName("utilisateur inconnu : 404 avec ProblemDetail")
    void returnsNotFoundForUnknownUser() throws Exception {
        given(service.reserve("u1", "w1")).willReturn(new Rejected(Reason.USER_NOT_FOUND));

        assertProblem(postReservation("{\"userId\":\"u1\"}"), 404, "Not Found", "USER_NOT_FOUND", "Utilisateur introuvable : u1");
    }

    @Test
    @DisplayName("atelier inconnu : 404 avec ProblemDetail")
    void returnsNotFoundForUnknownWorkshop() throws Exception {
        given(service.reserve("u1", "w1")).willReturn(new Rejected(Reason.WORKSHOP_NOT_FOUND));

        assertProblem(postReservation("{\"userId\":\"u1\"}"), 404, "Not Found", "WORKSHOP_NOT_FOUND", "Atelier introuvable : w1");
    }

    @Test
    @DisplayName("atelier complet : 409 avec ProblemDetail")
    void returnsConflictWhenFull() throws Exception {
        given(service.reserve("u1", "w1")).willReturn(new Rejected(Reason.FULL));

        assertProblem(postReservation("{\"userId\":\"u1\"}"), 409, "Conflict", "FULL", "Atelier complet : w1");
    }

    @Test
    @DisplayName("atelier déjà réservé par l'utilisateur : 409 avec ProblemDetail")
    void returnsConflictWhenAlreadyReserved() throws Exception {
        given(service.reserve("u1", "w1")).willReturn(new Rejected(Reason.ALREADY_RESERVED));

        assertProblem(postReservation("{\"userId\":\"u1\"}"), 409, "Conflict", "ALREADY_RESERVED",
                "Atelier déjà réservé par l'utilisateur u1 : w1");
    }

    @Test
    @DisplayName("userId vide ou absent : 400 avec ProblemDetail, sans appel au service")
    void rejectsBlankUserId() throws Exception {
        postReservation("{\"userId\":\" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        postReservation("{}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("X-Request-Id : lié pendant l'appel au service et renvoyé dans la réponse")
    void propagatesRequestIdToServiceAndResponse() throws Exception {
        var seenByService = new AtomicReference<String>();
        given(service.reserve("u1", "w1")).willAnswer(_ -> {
            seenByService.set(RequestContext.REQUEST_ID.get());
            return new Confirmed(new Reservation("r1", "u1", "w1"));
        });

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .header(RequestIdFilter.HEADER, "req-42")
                        .content("{\"userId\":\"u1\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(RequestIdFilter.HEADER, "req-42"));

        assertEquals("req-42", seenByService.get());
    }

    @Test
    @DisplayName("X-Request-Id : renvoyé aussi sur une réponse ProblemDetail")
    void returnsRequestIdOnProblemResponse() throws Exception {
        given(service.reserve("u1", "w1")).willReturn(new Rejected(Reason.FULL));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .header(RequestIdFilter.HEADER, "req-42")
                        .content("{\"userId\":\"u1\"}"))
                .andExpect(status().isConflict())
                .andExpect(header().string(RequestIdFilter.HEADER, "req-42"));
    }

    @Test
    @DisplayName("sans X-Request-Id : un identifiant généré est renvoyé")
    void returnsGeneratedRequestId() throws Exception {
        given(service.reserve("u1", "w1")).willReturn(new Rejected(Reason.USER_NOT_FOUND));

        postReservation("{\"userId\":\"u1\"}")
                .andExpect(header().string(RequestIdFilter.HEADER, not(emptyOrNullString())));
    }

    @Test
    @DisplayName("réservation existante : 200 et réservation dans le corps")
    void returnsReservation() throws Exception {
        given(service.find("w1", "r1")).willReturn(Optional.of(new Reservation("r1", "u1", "w1")));

        mvc.perform(get(URL + "/r1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("r1"))
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(jsonPath("$.workshopId").value("w1"));
    }

    @Test
    @DisplayName("réservation inconnue : 404 avec ProblemDetail")
    void returnsNotFoundForUnknownReservation() throws Exception {
        given(service.find("w1", "r1")).willReturn(Optional.empty());

        assertProblem(mvc.perform(get(URL + "/r1")), 404, "Not Found", "RESERVATION_NOT_FOUND",
                "Réservation introuvable : r1", URL + "/r1");
    }

    private ResultActions postReservation(String body) throws Exception {
        return mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void assertProblem(ResultActions result, int status, String title, String code, String detail) throws Exception {
        assertProblem(result, status, title, code, detail, URL);
    }

    private void assertProblem(ResultActions result, int status, String title, String code, String detail,
                               String instance) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.instance").value(instance));
    }
}
