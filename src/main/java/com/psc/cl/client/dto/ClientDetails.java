package com.psc.cl.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Client fields shared by the create and update payloads, so both are applied to the entity the
 * same way.
 */
public interface ClientDetails {

    String firstName();

    String lastName();

    String phone();

    String email();

    UUID managerId();

    /** @return the client discount, or null when the caller omitted it */
    BigDecimal discountPercent();
}
