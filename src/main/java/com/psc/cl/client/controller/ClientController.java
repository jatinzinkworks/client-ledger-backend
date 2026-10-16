package com.psc.cl.client.controller;

import com.psc.cl.client.dto.ClientManagerBulkUpdateRequest;
import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.ClientResponse;
import com.psc.cl.client.dto.ClientSummaryResponse;
import com.psc.cl.client.dto.ClientUpdateRequest;
import com.psc.cl.client.dto.WhatsAppOptInRequest;
import com.psc.cl.client.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Single entry point for every Client endpoint other than those over their companies.
 */
@RestController
@RequiredArgsConstructor
public class ClientController implements ClientApi {

    private static final Logger log = LoggerFactory.getLogger(ClientController.class);

    private final ClientService clientService;

    @Override
    public ResponseEntity<ClientResponse> createClient(ClientRequest request) {
        log.debug("Received request to create a client");
        ClientResponse response = clientService.createClient(request);
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + response.getId()))
                .body(response);
    }

    @Override
    public ResponseEntity<ClientResponse> updateClient(UUID id, ClientUpdateRequest request) {
        log.debug("Received request to update client {}", id);
        return ResponseEntity.ok(clientService.updateClient(id, request));
    }

    @Override
    public ResponseEntity<ClientResponse> updateWhatsAppOptIn(UUID id, WhatsAppOptInRequest request) {
        log.debug("Received request to set the WhatsApp opt-in of client {}", id);
        return ResponseEntity.ok(clientService.updateWhatsAppOptIn(id, request));
    }

    @Override
    public ResponseEntity<List<ClientSummaryResponse>> updateManager(ClientManagerBulkUpdateRequest request) {
        log.debug("Received request to assign {} client(s) to manager {}", request.clientIds().size(),
                request.managerId());
        return ResponseEntity.ok(clientService.updateManager(request));
    }

    @Override
    public ResponseEntity<Void> deleteClient(UUID id) {
        log.debug("Received request to delete client {}", id);
        clientService.deleteClient(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ClientResponse> getClient(UUID id) {
        log.debug("Received request to fetch client {}", id);
        return ResponseEntity.ok(clientService.getClient(id));
    }

    @Override
    public ResponseEntity<List<ClientSummaryResponse>> listClients(UUID managerId) {
        log.debug("Received request to list clients{}", managerId == null ? "" : " of manager " + managerId);
        return ResponseEntity.ok(clientService.listClients(managerId));
    }
}
