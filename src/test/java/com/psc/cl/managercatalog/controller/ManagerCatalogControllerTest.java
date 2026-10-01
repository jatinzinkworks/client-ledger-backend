package com.psc.cl.managercatalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psc.cl.managercatalog.dto.ManagerRequest;
import com.psc.cl.managercatalog.dto.ManagerResponse;
import com.psc.cl.managercatalog.exception.ManagerEmailAlreadyExistsException;
import com.psc.cl.managercatalog.exception.ManagerNotFoundException;
import com.psc.cl.managercatalog.model.ManagerRole;
import com.psc.cl.managercatalog.service.ManagerCatalogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@WebMvcTest(ManagerCatalogController.class)
class ManagerCatalogControllerTest {

    private static final String MANAGERS_URL = ManagerCatalogApi.BASE_PATH;

    /** A complete, valid manager payload. Variants below alter one field at a time. */
    private static final String VALID_MANAGER_JSON = """
            {
              "firstName": "Pranay",
              "lastName": "Singhal",
              "email": "pranay.singhal@pranaysinghal.in",
              "role": "SENIOR_MANAGER",
              "mobileNumber": "+91 98765 43210"
            }""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManagerCatalogService managerCatalogService;

    @Test
    @DisplayName("returns 201 with a Location header and deserialises the body onto the record")
    void createsManager() throws Exception {
        when(managerCatalogService.createManager(any())).thenReturn(response());

        mockMvc.perform(post(MANAGERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MANAGER_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.firstName").value("Pranay"))
                .andExpect(jsonPath("$.lastName").value("Singhal"))
                .andExpect(jsonPath("$.role").value("SENIOR_MANAGER"))
                .andExpect(jsonPath("$.mobileNumber").value("+91 98765 43210"));

        ArgumentCaptor<ManagerRequest> captor = ArgumentCaptor.forClass(ManagerRequest.class);
        verify(managerCatalogService).createManager(captor.capture());
        ManagerRequest received = captor.getValue();
        assertThat(received.firstName()).isEqualTo("Pranay");
        assertThat(received.lastName()).isEqualTo("Singhal");
        assertThat(received.email()).isEqualTo("pranay.singhal@pranaysinghal.in");
        assertThat(received.role()).isEqualTo(ManagerRole.SENIOR_MANAGER);
        assertThat(received.mobileNumber()).isEqualTo("+91 98765 43210");
    }

    @Test
    @DisplayName("returns 400 listing every missing mandatory field")
    void rejectsMissingMandatoryFields() throws Exception {
        mockMvc.perform(post(MANAGERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("firstName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("lastName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("role")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("mobileNumber")));

        verify(managerCatalogService, never()).createManager(any());
    }

    @Test
    @DisplayName("returns 400 when the email is malformed")
    void rejectsMalformedEmail() throws Exception {
        mockMvc.perform(post(MANAGERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Pranay",
                                  "lastName": "Singhal",
                                  "email": "not-an-email",
                                  "role": "SENIOR_MANAGER",
                                  "mobileNumber": "+91 98765 43210"
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")));

        verify(managerCatalogService, never()).createManager(any());
    }

    @Test
    @DisplayName("returns 400 when the mobile number contains letters")
    void rejectsMalformedMobileNumber() throws Exception {
        mockMvc.perform(post(MANAGERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Pranay",
                                  "lastName": "Singhal",
                                  "email": "pranay.singhal@pranaysinghal.in",
                                  "role": "SENIOR_MANAGER",
                                  "mobileNumber": "call me maybe"
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("mobileNumber")));

        verify(managerCatalogService, never()).createManager(any());
    }

    @Test
    @DisplayName("returns 400 when the role is not one of the three allowed values")
    void rejectsUnknownRole() throws Exception {
        mockMvc.perform(post(MANAGERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Pranay",
                                  "lastName": "Singhal",
                                  "email": "pranay.singhal@pranaysinghal.in",
                                  "role": "PARTNER",
                                  "mobileNumber": "+91 98765 43210"
                                }"""))
                .andExpect(status().isBadRequest());

        verify(managerCatalogService, never()).createManager(any());
    }

    @Test
    @DisplayName("returns 409 when a manager already holds the email")
    void returnsConflictOnDuplicateEmail() throws Exception {
        when(managerCatalogService.createManager(any()))
                .thenThrow(new ManagerEmailAlreadyExistsException("pranay.singhal@pranaysinghal.in"));

        mockMvc.perform(post(MANAGERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MANAGER_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("returns 200 with the manager on fetch by id")
    void fetchesManagerById() throws Exception {
        UUID id = UUID.randomUUID();
        when(managerCatalogService.getManager(id)).thenReturn(response());

        mockMvc.perform(get(MANAGERS_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("pranay.singhal@pranaysinghal.in"));
    }

    @Test
    @DisplayName("returns 404 when the manager id is unknown")
    void returnsNotFoundForUnknownId() throws Exception {
        UUID id = UUID.randomUUID();
        when(managerCatalogService.getManager(id)).thenThrow(new ManagerNotFoundException(id));

        mockMvc.perform(get(MANAGERS_URL + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 400 when the path identifier is not a UUID")
    void returnsBadRequestForMalformedId() throws Exception {
        mockMvc.perform(get(MANAGERS_URL + "/not-a-uuid"))
                .andExpect(status().isBadRequest());

        verify(managerCatalogService, never()).getManager(any());
    }

    @Test
    @DisplayName("returns 200 with the catalog on list")
    void listsManagers() throws Exception {
        when(managerCatalogService.listManagers()).thenReturn(List.of(response()));

        mockMvc.perform(get(MANAGERS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lastName").value("Singhal"));
    }

    @Test
    @DisplayName("returns 200 with an empty array when nobody has been added")
    void listsEmptyCatalog() throws Exception {
        when(managerCatalogService.listManagers()).thenReturn(List.of());

        mockMvc.perform(get(MANAGERS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("returns 200 on update and passes the id through")
    void updatesManager() throws Exception {
        UUID id = UUID.randomUUID();
        when(managerCatalogService.updateManager(any(), any())).thenReturn(response());

        mockMvc.perform(put(MANAGERS_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Aarti",
                                  "lastName": "Desai",
                                  "email": "aarti.desai@pranaysinghal.in",
                                  "role": "ASSOCIATE",
                                  "mobileNumber": "+91 99887 66554"
                                }"""))
                .andExpect(status().isOk());

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<ManagerRequest> bodyCaptor = ArgumentCaptor.forClass(ManagerRequest.class);
        verify(managerCatalogService).updateManager(idCaptor.capture(), bodyCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(id);
        assertThat(bodyCaptor.getValue().firstName()).isEqualTo("Aarti");
        assertThat(bodyCaptor.getValue().role()).isEqualTo(ManagerRole.ASSOCIATE);
    }

    @Test
    @DisplayName("applies the same validation on update as on create")
    void updateRejectsInvalidBody() throws Exception {
        mockMvc.perform(put(MANAGERS_URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("firstName")));

        verify(managerCatalogService, never()).updateManager(any(), any());
    }

    @Test
    @DisplayName("returns 404 when updating an unknown manager")
    void updateReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(managerCatalogService.updateManager(any(), any()))
                .thenThrow(new ManagerNotFoundException(id));

        mockMvc.perform(put(MANAGERS_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MANAGER_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 409 when updating onto an email another manager holds")
    void updateReturnsConflict() throws Exception {
        when(managerCatalogService.updateManager(any(), any()))
                .thenThrow(new ManagerEmailAlreadyExistsException("pranay.singhal@pranaysinghal.in"));

        mockMvc.perform(put(MANAGERS_URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MANAGER_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("returns 204 with no body on delete")
    void deletesManager() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(MANAGERS_URL + "/" + id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(managerCatalogService).deleteManager(id);
    }

    @Test
    @DisplayName("returns 404 when deleting an unknown manager")
    void deleteReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ManagerNotFoundException(id)).when(managerCatalogService).deleteManager(id);

        mockMvc.perform(delete(MANAGERS_URL + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private ManagerResponse response() {
        Instant now = Instant.now();
        return ManagerResponse.builder()
                .id(UUID.randomUUID())
                .firstName("Pranay")
                .lastName("Singhal")
                .email("pranay.singhal@pranaysinghal.in")
                .role(ManagerRole.SENIOR_MANAGER)
                .mobileNumber("+91 98765 43210")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
