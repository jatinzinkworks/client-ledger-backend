package com.psc.cl;

import com.psc.cl.config.TimeZones;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Client Ledger backend service.
 */
@SpringBootApplication
public class ClientLedgerApplication {

    static {
        // Runs on the way into main, which is before anything can open a database connection.
        // Tests never call main, so AbstractPostgresIT pins the zone for itself - see TimeZones.
        TimeZones.pinToUtc();
    }

    public static void main(String[] args) {
        SpringApplication.run(ClientLedgerApplication.class, args);
    }
}
