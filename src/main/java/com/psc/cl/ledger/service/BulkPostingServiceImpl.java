package com.psc.cl.ledger.service;

import com.psc.cl.client.repository.ClientCompanyRepository;
import com.psc.cl.client.repository.ClientCompanyRepository.CompanyRef;
import com.psc.cl.ledger.dto.BulkPostingResponse;
import com.psc.cl.ledger.dto.CompanyPostingFailure;
import com.psc.cl.ledger.dto.CompanyPostingResult;
import com.psc.cl.ledger.dto.LedgerEntryResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link BulkPostingService}.
 *
 * <p>Deliberately not transactional: each company is posted through {@link LedgerService}, whose
 * own transaction commits or rolls back that company alone. Holding every company in a single
 * transaction would lose every company's entries to one failure.
 */
@Service
@RequiredArgsConstructor
public class BulkPostingServiceImpl implements BulkPostingService {

    private static final Logger log = LoggerFactory.getLogger(BulkPostingServiceImpl.class);

    private final ClientCompanyRepository clientCompanyRepository;

    private final LedgerService ledgerService;

    private final Clock clock;

    @Override
    public BulkPostingResponse postAllDueServices() {
        LocalDate asOf = LocalDate.now(clock);
        List<CompanyRef> companies = clientCompanyRepository.findAllCompanyRefs();
        log.info("Posting due services for {} companies as of {}", companies.size(), asOf);

        List<CompanyPostingResult> posted = new ArrayList<>();
        List<CompanyPostingFailure> failures = new ArrayList<>();
        int entriesPosted = 0;
        for (CompanyRef company : companies) {
            try {
                List<LedgerEntryResponse> entries = ledgerService.postDueServices(company.getClientId(),
                        company.getCompanyId());
                if (!entries.isEmpty()) {
                    entriesPosted += entries.size();
                    posted.add(CompanyPostingResult.builder()
                            .clientId(company.getClientId())
                            .companyId(company.getCompanyId())
                            .companyName(company.getCompanyName())
                            .entriesPosted(entries.size())
                            .build());
                }
            } catch (RuntimeException e) {
                // Reported in the response and logged; the remaining companies are still posted.
                log.error("Failed to post due services for company {} of client {}", company.getCompanyId(),
                        company.getClientId(), e);
                failures.add(CompanyPostingFailure.builder()
                        .clientId(company.getClientId())
                        .companyId(company.getCompanyId())
                        .companyName(company.getCompanyName())
                        .error(e.getMessage())
                        .build());
            }
        }
        log.info("Posted {} ledger entries for {} of {} companies as of {}; {} failed", entriesPosted,
                posted.size(), companies.size(), asOf, failures.size());

        return BulkPostingResponse.builder()
                .asOf(asOf)
                .companyCount(companies.size())
                .entriesPosted(entriesPosted)
                .companies(posted)
                .failures(failures)
                .build();
    }
}
