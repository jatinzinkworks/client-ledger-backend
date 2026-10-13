package com.psc.cl.ledger.dto;

import org.springframework.data.domain.Sort;

import java.time.LocalDate;

/**
 * What a ledger list asks for: which entries, which page of them, and in what order. Already
 * validated by the controller.
 *
 * @param fromDate  earliest due date to include, or null to leave the range open at the start
 * @param toDate    latest due date to include, or null to leave the range open at the end
 * @param page      the page to return, counting from 0
 * @param size      the largest number of entries a page holds
 * @param sortBy    the field to order entries by
 * @param direction whether that field ascends or descends
 */
public record LedgerListQuery(LocalDate fromDate, LocalDate toDate, int page, int size, LedgerSortField sortBy,
                              Sort.Direction direction) {
}
