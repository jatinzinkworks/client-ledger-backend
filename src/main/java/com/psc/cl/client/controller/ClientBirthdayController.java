package com.psc.cl.client.controller;

import com.psc.cl.client.dto.ClientBirthdayResponse;
import com.psc.cl.client.service.ClientBirthdayService;
import com.psc.cl.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Month;
import java.util.List;

/**
 * Single entry point for clients' birthdays.
 */
@RestController
@RequiredArgsConstructor
public class ClientBirthdayController implements ClientBirthdayApi {

    private static final Logger log = LoggerFactory.getLogger(ClientBirthdayController.class);

    private final ClientBirthdayService clientBirthdayService;

    @Override
    public ResponseEntity<List<ClientBirthdayResponse>> listBirthdays(int month) {
        if (month < 1 || month > 12) {
            throw new InvalidRequestException("month must be between 1 and 12, not " + month);
        }
        log.debug("Received request to list the clients with a birthday in month {}", month);
        return ResponseEntity.ok(clientBirthdayService.listBirthdays(Month.of(month)));
    }
}
