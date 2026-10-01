package com.psc.cl.globalsettings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.psc.cl.globalsettings.dto.FirmDetailsRequest;
import com.psc.cl.globalsettings.dto.FirmDetailsResponse;
import com.psc.cl.globalsettings.exception.FirmDetailsNotFoundException;
import com.psc.cl.globalsettings.model.FirmDetails;
import com.psc.cl.globalsettings.repository.FirmDetailsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class FirmDetailsServiceImplTest {

    @Mock
    private FirmDetailsRepository firmDetailsRepository;

    @InjectMocks
    private FirmDetailsServiceImpl firmDetailsService;

    @Captor
    private ArgumentCaptor<FirmDetails> firmDetailsCaptor;

    @Test
    @DisplayName("stores every supplied field on a first save")
    void createsFirmDetails() {
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        stubSave();

        FirmDetailsSaveResult result = firmDetailsService.saveFirmDetails(fullRequest().build());

        FirmDetailsResponse response = result.firmDetails();
        assertThat(result.created()).isTrue();
        assertThat(response.getFirmName()).isEqualTo("Pranay Singhal & Company");
        assertThat(response.getGstin()).isEqualTo("27AAKFP4471M1ZS");
        assertThat(response.getFirmRegistrationNo()).isEqualTo("FRN 0148290W");
        assertThat(response.getEmail()).isEqualTo("accounts@pranaysinghal.in");
        assertThat(response.getPhone()).isEqualTo("+91 22 4012 8890");
        assertThat(response.getAddress()).contains("Andheri East");
    }

    @Test
    @DisplayName("trims surrounding whitespace and upper cases the GSTIN before storing")
    void normalisesValues() {
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        stubSave();

        firmDetailsService.saveFirmDetails(FirmDetailsRequest.builder()
                .firmName("  Pranay Singhal & Company  ")
                .gstin("27aakfp4471m1zs")
                .email("  accounts@pranaysinghal.in ")
                .address("  302, Hubtown Solaris, Mumbai 400069  ")
                .build());

        verify(firmDetailsRepository).saveAndFlush(firmDetailsCaptor.capture());
        FirmDetails saved = firmDetailsCaptor.getValue();
        assertThat(saved.getFirmName()).isEqualTo("Pranay Singhal & Company");
        assertThat(saved.getGstin()).isEqualTo("27AAKFP4471M1ZS");
        assertThat(saved.getEmail()).isEqualTo("accounts@pranaysinghal.in");
        assertThat(saved.getAddress()).isEqualTo("302, Hubtown Solaris, Mumbai 400069");
    }

    @Test
    @DisplayName("stores a blank optional field as null rather than an empty string")
    void blankOptionalBecomesNull() {
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        stubSave();

        FirmDetailsSaveResult result = firmDetailsService.saveFirmDetails(FirmDetailsRequest.builder()
                .firmName("Pranay Singhal & Company")
                .gstin("27AAKFP4471M1ZS")
                .address("302, Hubtown Solaris, Mumbai 400069")
                .firmRegistrationNo("   ")
                .build());

        assertThat(result.firmDetails().getFirmRegistrationNo()).isNull();
    }

    @Test
    @DisplayName("overwrites the existing record instead of creating a second one")
    void updatesExistingRecord() {
        FirmDetails existing = existing();
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(existing));
        stubSave();

        FirmDetailsSaveResult result = firmDetailsService.saveFirmDetails(FirmDetailsRequest.builder()
                .firmName("Pranay Singhal LLP")
                .gstin("27AAKFP4471M1ZS")
                .address("302, Hubtown Solaris, Mumbai 400069")
                .build());

        verify(firmDetailsRepository).saveAndFlush(firmDetailsCaptor.capture());
        FirmDetails saved = firmDetailsCaptor.getValue();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getId()).isEqualTo(existing.getId());
        assertThat(saved.getFirmName()).isEqualTo("Pranay Singhal LLP");
        assertThat(result.created()).isFalse();
    }

    @Test
    @DisplayName("clears omitted optional fields because a save replaces the record wholesale")
    void updateClearsOmittedFields() {
        FirmDetails existing = existing();
        existing.setPhone("+91 22 4012 8890");
        existing.setFirmRegistrationNo("FRN 0148290W");
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(existing));
        stubSave();

        FirmDetailsSaveResult result = firmDetailsService.saveFirmDetails(FirmDetailsRequest.builder()
                .firmName("Pranay Singhal & Company")
                .gstin("27AAKFP4471M1ZS")
                .address("302, Hubtown Solaris, Mumbai 400069")
                .build());

        assertThat(result.firmDetails().getPhone()).isNull();
        assertThat(result.firmDetails().getFirmRegistrationNo()).isNull();
    }

    @Test
    @DisplayName("returns the stored details on read")
    void readsStoredDetails() {
        FirmDetails existing = existing();
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(existing));

        FirmDetailsResponse response = firmDetailsService.getFirmDetails();

        assertThat(response.getId()).isEqualTo(existing.getId());
        assertThat(response.getFirmName()).isEqualTo("Pranay Singhal & Company");
    }

    @Test
    @DisplayName("raises not found when firm details have never been saved")
    void readFailsWhenNothingStored() {
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> firmDetailsService.getFirmDetails())
                .isInstanceOf(FirmDetailsNotFoundException.class);
    }

    private FirmDetailsRequest.FirmDetailsRequestBuilder fullRequest() {
        return FirmDetailsRequest.builder()
                .firmName("Pranay Singhal & Company")
                .gstin("27AAKFP4471M1ZS")
                .firmRegistrationNo("FRN 0148290W")
                .email("accounts@pranaysinghal.in")
                .phone("+91 22 4012 8890")
                .address("302, Hubtown Solaris, N.S. Phadke Marg, Andheri East, Mumbai 400069");
    }

    private void stubSave() {
        when(firmDetailsRepository.saveAndFlush(any(FirmDetails.class))).thenAnswer(invocation -> {
            FirmDetails toSave = invocation.getArgument(0);
            if (toSave.getId() == null) {
                toSave.setId(UUID.randomUUID());
            }
            return toSave;
        });
    }

    private FirmDetails existing() {
        Instant now = Instant.now();
        return FirmDetails.builder()
                .id(UUID.randomUUID())
                .firmName("Pranay Singhal & Company")
                .gstin("27AAKFP4471M1ZS")
                .address("302, Hubtown Solaris, Mumbai 400069")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
