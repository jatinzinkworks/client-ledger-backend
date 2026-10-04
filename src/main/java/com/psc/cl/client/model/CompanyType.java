package com.psc.cl.client.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Legal form of a company held by a client.
 */
@Schema(name = "CompanyType", description = "Legal form of a company held by a client")
public enum CompanyType {

    PRIVATE_LIMITED,
    PUBLIC_LIMITED,
    LLP,
    PARTNERSHIP,
    PROPRIETORSHIP,
    TRUST,
    HUF,
    INDIVIDUAL
}
