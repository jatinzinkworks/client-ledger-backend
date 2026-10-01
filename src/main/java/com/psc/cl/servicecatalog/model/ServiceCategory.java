package com.psc.cl.servicecatalog.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Practice area a catalog service belongs to.
 */
@Schema(name = "ServiceCategory", description = "Practice area a service belongs to")
public enum ServiceCategory {

    COMPLIANCE,
    TAX,
    AUDIT,
    ACCOUNTING,
    ADVISORY
}
