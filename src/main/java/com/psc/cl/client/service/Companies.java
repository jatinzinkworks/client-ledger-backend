package com.psc.cl.client.service;

import com.psc.cl.client.dto.CompanyDetails;
import com.psc.cl.client.model.Client;
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
     * Builds a new company for the client from the payload. The caller attaches it to the client.
     *
     * @param request the company payload
     * @param client  the client the company will belong to
     * @return the unsaved company
     */
    static ClientCompany newCompany(CompanyDetails request, Client client) {
        ClientCompany company = new ClientCompany();
        apply(request, client, company);
        return company;
    }

    /**
     * Copies the payload onto the company, trimming values. A company without a discount of its
     * own takes the client's.
     *
     * @param request the company payload
     * @param client  the client the company belongs to
     * @param company the company to write to
     */
    static void apply(CompanyDetails request, Client client, ClientCompany company) {
        company.setCompanyName(request.companyName().trim());
        company.setCompanyType(request.companyType());
        company.setAddress(request.address().trim());
        company.setDiscountPercent(Percentages.orElse(request.discountPercent(), client.getDiscountPercent()));
    }
}
