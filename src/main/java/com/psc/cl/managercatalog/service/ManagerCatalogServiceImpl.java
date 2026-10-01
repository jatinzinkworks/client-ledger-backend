package com.psc.cl.managercatalog.service;

import com.psc.cl.managercatalog.dto.ManagerRequest;
import com.psc.cl.managercatalog.dto.ManagerResponse;
import com.psc.cl.managercatalog.exception.ManagerEmailAlreadyExistsException;
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

import java.util.List;
import java.util.UUID;

/**
 * Default {@link ManagerCatalogService} backed by {@link ManagerRepository}.
 */
@Service
@RequiredArgsConstructor
public class ManagerCatalogServiceImpl implements ManagerCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ManagerCatalogServiceImpl.class);

    private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, "lastName", "firstName");

    private final ManagerRepository managerRepository;

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
        return ManagerResponse.from(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManagerResponse> listManagers() {
        return managerRepository.findAll(BY_NAME).stream()
                .map(ManagerResponse::from)
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
        managerRepository.delete(manager);
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
