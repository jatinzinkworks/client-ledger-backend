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
import com.psc.cl.service.XlsxWorkbookTest;
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
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

@ExtendWith(MockitoExtension.class)
class ClientImportServiceImplTest {

    private static final UUID MANAGER_ID = UUID.fromString("e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47");

    private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();

    private static final Cell[] CLIENT_HEADINGS = headings("Client Ref", "First Name", "Last Name", "Phone", "Email",
            "Manager Name", "Discount %", "Birthday");

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
                .containsExactly("Clients", "Companies", "Instructions", "Managers");
        XlsxReader.Sheet clients = template.sheet("Clients").orElseThrow();
        assertThat(IntStream.range(0, 8).mapToObj(column -> clients.cell(1, column)))
                .containsExactly("Client Ref", "First Name", "Last Name", "Phone", "Email", "Manager Name",
                        "Discount %", "Birthday");
        assertThat(clients.cell(1, 8)).isNull();
        assertThat(clients.rows()).containsOnlyKeys(1);
        XlsxReader.Sheet companies = template.sheet("Companies").orElseThrow();
        assertThat(IntStream.range(0, 7).mapToObj(column -> companies.cell(1, column)))
                .containsExactly("Client Ref", "Company Name", "Company Type", "Address", "Discount %",
                        "Opening Balance", "Opening Balance Date");
        assertThat(template.sheet("Instructions").orElseThrow().rows().values())
                .anySatisfy(row -> assertThat(row.values()).contains("Manager Name"))
                .anySatisfy(row -> assertThat(row.get(3)).contains("PRIVATE_LIMITED"));
    }

    @Test
    @DisplayName("the template offers drop-downs, the managers' names from a Managers sheet listing them")
    void offersDropDowns() {
        Manager withoutEmail = manager(UUID.randomUUID(), "Aarti", "Desai");
        withoutEmail.setEmail(null);
        when(managerRepository.findAll()).thenReturn(List.of(manager(), withoutEmail));

        byte[] template = importService.template();

        Map<String, String> parts = XlsxWorkbookTest.parts(template);
        // On the Clients sheet, only the manager's name has a set list of values.
        assertThat(parts.get("xl/worksheets/sheet1.xml"))
                .contains("<dataValidations count=\"1\">")
                .contains("sqref=\"F2:F1048576\"><formula1>'Managers'!$A$2:$A$3</formula1>")
                .doesNotContain("errorStyle");
        assertThat(parts.get("xl/worksheets/sheet2.xml"))
                .contains("<dataValidations count=\"2\">")
                .contains("sqref=\"A2:A1048576\"><formula1>'Clients'!$A$2:$A$501</formula1>")
                .contains("sqref=\"C2:C1048576\"><formula1>&quot;PRIVATE_LIMITED,PUBLIC_LIMITED,LLP,PARTNERSHIP,"
                        + "PROPRIETORSHIP,TRUST,HUF,INDIVIDUAL&quot;</formula1>")
                .doesNotContain("errorStyle");
        XlsxReader.Sheet managers = XlsxReader.read(template).sheet("Managers").orElseThrow();
        // By name, including the manager without an email.
        assertThat(managers.rows()).containsOnlyKeys(1, 2, 3);
        assertThat(managers.cell(2, 0)).isEqualTo("Aarti Desai");
        assertThat(managers.cell(2, 1)).isNull();
        assertThat(managers.cell(3, 0)).isEqualTo("Pranay Singhal");
        assertThat(managers.cell(3, 1)).isEqualTo("pranay@example.com");
    }

    @Test
    @DisplayName("without managers in the catalog, the template offers no Manager Name drop-down")
    void offersNoManagerDropDownWithoutManagers() {
        String clients = XlsxWorkbookTest.parts(importService.template()).get("xl/worksheets/sheet1.xml");

        // That was the Clients sheet's only drop-down, so it has none at all.
        assertThat(clients)
                .doesNotContain("<dataValidations")
                .doesNotContain("'Managers'");
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
                                "  PRANAY   singhal ", "10"),
                        row("C2", "Asha", "Kapoor", "9820012345", "asha@example.com", "Pranay Singhal",
                                null)),
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
        // The manager is found by name, ignoring case and spacing.
        assertThat(rohan.managerId()).isEqualTo(MANAGER_ID);
        assertThat(rohan.discountPercent()).isEqualByComparingTo("10");
        assertThat(rohan.companies()).extracting("companyName")
                .containsExactly("Mehta Textiles Pvt Ltd", "Rohan Mehta HUF");
        assertThat(rohan.companies()).extracting("companyType")
                .containsExactly(CompanyType.PRIVATE_LIMITED, CompanyType.HUF);
        // Typed as 5%, read as 5 percent.
        assertThat(rohan.companies().get(0).discountPercent()).isEqualByComparingTo("5");
        assertThat(rohan.companies().get(1).discountPercent()).isNull();

        ClientRequest asha = captor.getAllValues().get(1);
        assertThat(asha.discountPercent()).isNull();
        // Linked by a Client Ref differing only in case.
        assertThat(asha.companies()).extracting("companyName").containsExactly("Kapoor & Sons");

        assertThat(response.getClientCount()).isEqualTo(2);
        assertThat(response.getCompanyCount()).isEqualTo(3);
        assertThat(response.getClients()).extracting(ClientImportResponse.ImportedClient::getClientRef)
                .containsExactly("C1", "C2");
        assertThat(response.getClients().get(0).getClientName()).isEqualTo("Rohan Mehta");
        // No company brought an opening balance.
        assertThat(response.getOpeningBalanceCount()).isZero();
        assertThat(response.getOpeningBalanceTotal()).isEqualTo(new BigDecimal("0.00"));
        assertThat(response.getClients()).extracting(ClientImportResponse.ImportedClient::getOpeningBalanceTotal)
                .containsExactly(new BigDecimal("0.00"), new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("creates nothing when any cell is wrong, listing every cell at fault in sheet and row order")
    void reportsEveryInvalidCell() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        byte[] workbook = workbook(
                List.of(
                        row("C1", "Rohan", "Mehta", "+91 98200 12345", "not-an-email", "Nobody Here",
                                "ten"),
                        row("C1", "Asha", "Kapoor", "9820012345", "asha@example.com", "Pranay Singhal",
                                null),
                        row("C3", null, "Shah", "9820012345", "shah@example.com", "Pranay Singhal", "150")),
                List.of(
                        row("C1", "Mehta Textiles Pvt Ltd", "Corporation", "12 MG Road, Bengaluru", null),
                        row("C9", "Unknown Holdings", "LLP", "1 Main Road", null),
                        row(null, "No Ref Ltd", "LLP", "1 Main Road", null)));

        assertThatThrownBy(() -> importService.importClients(workbook))
                .isInstanceOfSatisfying(SpreadsheetValidationException.class, e -> {
                    assertThat(e.getMessage()).startsWith("10 cell(s) of the workbook are invalid");
                    assertThat(e.getErrors()).extracting(ErrorResponse.FieldError::getField).containsExactly(
                            "Clients!E2", "Clients!F2", "Clients!G2",
                            "Clients!A3",
                            "Clients!A4", "Clients!B4", "Clients!G4",
                            "Companies!C2", "Companies!A3", "Companies!A4");
                    assertThat(e.getErrors()).extracting(ErrorResponse.FieldError::getMessage).contains(
                            "Manager Name: no manager in the Manager Catalog is named Nobody Here",
                            "Discount %: must be a number, such as 10, 12.5 or 10%",
                            "Client Ref: repeats the Client Ref of row 2; give each client its own",
                            "Client Ref: must contain at least one company on the Companies sheet",
                            "First Name: must not be blank",
                            "Discount %: must not exceed 100",
                            "Client Ref: no client on the Clients sheet has the Client Ref C9");
                });

        verify(clientService, never()).createClient(any());
    }

    @Test
    @DisplayName("reads each client's birthday, as dates are typed, and none when left blank")
    void importsBirthdays() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        when(clientService.createClient(any())).thenAnswer(invocation -> created(invocation.getArgument(0)));
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Clients").addRow(CLIENT_HEADINGS)
                // A date cell, as Excel stores it.
                .addRow(Cell.text("C1"), Cell.text("Rohan"), Cell.text("Mehta"), Cell.text("9820012345"),
                        Cell.text("rohan@example.com"), Cell.text("Pranay Singhal"), Cell.empty(),
                        Cell.date(LocalDate.of(1985, 7, 23)))
                // Text, day first.
                .addRow(row("C2", "Asha", "Kapoor", "9820012345", "asha@example.com", "Pranay Singhal", null,
                        "01/02/1990"))
                .addRow(row("C3", "Neel", "Shah", "9820012345", "neel@example.com", "Pranay Singhal", null,
                        null));
        workbook.addSheet("Companies").addRow(COMPANY_HEADINGS)
                .addRow(row("C1", "Mehta Textiles Pvt Ltd", "LLP", "Pune", null))
                .addRow(row("C2", "Kapoor & Sons", "LLP", "Pune", null))
                .addRow(row("C3", "Shah Traders", "LLP", "Pune", null));

        importService.importClients(workbook.toBytes());

        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(clientService, times(3)).createClient(captor.capture());
        assertThat(captor.getAllValues()).extracting(ClientRequest::birthday)
                .containsExactly(LocalDate.of(1985, 7, 23), LocalDate.of(1990, 2, 1), null);
    }

    @Test
    @DisplayName("reports a birthday that cannot be read or is after today against its cell")
    void reportsInvalidBirthdays() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        byte[] workbook = workbook(
                List.of(
                        row("C1", "Rohan", "Mehta", "9820012345", "rohan@example.com", "Pranay Singhal", null,
                                "someday"),
                        row("C2", "Asha", "Kapoor", "9820012345", "asha@example.com", "Pranay Singhal", null,
                                "17/10/2026")),
                List.of(
                        row("C1", "Mehta Textiles Pvt Ltd", "LLP", "Pune", null),
                        row("C2", "Kapoor & Sons", "LLP", "Pune", null)));

        assertThatThrownBy(() -> importService.importClients(workbook))
                .isInstanceOfSatisfying(SpreadsheetValidationException.class, e ->
                        assertThat(e.getErrors()).extracting(ErrorResponse.FieldError::getField,
                                        ErrorResponse.FieldError::getMessage)
                                .containsExactly(
                                        tuple("Clients!H2", "Birthday: must be a date, such as 31/03/2026 or "
                                                + "2026-03-31"),
                                        tuple("Clients!H3", "Birthday: must not be after today, 2026-10-16")));

        verify(clientService, never()).createClient(any());
    }

    @Test
    @DisplayName("reads a Clients sheet from an older template, without the Birthday column")
    void importsClientsWithoutBirthdayColumn() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        when(clientService.createClient(any())).thenAnswer(invocation -> created(invocation.getArgument(0)));
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Clients")
                .addRow(headings("Client Ref", "First Name", "Last Name", "Phone", "Email", "Manager Name",
                        "Discount %"))
                .addRow(row("C1", "Rohan", "Mehta", "9820012345", "rohan@example.com", "Pranay Singhal", null));
        workbook.addSheet("Companies").addRow(COMPANY_HEADINGS)
                .addRow(row("C1", "Mehta Textiles Pvt Ltd", "LLP", "Pune", null));

        importService.importClients(workbook.toBytes());

        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(clientService).createClient(captor.capture());
        assertThat(captor.getValue().birthday()).isNull();
    }

    @Test
    @DisplayName("reports a manager name that more than one manager holds, as it cannot tell them apart")
    void reportsAmbiguousManagerName() {
        when(managerRepository.findAll())
                .thenReturn(List.of(manager(), manager(UUID.randomUUID(), "Pranay", "Singhal")));
        byte[] workbook = workbook(
                List.<Cell[]>of(row("C1", "Rohan", "Mehta", "9820012345", "rohan@example.com",
                        "Pranay Singhal", null)),
                List.<Cell[]>of(row("C1", "Mehta Textiles Pvt Ltd", "LLP", "Pune", null)));

        assertThatThrownBy(() -> importService.importClients(workbook))
                .isInstanceOfSatisfying(SpreadsheetValidationException.class, e ->
                        assertThat(e.getErrors()).extracting(ErrorResponse.FieldError::getField,
                                        ErrorResponse.FieldError::getMessage)
                                .contains(tuple("Clients!F2", "Manager Name: 2 managers in the Manager Catalog "
                                        + "are named Pranay Singhal; rename one of them in the catalog, or add this "
                                        + "client without the import")));

        verify(clientService, never()).createClient(any());
    }

    @Test
    @DisplayName("reads each company's opening balance and its date, as amounts and dates are typed")
    void importsOpeningBalances() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        when(clientService.createClient(any())).thenAnswer(invocation -> created(invocation.getArgument(0)));
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Clients").addRow(CLIENT_HEADINGS)
                .addRow(row("C1", "Rohan", "Mehta", "9820012345", "rohan@example.com", "Pranay Singhal",
                        null));
        workbook.addSheet("Companies").addRow(COMPANY_HEADINGS)
                // A number cell and a date cell, as Excel stores them.
                .addRow(Cell.text("C1"), Cell.text("Mehta Textiles Pvt Ltd"), Cell.text("LLP"), Cell.text("Pune"),
                        Cell.empty(), Cell.amount(new BigDecimal("25000")), Cell.date(LocalDate.of(2026, 3, 31)))
                // Text, with a rupee sign, separators and a day-first date.
                .addRow(row("C1", "Rohan Mehta HUF", "HUF", "Pune", null, "₹ -1,23,456.50", "01/04/2026"))
                .addRow(row("C1", "Mehta Exports", "LLP", "Pune", null, null, null));

        ClientImportResponse response = importService.importClients(workbook.toBytes());

        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(clientService).createClient(captor.capture());
        List<NewCompanyRequest> companies = captor.getValue().companies();
        assertThat(companies.get(0).openingBalance()).isEqualByComparingTo("25000");
        assertThat(companies.get(0).openingBalanceDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(companies.get(1).openingBalance()).isEqualByComparingTo("-123456.50");
        assertThat(companies.get(1).openingBalanceDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(companies.get(2).openingBalance()).isNull();
        assertThat(companies.get(2).openingBalanceDate()).isNull();

        // Two of the three companies brought one; owed and in credit net to the total.
        ClientImportResponse.ImportedClient rohan = response.getClients().getFirst();
        assertThat(rohan.getOpeningBalanceCount()).isEqualTo(2);
        assertThat(rohan.getOpeningBalanceTotal()).isEqualTo(new BigDecimal("-98456.50"));
        assertThat(response.getOpeningBalanceCount()).isEqualTo(2);
        assertThat(response.getOpeningBalanceTotal()).isEqualTo(new BigDecimal("-98456.50"));
        // Each company with the balance recorded for it, and none for the company that brought none.
        assertThat(rohan.getCompanies())
                .extracting(ClientImportResponse.ImportedCompany::getCompanyName,
                        ClientImportResponse.ImportedCompany::getOpeningBalanceAmount,
                        ClientImportResponse.ImportedCompany::getOpeningBalanceDate)
                .containsExactly(
                        tuple("Mehta Textiles Pvt Ltd", new BigDecimal("25000.00"), LocalDate.of(2026, 3, 31)),
                        tuple("Rohan Mehta HUF", new BigDecimal("-123456.50"), LocalDate.of(2026, 4, 1)),
                        tuple("Mehta Exports", null, null));
        assertThat(rohan.getCompanies()).allSatisfy(company -> assertThat(company.getCompanyId()).isNotNull());
    }

    @Test
    @DisplayName("reports an unreadable or future opening balance, and a date without a balance, against their cells")
    void reportsInvalidOpeningBalances() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        byte[] workbook = workbook(
                // One row is itself an array, so List.of needs telling it is the only element, not the elements.
                List.<Cell[]>of(row("C1", "Rohan", "Mehta", "9820012345", "rohan@example.com",
                        "Pranay Singhal", null)),
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
                .addRow(row("C1", "Rohan", "Mehta", "9820012345", "rohan@example.com", "Pranay Singhal",
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
                "Manager Name", "Discount %"));
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
                        "Pranay Singhal", null))
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
        return manager(MANAGER_ID, "Pranay", "Singhal");
    }

    private static Manager manager(UUID id, String firstName, String lastName) {
        Manager manager = new Manager();
        manager.setId(id);
        manager.setFirstName(firstName);
        manager.setLastName(lastName);
        manager.setEmail(firstName.toLowerCase() + "@example.com");
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
                                // As the ledger records it: at two decimals, and only when not zero.
                                .openingBalanceAmount(company.hasOpeningBalance()
                                        ? company.openingBalance().setScale(2, RoundingMode.HALF_UP) : null)
                                .openingBalanceDate(company.hasOpeningBalance() ? company.openingBalanceDate() : null)
                                .build())
                        .toList())
                .build();
    }
}
