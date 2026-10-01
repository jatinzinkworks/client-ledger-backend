package com.psc.cl.servicecatalog.controller;

import com.psc.cl.servicecatalog.dto.CatalogServiceRequest;
import com.psc.cl.servicecatalog.dto.CatalogServiceResponse;
import com.psc.cl.servicecatalog.service.ServiceCatalogService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Single entry point for every Service Catalog endpoint.
 */
@RestController
@RequiredArgsConstructor
public class ServiceCatalogController implements ServiceCatalogApi {

    private static final Logger log = LoggerFactory.getLogger(ServiceCatalogController.class);

    private final ServiceCatalogService serviceCatalogService;

    @Override
    public ResponseEntity<CatalogServiceResponse> createService(CatalogServiceRequest request) {
        log.debug("Received request to create a catalog service");
        CatalogServiceResponse response = serviceCatalogService.createService(request);
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + response.getId()))
                .body(response);
    }

    @Override
    public ResponseEntity<CatalogServiceResponse> getService(UUID id) {
        log.debug("Received request to fetch catalog service {}", id);
        return ResponseEntity.ok(serviceCatalogService.getService(id));
    }

    @Override
    public ResponseEntity<List<CatalogServiceResponse>> listServices() {
        log.debug("Received request to list catalog services");
        return ResponseEntity.ok(serviceCatalogService.listServices());
    }

    @Override
    public ResponseEntity<CatalogServiceResponse> updateService(UUID id, CatalogServiceRequest request) {
        log.debug("Received request to update catalog service {}", id);
        return ResponseEntity.ok(serviceCatalogService.updateService(id, request));
    }

    @Override
    public ResponseEntity<Void> deleteService(UUID id) {
        log.debug("Received request to delete catalog service {}", id);
        serviceCatalogService.deleteService(id);
        return ResponseEntity.noContent().build();
    }
}
