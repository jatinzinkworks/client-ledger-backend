package com.psc.cl.client.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psc.cl.client.dto.ClientBirthdayResponse;
import com.psc.cl.client.service.ClientBirthdayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

@WebMvcTest(ClientBirthdayController.class)
class ClientBirthdayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientBirthdayService clientBirthdayService;

    @Test
    @DisplayName("returns the clients with a birthday in the month")
    void listsBirthdays() throws Exception {
        when(clientBirthdayService.listBirthdays(Month.JULY)).thenReturn(List.of(ClientBirthdayResponse.builder()
                .clientId(UUID.fromString("7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35"))
                .firstName("Rohan")
                .lastName("Mehta")
                .birthday(LocalDate.of(1985, 7, 23))
                .dayOfMonth(23)
                .managerName("Pranay Singhal")
                .build()));

        mockMvc.perform(get(ClientBirthdayApi.BASE_PATH).param("month", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Rohan"))
                .andExpect(jsonPath("$[0].birthday").value("1985-07-23"))
                .andExpect(jsonPath("$[0].dayOfMonth").value(23))
                .andExpect(jsonPath("$[0].managerName").value("Pranay Singhal"));
    }

    @Test
    @DisplayName("returns an empty list when no client has a birthday in the month")
    void listsNoBirthdays() throws Exception {
        when(clientBirthdayService.listBirthdays(Month.FEBRUARY)).thenReturn(List.of());

        mockMvc.perform(get(ClientBirthdayApi.BASE_PATH).param("month", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "13", "-1", "July"})
    @DisplayName("returns 400 for a month that is not 1 to 12")
    void rejectsInvalidMonth(String month) throws Exception {
        mockMvc.perform(get(ClientBirthdayApi.BASE_PATH).param("month", month))
                .andExpect(status().isBadRequest());

        verify(clientBirthdayService, never()).listBirthdays(any());
    }

    @Test
    @DisplayName("returns 400 when the month is missing")
    void rejectsMissingMonth() throws Exception {
        mockMvc.perform(get(ClientBirthdayApi.BASE_PATH))
                .andExpect(status().isBadRequest());

        verify(clientBirthdayService, never()).listBirthdays(any());
    }
}
