package com.psc.cl.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.psc.cl.client.dto.ClientImportResponse;
import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.ClientResponse;
import com.psc.cl.client.dto.CompanyResponse;
import com.psc.cl.client.dto.NewCompanyRequest;
import com.psc.cl.client.model.CompanyType;
import com.psc.cl.dto.ErrorResponse;
import com.psc.cl.exception.SpreadsheetValidationException;
import com.psc.cl.managercatalog.model.Manager;
import com.psc.cl.managercatalog.repository.ManagerRepository;
import com.psc.cl.service.XlsxReader;
import com.psc.cl.service.XlsxWorkbook;
import com.psc.cl.service.XlsxWorkbook.Cell;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@ExtendWith(MockitoExtension.class)
class ClientImportServiceImplTest {

    private static final UUID MANAGER_ID = UUID.fromString("e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47");

    private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();

    private static final Cell[] CLIENT_HEADINGS = headings("Client Ref", "First Name", "Last Name", "Phone", "Email",
            "Manager Email", "Discount %", "WhatsApp Opt-In");

    private static final Cell[] COMPANY_HEADINGS = headings("Client Ref", "Company Name", "Company Type", "Address",
            "Discount %", "Opening Balance", "Opening Balance Date");

    /** The firm's today in these tests: 16 October 2026. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T06:30:00Z"), ZoneOffset.UTC);

    @Mock
    private ClientService clientService;

    @Mock
    private ManagerRepository managerRepository;

    private ClientImportServiceImpl importService;

    @BeforeEach
    void setUp() {
        Validator validator = VALIDATION.getValidator();
        importService = new ClientImportServiceImpl(clientService, managerRepository, validator, CLOCK);
    }

    @AfterAll
    static void closeValidation() {
        VALIDATION.close();
    }

    @Test
    @DisplayName("writes a template with the Clients and Companies headings and an Instructions sheet")
    void writesTemplate() {
        XlsxReader.Workbook template = XlsxReader.read(importService.template());

        assertThat(template.sheets()).extracting(XlsxReader.Sheet::name)
                .containsExactly("Clients", "Companies", "Instructions");
        XlsxReader.Sheet clients = template.sheet("Clients").orElseThrow();
        assertThat(IntStream.range(0, 8).mapToObj(column -> clients.cell(1, column)))
                .containsExactly("Client Ref", "First Name", "Last Name", "Phone", "Email", "Manager Email",
                        "Discount %", "WhatsApp Opt-In");
        assertThat(clients.rows()).containsOnlyKeys(1);
        XlsxReader.Sheet companies = template.sheet("Companies").orElseThrow();
        assertThat(IntStream.range(0, 7).mapToObj(column -> companies.cell(1, column)))
                .containsExactly("Client Ref", "Company Name", "Company Type", "Address", "Discount %",
                        "Opening Balance", "Opening Balance Date");
        assertThat(template.sheet("Instructions").orElseThrow().rows().values())
                .anySatisfy(row -> assertThat(row.values()).contains("Manager Email"))
                .anySatisfy(row -> assertThat(row.get(3)).contains("PRIVATE_LIMITED"));
    }

    @Test
    @DisplayName("an empty template is refused: there is nothing to import")
    void refusesEmptyTemplate() {
        assertThatThrownBy(() -> importService.importClients(importService.template()))
                .isInstanceOf(SpreadsheetValidationException.class)
                .hasMessageContaining("no clients");

        verify(clientService, never()).createClient(any());
    }

    @Test
    @DisplayName("creates each client with the companies linked to them, reading values as people type them")
    void importsClientsWithCompanies() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        when(clientService.createClient(any())).thenAnswer(invocation -> created(invocation.getArgument(0)));
        byte[] workbook = workbook(
                List.of(
                        row("C1", "Rohan", "Mehta", "+91 98200 12345", "rohan@example.com",
                                "PRANAY@Example.com", "10", "Yes"),
                        row("C2", "Asha", "Kapoor", "9820012345", "asha@example.com", "pranay@example.com",
                                null, null)),
                List.of(
                        row("C1", "Mehta Textiles Pvt Ltd", "Private Limited", "12 MG Road, Bengaluru", "5%"),
                        row("c2", "Kapoor & Sons", "PARTNERSHIP", "4 Park Street, Kolkata", null),
                        row("C1", "Rohan Mehta HUF", "huf", "4 Park Street, Kolkata", null)));

        ClientImportResponse response = importService.importClients(workbook);

        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(clientService, times(2)).createClient(captor.capture());
        ClientRequest rohan = captor.getAllValues().get(0);
        assertThat(rohan.firstName()).isEqualTo("Rohan");
        assertThat(rohan.phone()).isEqualTo("+91 98200 12345");
        // The manager is found by email, ignoring case.
        assertThat(rohan.managerId()).isEqualTo(MANAGER_ID);
        assertThat(rohan.discountPercent()).isEqualByComparingTo("10");
        assertThat(rohan.whatsappOptIn()).isTrue();
        assertThat(rohan.companies()).extracting("companyName")
                .containsExactly("Mehta Textiles Pvt Ltd", "Rohan Mehta HUF");
        assertThat(rohan.companies()).extracting("companyType")
                .containsExactly(CompanyType.PRIVATE_LIMITED, CompanyType.HUF);
        // Typed as 5%, read as 5 percent.
        assertThat(rohan.companies().get(0).discountPercent()).isEqualByComparingTo("5");
        assertThat(rohan.companies().get(1).discountPercent()).isNull();

        ClientRequest asha = captor.getAllValues().get(1);
        assertThat(asha.discountPercent()).isNull();
        assertThat(asha.whatsappOptIn()).isNull();
        // Linked by a Client Ref differing only in case.
        assertThat(asha.companies()).extracting("companyName").containsExactly("Kapoor & Sons");

        assertThat(response.getClientCount()).isEqualTo(2);
        assertThat(response.getCompanyCount()).isEqualTo(3);
        assertThat(response.getClients()).extracting(ClientImportResponse.ImportedClient::getClientRef)
                .containsExactly("C1", "C2");
        assertThat(response.getClients().get(0).getClientName()).isEqualTo("Rohan Mehta");
    }

    @Test
    @DisplayName("creates nothing when any cell is wrong, listing every cell at fault in sheet and row order")
    void reportsEveryInvalidCell() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        byte[] workbook = workbook(
                List.of(
                        row("C1", "Rohan", "Mehta", "+91 98200 12345", "not-an-email", "nobody@example.com",
                                "ten", "Maybe"),
                        row("C1", "Asha", "Kapoor", "9820012345", "asha@example.com", "pranay@example.com",
                                null, null),
                        row("C3", null, "Shah", "9820012345", "shah@example.com", "pranay@example.com", "150",
                                null)),
                List.of(
                        row("C1", "Mehta Textiles Pvt Ltd", "Corporation", "12 MG Road, Bengaluru", null),
                        row("C9", "Unknown Holdings", "LLP", "1 Main Road", null),
                        row(null, "No Ref Ltd", "LLP", "1 Main Road", null)));

        assertThatThrownBy(() -> importService.importClients(workbook))
                .isInstanceOfSatisfying(SpreadsheetValidationException.class, e -> {
                    assertThat(e.getMessage()).startsWith("11 cell(s) of the workbook are invalid");
                    assertThat(e.getErrors()).extracting(ErrorResponse.FieldError::getField).containsExactly(
                            "Clients!E2", "Clients!F2", "Clients!G2", "Clients!H2",
                            "Clients!A3",
                            "Clients!A4", "Clients!B4", "Clients!G4",
                            "Companies!C2", "Companies!A3", "Companies!A4");
                    assertThat(e.getErrors()).extracting(ErrorResponse.FieldError::getMessage).contains(
                            "Manager Email: no manager in the Manager Catalog has the email nobody@example.com",
                            "Discount %: must be a number, such as 10, 12.5 or 10%",
                            "WhatsApp Opt-In: must be Yes or No",
                            "Client Ref: repeats the Client Ref of row 2; give each client its own",
                            "Client Ref: must contain at least one company on the Companies sheet",
                            "First Name: must not be blank",
                            "Discount %: must not exceed 100",
                            "Client Ref: no client on the Clients sheet has the Client Ref C9");
                });

        verify(clientService, never()).createClient(any());
    }

    @Test
    @DisplayName("reads each company's opening balance and its date, as amounts and dates are typed")
    void importsOpeningBalances() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        when(clientService.createClient(any())).thenAnswer(invocation -> created(invocation.getArgument(0)));
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Clients").addRow(CLIENT_HEADINGS)
                .addRow(row("C1", "Rohan", "Mehta", "9820012345", "rohan@example.com", "pranay@example.com", null,
                        null));
        workbook.addSheet("Companies").addRow(COMPANY_HEADINGS)
                // A number cell and a date cell, as Excel stores them.
                .addRow(Cell.text("C1"), Cell.text("Mehta Textiles Pvt Ltd"), Cell.text("LLP"), Cell.text("Pune"),
                        Cell.empty(), Cell.amount(new BigDecimal("25000")), Cell.date(LocalDate.of(2026, 3, 31)))
                // Text, with a rupee sign, separators and a day-first date.
                .addRow(row("C1", "Rohan Mehta HUF", "HUF", "Pune", null, "₹ -1,23,456.50", "01/04/2026"))
                .addRow(row("C1", "Mehta Exports", "LLP", "Pune", null, null, null));

        importService.importClients(workbook.toBytes());

        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(clientService).createClient(captor.capture());
        List<NewCompanyRequest> companies = captor.getValue().companies();
        assertThat(companies.get(0).openingBalance()).isEqualByComparingTo("25000");
        assertThat(companies.get(0).openingBalanceDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(companies.get(1).openingBalance()).isEqualByComparingTo("-123456.50");
        assertThat(companies.get(1).openingBalanceDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(companies.get(2).openingBalance()).isNull();
        assertThat(companies.get(2).openingBalanceDate()).isNull();
    }

    @Test
    @DisplayName("reports an unreadable or future opening balance, and a date without a balance, against their cells")
    void reportsInvalidOpeningBalances() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        byte[] workbook = workbook(
                List.of(row("C1", "Rohan", "Mehta", "9820012345", "rohan@example.com", "pranay@example.com", null,
                        null)),
                List.of(
                        row("C1", "Mehta Textiles Pvt Ltd", "LLP", "Pune", null, "lots", "31/02/2026"),
                        row("C1", "Rohan Mehta HUF", "HUF", "Pune", null, "1000", "17/10/2026"),
                        row("C1", "Mehta Exports", "LLP", "Pune", null, null, "2026-03-31"),
                        row("C1", "Mehta Trust", "TRUST", "Pune", null, "1000.555", null)));

        assertThatThrownBy(() -> importService.importClients(workbook))
                .isInstanceOfSatisfying(SpreadsheetValidationException.class, e ->
                        assertThat(e.getErrors()).extracting(ErrorResponse.FieldError::getField,
                                        ErrorResponse.FieldError::getMessage)
                                .containsExactly(
                                        tuple("Companies!F2", "Opening Balance: must be an amount, such as "
                                                + "25000, 25,000.50 or -5000"),
                                        tuple("Companies!G2", "Opening Balance Date: must be a date, such as "
                                                + "31/03/2026 or 2026-03-31"),
                                        tuple("Companies!G3",
                                                "Opening Balance Date: must not be after today, 2026-10-16"),
                                        tuple("Companies!G4", "Opening Balance Date: must be given only with an "
                                                + "openingBalance other than 0"),
                                        tuple("Companies!F5", "Opening Balance: must have at most 12 digits before "
                                                + "the decimal point and 2 after it")));

        verify(clientService, never()).createClient(any());
    }

    @Test
    @DisplayName("reads a Companies sheet from an older template, without the opening balance columns")
    void importsOlderTemplate() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        when(clientService.createClient(any())).thenAnswer(invocation -> created(invocation.getArgument(0)));
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Clients").addRow(CLIENT_HEADINGS)
                .addRow(row("C1", "Rohan", "Mehta", "9820012345", "rohan@example.com", "pranay@example.com", null,
                        null));
        workbook.addSheet("Companies")
                .addRow(headings("Client Ref", "Company Name", "Company Type", "Address", "Discount %"))
                .addRow(row("C1", "Mehta Textiles Pvt Ltd", "LLP", "Pune", "5"));

        ClientImportResponse response = importService.importClients(workbook.toBytes());

        assertThat(response.getCompanyCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("refuses a workbook whose headings differ from the template's, naming each one")
    void refusesWrongHeadings() {
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Clients").addRow(headings("Ref", "First Name", "Surname", "Phone", "Email",
                "Manager Email", "Discount %", "WhatsApp Opt-In"));
        workbook.addSheet("Companies").addRow(COMPANY_HEADINGS);

        assertThatThrownBy(() -> importService.importClients(workbook.toBytes()))
                .isInstanceOfSatisfying(SpreadsheetValidationException.class, e -> {
                    assertThat(e.getMessage()).contains("headings of the Clients sheet");
                    assertThat(e.getErrors()).extracting(ErrorResponse.FieldError::getField)
                            .containsExactly("Clients!A1", "Clients!C1");
                });
    }

    @Test
    @DisplayName("refuses a workbook without a Companies sheet")
    void refusesMissingSheet() {
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Clients").addRow(CLIENT_HEADINGS);

        assertThatThrownBy(() -> importService.importClients(workbook.toBytes()))
                .isInstanceOf(SpreadsheetValidationException.class)
                .hasMessageContaining("no Companies sheet");
    }

    @Test
    @DisplayName("refuses more clients than one file may import")
    void refusesTooManyClients() {
        List<Cell[]> clients = IntStream.rangeClosed(1, ClientImportServiceImpl.MAX_CLIENTS + 1)
                .mapToObj(n -> row("C" + n, "Client", "Number " + n, "9820012345", "c" + n + "@example.com",
                        "pranay@example.com", null, null))
                .toList();

        assertThatThrownBy(() -> importService.importClients(workbook(clients, List.of())))
                .isInstanceOf(SpreadsheetValidationException.class)
                .hasMessageContaining("import at most 500 per file");

        verify(managerRepository, never()).findAll();
    }

    private static byte[] workbook(List<Cell[]> clients, List<Cell[]> companies) {
        XlsxWorkbook workbook = new XlsxWorkbook();
        XlsxWorkbook.Sheet clientSheet = workbook.addSheet("Clients").addRow(CLIENT_HEADINGS);
        clients.forEach(clientSheet::addRow);
        XlsxWorkbook.Sheet companySheet = workbook.addSheet("Companies").addRow(COMPANY_HEADINGS);
        companies.forEach(companySheet::addRow);
        return workbook.toBytes();
    }

    private static Cell[] row(String... values) {
        return Arrays.stream(values).map(Cell::text).toArray(Cell[]::new);
    }

    private static Cell[] headings(String... headings) {
        return Arrays.stream(headings).map(Cell::bold).toArray(Cell[]::new);
    }

    private static Manager manager() {
        Manager manager = new Manager();
        manager.setId(MANAGER_ID);
        manager.setFirstName("Pranay");
        manager.setLastName("Singhal");
        manager.setEmail("pranay@example.com");
        return manager;
    }

    private static ClientResponse created(ClientRequest request) {
        return ClientResponse.builder()
                .id(UUID.randomUUID())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .managerId(request.managerId())
                .discountPercent(request.discountPercent() == null ? BigDecimal.ZERO : request.discountPercent())
                .companies(request.companies().stream()
                        .map(company -> CompanyResponse.builder()
                                .id(UUID.randomUUID())
                                .companyName(company.companyName())
                                .build())
                        .toList())
                .build();
    }
}
