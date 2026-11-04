package org.estaque.booking.api;

import org.estaque.booking.TestcontainersConfiguration;
import org.estaque.booking.context.RequestContext;
import org.estaque.booking.model.User;
import org.estaque.booking.model.Workshop;
import org.estaque.booking.repository.Latency;
import org.estaque.booking.repository.UserRepository;
import org.estaque.booking.repository.WorkshopRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class RequestIdPropagationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private WorkshopRepository workshops;

    @MockitoBean
    private Latency latency;

    @Test
    @DisplayName("X-Request-Id atteint les deux lectures parallèles")
    void propagatesRequestIdToRepositories() throws Exception {
        var seenByRepositories = new CopyOnWriteArrayList<String>();
        willAnswer(_ -> seenByRepositories.add(RequestContext.REQUEST_ID.get())).given(latency).simulate();
        users.save(new User("u-propagation", "Alice"));
        workshops.save(new Workshop("w-propagation", "Spring Boot", 1));

        mvc.perform(post("/workshops/w-propagation/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(RequestIdFilter.HEADER, "req-42")
                        .content("{\"userId\":\"u-propagation\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(RequestIdFilter.HEADER, "req-42"));

        assertEquals(List.of("req-42", "req-42"), seenByRepositories);
    }
}
