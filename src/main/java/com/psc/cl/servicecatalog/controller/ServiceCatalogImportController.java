package com.psc.cl.servicecatalog.controller;

import com.psc.cl.exception.InvalidRequestException;
import com.psc.cl.ledger.dto.LedgerExport;
import com.psc.cl.servicecatalog.dto.ServiceCatalogImportResponse;
import com.psc.cl.servicecatalog.service.ServiceCatalogImportService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Single entry point for importing catalog services from an Excel workbook.
 */
@RestController
@RequiredArgsConstructor
public class ServiceCatalogImportController implements ServiceCatalogImportApi {

    private static final Logger log = LoggerFactory.getLogger(ServiceCatalogImportController.class);

    private final ServiceCatalogImportService serviceCatalogImportService;

    @Override
    public ResponseEntity<byte[]> downloadTemplate() {
        log.debug("Received request to download the service catalog import template");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(LedgerExport.XLSX_MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(TEMPLATE_FILE_NAME).build().toString())
                .body(serviceCatalogImportService.template());
    }

    @Override
    public ResponseEntity<ServiceCatalogImportResponse> importServices(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException(
                    "Attach the filled-in service catalog import workbook (.xlsx) as the file part.");
        }
        log.debug("Received request to import catalog services from a workbook of {} bytes", file.getSize());
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("The uploaded workbook could not be read", e);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceCatalogImportService.importServices(content));
    }
}
