package com.psc.cl.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * The firm's logo, {@value #RESOURCE} on the classpath, as shown at the top of the ledger statement
 * PDF and of the emails sent to clients. Read once; without it, both are written without a logo.
 */
public final class FirmLogo {

    private static final Logger log = LoggerFactory.getLogger(FirmLogo.class);

    /** Where the logo is on the classpath. */
    public static final String RESOURCE = "ca-logo.png";

    /** The logo's media type. */
    public static final String MEDIA_TYPE = "image/png";

    private static final byte[] PNG = load();

    private FirmLogo() {
        // utility class
    }

    /**
     * @return the content of the logo's .png file, a fresh copy each time; empty when it is not on the
     *         classpath
     */
    public static Optional<byte[]> png() {
        return Optional.ofNullable(PNG).map(byte[]::clone);
    }

    private static byte[] load() {
        try (InputStream in = FirmLogo.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("Logo {} is not on the classpath; statements and emails are sent without it", RESOURCE);
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            log.error("Logo {} could not be read; statements and emails are sent without it", RESOURCE, e);
            return null;
        }
    }
}
