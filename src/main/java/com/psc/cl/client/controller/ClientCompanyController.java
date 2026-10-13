package com.psc.cl.client.controller;

import com.psc.cl.client.dto.CompanyRequest;
import com.psc.cl.client.dto.CompanyResponse;
import com.psc.cl.client.service.ClientCompanyService;
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
 * Single entry point for the companies of a client.
 */
@RestController
@RequiredArgsConstructor
public class ClientCompanyController implements ClientCompanyApi {

    private static final Logger log = LoggerFactory.getLogger(ClientCompanyController.class);

    private final ClientCompanyService clientCompanyService;

    @Override
    public ResponseEntity<List<CompanyResponse>> listCompanies(UUID id) {
        log.debug("Received request to list companies of client {}", id);
        return ResponseEntity.ok(clientCompanyService.listCompanies(id));
    }

    @Override
    public ResponseEntity<CompanyResponse> addCompany(UUID id, CompanyRequest request) {
        log.debug("Received request to add a company to client {}", id);
        CompanyResponse response = clientCompanyService.addCompany(id, request);
        URI location = UriComponentsBuilder.fromPath(BASE_PATH + BY_ID_PATH)
                .buildAndExpand(id, response.getId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Override
    public ResponseEntity<CompanyResponse> getCompany(UUID id, UUID companyId) {
        log.debug("Received request to fetch company {} of client {}", companyId, id);
        return ResponseEntity.ok(clientCompanyService.getCompany(id, companyId));
    }

    @Override
    public ResponseEntity<CompanyResponse> updateCompany(UUID id, UUID companyId, CompanyRequest request) {
        log.debug("Received request to update company {} of client {}", companyId, id);
        return ResponseEntity.ok(clientCompanyService.updateCompany(id, companyId, request));
    }

    @Override
    public ResponseEntity<Void> deleteCompany(UUID id, UUID companyId) {
        log.debug("Received request to delete company {} of client {}", companyId, id);
        clientCompanyService.deleteCompany(id, companyId);
        return ResponseEntity.noContent().build();
    }
}
