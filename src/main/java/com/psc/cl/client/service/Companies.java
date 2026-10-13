package com.psc.cl.client.service;

import com.psc.cl.client.dto.CompanyDetails;
import com.psc.cl.client.model.ClientCompany;

/**
 * Company field handling shared by client creation and the company endpoints, so a company is
 * stored the same way whichever way it arrives.
 */
final class Companies {

    private Companies() {
        // utility class
    }

    /**
     * Builds a new company from the payload. The caller attaches it to its client.
     *
     * @param request the company payload
     * @return the unsaved company
     */
    static ClientCompany newCompany(CompanyDetails request) {
        ClientCompany company = new ClientCompany();
        apply(request, company);
        return company;
    }

    /**
     * Copies the payload onto the company, trimming values. A company sent without a discount
     * stores none, so it takes its client's discount, whatever that is at the time.
     *
     * @param request the company payload
     * @param company the company to write to
     */
    static void apply(CompanyDetails request, ClientCompany company) {
        company.setCompanyName(request.companyName().trim());
        company.setCompanyType(request.companyType());
        company.setAddress(request.address().trim());
        company.setDiscountPercent(Percentages.orNull(request.discountPercent()));
    }
}
