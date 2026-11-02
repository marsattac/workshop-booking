package org.estaque.booking.api;

import org.estaque.booking.context.RequestContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicReference<String> boundId = new AtomicReference<>();

    @Test
    @DisplayName("en-tête fourni : lié à REQUEST_ID et renvoyé dans la réponse")
    void bindsAndReturnsProvidedId() throws Exception {
        request.addHeader(RequestIdFilter.HEADER, "req-42");

        doFilter();

        assertEquals("req-42", boundId.get());
        assertEquals("req-42", response.getHeader(RequestIdFilter.HEADER));
    }

    @Test
    @DisplayName("en-tête absent : un identifiant est généré")
    void generatesIdWhenHeaderIsMissing() throws Exception {
        doFilter();

        assertNotNull(boundId.get());
        assertEquals(boundId.get(), response.getHeader(RequestIdFilter.HEADER));
    }

    @Test
    @DisplayName("en-tête invalide : remplacé par un identifiant généré")
    void replacesInvalidId() throws Exception {
        request.addHeader(RequestIdFilter.HEADER, "bad id\nforged log line");

        doFilter();

        assertNotEquals("bad id\nforged log line", boundId.get());
        assertEquals(boundId.get(), response.getHeader(RequestIdFilter.HEADER));
    }

    @Test
    @DisplayName("le binding ne survit pas à la requête")
    void unbindsAfterRequest() throws Exception {
        doFilter();

        assertFalse(RequestContext.REQUEST_ID.isBound());
    }

    private void doFilter() throws Exception {
        filter.doFilter(request, response, (_, _) -> boundId.set(RequestContext.REQUEST_ID.get()));
    }
}
