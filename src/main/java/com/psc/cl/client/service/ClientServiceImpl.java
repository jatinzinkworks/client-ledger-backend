package com.psc.cl.client.service;

import com.psc.cl.client.dto.ClientDetails;
import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.ClientResponse;
import com.psc.cl.client.dto.ClientSummaryResponse;
import com.psc.cl.client.dto.ClientUpdateRequest;
import com.psc.cl.client.exception.AssignedManagerNotFoundException;
import com.psc.cl.client.exception.ClientHasServicesException;
import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.model.Client;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.client.repository.ServiceAssignmentRepository;
import com.psc.cl.managercatalog.model.Manager;
import com.psc.cl.managercatalog.repository.ManagerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final ClientRepository clientRepository;

    private final ServiceAssignmentRepository serviceAssignmentRepository;

    private final ManagerRepository managerRepository;

    @Override
    @Transactional
    public ClientResponse createClient(ClientRequest request) {
        Manager manager = requireAssignableManager(request.managerId());

        Client client = new Client();
        applyClient(request, client);
        request.companies().forEach(company -> client.addCompany(Companies.newCompany(company, client)));

        Client saved = clientRepository.save(client);
        log.info("Created client {} assigned to manager {} with {} company(ies)",
                saved.getId(), saved.getManagerId(), saved.getCompanies().size());

        return ClientResponse.from(saved, manager);
    }

    @Override
    @Transactional
    public ClientResponse updateClient(UUID id, ClientUpdateRequest request) {
        Client client = clientRepository.findById(id).orElseThrow(() -> new ClientNotFoundException(id));
        Manager manager = requireAssignableManager(request.managerId());

        applyClient(request, client);
        clientRepository.flush();
        log.info("Updated client {} assigned to manager {}", id, client.getManagerId());

        return ClientResponse.from(client, manager);
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

        try {
            clientRepository.delete(client);
            clientRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // The foreign key caught a service assigned concurrently, after the check above.
            log.warn("Delete of client {} was rejected by the database: services are assigned", id, ex);
            throw new ClientHasServicesException(id);
        }
        log.info("Deleted client {} with {} company(ies)", id, companyIds.size());
    }

    @Override
    @Transactional(readOnly = true)
    public ClientResponse getClient(UUID id) {
        Client client = clientRepository.findById(id).orElseThrow(() -> new ClientNotFoundException(id));
        return ClientResponse.from(client, requireManager(client.getManagerId()));
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

        return clients.stream()
                .map(client -> ClientSummaryResponse.from(client,
                        assignedManager(managersById.get(client.getManagerId()), client.getManagerId())))
                .toList();
    }

    /** An omitted discount resets to the default, so nothing is left behind from a previous version. */
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
