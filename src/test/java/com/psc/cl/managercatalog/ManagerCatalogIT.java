package com.psc.cl.managercatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psc.cl.AbstractPostgresIT;
import com.psc.cl.managercatalog.controller.ManagerCatalogApi;
import com.psc.cl.managercatalog.repository.ManagerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

class ManagerCatalogIT extends AbstractPostgresIT {

    private static final String MANAGERS_URL = ManagerCatalogApi.BASE_PATH;

    private static final String SENIOR_MANAGER_JSON = """
            {
              "firstName": "Pranay",
              "lastName": "Singhal",
              "email": "pranay.singhal@pranaysinghal.in",
              "role": "SENIOR_MANAGER",
              "mobileNumber": "+91 98765 43210"
            }""";

    @Autowired
    private ManagerRepository managerRepository;

    @BeforeEach
    void clearManagers() {
        managerRepository.deleteAll();
    }

    @Test
    @DisplayName("creates, reads, lists, updates and deletes a manager end to end")
    void fullLifecycle() throws Exception {
        String created = mockMvc.perform(post(MANAGERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SENIOR_MANAGER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SENIOR_MANAGER"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mockMvc.perform(get(MANAGERS_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("pranay.singhal@pranaysinghal.in"))
                .andExpect(jsonPath("$.mobileNumber").value("+91 98765 43210"));

        mockMvc.perform(get(MANAGERS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));

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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.firstName").value("Aarti"))
                .andExpect(jsonPath("$.role").value("ASSOCIATE"));

        mockMvc.perform(delete(MANAGERS_URL + "/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(MANAGERS_URL + "/" + id))
                .andExpect(status().isNotFound());

        assertThat(managerRepository.count()).isZero();
    }

    @Test
    @DisplayName("the unique email index rejects a duplicate differing only in case")
    void rejectsDuplicateEmailIgnoringCase() throws Exception {
        mockMvc.perform(post(MANAGERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SENIOR_MANAGER_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post(MANAGERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Someone",
                                  "lastName": "Else",
                                  "email": "PRANAY.SINGHAL@PRANAYSINGHAL.IN",
                                  "role": "ASSOCIATE",
                                  "mobileNumber": "+91 90000 00000"
                                }"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));

        assertThat(managerRepository.count()).isOne();
    }

    @Test
    @DisplayName("a manager keeps their own email on update without conflicting with themselves")
    void updateKeepingOwnEmail() throws Exception {
        String created = mockMvc.perform(post(MANAGERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SENIOR_MANAGER_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mockMvc.perform(put(MANAGERS_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Pranay",
                                  "lastName": "Singhal",
                                  "email": "pranay.singhal@pranaysinghal.in",
                                  "role": "MANAGER",
                                  "mobileNumber": "+91 98765 43210"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    @DisplayName("rejects an update onto an email a different manager already holds")
    void updateOntoAnotherEmailConflicts() throws Exception {
        mockMvc.perform(post(MANAGERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SENIOR_MANAGER_JSON))
                .andExpect(status().isCreated());

        String second = mockMvc.perform(post(MANAGERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Aarti",
                                  "lastName": "Desai",
                                  "email": "aarti.desai@pranaysinghal.in",
                                  "role": "ASSOCIATE",
                                  "mobileNumber": "+91 99887 66554"
                                }"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondId = JsonPath.read(second, "$.id");

        mockMvc.perform(put(MANAGERS_URL + "/" + secondId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SENIOR_MANAGER_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("lists managers ordered by last name then first name")
    void listsOrderedByName() throws Exception {
        createManager("Pranay", "Singhal", "pranay.singhal@pranaysinghal.in", "SENIOR_MANAGER");
        createManager("Aarti", "Desai", "aarti.desai@pranaysinghal.in", "ASSOCIATE");
        createManager("Bhavna", "Desai", "bhavna.desai@pranaysinghal.in", "MANAGER");

        mockMvc.perform(get(MANAGERS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].firstName").value("Aarti"))
                .andExpect(jsonPath("$[1].firstName").value("Bhavna"))
                .andExpect(jsonPath("$[2].lastName").value("Singhal"));
    }

    @Test
    @DisplayName("a request failing validation never reaches the database")
    void invalidRequestIsNotStored() throws Exception {
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
                .andExpect(status().isBadRequest());

        assertThat(managerRepository.count()).isZero();
    }

    @Test
    @DisplayName("returns 404 for an unknown id and 400 for a malformed one")
    void unknownAndMalformedIds() throws Exception {
        mockMvc.perform(get(MANAGERS_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(MANAGERS_URL + "/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    private void createManager(String firstName, String lastName, String email, String role)
            throws Exception {
        mockMvc.perform(post(MANAGERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "%s",
                                  "lastName": "%s",
                                  "email": "%s",
                                  "role": "%s",
                                  "mobileNumber": "+91 98765 43210"
                                }""".formatted(firstName, lastName, email, role)))
                .andExpect(status().isCreated());
    }
}
