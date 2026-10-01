package com.psc.cl.config;

/**
 * Central definition of the public API base paths so every controller versions consistently.
 */
public final class ApiPaths {

    /** Root of every externally exposed Client Ledger endpoint. */
    public static final String V1 = "/psc/cl/v1";

    private ApiPaths() {
        // utility class
    }
}
