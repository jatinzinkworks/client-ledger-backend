package com.psc.cl.managercatalog.service;

import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.client.repository.ClientRepository.ManagerClientCount;
import com.psc.cl.client.service.ClientService;
import com.psc.cl.ledger.service.LedgerService;
import com.psc.cl.managercatalog.dto.ManagerRequest;
import com.psc.cl.managercatalog.dto.ManagerResponse;
import com.psc.cl.managercatalog.exception.ManagerEmailAlreadyExistsException;
import com.psc.cl.managercatalog.exception.ManagerHasClientsException;
import com.psc.cl.managercatalog.exception.ManagerNotFoundException;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link ManagerCatalogService} backed by {@link ManagerRepository}.
 */
@Service
@RequiredArgsConstructor
public class ManagerCatalogServiceImpl implements ManagerCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ManagerCatalogServiceImpl.class);

    private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, "lastName", "firstName");

    /** What the clients of a manager with no ledger entries owe, at the ledger's scale. */
    private static final BigDecimal NOTHING_PENDING = new BigDecimal("0.00");

    private final ManagerRepository managerRepository;

    private final ClientRepository clientRepository;

    private final LedgerService ledgerService;

    private final ClientService clientService;

    @Override
    @Transactional
    public ManagerResponse createManager(ManagerRequest request) {
        String email = request.email().trim();
        if (managerRepository.existsByEmailIgnoreCase(email)) {
            throw new ManagerEmailAlreadyExistsException(email);
        }

        Manager manager = new Manager();
        apply(request, email, manager);

        Manager saved = persist(manager, email);
        log.info("Created manager {} with role {}", saved.getId(), saved.getRole());

        return ManagerResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ManagerResponse getManager(UUID id) {
        Manager manager = require(id);
        // The client list's own entries, so they read the same here as there.
        return ManagerResponse.from(manager, clientService.listClients(id), ledgerService.pendingForManager(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManagerResponse> listManagers() {
        // One grouped query each for every manager's client count and pending, rather than one per manager.
        Map<UUID, Long> clientCounts = clientRepository.countClientsByManager().stream()
                .collect(Collectors.toMap(ManagerClientCount::getManagerId, ManagerClientCount::getClientCount));
        Map<UUID, BigDecimal> pending = ledgerService.pendingByManager();

        return managerRepository.findAll(BY_NAME).stream()
                .map(manager -> ManagerResponse.from(manager, clientCounts.getOrDefault(manager.getId(), 0L),
                        pending.getOrDefault(manager.getId(), NOTHING_PENDING)))
                .toList();
    }

    @Override
    @Transactional
    public ManagerResponse updateManager(UUID id, ManagerRequest request) {
        Manager manager = require(id);

        String email = request.email().trim();
        if (managerRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ManagerEmailAlreadyExistsException(email);
        }

        apply(request, email, manager);

        Manager saved = persist(manager, email);
        log.info("Updated manager {} with role {}", saved.getId(), saved.getRole());

        return ManagerResponse.from(saved);
    }

    @Override
    @Transactional
    public void deleteManager(UUID id) {
        Manager manager = require(id);

        long assignedClients = clientRepository.countByManagerId(id);
        if (assignedClients > 0) {
            throw new ManagerHasClientsException(id, assignedClients);
        }

        try {
            managerRepository.delete(manager);
            managerRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // The foreign key caught a client assigned concurrently, after the check above.
            log.warn("Delete of manager {} was rejected by the database: clients are assigned", id, ex);
            throw new ManagerHasClientsException(id);
        }
        log.info("Deleted manager {}", id);
    }

    private void apply(ManagerRequest request, String email, Manager manager) {
        manager.setFirstName(request.firstName().trim());
        manager.setLastName(request.lastName().trim());
        manager.setEmail(email);
        manager.setRole(request.role());
        manager.setMobileNumber(request.mobileNumber().trim());
    }

    private Manager persist(Manager manager, String email) {
        try {
            return managerRepository.saveAndFlush(manager);
        } catch (DataIntegrityViolationException ex) {
            // The unique index caught a concurrent write that slipped past the email check above.
            log.warn("Concurrent write for manager email was rejected by the database", ex);
            throw new ManagerEmailAlreadyExistsException(email);
        }
    }

    private Manager require(UUID id) {
        return managerRepository.findById(id).orElseThrow(() -> new ManagerNotFoundException(id));
    }
}
