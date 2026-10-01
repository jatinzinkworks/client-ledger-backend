package com.psc.cl.globalsettings.service;

import com.psc.cl.globalsettings.dto.PaymentTermsRequest;
import com.psc.cl.globalsettings.dto.PaymentTermsResponse;
import com.psc.cl.globalsettings.exception.PaymentTermsNotFoundException;
import com.psc.cl.globalsettings.model.PaymentTerms;
import com.psc.cl.globalsettings.repository.PaymentTermsRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Default {@link PaymentTermsService} backed by {@link PaymentTermsRepository}.
 */
@Service
@RequiredArgsConstructor
public class PaymentTermsServiceImpl implements PaymentTermsService {

    private static final Logger log = LoggerFactory.getLogger(PaymentTermsServiceImpl.class);

    private final PaymentTermsRepository paymentTermsRepository;

    @Override
    @Transactional
    public PaymentTermsSaveResult savePaymentTerms(PaymentTermsRequest request) {
        Optional<PaymentTerms> existing = paymentTermsRepository.findFirstByOrderByCreatedAtAsc();
        PaymentTerms terms = existing.orElseGet(PaymentTerms::new);

        terms.setPaymentDueAfterDays(request.effectivePaymentDueAfterDays());
        terms.setMarkOverdueAfterDays(request.effectiveMarkOverdueAfterDays());
        terms.setPaymentReminderEnabled(request.effectivePaymentReminderEnabled());
        terms.setPaymentReminderDays(request.effectivePaymentReminderDays());

        PaymentTerms saved = paymentTermsRepository.saveAndFlush(terms);
        log.info("{} payment terms {} — due after {} day(s), overdue after {} day(s), reminders {}",
                existing.isEmpty() ? "Created" : "Updated", saved.getId(),
                saved.getPaymentDueAfterDays(), saved.getMarkOverdueAfterDays(),
                Boolean.TRUE.equals(saved.getPaymentReminderEnabled()) ? "enabled" : "disabled");

        return new PaymentTermsSaveResult(PaymentTermsResponse.from(saved), existing.isEmpty());
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentTermsResponse getPaymentTerms() {
        return paymentTermsRepository.findFirstByOrderByCreatedAtAsc()
                .map(PaymentTermsResponse::from)
                .orElseThrow(PaymentTermsNotFoundException::new);
    }
}
