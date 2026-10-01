package com.psc.cl.managercatalog.controller;

import com.psc.cl.managercatalog.dto.ManagerRequest;
import com.psc.cl.managercatalog.dto.ManagerResponse;
import com.psc.cl.managercatalog.service.ManagerCatalogService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Single entry point for every Manager Catalog endpoint.
 */
@RestController
@RequiredArgsConstructor
public class ManagerCatalogController implements ManagerCatalogApi {

    private static final Logger log = LoggerFactory.getLogger(ManagerCatalogController.class);

    private final ManagerCatalogService managerCatalogService;

    @Override
    public ResponseEntity<ManagerResponse> createManager(ManagerRequest request) {
        log.debug("Received request to create a manager");
        ManagerResponse response = managerCatalogService.createManager(request);
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + response.getId()))
                .body(response);
    }

    @Override
    public ResponseEntity<ManagerResponse> getManager(UUID id) {
        log.debug("Received request to fetch manager {}", id);
        return ResponseEntity.ok(managerCatalogService.getManager(id));
    }

    @Override
    public ResponseEntity<List<ManagerResponse>> listManagers() {
        log.debug("Received request to list managers");
        return ResponseEntity.ok(managerCatalogService.listManagers());
    }

    @Override
    public ResponseEntity<ManagerResponse> updateManager(UUID id, ManagerRequest request) {
        log.debug("Received request to update manager {}", id);
        return ResponseEntity.ok(managerCatalogService.updateManager(id, request));
    }

    @Override
    public ResponseEntity<Void> deleteManager(UUID id) {
        log.debug("Received request to delete manager {}", id);
        managerCatalogService.deleteManager(id);
        return ResponseEntity.noContent().build();
    }
}
