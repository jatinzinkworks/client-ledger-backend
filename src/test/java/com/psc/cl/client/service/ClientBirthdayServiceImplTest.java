package com.psc.cl.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.psc.cl.client.dto.ClientBirthdayResponse;
import com.psc.cl.client.model.Client;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.managercatalog.model.Manager;
import com.psc.cl.managercatalog.repository.ManagerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ClientBirthdayServiceImplTest {

    private static final UUID MANAGER_ID = UUID.fromString("e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47");

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ManagerRepository managerRepository;

    @InjectMocks
    private ClientBirthdayServiceImpl clientBirthdayService;

    @Test
    @DisplayName("lists the month's birthdays in the repository's order, each with its manager's name")
    void listsBirthdays() {
        Client asha = client("Asha", "Kapoor", LocalDate.of(1990, 7, 4));
        Client rohan = client("Rohan", "Mehta", LocalDate.of(1985, 7, 23));
        when(clientRepository.findByBirthdayMonth(7)).thenReturn(List.of(asha, rohan));
        when(managerRepository.findAllById(any())).thenReturn(List.of(manager()));

        List<ClientBirthdayResponse> birthdays = clientBirthdayService.listBirthdays(Month.JULY);

        assertThat(birthdays).extracting(ClientBirthdayResponse::getFirstName).containsExactly("Asha", "Rohan");
        assertThat(birthdays).extracting(ClientBirthdayResponse::getDayOfMonth).containsExactly(4, 23);
        assertThat(birthdays.get(1).getBirthday()).isEqualTo(LocalDate.of(1985, 7, 23));
        assertThat(birthdays.get(1).getClientId()).isEqualTo(rohan.getId());
        assertThat(birthdays).allSatisfy(birthday -> {
            assertThat(birthday.getManagerId()).isEqualTo(MANAGER_ID);
            assertThat(birthday.getManagerName()).isEqualTo("Pranay Singhal");
        });
    }

    @Test
    @DisplayName("returns an empty list when no client has a birthday in the month")
    void listsNoBirthdays() {
        when(clientRepository.findByBirthdayMonth(2)).thenReturn(List.of());
        when(managerRepository.findAllById(any())).thenReturn(List.of());

        assertThat(clientBirthdayService.listBirthdays(Month.FEBRUARY)).isEmpty();
    }

    @Test
    @DisplayName("reports a client whose assigned manager is missing as an inconsistency")
    void failsOnMissingManager() {
        when(clientRepository.findByBirthdayMonth(7))
                .thenReturn(List.of(client("Rohan", "Mehta", LocalDate.of(1985, 7, 23))));
        when(managerRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> clientBirthdayService.listBirthdays(Month.JULY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(MANAGER_ID.toString());
    }

    private static Client client(String firstName, String lastName, LocalDate birthday) {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setFirstName(firstName);
        client.setLastName(lastName);
        client.setPhone("9820012345");
        client.setEmail(firstName.toLowerCase() + "@example.com");
        client.setManagerId(MANAGER_ID);
        client.setBirthday(birthday);
        return client;
    }

    private static Manager manager() {
        Manager manager = new Manager();
        manager.setId(MANAGER_ID);
        manager.setFirstName("Pranay");
        manager.setLastName("Singhal");
        return manager;
    }
}
