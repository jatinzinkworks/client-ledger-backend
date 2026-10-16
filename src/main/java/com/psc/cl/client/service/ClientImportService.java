package com.psc.cl.client.service;

import com.psc.cl.client.dto.ClientImportResponse;

/**
 * Bulk import of clients and their companies from an Excel workbook.
 *
 * <p>The workbook has a Clients sheet, one row per client, and a Companies sheet, one row per
 * company, linked to its client by the client's reference. {@link #template()} writes an empty one
 * with the headings and instructions.
 */
public interface ClientImportService {

    /**
     * Writes the workbook to fill in for an import: the Clients and Companies sheets with their
     * headings only, and an Instructions sheet describing every column with an example.
     *
     * @return the content of the .xlsx file
     */
    byte[] template();

    /**
     * Creates every client on the workbook with their companies, all or nothing: when any cell is
     * missing or invalid, nothing is created and every such cell is reported. Each client is created
     * as {@link ClientService#createClient} creates one.
     *
     * @param content the bytes of the filled-in .xlsx file
     * @return the clients created, in the order of the Clients sheet
     * @throws com.psc.cl.exception.SpreadsheetValidationException when the file is not a readable
     *         workbook, does not follow the template, holds no clients, or has invalid cells
     */
    ClientImportResponse importClients(byte[] content);
}
