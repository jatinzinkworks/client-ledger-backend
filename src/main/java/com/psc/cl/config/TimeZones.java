package com.psc.cl.config;

import java.util.TimeZone;

/**
 * Pins the JVM to UTC.
 *
 * <p>The PostgreSQL driver sends the default zone of the JVM to the server as the session
 * TimeZone parameter on every connection, so a host zone the server does not recognise - such as
 * the legacy "Asia/Calcutta" alias - fails the connection outright. Running in UTC also keeps
 * Instant round-trips aligned with the TIMESTAMPTZ columns and with the UTC database container.
 *
 * <p>This has to happen before anything opens a connection, which is earlier than any Spring
 * bean. Each entry point therefore calls it from a static initialiser: the application class for
 * production, and the integration test base class for tests, where {@code main} never runs.
 */
public final class TimeZones {

    private TimeZones() {
        // utility class
    }

    /**
     * Sets the default time zone of this JVM to UTC.
     */
    public static void pinToUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
