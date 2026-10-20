package com.psc.cl.client.service;

import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.ClientResponse;
import com.psc.cl.client.dto.CompanyRequest;
import com.psc.cl.client.dto.CompanyResponse;
import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.exception.CompanyHasLedgerEntriesException;
import com.psc.cl.client.exception.CompanyHasServicesException;
import com.psc.cl.client.exception.CompanyLimitExceededException;
import com.psc.cl.client.exception.CompanyNotFoundException;
import com.psc.cl.client.exception.LastCompanyRemovalException;
import com.psc.cl.client.model.Client;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.repository.ClientCompanyRepository;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.client.repository.CompanyServiceCount;
import com.psc.cl.client.repository.ServiceAssignmentRepository;
import com.psc.cl.exception.ResourceInUseException;
import com.psc.cl.ledger.dto.CompanyBalance;
import com.psc.cl.ledger.dto.OpeningBalance;
import com.psc.cl.ledger.model.FinancialYear;
import com.psc.cl.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link ClientCompanyService}. Companies are reached through their client, whose row is
 * locked whenever the number of companies it holds is what decides the outcome.
 */
@Service
@RequiredArgsConstructor
public class ClientCompanyServiceImpl implements ClientCompanyService {

    private static final Logger log = LoggerFactory.getLogger(ClientCompanyServiceImpl.class);

    private final ClientRepository clientRepository;

    private final ClientCompanyRepository clientCompanyRepository;

    private final ServiceAssignmentRepository serviceAssignmentRepository;

    private final LedgerService ledgerService;

    @Override
    @Transactional(readOnly = true)
    public List<CompanyResponse> listCompanies(UUID clientId, FinancialYear financialYear) {
        Client client = requireClient(clientId);
        Set<UUID> companyIds = client.getCompanies().stream()
                .map(ClientCompany::getId)
                .collect(Collectors.toSet());

        // One query for every company of the client, rather than one per company.
        Map<UUID, Long> serviceCounts = serviceAssignmentRepository.countByCompanyIdIn(companyIds).stream()
                .collect(Collectors.toMap(CompanyServiceCount::getCompanyId, CompanyServiceCount::getServiceCount));
        // Likewise one ledger query for every company's balance, and only when a year is asked for.
        Map<UUID, CompanyBalance> balances = financialYear == null
                ? Map.of()
                : ledgerService.companyBalances(clientId, financialYear);
        Map<UUID, OpeningBalance> openings = ledgerService.openingBalances(companyIds);

        return client.getCompanies().stream()
                .map(company -> {
                    long serviceCount = serviceCounts.getOrDefault(company.getId(), 0L);
                    OpeningBalance opening = openings.get(company.getId());
                    return financialYear == null
                            ? CompanyResponse.from(company, serviceCount, opening)
                            : CompanyResponse.from(company, serviceCount, financialYear.label(),
                                    balances.getOrDefault(company.getId(), CompanyBalance.ZERO), opening);
                })
                .sorted(ClientResponse.BY_COMPANY_NAME)
                .toList();
    }

    @Override
    @Transactional
    public CompanyResponse addCompany(UUID clientId, CompanyRequest request) {
        // Locked so concurrent additions cannot together take the client past the limit.
        Client client = clientRepository.findByIdForUpdate(clientId)
                .orElseThrow(() -> new ClientNotFoundException(clientId));
        if (client.getCompanies().size() >= ClientRequest.MAX_COMPANIES) {
            throw new CompanyLimitExceededException(clientId, ClientRequest.MAX_COMPANIES);
        }

        ClientCompany company = Companies.newCompany(request);
        client.addCompany(company);
        client.setUpdatedAt(Instant.now());

        // Flushing here assigns the new company its identifier, which its opening balance needs.
        clientRepository.flush();
        if (request.hasOpeningBalance()) {
            ledgerService.recordOpeningBalance(clientId, company.getId(), request.openingBalance(),
                    request.openingBalanceDate());
        }
        log.info("Added company {} to client {}, with an opening balance: {}", company.getId(), clientId,
                request.hasOpeningBalance());

        // A company just added has an opening balance only when it brought one.
        return CompanyResponse.from(company, request.hasOpeningBalance() ? openingOf(company.getId()) : null);
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyResponse getCompany(UUID clientId, UUID companyId) {
        if (!clientRepository.existsById(clientId)) {
            throw new ClientNotFoundException(clientId);
        }
        return clientCompanyRepository.findByIdAndClientId(companyId, clientId)
                .map(company -> CompanyResponse.from(company, openingOf(companyId)))
                .orElseThrow(() -> new CompanyNotFoundException(clientId, companyId));
    }

    @Override
    @Transactional
    public CompanyResponse updateCompany(UUID clientId, UUID companyId, CompanyRequest request) {
        Client client = requireClient(clientId);
        ClientCompany company = requireCompany(client, companyId);

        Companies.apply(request, company);
        // A change to a company alone would not otherwise make the client row dirty.
        client.setUpdatedAt(Instant.now());

        clientRepository.flush();
        // An omitted opening balance leaves the company's as it is.
        if (request.openingBalance() != null) {
            ledgerService.setOpeningBalance(clientId, companyId, request.openingBalance(),
                    request.openingBalanceDate());
        }
        log.info("Updated company {} of client {}", companyId, clientId);

        return CompanyResponse.from(company, openingOf(companyId));
    }

    @Override
    @Transactional
    public void deleteCompany(UUID clientId, UUID companyId) {
        // Locked so concurrent deletions cannot together remove the client's last company.
        Client client = clientRepository.findByIdForUpdate(clientId)
                .orElseThrow(() -> new ClientNotFoundException(clientId));
        ClientCompany company = requireCompany(client, companyId);

        if (client.getCompanies().size() == 1) {
            throw new LastCompanyRemovalException(clientId, companyId);
        }
        if (serviceAssignmentRepository.existsByCompanyIdIn(Set.of(companyId))) {
            throw new CompanyHasServicesException(clientId, companyId);
        }
        // Payments need no service, so a company without services may still have them. An opening balance
        // alone is no history of dealings with the firm, so it goes with the company.
        if (ledgerService.companyHasEntriesBesidesOpeningBalance(companyId)) {
            throw new CompanyHasLedgerEntriesException(clientId, companyId);
        }
        ledgerService.removeCompanyOpeningBalance(companyId);

        client.getCompanies().remove(company);
        client.setUpdatedAt(Instant.now());
        try {
            clientRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // A foreign key caught a service assigned, or a ledger entry recorded, after the checks above.
            log.warn("Delete of company {} was rejected by the database: services or ledger entries were added",
                    companyId, ex);
            throw new ResourceInUseException("Company " + companyId + " of client " + clientId + " was given a "
                    + "service or a ledger entry while it was being deleted, and cannot be deleted.");
        }
        log.info("Deleted company {} of client {}", companyId, clientId);
    }

    /** A company's opening balance, as its ledger now holds it, or null when it has none. */
    private OpeningBalance openingOf(UUID companyId) {
        return ledgerService.openingBalances(Set.of(companyId)).get(companyId);
    }

    private Client requireClient(UUID clientId) {
        return clientRepository.findById(clientId).orElseThrow(() -> new ClientNotFoundException(clientId));
    }

    private ClientCompany requireCompany(Client client, UUID companyId) {
        return client.getCompanies().stream()
                .filter(candidate -> candidate.getId().equals(companyId))
                .findFirst()
                .orElseThrow(() -> new CompanyNotFoundException(client.getId(), companyId));
    }
}
