package com.psc.cl.ledger.dto;

/**
 * A ledger exported as a file, ready to download or attach: an Excel workbook, or a PDF statement.
 *
 * <p>Holds an array, so two exports are equal only when they are the same instance; compare
 * {@link #content()} with {@code Arrays.equals} instead.
 *
 * @param fileName the name to save the file under, such as {@code Rohan_Mehta_Ledger_FY2026-27.xlsx}
 * @param content  the file's bytes
 */
public record LedgerExport(String fileName, byte[] content) {

    /** Media type of an Excel workbook (.xlsx). */
    public static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Media type of a PDF document. */
    public static final String PDF_MEDIA_TYPE = "application/pdf";
}
