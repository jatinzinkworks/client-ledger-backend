package com.psc.cl.client.service;

import com.psc.cl.client.dto.ClientDetails;
import com.psc.cl.client.dto.ClientManagerBulkUpdateRequest;
import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.ClientResponse;
import com.psc.cl.client.dto.ClientSummaryResponse;
import com.psc.cl.client.dto.ClientUpdateRequest;
import com.psc.cl.client.dto.NewCompanyRequest;
import com.psc.cl.client.exception.AssignedManagerNotFoundException;
import com.psc.cl.client.exception.ClientHasLedgerEntriesException;
import com.psc.cl.client.exception.ClientHasServicesException;
import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.exception.ClientsNotFoundException;
import com.psc.cl.client.model.Client;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.client.repository.ServiceAssignmentRepository;
import com.psc.cl.exception.ResourceInUseException;
import com.psc.cl.ledger.dto.OpeningBalance;
import com.psc.cl.ledger.service.LedgerService;
import com.psc.cl.managercatalog.model.Manager;
import com.psc.cl.managercatalog.repository.ManagerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default {@link ClientService} backed by {@link ClientRepository}.
 */
@Service
@RequiredArgsConstructor
public class ClientServiceImpl implements ClientService {

    private static final Logger log = LoggerFactory.getLogger(ClientServiceImpl.class);

    private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, "lastName", "firstName");

    /** What a client with no ledger entries owes, at the ledger's scale. */
    private static final BigDecimal NOTHING_PENDING = new BigDecimal("0.00");

    private final ClientRepository clientRepository;

    private final ServiceAssignmentRepository serviceAssignmentRepository;

    private final ManagerRepository managerRepository;

    private final LedgerService ledgerService;

    @Override
    @Transactional
    public ClientResponse createClient(ClientRequest request) {
        Manager manager = requireAssignableManager(request.managerId());

        Client client = new Client();
        applyClient(request, client);
        request.companies().forEach(company -> client.addCompany(Companies.newCompany(company)));

        Client saved = clientRepository.save(client);
        int openingBalances = recordOpeningBalances(saved, request.companies());
        log.info("Created client {} assigned to manager {} with {} company(ies), {} with an opening balance",
                saved.getId(), saved.getManagerId(), saved.getCompanies().size(), openingBalances);

        return ClientResponse.from(saved, manager, openingBalancesOf(saved));
    }

    @Override
    @Transactional
    public ClientResponse updateClient(UUID id, ClientUpdateRequest request) {
        Client client = clientRepository.findById(id).orElseThrow(() -> new ClientNotFoundException(id));
        Manager manager = requireAssignableManager(request.managerId());

        applyClient(request, client);
        clientRepository.flush();
        log.info("Updated client {} assigned to manager {}", id, client.getManagerId());

        return ClientResponse.from(client, manager, openingBalancesOf(client));
    }

    @Override
    @Transactional
    public List<ClientSummaryResponse> updateManager(ClientManagerBulkUpdateRequest request) {
        Manager manager = requireAssignableManager(request.managerId());
        // Every mention of a client asks for the same manager, so a repeat is assigned once.
        Set<UUID> clientIds = new LinkedHashSet<>(request.clientIds());

        // One query for every client, and every unknown identifier reported together.
        Map<UUID, Client> clientsById = clientRepository.findByIdIn(clientIds).stream()
                .collect(Collectors.toMap(Client::getId, Function.identity()));
        List<UUID> missing = clientIds.stream()
                .filter(id -> !clientsById.containsKey(id))
                .toList();
        if (!missing.isEmpty()) {
            throw new ClientsNotFoundException(missing);
        }

        List<Client> clients = clientIds.stream().map(clientsById::get).toList();
        clients.forEach(client -> {
            log.debug("Reassigning client {} from manager {} to {}", client.getId(), client.getManagerId(),
                    manager.getId());
            client.setManagerId(manager.getId());
        });

        try {
            // Flushing here stamps updatedAt before the clients are returned.
            clientRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // The foreign key caught the manager being deleted concurrently, after the check above.
            log.warn("Reassigning clients to manager {} was rejected by the database", manager.getId(), ex);
            throw new AssignedManagerNotFoundException(manager.getId());
        }
        log.info("Assigned {} client(s) to manager {}", clients.size(), manager.getId());

        Map<UUID, BigDecimal> pending = ledgerService.pendingByClient(List.copyOf(clientIds));
        return clients.stream()
                .map(client -> ClientSummaryResponse.from(client, manager,
                        pending.getOrDefault(client.getId(), NOTHING_PENDING)))
                .toList();
    }

    @Override
    @Transactional
    public void deleteClient(UUID id) {
        Client client = clientRepository.findById(id).orElseThrow(() -> new ClientNotFoundException(id));

        Set<UUID> companyIds = client.getCompanies().stream()
                .map(ClientCompany::getId)
                .collect(Collectors.toSet());
        if (!companyIds.isEmpty() && serviceAssignmentRepository.existsByCompanyIdIn(companyIds)) {
            throw new ClientHasServicesException(id);
        }
        // Payments need no service, so a client without services may still have them. Opening balances
        // alone are no history of dealings with the firm, so they go with the client.
        if (ledgerService.clientHasEntriesBesidesOpeningBalances(id)) {
            throw new ClientHasLedgerEntriesException(id);
        }
        ledgerService.removeClientOpeningBalances(id);

        try {
            clientRepository.delete(client);
            clientRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // A foreign key caught a service assigned, or a ledger entry recorded, after the checks above.
            log.warn("Delete of client {} was rejected by the database: services or ledger entries were added",
                    id, ex);
            throw new ResourceInUseException("Client " + id + " was given a service or a ledger entry while it "
                    + "was being deleted, and cannot be deleted.");
        }
        log.info("Deleted client {} with {} company(ies)", id, companyIds.size());
    }

    @Override
    @Transactional(readOnly = true)
    public ClientResponse getClient(UUID id) {
        Client client = clientRepository.findById(id).orElseThrow(() -> new ClientNotFoundException(id));
        BigDecimal pending = ledgerService.pendingByClient(List.of(id)).getOrDefault(id, NOTHING_PENDING);
        return ClientResponse.from(client, requireManager(client.getManagerId()), openingBalancesOf(client),
                pending);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientSummaryResponse> listClients(UUID managerId) {
        List<Client> clients = managerId == null
                ? clientRepository.findAll(BY_NAME)
                : clientRepository.findByManagerId(managerId, BY_NAME);

        // One query for every manager involved, rather than one per client.
        Set<UUID> managerIds = clients.stream().map(Client::getManagerId).collect(Collectors.toSet());
        Map<UUID, Manager> managersById = managerRepository.findAllById(managerIds).stream()
                .collect(Collectors.toMap(Manager::getId, Function.identity()));
        // Likewise one ledger query for every client's pending amount.
        Map<UUID, BigDecimal> pending = ledgerService.pendingByClient(clients.stream().map(Client::getId).toList());

        return clients.stream()
                .map(client -> ClientSummaryResponse.from(client,
                        assignedManager(managersById.get(client.getManagerId()), client.getManagerId()),
                        pending.getOrDefault(client.getId(), NOTHING_PENDING)))
                .toList();
    }

    /**
     * Records the opening balance of each new company that brings one, in the same transaction, so a
     * balance that cannot be recorded leaves no client behind either.
     *
     * @param client    the saved client, its companies in the order of the request
     * @param companies the companies as requested
     * @return how many opening balances were recorded
     */
    /** The opening balances of a client's companies, by company, for the client's response. */
    private Map<UUID, OpeningBalance> openingBalancesOf(Client client) {
        return ledgerService.openingBalances(client.getCompanies().stream().map(ClientCompany::getId).toList());
    }

    private int recordOpeningBalances(Client client, List<NewCompanyRequest> companies) {
        int recorded = 0;
        for (int i = 0; i < companies.size(); i++) {
            NewCompanyRequest company = companies.get(i);
            if (company.hasOpeningBalance()) {
                ledgerService.recordOpeningBalance(client.getId(), client.getCompanies().get(i).getId(),
                        company.openingBalance(), company.openingBalanceDate());
                recorded++;
            }
        }
        return recorded;
    }

    /**
     * An omitted discount resets to the default, so nothing is left behind from a previous version.
     */
    private void applyClient(ClientDetails request, Client client) {
        client.setFirstName(request.firstName().trim());
        client.setLastName(request.lastName().trim());
        client.setPhone(request.phone().trim());
        client.setEmail(request.email().trim());
        client.setManagerId(request.managerId());
        client.setDiscountPercent(Percentages.orElse(request.discountPercent(), Client.DEFAULT_DISCOUNT_PERCENT));
    }

    private Manager requireAssignableManager(UUID managerId) {
        return managerRepository.findById(managerId)
                .orElseThrow(() -> new AssignedManagerNotFoundException(managerId));
    }

    /**
     * The foreign key guarantees an assigned manager exists, so a miss here means the data is
     * inconsistent and is reported as a server error.
     */
    private Manager requireManager(UUID managerId) {
        return assignedManager(managerRepository.findById(managerId).orElse(null), managerId);
    }

    private Manager assignedManager(Manager manager, UUID managerId) {
        if (manager == null) {
            throw new IllegalStateException("Assigned manager " + managerId + " does not exist");
        }
        return manager;
    }
}
