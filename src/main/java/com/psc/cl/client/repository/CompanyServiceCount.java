package com.psc.cl.client.repository;

import java.util.UUID;

/**
 * How many services are assigned to one company.
 */
public interface CompanyServiceCount {

    /** @return the company */
    UUID getCompanyId();

    /** @return the number of services assigned to it */
    long getServiceCount();
}
