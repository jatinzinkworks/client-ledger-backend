package com.psc.cl.client.controller;

import com.psc.cl.client.dto.ServiceAssignmentRequest;
import com.psc.cl.client.dto.ServiceAssignmentResponse;
import com.psc.cl.client.dto.ServiceAssignmentUpdateRequest;
import com.psc.cl.client.service.ServiceAssignmentService;
import com.psc.cl.ledger.model.FinancialYear;
import com.psc.cl.ledger.service.FinancialYears;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Single entry point for the services delivered to a company of a client.
 */
@RestController
@RequiredArgsConstructor
public class ServiceAssignmentController implements ServiceAssignmentApi {

    private static final Logger log = LoggerFactory.getLogger(ServiceAssignmentController.class);

    private final ServiceAssignmentService serviceAssignmentService;

    @Override
    public ResponseEntity<ServiceAssignmentResponse> assignService(UUID clientId, UUID companyId,
                                                                   ServiceAssignmentRequest request) {
        log.debug("Received request to assign a service to company {} of client {}", companyId, clientId);
        ServiceAssignmentResponse response = serviceAssignmentService.assignService(clientId, companyId, request);
        URI location = UriComponentsBuilder.fromPath(BASE_PATH + BY_ID_PATH)
                .buildAndExpand(clientId, companyId, response.getId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Override
    public ResponseEntity<List<ServiceAssignmentResponse>> listServices(UUID clientId, UUID companyId,
                                                                        String financialYear) {
        log.debug("Received request to list services of company {} of client {} for FY {}", companyId, clientId,
                financialYear);
        FinancialYear year = financialYear == null ? null : FinancialYears.parse(financialYear);
        return ResponseEntity.ok(serviceAssignmentService.listServices(clientId, companyId, year));
    }

    @Override
    public ResponseEntity<ServiceAssignmentResponse> getService(UUID clientId, UUID companyId,
                                                                UUID assignmentId) {
        log.debug("Received request to fetch service assignment {} of company {} of client {}",
                assignmentId, companyId, clientId);
        return ResponseEntity.ok(serviceAssignmentService.getService(clientId, companyId, assignmentId));
    }

    @Override
    public ResponseEntity<ServiceAssignmentResponse> updateService(UUID clientId, UUID companyId,
                                                                   UUID assignmentId,
                                                                   ServiceAssignmentUpdateRequest request) {
        log.debug("Received request to update service assignment {} of company {} of client {}",
                assignmentId, companyId, clientId);
        return ResponseEntity.ok(serviceAssignmentService.updateService(clientId, companyId, assignmentId, request));
    }

    @Override
    public ResponseEntity<Void> removeService(UUID clientId, UUID companyId, UUID assignmentId) {
        log.debug("Received request to remove service assignment {} from company {} of client {}",
                assignmentId, companyId, clientId);
        serviceAssignmentService.removeService(clientId, companyId, assignmentId);
        return ResponseEntity.noContent().build();
    }
}
