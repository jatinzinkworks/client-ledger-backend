package com.psc.cl.servicecatalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psc.cl.dto.ErrorResponse;
import com.psc.cl.exception.SpreadsheetValidationException;
import com.psc.cl.ledger.dto.LedgerExport;
import com.psc.cl.servicecatalog.dto.CatalogServiceResponse;
import com.psc.cl.servicecatalog.dto.ServiceCatalogImportResponse;
import com.psc.cl.servicecatalog.service.ServiceCatalogImportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

@WebMvcTest(ServiceCatalogImportController.class)
class ServiceCatalogImportControllerTest {

    private static final String IMPORT_URL = ServiceCatalogApi.BASE_PATH + ServiceCatalogImportApi.IMPORT_PATH;

    private static final String TEMPLATE_URL = ServiceCatalogApi.BASE_PATH + ServiceCatalogImportApi.TEMPLATE_PATH;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServiceCatalogImportService serviceCatalogImportService;

    @Test
    @DisplayName("downloads the template as an xlsx attachment")
    void downloadsTemplate() throws Exception {
        byte[] template = {1, 2, 3};
        when(serviceCatalogImportService.template()).thenReturn(template);

        mockMvc.perform(get(TEMPLATE_URL))
                .andExpect(status().isOk())
                .andExpect(content().contentType(LedgerExport.XLSX_MEDIA_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment; filename=\"Service_Catalog_Import_Template.xlsx\"")))
                .andExpect(content().bytes(template));
    }

    @Test
    @DisplayName("imports the uploaded workbook and returns 201 with the services created")
    void importsWorkbook() throws Exception {
        byte[] workbook = {4, 5, 6};
        when(serviceCatalogImportService.importServices(any())).thenReturn(ServiceCatalogImportResponse.builder()
                .serviceCount(1)
                .services(List.of(CatalogServiceResponse.builder()
                        .id(UUID.randomUUID())
                        .serviceName("GST Monthly Return Filing")
                        .build()))
                .build());

        mockMvc.perform(multipart(IMPORT_URL).file(new MockMultipartFile("file", "services.xlsx",
                        LedgerExport.XLSX_MEDIA_TYPE, workbook)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceCount").value(1))
                .andExpect(jsonPath("$.services[0].serviceName").value("GST Monthly Return Filing"));

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(serviceCatalogImportService).importServices(captor.capture());
        assertThat(captor.getValue()).containsExactly(workbook);
    }

    @Test
    @DisplayName("returns 400 listing every cell at fault when the workbook is invalid")
    void reportsInvalidCells() throws Exception {
        when(serviceCatalogImportService.importServices(any())).thenThrow(new SpreadsheetValidationException(
                "1 cell(s) of the workbook are invalid, so nothing was imported",
                List.of(ErrorResponse.FieldError.builder()
                        .field("Services!C2")
                        .message("Category: must be one of COMPLIANCE, TAX")
                        .build())));

        mockMvc.perform(multipart(IMPORT_URL).file(new MockMultipartFile("file", "services.xlsx",
                        LedgerExport.XLSX_MEDIA_TYPE, new byte[] {1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SPREADSHEET_INVALID"))
                .andExpect(jsonPath("$.errors[0].field").value("Services!C2"));
    }

    @Test
    @DisplayName("returns 400 without calling the service when the file is empty or missing")
    void rejectsMissingFile() throws Exception {
        mockMvc.perform(multipart(IMPORT_URL).file(new MockMultipartFile("file", "services.xlsx",
                        LedgerExport.XLSX_MEDIA_TYPE, new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(multipart(IMPORT_URL))
                .andExpect(status().isBadRequest());

        verify(serviceCatalogImportService, never()).importServices(any());
    }
}
