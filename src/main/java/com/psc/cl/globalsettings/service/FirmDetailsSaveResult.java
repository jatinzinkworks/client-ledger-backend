package com.psc.cl.globalsettings.service;

import com.psc.cl.globalsettings.dto.FirmDetailsResponse;

/**
 * Outcome of a firm details save, letting the controller distinguish a first-time create
 * (201 Created) from an update of the existing record (200 OK).
 *
 * @param firmDetails the stored details after the save
 * @param created     true when this call created the record, false when it updated it
 */
public record FirmDetailsSaveResult(FirmDetailsResponse firmDetails, boolean created) {
}
