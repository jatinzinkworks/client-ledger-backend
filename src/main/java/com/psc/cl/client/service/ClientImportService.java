package com.psc.cl.client.service;

import com.psc.cl.client.dto.ClientImportResponse;

/**
 * Bulk import of clients and their companies from an Excel workbook.
 *
 * <p>The workbook has a Clients sheet, one row per client, and a Companies sheet, one row per
 * company, linked to its client by the client's client id. {@link #template()} writes an empty one
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
     * Imports the workbook, all or nothing: when any cell is missing or invalid, nothing is created
     * and every such cell is reported. A client id no client holds yet creates the client with their
     * companies, as {@link ClientService#createClient} creates one; a client id a client already holds,
     * ignoring case, adds the companies linked to it to that client, as
     * {@link ClientCompanyService#addCompany} adds one, and leaves the client's own details as they are.
     *
     * @param content the bytes of the filled-in .xlsx file
     * @return the clients created or added to: those on the Clients sheet in its order, then any client
     *         on file named only on the Companies sheet
     * @throws com.psc.cl.exception.SpreadsheetValidationException when the file is not a readable
     *         workbook, does not follow the template, holds no clients, or has invalid cells
     */
    ClientImportResponse importClients(byte[] content);
}
