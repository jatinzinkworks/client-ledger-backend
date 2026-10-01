package com.psc.cl.managercatalog.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Seniority a manager holds within the firm.
 */
@Schema(name = "ManagerRole", description = "Seniority a manager holds within the firm")
public enum ManagerRole {

    SENIOR_MANAGER,
    MANAGER,
    ASSOCIATE
}
