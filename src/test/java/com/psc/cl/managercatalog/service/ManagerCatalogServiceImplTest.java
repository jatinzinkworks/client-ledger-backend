package com.psc.cl.managercatalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.psc.cl.managercatalog.dto.ManagerRequest;
import com.psc.cl.managercatalog.dto.ManagerResponse;
import com.psc.cl.managercatalog.exception.ManagerEmailAlreadyExistsException;
import com.psc.cl.managercatalog.exception.ManagerNotFoundException;
import com.psc.cl.managercatalog.model.Manager;
import com.psc.cl.managercatalog.model.ManagerRole;
import com.psc.cl.managercatalog.repository.ManagerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ManagerCatalogServiceImplTest {

    @Mock
    private ManagerRepository managerRepository;

    @InjectMocks
    private ManagerCatalogServiceImpl managerCatalogService;

    @Captor
    private ArgumentCaptor<Manager> managerCaptor;

    @Test
    @DisplayName("stores every supplied field on create")
    void createsManager() {
        when(managerRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        stubSave();

        ManagerResponse response = managerCatalogService.createManager(request().build());

        verify(managerRepository).saveAndFlush(managerCaptor.capture());
        Manager saved = managerCaptor.getValue();
        assertThat(saved.getFirstName()).isEqualTo("Pranay");
        assertThat(saved.getLastName()).isEqualTo("Singhal");
        assertThat(saved.getEmail()).isEqualTo("pranay.singhal@pranaysinghal.in");
        assertThat(saved.getRole()).isEqualTo(ManagerRole.SENIOR_MANAGER);
        assertThat(saved.getMobileNumber()).isEqualTo("+91 98765 43210");
        assertThat(response.getRole()).isEqualTo(ManagerRole.SENIOR_MANAGER);
    }

    @Test
    @DisplayName("trims surrounding whitespace before storing")
    void trimsValues() {
        when(managerRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        stubSave();

        managerCatalogService.createManager(request()
                .firstName("  Pranay  ")
                .lastName("  Singhal  ")
                .email("  pranay.singhal@pranaysinghal.in  ")
                .mobileNumber("  +91 98765 43210  ")
                .build());

        verify(managerRepository).saveAndFlush(managerCaptor.capture());
        Manager saved = managerCaptor.getValue();
        assertThat(saved.getFirstName()).isEqualTo("Pranay");
        assertThat(saved.getLastName()).isEqualTo("Singhal");
        assertThat(saved.getEmail()).isEqualTo("pranay.singhal@pranaysinghal.in");
        assertThat(saved.getMobileNumber()).isEqualTo("+91 98765 43210");
    }

    @Test
    @DisplayName("rejects an email another manager already holds")
    void rejectsDuplicateEmail() {
        when(managerRepository.existsByEmailIgnoreCase("pranay.singhal@pranaysinghal.in"))
                .thenReturn(true);

        assertThatThrownBy(() -> managerCatalogService.createManager(request().build()))
                .isInstanceOf(ManagerEmailAlreadyExistsException.class)
                .hasMessageContaining("pranay.singhal@pranaysinghal.in");

        verify(managerRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("maps a unique index violation from a concurrent create onto a conflict")
    void mapsIntegrityViolationToConflict() {
        when(managerRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(managerRepository.saveAndFlush(any(Manager.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> managerCatalogService.createManager(request().build()))
                .isInstanceOf(ManagerEmailAlreadyExistsException.class);
    }

    @Test
    @DisplayName("returns the stored manager on read by id")
    void readsManagerById() {
        Manager stored = stored();
        when(managerRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        ManagerResponse response = managerCatalogService.getManager(stored.getId());

        assertThat(response.getId()).isEqualTo(stored.getId());
        assertThat(response.getLastName()).isEqualTo("Singhal");
    }

    @Test
    @DisplayName("raises not found when reading an unknown id")
    void readFailsForUnknownId() {
        UUID missing = UUID.randomUUID();
        when(managerRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> managerCatalogService.getManager(missing))
                .isInstanceOf(ManagerNotFoundException.class)
                .hasMessageContaining(missing.toString());
    }

    @Test
    @DisplayName("lists managers ordered by last name then first name")
    void listsManagersOrderedByName() {
        when(managerRepository.findAll(any(Sort.class))).thenReturn(List.of(stored()));

        List<ManagerResponse> managers = managerCatalogService.listManagers();

        assertThat(managers).hasSize(1);
        assertThat(managers.getFirst().getLastName()).isEqualTo("Singhal");

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(managerRepository).findAll(sortCaptor.capture());
        assertThat(sortCaptor.getValue())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "lastName", "firstName"));
    }

    @Test
    @DisplayName("returns an empty list when nobody has been added")
    void listsEmptyCatalog() {
        when(managerRepository.findAll(any(Sort.class))).thenReturn(List.of());

        assertThat(managerCatalogService.listManagers()).isEmpty();
    }

    @Test
    @DisplayName("replaces a manager wholesale on update")
    void updatesManager() {
        Manager stored = stored();
        when(managerRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(managerRepository.existsByEmailIgnoreCaseAndIdNot(any(), any())).thenReturn(false);
        stubSave();

        ManagerResponse response = managerCatalogService.updateManager(stored.getId(),
                ManagerRequest.builder()
                        .firstName("Aarti")
                        .lastName("Desai")
                        .email("aarti.desai@pranaysinghal.in")
                        .role(ManagerRole.ASSOCIATE)
                        .mobileNumber("+91 99887 66554")
                        .build());

        verify(managerRepository).saveAndFlush(managerCaptor.capture());
        Manager saved = managerCaptor.getValue();
        assertThat(saved).isSameAs(stored);
        assertThat(saved.getFirstName()).isEqualTo("Aarti");
        assertThat(saved.getRole()).isEqualTo(ManagerRole.ASSOCIATE);
        assertThat(response.getEmail()).isEqualTo("aarti.desai@pranaysinghal.in");
    }

    @Test
    @DisplayName("lets a manager keep their own email on update")
    void updateAllowsKeepingOwnEmail() {
        Manager stored = stored();
        when(managerRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(managerRepository.existsByEmailIgnoreCaseAndIdNot(any(), any())).thenReturn(false);
        stubSave();

        managerCatalogService.updateManager(stored.getId(), request().build());

        verify(managerRepository).existsByEmailIgnoreCaseAndIdNot(
                eq("pranay.singhal@pranaysinghal.in"), eq(stored.getId()));
        verify(managerRepository).saveAndFlush(any(Manager.class));
    }

    @Test
    @DisplayName("rejects an update onto an email a different manager already holds")
    void updateRejectsDuplicateEmail() {
        Manager stored = stored();
        when(managerRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(managerRepository.existsByEmailIgnoreCaseAndIdNot(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> managerCatalogService.updateManager(stored.getId(),
                request().build()))
                .isInstanceOf(ManagerEmailAlreadyExistsException.class);

        verify(managerRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("raises not found when updating an unknown id")
    void updateFailsForUnknownId() {
        UUID missing = UUID.randomUUID();
        when(managerRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> managerCatalogService.updateManager(missing, request().build()))
                .isInstanceOf(ManagerNotFoundException.class);

        verify(managerRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("deletes a manager by id")
    void deletesManager() {
        Manager stored = stored();
        when(managerRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        managerCatalogService.deleteManager(stored.getId());

        verify(managerRepository).delete(stored);
    }

    @Test
    @DisplayName("raises not found when deleting an unknown id")
    void deleteFailsForUnknownId() {
        UUID missing = UUID.randomUUID();
        when(managerRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> managerCatalogService.deleteManager(missing))
                .isInstanceOf(ManagerNotFoundException.class);

        verify(managerRepository, never()).delete(any());
    }

    private ManagerRequest.ManagerRequestBuilder request() {
        return ManagerRequest.builder()
                .firstName("Pranay")
                .lastName("Singhal")
                .email("pranay.singhal@pranaysinghal.in")
                .role(ManagerRole.SENIOR_MANAGER)
                .mobileNumber("+91 98765 43210");
    }

    private Manager stored() {
        Manager manager = new Manager();
        manager.setId(UUID.randomUUID());
        manager.setFirstName("Pranay");
        manager.setLastName("Singhal");
        manager.setEmail("pranay.singhal@pranaysinghal.in");
        manager.setRole(ManagerRole.SENIOR_MANAGER);
        manager.setMobileNumber("+91 98765 43210");
        return manager;
    }

    private void stubSave() {
        when(managerRepository.saveAndFlush(any(Manager.class))).thenAnswer(invocation -> {
            Manager toSave = invocation.getArgument(0);
            if (toSave.getId() == null) {
                toSave.setId(UUID.randomUUID());
            }
            return toSave;
        });
    }
}
