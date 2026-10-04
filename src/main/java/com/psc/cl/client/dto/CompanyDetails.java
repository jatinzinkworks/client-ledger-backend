package com.psc.cl.client.dto;

import com.psc.cl.client.model.CompanyType;

import java.math.BigDecimal;

/**
 * Company fields shared by the create and update payloads, so both are applied to the entity the
 * same way.
 */
public interface CompanyDetails {

    String companyName();

    CompanyType companyType();

    String address();

    /** @return the company's own discount, or null when it should take the client's */
    BigDecimal discountPercent();
}
