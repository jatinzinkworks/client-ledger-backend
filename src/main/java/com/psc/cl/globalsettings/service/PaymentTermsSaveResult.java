package com.psc.cl.globalsettings.service;

import com.psc.cl.globalsettings.dto.PaymentTermsResponse;

/**
 * Outcome of a payment terms save, letting the controller distinguish a first-time create
 * (201 Created) from an update of the existing record (200 OK).
 *
 * @param paymentTerms the stored terms after the save
 * @param created      true when this call created the record, false when it updated it
 */
public record PaymentTermsSaveResult(PaymentTermsResponse paymentTerms, boolean created) {
}
