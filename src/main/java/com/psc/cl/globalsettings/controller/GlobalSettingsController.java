package com.psc.cl.globalsettings.controller;

import com.psc.cl.globalsettings.dto.FirmDetailsRequest;
import com.psc.cl.globalsettings.dto.FirmDetailsResponse;
import com.psc.cl.globalsettings.dto.PaymentTermsRequest;
import com.psc.cl.globalsettings.dto.PaymentTermsResponse;
import com.psc.cl.globalsettings.service.FirmDetailsSaveResult;
import com.psc.cl.globalsettings.service.FirmDetailsService;
import com.psc.cl.globalsettings.service.PaymentTermsSaveResult;
import com.psc.cl.globalsettings.service.PaymentTermsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Single entry point for every Global Settings endpoint.
 */
@RestController
@RequiredArgsConstructor
public class GlobalSettingsController implements GlobalSettingsApi {

    private static final Logger log = LoggerFactory.getLogger(GlobalSettingsController.class);

    private final PaymentTermsService paymentTermsService;
    private final FirmDetailsService firmDetailsService;

    @Override
    public ResponseEntity<PaymentTermsResponse> savePaymentTerms(PaymentTermsRequest request) {
        log.debug("Received request to save payment terms");
        PaymentTermsSaveResult result = paymentTermsService.savePaymentTerms(request);
        if (result.created()) {
            return ResponseEntity.created(URI.create(BASE_PATH + PAYMENT_TERMS_PATH))
                    .body(result.paymentTerms());
        }
        return ResponseEntity.ok(result.paymentTerms());
    }

    @Override
    public ResponseEntity<PaymentTermsResponse> getPaymentTerms() {
        log.debug("Received request to fetch payment terms");
        return ResponseEntity.ok(paymentTermsService.getPaymentTerms());
    }

    @Override
    public ResponseEntity<FirmDetailsResponse> saveFirmDetails(FirmDetailsRequest request) {
        log.debug("Received request to save firm details");
        FirmDetailsSaveResult result = firmDetailsService.saveFirmDetails(request);
        if (result.created()) {
            return ResponseEntity.created(URI.create(BASE_PATH + FIRM_DETAILS_PATH))
                    .body(result.firmDetails());
        }
        return ResponseEntity.ok(result.firmDetails());
    }

    @Override
    public ResponseEntity<FirmDetailsResponse> getFirmDetails() {
        log.debug("Received request to fetch firm details");
        return ResponseEntity.ok(firmDetailsService.getFirmDetails());
    }
}
