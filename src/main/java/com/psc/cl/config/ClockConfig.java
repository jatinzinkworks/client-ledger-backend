package com.psc.cl.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Supplies the clock business dates are read from.
 *
 * <p>The JVM runs in UTC (see {@link TimeZones}), but "today" for billing is the firm's calendar
 * day. Reading dates from this clock rather than the default zone keeps a posting made just after
 * midnight in India from landing on the previous day, and lets tests fix the date.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${client-ledger.business-time-zone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
