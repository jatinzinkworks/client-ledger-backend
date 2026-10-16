package com.psc.cl.client.controller;

import com.psc.cl.client.dto.ClientImportResponse;
import com.psc.cl.client.service.ClientImportService;
import com.psc.cl.exception.InvalidRequestException;
import com.psc.cl.ledger.dto.LedgerExport;
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
 * Single entry point for importing clients and their companies from an Excel workbook.
 */
@RestController
@RequiredArgsConstructor
public class ClientImportController implements ClientImportApi {

    private static final Logger log = LoggerFactory.getLogger(ClientImportController.class);

    private final ClientImportService clientImportService;

    @Override
    public ResponseEntity<byte[]> downloadTemplate() {
        log.debug("Received request to download the client import template");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(LedgerExport.XLSX_MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(TEMPLATE_FILE_NAME).build().toString())
                .body(clientImportService.template());
    }

    @Override
    public ResponseEntity<ClientImportResponse> importClients(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Attach the filled-in client import workbook (.xlsx) as the file part.");
        }
        log.debug("Received request to import clients from a workbook of {} bytes", file.getSize());
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("The uploaded workbook could not be read", e);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(clientImportService.importClients(content));
    }
}
