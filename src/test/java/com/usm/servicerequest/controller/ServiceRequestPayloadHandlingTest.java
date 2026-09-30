package com.usm.servicerequest.controller;

import com.usm.servicerequest.domain.RequestCategory;
import com.usm.servicerequest.domain.RequestPriority;
import com.usm.servicerequest.domain.RequestStatus;
import com.usm.servicerequest.dto.ServiceRequestResponse;
import com.usm.servicerequest.exception.GlobalExceptionHandler;
import com.usm.servicerequest.security.AuthContext;
import com.usm.servicerequest.security.Role;
import com.usm.servicerequest.service.ServiceRequestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ServiceRequestPayloadHandlingTest {

    @Mock
    private ServiceRequestService service;

    private MockMvc mockMvc;

    private static final AuthContext CALLER = new AuthContext("student-1", Role.STUDENT, "Faculty of Science");

    @BeforeEach
    void setUp() {
        ServiceRequestController controller = new ServiceRequestController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        var auth = new UsernamePasswordAuthenticationToken(
                CALLER, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ServiceRequestResponse sampleResponse(String id, RequestCategory cat, RequestPriority prio, String loc) {
        return new ServiceRequestResponse(
                id, CALLER.getUserId(), cat, loc, prio,
                "Test description", null, RequestStatus.NEW,
                null, null, null, Instant.now(), null, null, null, null
        );
    }

    @Test
    void create_withTitleCaseCategoryAndPriority_succeedsWith201() throws Exception {
        when(service.create(any(), any())).thenReturn(
                sampleResponse("SR-2026-0001", RequestCategory.FACILITY, RequestPriority.HIGH, "LAB-101"));

        String body = """
                {
                  "category": "Facility",
                  "priority": "High",
                  "location": "LAB-101",
                  "description": "Projector broken"
                }
                """;

        mockMvc.perform(post("/api/service-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value("SR-2026-0001"))
                .andExpect(jsonPath("$.category").value("FACILITY"))
                .andExpect(jsonPath("$.priority").value("HIGH"));

        verify(service).create(argThat(req ->
                req.category() == RequestCategory.FACILITY && req.priority() == RequestPriority.HIGH), any());
    }

    @Test
    void create_withLowercaseCategoryAndPriority_succeedsWith201() throws Exception {
        when(service.create(any(), any())).thenReturn(
                sampleResponse("SR-2026-0002", RequestCategory.IT, RequestPriority.MEDIUM, "Library Room 12"));

        String body = """
                {
                  "category": "it",
                  "priority": "medium",
                  "location": "Library Room 12",
                  "description": "Network switch issue"
                }
                """;

        mockMvc.perform(post("/api/service-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value("SR-2026-0002"))
                .andExpect(jsonPath("$.category").value("IT"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"));

        verify(service).create(argThat(req ->
                req.category() == RequestCategory.IT && req.priority() == RequestPriority.MEDIUM), any());
    }

    @Test
    void create_withFacilityCodeAlias_mapsToLocationAndSucceedsWith201() throws Exception {
        when(service.create(any(), any())).thenReturn(
                sampleResponse("SR-2026-0003", RequestCategory.FACILITY, RequestPriority.LOW, "AUD-MAIN"));

        String body = """
                {
                  "category": "FACILITY",
                  "priority": "LOW",
                  "facilityCode": "AUD-MAIN",
                  "description": "Light bulb replacement"
                }
                """;

        mockMvc.perform(post("/api/service-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value("SR-2026-0003"))
                .andExpect(jsonPath("$.location").value("AUD-MAIN"));

        verify(service).create(argThat(req -> "AUD-MAIN".equals(req.location())), any());
    }

    @Test
    void create_withRoomCodeAlias_mapsToLocationAndSucceedsWith201() throws Exception {
        when(service.create(any(), any())).thenReturn(
                sampleResponse("SR-2026-0004", RequestCategory.EQUIPMENT, RequestPriority.CRITICAL, "STUDY-POD-01"));

        String body = """
                {
                  "category": "EQUIPMENT",
                  "priority": "CRITICAL",
                  "roomCode": "STUDY-POD-01",
                  "description": "Power socket sparking"
                }
                """;

        mockMvc.perform(post("/api/service-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.location").value("STUDY-POD-01"));

        verify(service).create(argThat(req -> "STUDY-POD-01".equals(req.location())), any());
    }

    @Test
    void create_withUnknownJsonFields_safelyIgnoredAndSucceedsWith201() throws Exception {
        when(service.create(any(), any())).thenReturn(
                sampleResponse("SR-2026-0005", RequestCategory.GENERAL, RequestPriority.LOW, "Building B"));

        String body = """
                {
                  "category": "GENERAL",
                  "priority": "LOW",
                  "location": "Building B",
                  "description": "Clean floor",
                  "extraField": "ignoredValue",
                  "clientTraceId": "12345"
                }
                """;

        mockMvc.perform(post("/api/service-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value("SR-2026-0005"));
    }

    @Test
    void create_withInvalidCategory_returns400BadRequestNot500() throws Exception {
        String body = """
                {
                  "category": "INVALID_CAT",
                  "priority": "HIGH",
                  "location": "LAB-101",
                  "description": "Something broke"
                }
                """;

        mockMvc.perform(post("/api/service-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("Unknown category: 'INVALID_CAT'")));
    }

    @Test
    void create_withMalformedJson_returns400BadRequestNot500() throws Exception {
        String malformedJson = "{ \"category\": \"FACILITY\", \"location\": ";

        mockMvc.perform(post("/api/service-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("Malformed JSON request")));
    }

    @Test
    void create_whenDataIntegrityViolationOccurs_returns409ConflictNot500() throws Exception {
        when(service.create(any(), any())).thenThrow(
                new DataIntegrityViolationException("Duplicate entry 'SR-2026-0001' for key 'PRIMARY'"));

        String body = """
                {
                  "category": "FACILITY",
                  "priority": "HIGH",
                  "location": "LAB-101",
                  "description": "Duplicate key test"
                }
                """;

        mockMvc.perform(post("/api/service-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message", containsString("Duplicate entry 'SR-2026-0001'")));
    }
}
