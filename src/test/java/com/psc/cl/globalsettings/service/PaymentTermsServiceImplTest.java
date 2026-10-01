package com.psc.cl.globalsettings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.psc.cl.globalsettings.dto.PaymentTermsRequest;
import com.psc.cl.globalsettings.dto.PaymentTermsResponse;
import com.psc.cl.globalsettings.exception.PaymentTermsNotFoundException;
import com.psc.cl.globalsettings.model.PaymentTerms;
import com.psc.cl.globalsettings.repository.PaymentTermsRepository;
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
class PaymentTermsServiceImplTest {

    @Mock
    private PaymentTermsRepository paymentTermsRepository;

    @InjectMocks
    private PaymentTermsServiceImpl paymentTermsService;

    @Captor
    private ArgumentCaptor<PaymentTerms> paymentTermsCaptor;

    @Test
    @DisplayName("applies documented defaults when the request omits every field")
    void createsWithDefaultsWhenRequestIsEmpty() {
        when(paymentTermsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        stubSave();

        PaymentTermsSaveResult result = paymentTermsService.savePaymentTerms(
                PaymentTermsRequest.builder().build());

        verify(paymentTermsRepository).saveAndFlush(paymentTermsCaptor.capture());
        PaymentTerms saved = paymentTermsCaptor.getValue();
        assertThat(saved.getPaymentDueAfterDays()).isEqualTo(15);
        assertThat(saved.getMarkOverdueAfterDays()).isEqualTo(60);
        assertThat(saved.getPaymentReminderEnabled()).isFalse();
        assertThat(saved.getPaymentReminderDays()).isNull();
        assertThat(result.created()).isTrue();
        assertThat(result.paymentTerms().getPaymentDueAfterDays()).isEqualTo(15);
        assertThat(result.paymentTerms().getMarkOverdueAfterDays()).isEqualTo(60);
    }

    @Test
    @DisplayName("persists the supplied values when the request is fully populated")
    void createsWithSuppliedValues() {
        when(paymentTermsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        stubSave();

        PaymentTermsSaveResult result = paymentTermsService.savePaymentTerms(PaymentTermsRequest.builder()
                .paymentDueAfterDays(30)
                .markOverdueAfterDays(90)
                .paymentReminderEnabled(true)
                .paymentReminderDays(7)
                .build());

        PaymentTermsResponse response = result.paymentTerms();
        assertThat(result.created()).isTrue();
        assertThat(response.getPaymentDueAfterDays()).isEqualTo(30);
        assertThat(response.getMarkOverdueAfterDays()).isEqualTo(90);
        assertThat(response.getPaymentReminderEnabled()).isTrue();
        assertThat(response.getPaymentReminderDays()).isEqualTo(7);
    }

    @Test
    @DisplayName("drops the reminder lead time when reminders are disabled")
    void clearsReminderDaysWhenRemindersDisabled() {
        when(paymentTermsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        stubSave();

        PaymentTermsSaveResult result = paymentTermsService.savePaymentTerms(PaymentTermsRequest.builder()
                .paymentReminderEnabled(false)
                .paymentReminderDays(7)
                .build());

        assertThat(result.paymentTerms().getPaymentReminderEnabled()).isFalse();
        assertThat(result.paymentTerms().getPaymentReminderDays()).isNull();
    }

    @Test
    @DisplayName("overwrites the existing record instead of creating a second one")
    void updatesExistingRecord() {
        PaymentTerms existing = existing();
        when(paymentTermsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(existing));
        stubSave();

        PaymentTermsSaveResult result = paymentTermsService.savePaymentTerms(PaymentTermsRequest.builder()
                .paymentDueAfterDays(20)
                .markOverdueAfterDays(45)
                .paymentReminderEnabled(true)
                .paymentReminderDays(3)
                .build());

        verify(paymentTermsRepository).saveAndFlush(paymentTermsCaptor.capture());
        PaymentTerms saved = paymentTermsCaptor.getValue();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getId()).isEqualTo(existing.getId());
        assertThat(saved.getPaymentDueAfterDays()).isEqualTo(20);
        assertThat(saved.getMarkOverdueAfterDays()).isEqualTo(45);
        assertThat(saved.getPaymentReminderDays()).isEqualTo(3);
        assertThat(result.created()).isFalse();
    }

    @Test
    @DisplayName("resets omitted fields to their defaults because a save replaces the record wholesale")
    void updateResetsOmittedFieldsToDefaults() {
        PaymentTerms existing = existing();
        existing.setPaymentReminderEnabled(true);
        existing.setPaymentReminderDays(5);
        when(paymentTermsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(existing));
        stubSave();

        PaymentTermsSaveResult result = paymentTermsService.savePaymentTerms(
                PaymentTermsRequest.builder().build());

        assertThat(result.paymentTerms().getPaymentDueAfterDays()).isEqualTo(15);
        assertThat(result.paymentTerms().getMarkOverdueAfterDays()).isEqualTo(60);
        assertThat(result.paymentTerms().getPaymentReminderEnabled()).isFalse();
        assertThat(result.paymentTerms().getPaymentReminderDays()).isNull();
    }

    @Test
    @DisplayName("returns the stored terms on read")
    void readsStoredTerms() {
        PaymentTerms existing = existing();
        when(paymentTermsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(existing));

        PaymentTermsResponse response = paymentTermsService.getPaymentTerms();

        assertThat(response.getId()).isEqualTo(existing.getId());
        assertThat(response.getPaymentDueAfterDays()).isEqualTo(15);
        assertThat(response.getMarkOverdueAfterDays()).isEqualTo(60);
    }

    @Test
    @DisplayName("raises not found when payment terms have never been saved")
    void readFailsWhenNothingStored() {
        when(paymentTermsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentTermsService.getPaymentTerms())
                .isInstanceOf(PaymentTermsNotFoundException.class);
    }

    private void stubSave() {
        when(paymentTermsRepository.saveAndFlush(any(PaymentTerms.class))).thenAnswer(invocation -> {
            PaymentTerms toSave = invocation.getArgument(0);
            if (toSave.getId() == null) {
                toSave.setId(UUID.randomUUID());
            }
            return toSave;
        });
    }

    private PaymentTerms existing() {
        Instant now = Instant.now();
        return PaymentTerms.builder()
                .id(UUID.randomUUID())
                .paymentDueAfterDays(15)
                .markOverdueAfterDays(60)
                .paymentReminderEnabled(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
