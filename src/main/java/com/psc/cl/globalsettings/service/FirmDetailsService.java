package com.psc.cl.globalsettings.service;

import com.psc.cl.globalsettings.dto.FirmDetailsRequest;
import com.psc.cl.globalsettings.dto.FirmDetailsResponse;

/**
 * Business operations over the firm details held under Global Settings.
 */
public interface FirmDetailsService {

    /**
     * Creates the firm details, or overwrites them when they already exist.
     *
     * @param request the details to store
     * @return the stored details, and whether they were newly created
     */
    FirmDetailsSaveResult saveFirmDetails(FirmDetailsRequest request);

    /**
     * Reads the firm details.
     *
     * @return the stored details
     * @throws com.psc.cl.globalsettings.exception.FirmDetailsNotFoundException
     *         when no firm details have been saved yet
     */
    FirmDetailsResponse getFirmDetails();
}
