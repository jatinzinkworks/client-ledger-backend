package com.psc.cl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

/**
 * Entry point for the Client Ledger backend service.
 */
@SpringBootApplication
public class ClientLedgerApplication {

    static {
        // Pin the JVM to UTC before anything can open a connection. The PostgreSQL driver sends the
        // JVM's default zone to the server as the session TimeZone parameter, so a host zone the
        // server does not recognise - such as the legacy "Asia/Calcutta" alias - fails the
        // connection outright. Running in UTC also keeps Instant round-trips aligned with the
        // TIMESTAMPTZ columns and with the UTC database container.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(ClientLedgerApplication.class, args);
    }
}
