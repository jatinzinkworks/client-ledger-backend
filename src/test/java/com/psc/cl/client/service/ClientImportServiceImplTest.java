package com.psc.cl.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.psc.cl.client.dto.ClientImportResponse;
import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.ClientResponse;
import com.psc.cl.client.dto.CompanyRequest;
import com.psc.cl.client.dto.CompanyResponse;
import com.psc.cl.client.dto.NewCompanyRequest;
import com.psc.cl.client.model.Client;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.model.CompanyType;
import com.psc.cl.client.repository.ClientRepository;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

@ExtendWith(MockitoExtension.class)
class ClientImportServiceImplTest {

    private static final UUID MANAGER_ID = UUID.fromString("e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47");

    private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();

    private static final Cell[] CLIENT_HEADINGS = headings("Client ID", "First Name", "Last Name", "Phone", "Email",
            "Manager Name", "Discount %", "Birthday");

    private static final Cell[] COMPANY_HEADINGS = headings("Client ID", "Company Name", "Company Type", "Address",
            "Discount %", "Opening Balance", "Opening Balance Date");

    /** The firm's today in these tests: 16 October 2026. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T06:30:00Z"), ZoneOffset.UTC);

    @Mock
    private ClientService clientService;

    @Mock
    private ClientCompanyService clientCompanyService;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ManagerRepository managerRepository;

    private ClientImportServiceImpl importService;

    @BeforeEach
    void setUp() {
        Validator validator = VALIDATION.getValidator();
        importService = new ClientImportServiceImpl(clientService, clientCompanyService, clientRepository,
                managerRepository, validator, CLOCK);
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
                .containsExactly("Client ID", "First Name", "Last Name", "Phone", "Email", "Manager Name",
                        "Discount %", "Birthday");
        assertThat(clients.cell(1, 8)).isNull();
        assertThat(clients.rows()).containsOnlyKeys(1);
        XlsxReader.Sheet companies = template.sheet("Companies").orElseThrow();
        assertThat(IntStream.range(0, 7).mapToObj(column -> companies.cell(1, column)))
                .containsExactly("Client ID", "Company Name", "Company Type", "Address", "Discount %",
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
        // The Client ID only warns, as a client already on file may be typed in; the company type is strict.
        assertThat(parts.get("xl/worksheets/sheet2.xml"))
                .contains("<dataValidations count=\"2\">")
                .contains("<dataValidation type=\"list\" errorStyle=\"warning\"")
                .contains("sqref=\"A2:A1048576\"><formula1>'Clients'!$A$2:$A$501</formula1>")
                .contains("sqref=\"C2:C1048576\"><formula1>&quot;PRIVATE_LIMITED,PUBLIC_LIMITED,LLP,PARTNERSHIP,"
                        + "PROPRIETORSHIP,TRUST,HUF,INDIVIDUAL&quot;</formula1>");
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
    @DisplayName("creates each new client with the companies linked to them, reading values as people type them")
    void importsClientsWithCompanies() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        when(clientService.createClient(any())).thenAnswer(invocation -> created(invocation.getArgument(0)));
        byte[] workbook = workbook(
                List.of(
                        row("PSC-00001", "Rohan", "Mehta", "+91 98200 12345", "rohan@example.com",
                                "  PRANAY   singhal ", "10"),
                        // Typed in lower case, stored in upper case.
                        row("psc-00002", "Asha", "Kapoor", "9820012345", "asha@example.com", "Pranay Singhal",
                                null)),
                List.of(
                        row("PSC-00001", "Mehta Textiles Pvt Ltd", "Private Limited", "12 MG Road, Bengaluru",
                                "5%"),
                        row("PSC-00002", "Kapoor & Sons", "PARTNERSHIP", "4 Park Street, Kolkata", null),
                        row("PSC-00001", "Rohan Mehta HUF", "huf", "4 Park Street, Kolkata", null)));

        ClientImportResponse response = importService.importClients(workbook);

        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(clientService, times(2)).createClient(captor.capture());
        ClientRequest rohan = captor.getAllValues().get(0);
        assertThat(rohan.clientId()).isEqualTo("PSC-00001");
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
        assertThat(asha.clientId()).isEqualTo("PSC-00002");
        assertThat(asha.discountPercent()).isNull();
        // Linked by a Client ID differing only in case.
        assertThat(asha.companies()).extracting("companyName").containsExactly("Kapoor & Sons");

        assertThat(response.getClientCount()).isEqualTo(2);
        assertThat(response.getExistingClientCount()).isZero();
        assertThat(response.getCompanyCount()).isEqualTo(3);
        assertThat(response.getClients()).extracting(ClientImportResponse.ImportedClient::getClientId,
                        ClientImportResponse.ImportedClient::isCreated)
                .containsExactly(tuple("PSC-00001", true), tuple("PSC-00002", true));
        assertThat(response.getClients()).allSatisfy(client -> assertThat(client.getId()).isNotNull());
        assertThat(response.getClients().get(0).getClientName()).isEqualTo("Rohan Mehta");
        // No company brought an opening balance.
        assertThat(response.getOpeningBalanceCount()).isZero();
        assertThat(response.getOpeningBalanceTotal()).isEqualTo(new BigDecimal("0.00"));
        assertThat(response.getClients()).extracting(ClientImportResponse.ImportedClient::getOpeningBalanceTotal)
                .containsExactly(new BigDecimal("0.00"), new BigDecimal("0.00"));
        verify(clientCompanyService, never()).addCompany(any(), any());
    }

    @Test
    @DisplayName("adds the companies of a client already on file to them, leaving the client as they are")
    void addsCompaniesToClientsOnFile() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        Client rohan = onFile("PSC-00001", "Rohan", "Mehta", 1);
        Client asha = onFile("PSC-00007", "Asha", "Kapoor", 2);
        when(clientRepository.findByClientIdIn(any())).thenReturn(List.of(rohan, asha));
        when(clientService.createClient(any())).thenAnswer(invocation -> created(invocation.getArgument(0)));
        when(clientCompanyService.addCompany(any(), any()))
                .thenAnswer(invocation -> added(invocation.getArgument(1)));
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Clients").addRow(CLIENT_HEADINGS)
                // On file: the rest of the row is not read, so its unreadable cells are no error.
                .addRow(row("psc-00001", null, null, "call me", "not-an-email", "Nobody Here", "ten"))
                .addRow(row("PSC-00002", "Neel", "Shah", "9820012345", "neel@example.com", "Pranay Singhal",
                        null));
        workbook.addSheet("Companies").addRow(COMPANY_HEADINGS)
                .addRow(row("PSC-00001", "Mehta Exports", "LLP", "Pune", null, "1000", "31/03/2026"))
                .addRow(row("PSC-00002", "Shah Traders", "LLP", "Pune", null))
                // On file, though not on the Clients sheet.
                .addRow(row("PSC-00007", "Kapoor Holdings", "TRUST", "Kolkata", "5"));

        ClientImportResponse response = importService.importClients(workbook.toBytes());

        ArgumentCaptor<CompanyRequest> toRohan = ArgumentCaptor.forClass(CompanyRequest.class);
        verify(clientCompanyService).addCompany(eq(rohan.getId()), toRohan.capture());
        assertThat(toRohan.getValue().companyName()).isEqualTo("Mehta Exports");
        assertThat(toRohan.getValue().openingBalance()).isEqualByComparingTo("1000");
        assertThat(toRohan.getValue().openingBalanceDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        ArgumentCaptor<CompanyRequest> toAsha = ArgumentCaptor.forClass(CompanyRequest.class);
        verify(clientCompanyService).addCompany(eq(asha.getId()), toAsha.capture());
        assertThat(toAsha.getValue().companyType()).isEqualTo(CompanyType.TRUST);
        assertThat(toAsha.getValue().discountPercent()).isEqualByComparingTo("5");
        // Only the client not on file is created.
        ArgumentCaptor<ClientRequest> createdCaptor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(clientService).createClient(createdCaptor.capture());
        assertThat(createdCaptor.getValue().clientId()).isEqualTo("PSC-00002");

        assertThat(response.getClientCount()).isEqualTo(1);
        assertThat(response.getExistingClientCount()).isEqualTo(2);
        assertThat(response.getCompanyCount()).isEqualTo(3);
        assertThat(response.getOpeningBalanceCount()).isEqualTo(1);
        assertThat(response.getOpeningBalanceTotal()).isEqualTo(new BigDecimal("1000.00"));
        // Those on the Clients sheet in its order, then the client named only on the Companies sheet.
        assertThat(response.getClients())
                .extracting(ClientImportResponse.ImportedClient::getClientId,
                        ClientImportResponse.ImportedClient::isCreated,
                        ClientImportResponse.ImportedClient::getClientName,
                        ClientImportResponse.ImportedClient::getCompanyCount)
                .containsExactly(
                        tuple("PSC-00001", false, "Rohan Mehta", 1),
                        tuple("PSC-00002", true, "Neel Shah", 1),
                        tuple("PSC-00007", false, "Asha Kapoor", 1));
        assertThat(response.getClients().get(0).getId()).isEqualTo(rohan.getId());
    }

    @Test
    @DisplayName("reports a client on file given no companies, and companies that would take one past the limit")
    void reportsInvalidAdditionsToClientsOnFile() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        Client rohan = onFile("PSC-00001", "Rohan", "Mehta", 1);
        Client asha = onFile("PSC-00007", "Asha", "Kapoor", ClientRequest.MAX_COMPANIES - 1);
        when(clientRepository.findByClientIdIn(any())).thenReturn(List.of(rohan, asha));
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Clients").addRow(CLIENT_HEADINGS)
                .addRow(row("PSC-00001", "Rohan", "Mehta", "9820012345", "rohan@example.com", "Pranay Singhal",
                        null));
        workbook.addSheet("Companies").addRow(COMPANY_HEADINGS)
                .addRow(row("PSC-00007", "Kapoor Holdings", "TRUST", "Kolkata", null))
                .addRow(row("PSC-00007", "Kapoor Exports", "LLP", "Kolkata", null))
                .addRow(row("PSC-00007", null, "LLP", "Kolkata", null));

        assertThatThrownBy(() -> importService.importClients(workbook.toBytes()))
                .isInstanceOfSatisfying(SpreadsheetValidationException.class, e ->
                        assertThat(e.getErrors()).extracting(ErrorResponse.FieldError::getField,
                                        ErrorResponse.FieldError::getMessage)
                                .containsExactly(
                                        tuple("Clients!A2", "Client ID: is already on file, so only companies "
                                                + "are added to that client: link at least one to it on the "
                                                + "Companies sheet"),
                                        tuple("Companies!A3", "Client ID: client PSC-00007 already holds 49 "
                                                + "companies, and may hold at most 50; 3 are linked to it here"),
                                        tuple("Companies!B4", "Company Name: must not be blank")));

        verify(clientCompanyService, never()).addCompany(any(), any());
        verify(clientService, never()).createClient(any());
    }

    @Test
    @DisplayName("looks clients on file up by every Client ID on either sheet, in upper case")
    void looksUpClientsOnFile() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        byte[] workbook = workbook(
                List.<Cell[]>of(row("psc-00001", "Rohan", "Mehta", "9820012345", "rohan@example.com",
                        "Pranay Singhal", null)),
                List.of(
                        row("PSC-00001", "Mehta Textiles Pvt Ltd", "LLP", "Pune", null),
                        row(" psc-00009 ", "Unknown Holdings", "LLP", "Pune", null)));

        assertThatThrownBy(() -> importService.importClients(workbook))
                .isInstanceOf(SpreadsheetValidationException.class);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(clientRepository).findByClientIdIn(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder("PSC-00001", "PSC-00009");
    }

    @Test
    @DisplayName("creates nothing when any cell is wrong, listing every cell at fault in sheet and row order")
    void reportsEveryInvalidCell() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        byte[] workbook = workbook(
                List.of(
                        row("PSC-00001", "Rohan", "Mehta", "+91 98200 12345", "not-an-email", "Nobody Here",
                                "ten"),
                        row("PSC-00001", "Asha", "Kapoor", "9820012345", "asha@example.com", "Pranay Singhal",
                                null),
                        row("PSC-3", null, "Shah", "9820012345", "shah@example.com", "Pranay Singhal", "150")),
                List.of(
                        row("PSC-00001", "Mehta Textiles Pvt Ltd", "Corporation", "12 MG Road, Bengaluru", null),
                        row("PSC-00009", "Unknown Holdings", "LLP", "1 Main Road", null),
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
                            "Client ID: repeats the Client ID of row 2; give each client their own",
                            "First Name: must not be blank",
                            "Discount %: must not exceed 100",
                            "Client ID: no client on the Clients sheet or already on file has the Client ID "
                                    + "PSC-00009");
                    // The malformed Client ID is reported before the missing companies, its cell's first error.
                    assertThat(e.getErrors()).extracting(ErrorResponse.FieldError::getMessage).containsAnyOf(
                            "Client ID: must be PSC- followed by 5 digits, such as PSC-00001",
                            "Client ID: must contain at least one company on the Companies sheet");
                });

        verify(clientService, never()).createClient(any());
    }

    @Test
    @DisplayName("rejects the whole import when a Client ID is blank on either sheet, creating nothing")
    void rejectsBlankClientIds() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        byte[] workbook = workbook(
                List.of(
                        row("PSC-00001", "Rohan", "Mehta", "9820012345", "rohan@example.com", "Pranay Singhal",
                                null),
                        // Only spaces counts as blank too.
                        row("   ", "Asha", "Kapoor", "9820012345", "asha@example.com", "Pranay Singhal", null)),
                List.of(
                        row("PSC-00001", "Mehta Textiles Pvt Ltd", "LLP", "Pune", null),
                        row(null, "Kapoor & Sons", "LLP", "Pune", null)));

        assertThatThrownBy(() -> importService.importClients(workbook))
                .isInstanceOfSatisfying(SpreadsheetValidationException.class, e ->
                        assertThat(e.getErrors()).extracting(ErrorResponse.FieldError::getField,
                                        ErrorResponse.FieldError::getMessage)
                                .containsExactly(
                                        tuple("Clients!A3", "Client ID: must not be blank: give each client "
                                                + "their client id"),
                                        tuple("Companies!A3", "Client ID: must not be blank: give the Client ID "
                                                + "of the client who holds the company")));

        // The valid client is not created either: the import is all or nothing.
        verify(clientService, never()).createClient(any());
        verify(clientCompanyService, never()).addCompany(any(), any());
    }

    @Test
    @DisplayName("reads each client's birthday, as dates are typed, and none when left blank")
    void importsBirthdays() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        when(clientService.createClient(any())).thenAnswer(invocation -> created(invocation.getArgument(0)));
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Clients").addRow(CLIENT_HEADINGS)
                // A date cell, as Excel stores it.
                .addRow(Cell.text("PSC-00001"), Cell.text("Rohan"), Cell.text("Mehta"), Cell.text("9820012345"),
                        Cell.text("rohan@example.com"), Cell.text("Pranay Singhal"), Cell.empty(),
                        Cell.date(LocalDate.of(1985, 7, 23)))
                // Text, day first.
                .addRow(row("PSC-00002", "Asha", "Kapoor", "9820012345", "asha@example.com", "Pranay Singhal",
                        null, "01/02/1990"))
                .addRow(row("PSC-00003", "Neel", "Shah", "9820012345", "neel@example.com", "Pranay Singhal",
                        null, null));
        workbook.addSheet("Companies").addRow(COMPANY_HEADINGS)
                .addRow(row("PSC-00001", "Mehta Textiles Pvt Ltd", "LLP", "Pune", null))
                .addRow(row("PSC-00002", "Kapoor & Sons", "LLP", "Pune", null))
                .addRow(row("PSC-00003", "Shah Traders", "LLP", "Pune", null));

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
                        row("PSC-00001", "Rohan", "Mehta", "9820012345", "rohan@example.com", "Pranay Singhal",
                                null, "someday"),
                        row("PSC-00002", "Asha", "Kapoor", "9820012345", "asha@example.com", "Pranay Singhal",
                                null, "17/10/2026")),
                List.of(
                        row("PSC-00001", "Mehta Textiles Pvt Ltd", "LLP", "Pune", null),
                        row("PSC-00002", "Kapoor & Sons", "LLP", "Pune", null)));

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
    @DisplayName("reads a Clients sheet without the Birthday column")
    void importsClientsWithoutBirthdayColumn() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        when(clientService.createClient(any())).thenAnswer(invocation -> created(invocation.getArgument(0)));
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Clients")
                .addRow(headings("Client ID", "First Name", "Last Name", "Phone", "Email", "Manager Name",
                        "Discount %"))
                .addRow(row("PSC-00001", "Rohan", "Mehta", "9820012345", "rohan@example.com", "Pranay Singhal",
                        null));
        workbook.addSheet("Companies").addRow(COMPANY_HEADINGS)
                .addRow(row("PSC-00001", "Mehta Textiles Pvt Ltd", "LLP", "Pune", null));

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
                List.<Cell[]>of(row("PSC-00001", "Rohan", "Mehta", "9820012345", "rohan@example.com",
                        "Pranay Singhal", null)),
                List.<Cell[]>of(row("PSC-00001", "Mehta Textiles Pvt Ltd", "LLP", "Pune", null)));

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
                .addRow(row("PSC-00001", "Rohan", "Mehta", "9820012345", "rohan@example.com", "Pranay Singhal",
                        null));
        workbook.addSheet("Companies").addRow(COMPANY_HEADINGS)
                // A number cell and a date cell, as Excel stores them.
                .addRow(Cell.text("PSC-00001"), Cell.text("Mehta Textiles Pvt Ltd"), Cell.text("LLP"),
                        Cell.text("Pune"), Cell.empty(), Cell.amount(new BigDecimal("25000")),
                        Cell.date(LocalDate.of(2026, 3, 31)))
                // Text, with a rupee sign, separators and a day-first date.
                .addRow(row("PSC-00001", "Rohan Mehta HUF", "HUF", "Pune", null, "₹ -1,23,456.50", "01/04/2026"))
                .addRow(row("PSC-00001", "Mehta Exports", "LLP", "Pune", null, null, null));

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
                List.<Cell[]>of(row("PSC-00001", "Rohan", "Mehta", "9820012345", "rohan@example.com",
                        "Pranay Singhal", null)),
                List.of(
                        row("PSC-00001", "Mehta Textiles Pvt Ltd", "LLP", "Pune", null, "lots", "31/02/2026"),
                        row("PSC-00001", "Rohan Mehta HUF", "HUF", "Pune", null, "1000", "17/10/2026"),
                        row("PSC-00001", "Mehta Exports", "LLP", "Pune", null, null, "2026-03-31"),
                        row("PSC-00001", "Mehta Trust", "TRUST", "Pune", null, "1000.555", null)));

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
    @DisplayName("reads a Companies sheet without the opening balance columns")
    void importsOlderTemplate() {
        when(managerRepository.findAll()).thenReturn(List.of(manager()));
        when(clientService.createClient(any())).thenAnswer(invocation -> created(invocation.getArgument(0)));
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Clients").addRow(CLIENT_HEADINGS)
                .addRow(row("PSC-00001", "Rohan", "Mehta", "9820012345", "rohan@example.com", "Pranay Singhal",
                        null));
        workbook.addSheet("Companies")
                .addRow(headings("Client ID", "Company Name", "Company Type", "Address", "Discount %"))
                .addRow(row("PSC-00001", "Mehta Textiles Pvt Ltd", "LLP", "Pune", "5"));

        ClientImportResponse response = importService.importClients(workbook.toBytes());

        assertThat(response.getCompanyCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("refuses a workbook whose headings differ from the template's, naming each one")
    void refusesWrongHeadings() {
        XlsxWorkbook workbook = new XlsxWorkbook();
        // A template from before Client IDs, with a Client Ref.
        workbook.addSheet("Clients").addRow(headings("Client Ref", "First Name", "Surname", "Phone", "Email",
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
                .mapToObj(n -> row("PSC-%05d".formatted(n), "Client", "Number " + n, "9820012345",
                        "c" + n + "@example.com", "Pranay Singhal", null))
                .toList();

        assertThatThrownBy(() -> importService.importClients(workbook(clients, List.of())))
                .isInstanceOf(SpreadsheetValidationException.class)
                .hasMessageContaining("import at most 500 per file");

        verify(managerRepository, never()).findAll();
        verify(clientRepository, never()).findByClientIdIn(any());
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

    /** A client already on file, holding a number of companies. */
    private static Client onFile(String clientId, String firstName, String lastName, int companyCount) {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setClientId(clientId);
        client.setFirstName(firstName);
        client.setLastName(lastName);
        client.setManagerId(MANAGER_ID);
        for (int i = 0; i < companyCount; i++) {
            ClientCompany company = new ClientCompany();
            company.setId(UUID.randomUUID());
            company.setCompanyName(lastName + " Company " + i);
            company.setCompanyType(CompanyType.LLP);
            company.setAddress("Pune");
            client.addCompany(company);
        }
        return client;
    }

    private static ClientResponse created(ClientRequest request) {
        return ClientResponse.builder()
                .id(UUID.randomUUID())
                .clientId(request.clientId())
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

    /** A company as added to a client on file, with its opening balance as the ledger records it. */
    private static CompanyResponse added(CompanyRequest company) {
        return CompanyResponse.builder()
                .id(UUID.randomUUID())
                .companyName(company.companyName())
                .openingBalanceAmount(company.hasOpeningBalance()
                        ? company.openingBalance().setScale(2, RoundingMode.HALF_UP) : null)
                .openingBalanceDate(company.hasOpeningBalance() ? company.openingBalanceDate() : null)
                .build();
    }
}
