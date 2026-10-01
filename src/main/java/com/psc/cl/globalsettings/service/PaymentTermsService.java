package com.psc.cl.globalsettings.service;

import com.psc.cl.globalsettings.dto.PaymentTermsRequest;
import com.psc.cl.globalsettings.dto.PaymentTermsResponse;

/**
 * Business operations over the payment terms held under Global Settings.
 */
public interface PaymentTermsService {

    /**
     * Creates the tenant-wide payment terms, or overwrites them when they already exist.
     * Defaults are applied for any omitted value.
     *
     * @param request the terms to store
     * @return the stored terms with all defaults resolved, and whether they were newly created
     */
    PaymentTermsSaveResult savePaymentTerms(PaymentTermsRequest request);

    /**
     * Reads the tenant-wide payment terms.
     *
     * @return the stored terms
     * @throws com.psc.cl.globalsettings.exception.PaymentTermsNotFoundException
     *         when no payment terms have been saved yet
     */
    PaymentTermsResponse getPaymentTerms();
}
