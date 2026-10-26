package com.psc.cl.client.service;

import com.psc.cl.client.dto.ClientImportResponse;
import com.psc.cl.client.dto.ClientImportResponse.ImportedClient;
import com.psc.cl.client.dto.ClientImportResponse.ImportedCompany;
import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.ClientResponse;
import com.psc.cl.client.dto.CompanyRequest;
import com.psc.cl.client.dto.CompanyResponse;
import com.psc.cl.client.dto.NewCompanyRequest;
import com.psc.cl.client.model.Client;
import com.psc.cl.client.model.CompanyType;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.exception.SpreadsheetValidationException;
import com.psc.cl.managercatalog.model.Manager;
import com.psc.cl.managercatalog.repository.ManagerRepository;
import com.psc.cl.service.SpreadsheetImport;
import com.psc.cl.service.SpreadsheetImport.CellErrors;
import com.psc.cl.service.SpreadsheetImport.Choices;
import com.psc.cl.service.SpreadsheetImport.Column;
import com.psc.cl.service.SpreadsheetImport.Row;
import com.psc.cl.service.SpreadsheetImport.SheetLayout;
import com.psc.cl.service.XlsxReader;
import com.psc.cl.service.XlsxWorkbook;
import com.psc.cl.service.XlsxWorkbook.Cell;
import com.psc.cl.service.XlsxWorkbook.Range;
import com.psc.cl.service.XlsxWorkbook.Sheet;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Default {@link ClientImportService}.
 *
 * <p>Clients are named by their client id. A client id no client holds yet creates the client, with the
 * companies linked to it; one a client already holds, ignoring case, adds the companies linked to it to
 * that client, leaving the client's own details as they are.
 *
 * <p>Every row is read and checked before anything is created: values that cannot be read, such as
 * an unknown manager or company type, are reported against their cell, and each new client is then
 * checked against the same rules as {@code POST /clients}, and each company added to a client already on
 * file against those of {@code POST /clients/{id}/companies}, with each rule broken also reported
 * against its cell. Only a workbook without a single error is imported, in one transaction.
 */
@Service
@RequiredArgsConstructor
public class ClientImportServiceImpl implements ClientImportService {

    private static final Logger log = LoggerFactory.getLogger(ClientImportServiceImpl.class);

    static final String CLIENTS_SHEET = "Clients";

    static final String COMPANIES_SHEET = "Companies";

    static final String INSTRUCTIONS_SHEET = "Instructions";

    /** The template's list of managers, which the Manager Name drop-down offers; it is not imported. */
    static final String MANAGERS_SHEET = "Managers";

    /** Most clients one workbook may import. */
    static final int MAX_CLIENTS = 500;

    /** What the template is called in messages. */
    private static final String TEMPLATE = "client import template";

    /** Column of the client id, first on both sheets. */
    private static final int CLIENT_ID = 0;

    /** Column of the manager's name on the Clients sheet. */
    private static final int MANAGER_NAME = 5;

    /** Runs of whitespace in a name, read as one space so a name matches however it is spaced. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** The path of a broken rule on one of a client's companies, such as companies[2].address. */
    private static final Pattern COMPANY_PATH = Pattern.compile("^companies\\[(\\d+)]\\.(\\w+)$");

    /**
     * Fields a broken rule may name that are not a column's own, by the field of the column they
     * concern: whether an opening balance date comes with a balance concerns the date.
     */
    private static final Map<String, String> FIELD_COLUMNS = Map.of("openingBalanceDated", "openingBalanceDate");

    /** The Clients sheet, column A onwards; each field is the {@link ClientRequest} component it fills. */
    static final SheetLayout CLIENTS = new SheetLayout(CLIENTS_SHEET, List.of(
            new Column("Client ID", "clientId", 14, true, "The firm's own identifier for the client, different "
                    + "for each row of this sheet: PSC- followed by 5 digits. Companies are linked to their client "
                    + "by it. It cannot be changed once the client is added. When a client already holds it, the "
                    + "rest of the row is not read and the client is left as it is: only the companies linked to "
                    + "it are added", "PSC-00001"),
            new Column("First Name", "firstName", 18, true, "Given name, up to 100 characters", "Rohan"),
            new Column("Last Name", "lastName", 18, true, "Family name, up to 100 characters", "Mehta"),
            new Column("Phone", "phone", 18, true, "6 to 20 digits, spaces, brackets or hyphens, with an optional "
                    + "leading +. Start it with + or format the column as text to keep a leading 0",
                    "+91 98200 12345"),
            new Column("Email", "email", 30, true, "Contact email address", "rohan.mehta@example.com"),
            // Its drop-down lists the managers of the moment, so it is added as the template is written.
            new Column("Manager Name", "managerId", 30, true, "First and last name of the manager in the Manager "
                    + "Catalog who looks after the client, picked from the drop-down, which offers those on the "
                    + MANAGERS_SHEET + " sheet. Two managers with the same name cannot be told apart", "Pranay "
                    + "Singhal"),
            new Column("Discount %", "discountPercent", 12, false, "Discount for the client, 0 to 100 with at most 2 "
                    + "decimals. Type 10 or 10% for 10 percent. Blank for none. Applies to each of the client's "
                    + "companies without a discount of its own", "10"),
            new Column("Birthday", "birthday", 14, false, "The client's date of birth; not after today. A date "
                    + "cell, or text as 23/07/1985 or 1985-07-23. Blank for none", "23/07/1985")), 0);

    /** The Companies sheet, column A onwards; each field is the {@link NewCompanyRequest} component it fills. */
    static final SheetLayout COMPANIES = new SheetLayout(COMPANIES_SHEET, List.of(
            // Not strict: a client already on file may be typed in, though the sheet does not list it.
            new Column("Client ID", null, 14, true, "The Client ID of the client who holds the company: one on "
                    + "the " + CLIENTS_SHEET + " sheet, picked from the drop-down, which offers those typed there, "
                    + "or that of a client already on file, typed in, to add the company to them", "PSC-00001",
                    new Choices(List.of(), new Range(CLIENTS_SHEET, CLIENT_ID, 2, MAX_CLIENTS + 1), false)),
            new Column("Company Name", "companyName", 32, true, "Registered name, up to 200 characters",
                    "Mehta Textiles Pvt Ltd"),
            new Column("Company Type", "companyType", 18, true, "One of "
                    + SpreadsheetImport.choices(CompanyType.class) + ", picked from the drop-down", "PRIVATE_LIMITED",
                    Choices.of(CompanyType.class)),
            new Column("Address", "address", 45, true, "Registered address, up to 500 characters",
                    "12 MG Road, Bengaluru, Karnataka 560001"),
            new Column("Discount %", "discountPercent", 12, false, "Discount for this company, 0 to 100 with at most "
                    + "2 decimals, typed as for clients. Blank to take the client's discount", "5"),
            new Column("Opening Balance", "openingBalance", 16, false, "What the company owes as it is taken on, "
                    + "brought over from before; negative when it starts in credit, such as an advance. At most 2 "
                    + "decimals. Counted in what is pending, not as billed. Blank or 0 for none", "25000"),
            new Column("Opening Balance Date", "openingBalanceDate", 20, false, "The date the opening balance "
                    + "stood at, which places it in a financial year; not after today. A date cell, or text as "
                    + "31/03/2026 or 2026-03-31. Only with an opening balance. Blank for the day of the import",
                    "31/03/2026")), 1);

    private final ClientService clientService;

    private final ClientCompanyService clientCompanyService;

    private final ClientRepository clientRepository;

    private final ManagerRepository managerRepository;

    private final Validator validator;

    private final Clock clock;

    @Override
    public byte[] template() {
        List<Manager> managers = managerRepository.findAll().stream()
                .sorted(Comparator.comparing(ClientImportServiceImpl::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        XlsxWorkbook workbook = new XlsxWorkbook();
        Sheet clients = SpreadsheetImport.addSheet(workbook, CLIENTS);
        if (!managers.isEmpty()) {
            clients.addDropDown(MANAGER_NAME, 2, new Range(MANAGERS_SHEET, 0, 2, managers.size() + 1), true);
        }
        SpreadsheetImport.addSheet(workbook, COMPANIES);

        Sheet instructions = workbook.addSheet(INSTRUCTIONS_SHEET, 14, 18, 10, 100, 40);
        instructions.addRow(Cell.bold("Importing clients and their companies"))
                .addBlankRow()
                .addRow(Cell.text("1. On the " + CLIENTS_SHEET + " sheet, add one row per new client, each with "
                        + "its own Client ID, such as PSC-00001, PSC-00002. Pick the Manager Name from the drop-down, "
                        + "which offers the managers on the " + MANAGERS_SHEET + " sheet as they were when this "
                        + "template was downloaded. A client whose Client ID is already on file is not created "
                        + "again, and its details are left as they are."))
                .addRow(Cell.text("2. On the " + COMPANIES_SHEET + " sheet, add one row per company, giving the "
                        + "Client ID of the client who holds it: picked from the drop-down for a client on the "
                        + CLIENTS_SHEET + " sheet, or typed in for a client already on file, who then need not be "
                        + "on the " + CLIENTS_SHEET + " sheet. Every new client needs 1 to "
                        + ClientRequest.MAX_COMPANIES + " companies, and no client may hold more than "
                        + ClientRequest.MAX_COMPANIES + " in all. Give a company's opening balance there too, if it "
                        + "brings one."))
                .addRow(Cell.text("3. Keep the headings in row 1 as they are. Blank rows are skipped. Up to "
                        + MAX_CLIENTS + " clients per file. Columns with a set list of values offer it in a "
                        + "drop-down. The " + MANAGERS_SHEET + " sheet is for reference and is not imported."))
                .addRow(Cell.text("4. The file is imported whole or not at all: if any cell is wrong, nothing is "
                        + "imported, and every cell at fault is listed so it can be corrected."))
                .addRow(Cell.text("Importing the same file twice does not create its clients twice, as their "
                        + "Client IDs are then on file, but it does add their companies to them again."))
                .addBlankRow()
                .addRow(Cell.bold("Sheet"), Cell.bold("Column"), Cell.bold("Required"), Cell.bold("What to enter"),
                        Cell.bold("Example"));
        SpreadsheetImport.describe(instructions, CLIENTS);
        SpreadsheetImport.describe(instructions, COMPANIES);
        addManagers(workbook, managers);
        return workbook.toBytes();
    }

    /**
     * The managers the Manager Name drop-down offers, by name in column A, with their email, if any,
     * for reference.
     */
    private static void addManagers(XlsxWorkbook workbook, List<Manager> managers) {
        Sheet sheet = workbook.addSheet(MANAGERS_SHEET, 30, 34)
                .freezeHeaderRow()
                .addRow(Cell.bold("Name"), Cell.bold("Email"));
        managers.forEach(manager -> sheet.addRow(Cell.text(name(manager)),
                manager.getEmail() == null ? Cell.empty() : Cell.text(manager.getEmail())));
    }

    private static String name(Manager manager) {
        return Stream.of(manager.getFirstName(), manager.getLastName())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    @Override
    @Transactional
    public ClientImportResponse importClients(byte[] content) {
        XlsxReader.Workbook workbook = XlsxReader.read(content);
        XlsxReader.Sheet clientSheet = SpreadsheetImport.requireSheet(workbook, CLIENTS, TEMPLATE);
        XlsxReader.Sheet companySheet = SpreadsheetImport.requireSheet(workbook, COMPANIES, TEMPLATE);
        int clientRows = SpreadsheetImport.dataRows(clientSheet).size();
        if (clientRows > MAX_CLIENTS) {
            throw new SpreadsheetValidationException("The " + CLIENTS_SHEET + " sheet has " + clientRows
                    + " clients; import at most " + MAX_CLIENTS + " per file");
        }

        CellErrors errors = new CellErrors();
        Map<String, Client> onFile = clientsOnFile(clientSheet, companySheet);
        Map<String, PendingClient> clients = readClients(clientSheet, onFile, errors);
        readCompanies(companySheet, clients, onFile, errors);
        clients.values().forEach(client -> validate(client, errors));
        if (!errors.isEmpty()) {
            throw errors.toException();
        }
        if (clients.isEmpty()) {
            throw new SpreadsheetValidationException("The workbook has no clients or companies below the "
                    + "headings of its " + CLIENTS_SHEET + " and " + COMPANIES_SHEET + " sheets");
        }

        List<ImportedClient> imported = clients.values().stream().map(this::importClient).toList();
        int createdCount = (int) imported.stream().filter(ImportedClient::isCreated).count();
        int companyCount = imported.stream().mapToInt(ImportedClient::getCompanyCount).sum();
        int openingBalanceCount = imported.stream().mapToInt(ImportedClient::getOpeningBalanceCount).sum();
        log.info("Imported {} new client(s) and added to {} client(s) on file, with {} company(ies), {} with an "
                + "opening balance, from a workbook", createdCount, imported.size() - createdCount, companyCount,
                openingBalanceCount);

        return ClientImportResponse.builder()
                .clientCount(createdCount)
                .existingClientCount(imported.size() - createdCount)
                .companyCount(companyCount)
                .openingBalanceCount(openingBalanceCount)
                .openingBalanceTotal(sum(imported.stream().map(ImportedClient::getOpeningBalanceTotal)))
                .clients(imported)
                .build();
    }

    /**
     * Creates a client not yet on file, with their companies, or adds the companies to a client who is.
     *
     * @param client the client as read and checked
     * @return what was imported for the client
     */
    private ImportedClient importClient(PendingClient client) {
        if (client.existing == null) {
            ClientResponse created = clientService.createClient(client.request());
            return imported(created.getId(), created.getClientId(), true,
                    created.getFirstName() + " " + created.getLastName(), created.getCompanies());
        }
        Client existing = client.existing;
        List<CompanyResponse> added = client.companies.stream()
                .map(company -> clientCompanyService.addCompany(existing.getId(), companyRequest(company.request())))
                .toList();
        return imported(existing.getId(), existing.getClientId(), false,
                existing.getFirstName() + " " + existing.getLastName(), added);
    }

    private static ImportedClient imported(UUID id, String clientId, boolean created, String clientName,
                                           List<CompanyResponse> companies) {
        // The opening balances as the ledger recorded them, which the created companies carry.
        List<BigDecimal> openings = companies.stream()
                .map(CompanyResponse::getOpeningBalanceAmount)
                .filter(Objects::nonNull)
                .toList();
        return ImportedClient.builder()
                .id(id)
                .clientId(clientId)
                .created(created)
                .clientName(clientName)
                .companyCount(companies.size())
                .openingBalanceCount(openings.size())
                .openingBalanceTotal(sum(openings.stream()))
                .companies(companies.stream().map(ClientImportServiceImpl::imported).toList())
                .build();
    }

    /** A company read for a client already on file, as the company endpoint takes it. */
    private static CompanyRequest companyRequest(NewCompanyRequest company) {
        return CompanyRequest.builder()
                .companyName(company.companyName())
                .companyType(company.companyType())
                .address(company.address())
                .discountPercent(company.discountPercent())
                .openingBalance(company.openingBalance())
                .openingBalanceDate(company.openingBalanceDate())
                .build();
    }

    /**
     * The clients already on file that either sheet names by client id, with their companies, keyed by
     * {@link SpreadsheetImport#key}, so a client id typed in lower case still finds its client. Read in
     * one query.
     */
    private Map<String, Client> clientsOnFile(XlsxReader.Sheet clientSheet, XlsxReader.Sheet companySheet) {
        // Client ids are stored in upper case, as PSC-00001.
        Set<String> clientIds = Stream.of(clientSheet, companySheet)
                .flatMap(sheet -> SpreadsheetImport.dataRows(sheet).values().stream())
                .map(values -> values.get(CLIENT_ID))
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.strip().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (clientIds.isEmpty()) {
            return Map.of();
        }
        return clientRepository.findByClientIdIn(clientIds).stream()
                .collect(Collectors.toMap(client -> SpreadsheetImport.key(client.getClientId()),
                        Function.identity()));
    }

    private Map<String, PendingClient> readClients(XlsxReader.Sheet sheet, Map<String, Client> onFile,
                                                   CellErrors errors) {
        // "Today" is the firm's calendar day, read from the business clock.
        LocalDate today = LocalDate.now(clock);
        Map<String, List<UUID>> managers = managersByName();
        Map<String, PendingClient> clients = new LinkedHashMap<>();
        SpreadsheetImport.dataRows(sheet).forEach((row, values) -> {
            Row cells = new Row(CLIENTS, row, values, errors);
            String clientId = cells.text(CLIENT_ID);
            if (clientId == null) {
                cells.error(CLIENT_ID, "must not be blank: give each client their client id");
                return;
            }
            String key = SpreadsheetImport.key(clientId);
            PendingClient earlier = clients.get(key);
            if (earlier != null) {
                cells.error(CLIENT_ID, "repeats the Client ID of row " + earlier.row + "; give each client their "
                        + "own");
                return;
            }
            Client existing = onFile.get(key);
            if (existing != null) {
                // Already on file: the rest of the row is not read, and only companies are added.
                clients.put(key, new PendingClient(row, existing));
                return;
            }
            ClientRequest.ClientRequestBuilder request = ClientRequest.builder()
                    // Stored in upper case, so psc-00001 typed reads as PSC-00001.
                    .clientId(clientId.toUpperCase(Locale.ROOT))
                    .firstName(cells.text(1))
                    .lastName(cells.text(2))
                    .phone(cells.text(3))
                    .email(cells.text(4))
                    .managerId(manager(cells, MANAGER_NAME, managers))
                    .discountPercent(cells.percent(6))
                    .birthday(cells.dateUpTo(7, today));
            clients.put(key, new PendingClient(row, request));
        });
        return clients;
    }

    /** A company as created, with the opening balance the ledger recorded for it, if any. */
    private static ImportedCompany imported(CompanyResponse company) {
        return ImportedCompany.builder()
                .companyId(company.getId())
                .companyName(company.getCompanyName())
                .openingBalanceAmount(company.getOpeningBalanceAmount())
                .openingBalanceDate(company.getOpeningBalanceDate())
                .build();
    }

    /** Amounts added up, at two decimals; 0.00 when there are none. */
    private static BigDecimal sum(Stream<BigDecimal> amounts) {
        return amounts.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Every manager's identifier by full name, keyed by {@link #nameKey}. Names are not unique, so
     * a name may stand for more than one manager.
     */
    private Map<String, List<UUID>> managersByName() {
        return managerRepository.findAll().stream()
                .collect(Collectors.groupingBy(manager -> nameKey(name(manager)),
                        Collectors.mapping(Manager::getId, Collectors.toList())));
    }

    private static UUID manager(Row cells, int column, Map<String, List<UUID>> managers) {
        String name = cells.text(column);
        if (name == null) {
            // Left to validation, which reports the missing manager.
            return null;
        }
        List<UUID> matches = managers.getOrDefault(nameKey(name), List.of());
        if (matches.isEmpty()) {
            cells.error(column, "no manager in the Manager Catalog is named " + name);
            return null;
        }
        if (matches.size() > 1) {
            cells.error(column, matches.size() + " managers in the Manager Catalog are named " + name
                    + "; rename one of them in the catalog, or add this client without the import");
            return null;
        }
        return matches.getFirst();
    }

    /** A name as compared: ignoring case, surrounding whitespace and how the words are spaced. */
    private static String nameKey(String name) {
        return WHITESPACE.matcher(SpreadsheetImport.key(name)).replaceAll(" ");
    }

    private void readCompanies(XlsxReader.Sheet sheet, Map<String, PendingClient> clients, Map<String, Client> onFile,
                               CellErrors errors) {
        // "Today" is the firm's calendar day, read from the business clock, as the ledger reads it.
        LocalDate today = LocalDate.now(clock);
        SpreadsheetImport.dataRows(sheet).forEach((row, values) -> {
            Row cells = new Row(COMPANIES, row, values, errors);
            NewCompanyRequest request = NewCompanyRequest.builder()
                    .companyName(cells.text(1))
                    .companyType(cells.choice(2, CompanyType.class))
                    .address(cells.text(3))
                    .discountPercent(cells.percent(4))
                    .openingBalance(cells.amount(5))
                    .openingBalanceDate(cells.dateUpTo(6, today))
                    .build();
            String clientId = cells.text(CLIENT_ID);
            PendingClient client = clientId == null
                    ? null
                    : clientFor(SpreadsheetImport.key(clientId), clients, onFile);
            if (clientId == null) {
                cells.error(CLIENT_ID, "must not be blank: give the Client ID of the client who holds the company");
            } else if (client == null) {
                cells.error(CLIENT_ID, "no client on the " + CLIENTS_SHEET + " sheet or already on file has the "
                        + "Client ID " + clientId);
            } else {
                client.companies.add(new PendingCompany(row, request));
            }
        });
    }

    /**
     * The client a company is linked to: one on the Clients sheet, or else one already on file, who is
     * then added to the clients imported, after those on the Clients sheet. Null when there is neither.
     */
    private static PendingClient clientFor(String key, Map<String, PendingClient> clients,
                                           Map<String, Client> onFile) {
        PendingClient client = clients.get(key);
        Client existing = onFile.get(key);
        if (client == null && existing != null) {
            client = new PendingClient(null, existing);
            clients.put(key, client);
        }
        return client;
    }

    private void validate(PendingClient client, CellErrors errors) {
        if (client.existing == null) {
            validateNew(client, errors);
        } else {
            validateAdded(client, errors);
        }
    }

    /**
     * Checks a client and their companies against the rules of {@code POST /clients}, reporting each
     * rule broken against the cell it concerns. A cell already reported keeps its first error.
     */
    private void validateNew(PendingClient client, CellErrors errors) {
        for (ConstraintViolation<ClientRequest> violation : validator.validate(client.request())) {
            String path = violation.getPropertyPath().toString();
            Matcher company = COMPANY_PATH.matcher(path);
            if (company.matches()) {
                int index = Integer.parseInt(company.group(1));
                errors.add(COMPANIES, client.companies.get(index).row(), columnOf(COMPANIES, company.group(2)),
                        violation.getMessage());
            } else if (path.startsWith("companies")) {
                // The client has no companies, or too many: reported on the client's own row.
                errors.add(CLIENTS, client.row, CLIENT_ID, violation.getMessage() + " on the " + COMPANIES_SHEET
                        + " sheet");
            } else {
                errors.add(CLIENTS, client.row, columnOf(CLIENTS, path), violation.getMessage());
            }
        }
    }

    /**
     * Checks the companies to add to a client already on file against the rules of
     * {@code POST /clients/{id}/companies}, reporting each rule broken against the cell it concerns: the
     * client must be given at least one, and may not then hold more than {@link ClientRequest#MAX_COMPANIES}.
     */
    private void validateAdded(PendingClient client, CellErrors errors) {
        Client existing = client.existing;
        if (client.companies.isEmpty()) {
            // Only a client on the Clients sheet can be without companies, so the client has a row.
            errors.add(CLIENTS, client.row, CLIENT_ID, "is already on file, so only companies are added to that "
                    + "client: link at least one to it on the " + COMPANIES_SHEET + " sheet");
            return;
        }
        for (PendingCompany company : client.companies) {
            for (ConstraintViolation<NewCompanyRequest> violation : validator.validate(company.request())) {
                errors.add(COMPANIES, company.row(), columnOf(COMPANIES, violation.getPropertyPath().toString()),
                        violation.getMessage());
            }
        }
        int held = existing.getCompanies().size();
        if (held + client.companies.size() > ClientRequest.MAX_COMPANIES) {
            // Reported on the first company that would take the client past the limit.
            PendingCompany over = client.companies.get(Math.max(0, ClientRequest.MAX_COMPANIES - held));
            errors.add(COMPANIES, over.row(), CLIENT_ID, "client " + existing.getClientId() + " already holds "
                    + held + " companies, and may hold at most " + ClientRequest.MAX_COMPANIES + "; "
                    + client.companies.size() + " are linked to it here");
        }
    }

    /** The column filling this field, or the client id column for a field no column fills. */
    private static int columnOf(SheetLayout layout, String field) {
        int column = layout.columnOf(FIELD_COLUMNS.getOrDefault(field, field));
        return column < 0 ? CLIENT_ID : column;
    }

    /**
     * A client named on the workbook, gathering their companies from the Companies sheet: either a new
     * client, read from the Clients sheet, or one already on file, to add the companies to.
     */
    private static final class PendingClient {

        /** The client's row on the Clients sheet; null for a client on file named only on the Companies sheet. */
        private final Integer row;

        /** The new client as read; null for a client already on file. */
        private final ClientRequest.ClientRequestBuilder request;

        /** The client already on file, with their companies; null for a new client. */
        private final Client existing;

        private final List<PendingCompany> companies = new ArrayList<>();

        /** A new client, read from a row of the Clients sheet. */
        private PendingClient(int row, ClientRequest.ClientRequestBuilder request) {
            this.row = row;
            this.request = request;
            this.existing = null;
        }

        /** A client already on file, named on the Clients sheet at a row, or only on the Companies sheet. */
        private PendingClient(Integer row, Client existing) {
            this.row = row;
            this.request = null;
            this.existing = existing;
        }

        /** The client as a create request, with their companies in the order of the Companies sheet. */
        ClientRequest request() {
            return request.companies(companies.stream().map(PendingCompany::request).toList()).build();
        }
    }

    private record PendingCompany(int row, NewCompanyRequest request) {
    }
}
