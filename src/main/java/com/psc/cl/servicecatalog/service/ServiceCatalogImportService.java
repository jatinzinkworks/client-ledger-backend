package com.psc.cl.servicecatalog.service;

import com.psc.cl.servicecatalog.dto.ServiceCatalogImportResponse;

/**
 * Bulk import of catalog services from an Excel workbook, with a Services sheet of one row per
 * service. {@link #template()} writes an empty one with the headings and instructions.
 */
public interface ServiceCatalogImportService {

    /**
     * Writes the workbook to fill in for an import: the Services sheet with its headings only, and
     * an Instructions sheet describing every column with an example.
     *
     * @return the content of the .xlsx file
     */
    byte[] template();

    /**
     * Creates every service on the workbook, all or nothing: when any cell is missing or invalid, or
     * a name is taken, nothing is created and every such cell is reported. Each service is created as
     * {@link ServiceCatalogService#createService} creates one.
     *
     * @param content the bytes of the filled-in .xlsx file
     * @return the services created, in the order of the Services sheet
     * @throws com.psc.cl.exception.SpreadsheetValidationException when the file is not a readable
     *         workbook, does not follow the template, holds no services or too many, or has invalid
     *         cells
     */
    ServiceCatalogImportResponse importServices(byte[] content);
}
