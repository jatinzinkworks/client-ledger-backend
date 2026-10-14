package com.psc.cl.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.psc.cl.client.exception.CompanyNotFoundException;
import com.psc.cl.client.repository.ClientCompanyRepository;
import com.psc.cl.client.repository.ClientCompanyRepository.CompanyRef;
import com.psc.cl.ledger.dto.BulkPostingResponse;
import com.psc.cl.ledger.dto.CompanyPostingResult;
import com.psc.cl.ledger.dto.LedgerEntryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class BulkPostingServiceImplTest {

    /** 14 October 2026 in India. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T20:00:00Z"), ZoneId.of("Asia/Kolkata"));

    private static final UUID CLIENT_ID = UUID.fromString("7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35");

    @Mock
    private ClientCompanyRepository clientCompanyRepository;

    @Mock
    private LedgerService ledgerService;

    private BulkPostingServiceImpl bulkPostingService;

    @BeforeEach
    void setUp() {
        bulkPostingService = new BulkPostingServiceImpl(clientCompanyRepository, ledgerService, CLOCK);
    }

    @Test
    @DisplayName("posts every company, listing those posted for and leaving out those with nothing due")
    void postsEveryCompany() {
        CompanyRef billed = company("Mehta Textiles Pvt Ltd");
        CompanyRef nothingDue = company("Rohan Mehta HUF");
        when(clientCompanyRepository.findAllCompanyRefs()).thenReturn(List.of(billed, nothingDue));
        when(ledgerService.postDueServices(CLIENT_ID, billed.getCompanyId())).thenReturn(List.of(
                LedgerEntryResponse.builder().build(), LedgerEntryResponse.builder().build()));
        when(ledgerService.postDueServices(CLIENT_ID, nothingDue.getCompanyId())).thenReturn(List.of());

        BulkPostingResponse response = bulkPostingService.postAllDueServices();

        assertThat(response.getAsOf()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(response.getCompanyCount()).isEqualTo(2);
        assertThat(response.getEntriesPosted()).isEqualTo(2);
        assertThat(response.getCompanies()).extracting(CompanyPostingResult::getCompanyId)
                .containsExactly(billed.getCompanyId());
        assertThat(response.getCompanies().getFirst().getCompanyName()).isEqualTo("Mehta Textiles Pvt Ltd");
        assertThat(response.getCompanies().getFirst().getEntriesPosted()).isEqualTo(2);
        assertThat(response.getFailures()).isEmpty();
    }

    @Test
    @DisplayName("reports a company that fails and still posts the companies after it")
    void continuesPastFailure() {
        CompanyRef failing = company("Rohan Mehta HUF");
        CompanyRef after = company("Mehta Textiles Pvt Ltd");
        when(clientCompanyRepository.findAllCompanyRefs()).thenReturn(List.of(failing, after));
        CompanyNotFoundException failure = new CompanyNotFoundException(CLIENT_ID, failing.getCompanyId());
        when(ledgerService.postDueServices(CLIENT_ID, failing.getCompanyId())).thenThrow(failure);
        when(ledgerService.postDueServices(CLIENT_ID, after.getCompanyId())).thenReturn(List.of(
                LedgerEntryResponse.builder().build()));

        BulkPostingResponse response = bulkPostingService.postAllDueServices();

        verify(ledgerService).postDueServices(CLIENT_ID, after.getCompanyId());
        assertThat(response.getEntriesPosted()).isEqualTo(1);
        assertThat(response.getCompanies()).extracting(CompanyPostingResult::getCompanyId)
                .containsExactly(after.getCompanyId());
        assertThat(response.getFailures()).singleElement().satisfies(f -> {
            assertThat(f.getCompanyId()).isEqualTo(failing.getCompanyId());
            assertThat(f.getCompanyName()).isEqualTo("Rohan Mehta HUF");
            assertThat(f.getError()).isEqualTo(failure.getMessage());
        });
    }

    @Test
    @DisplayName("posts nothing when there are no companies")
    void postsNothingWithoutCompanies() {
        when(clientCompanyRepository.findAllCompanyRefs()).thenReturn(List.of());

        BulkPostingResponse response = bulkPostingService.postAllDueServices();

        assertThat(response.getCompanyCount()).isZero();
        assertThat(response.getEntriesPosted()).isZero();
        assertThat(response.getCompanies()).isEmpty();
        assertThat(response.getFailures()).isEmpty();
    }

    private static CompanyRef company(String name) {
        UUID companyId = UUID.randomUUID();
        return new CompanyRef() {
            @Override
            public UUID getCompanyId() {
                return companyId;
            }

            @Override
            public UUID getClientId() {
                return CLIENT_ID;
            }

            @Override
            public String getCompanyName() {
                return name;
            }
        };
    }
}
