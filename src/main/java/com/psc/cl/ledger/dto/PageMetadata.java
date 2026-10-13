package com.psc.cl.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.domain.Page;

/**
 * Where a page sits within the full list it was cut from.
 */
@Value
@Builder
@Schema(name = "PageMetadata", description = "Position of the page returned within the full list")
public class PageMetadata {

    @Schema(description = "Number of this page, counting from 0", example = "0")
    int number;

    @Schema(description = "Largest number of entries a page holds", example = "50")
    int size;

    @Schema(description = "Number of entries on this page", example = "50")
    int numberOfElements;

    @Schema(description = "Number of entries across every page", example = "132")
    long totalElements;

    @Schema(description = "Number of pages; 0 when there are no entries", example = "3")
    int totalPages;

    @Schema(description = "Whether a page follows this one", example = "true")
    boolean hasNext;

    /**
     * Describes the given page.
     *
     * @param page the page read
     * @return its position within the full list
     */
    public static PageMetadata of(Page<?> page) {
        return PageMetadata.builder()
                .number(page.getNumber())
                .size(page.getSize())
                .numberOfElements(page.getNumberOfElements())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .build();
    }
}
