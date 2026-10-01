package com.psc.cl.globalsettings.service;

import com.psc.cl.globalsettings.dto.FirmDetailsRequest;
import com.psc.cl.globalsettings.dto.FirmDetailsResponse;
import com.psc.cl.globalsettings.exception.FirmDetailsNotFoundException;
import com.psc.cl.globalsettings.model.FirmDetails;
import com.psc.cl.globalsettings.repository.FirmDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Default {@link FirmDetailsService} backed by {@link FirmDetailsRepository}.
 */
@Service
@RequiredArgsConstructor
public class FirmDetailsServiceImpl implements FirmDetailsService {

    private static final Logger log = LoggerFactory.getLogger(FirmDetailsServiceImpl.class);

    private final FirmDetailsRepository firmDetailsRepository;

    @Override
    @Transactional
    public FirmDetailsSaveResult saveFirmDetails(FirmDetailsRequest request) {
        Optional<FirmDetails> existing = firmDetailsRepository.findFirstByOrderByCreatedAtAsc();
        FirmDetails details = existing.orElseGet(FirmDetails::new);

        details.setFirmName(trimToNull(request.firmName()));
        details.setGstin(upperCase(trimToNull(request.gstin())));
        details.setFirmRegistrationNo(trimToNull(request.firmRegistrationNo()));
        details.setEmail(trimToNull(request.email()));
        details.setPhone(trimToNull(request.phone()));
        details.setAddress(trimToNull(request.address()));

        FirmDetails saved = firmDetailsRepository.saveAndFlush(details);
        // The firm name is business reference data rather than PII, so it is safe to log.
        log.info("{} firm details {} for '{}'", existing.isEmpty() ? "Created" : "Updated",
                saved.getId(), saved.getFirmName());

        return new FirmDetailsSaveResult(FirmDetailsResponse.from(saved), existing.isEmpty());
    }

    @Override
    @Transactional(readOnly = true)
    public FirmDetailsResponse getFirmDetails() {
        return firmDetailsRepository.findFirstByOrderByCreatedAtAsc()
                .map(FirmDetailsResponse::from)
                .orElseThrow(FirmDetailsNotFoundException::new);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String upperCase(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
