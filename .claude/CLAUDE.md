# CLAUDE.md

## Project Overview
Java-based microservices project developed in IntelliJ IDEA.

## ⚠️ Critical Constraints — Read First
- **DO NOT run any build commands** (mvn, gradle, javac, etc.)
- **DO NOT run any test commands** (mvn test, gradle test, junit, etc.)
- **DO NOT run any server/application startup commands**
- **DO NOT execute any shell commands unless explicitly asked**
- All builds and tests are run manually by the developer via CLI
- Your role is strictly: **read, analyze, and edit code files only**
- **DO NOT UPDATE api spec**

## Allowed Actions
- Read and analyze source files
- Create new Java classes, interfaces, and configuration files
- Edit existing source files
- Suggest code changes and explain reasoning
- Review code for bugs, issues, or improvements when asked

## Package Structure
All code lives under the base package `com.psc.cl`. Place new
classes in the package matching their responsibility — do not introduce other
top-level packages without being asked.
This project will have different modeules in backend as part of same project. Repeat below structure for each module.

| Package      | Contents                                                                       |
|--------------|--------------------------------------------------------------------------------|
| `config`     | All configuration classes (`@Configuration`, `@ConfigurationProperties`, etc.) |
| `controller` | REST/API controllers (`@RestController`)                                       |
| `dto`        | Data Transfer Objects — request/response payloads (incl. error responses)      |
| `exception`  | Custom exception classes and the global exception handler (`@RestControllerAdvice`) |
| `model`      | Plain domain POJOs / models                                                    |
| `repository` | Spring Data repositories                                                        |
| `security`   | Authentication / authorization components                                      |
| `service`    | Business-logic services                                                        |

## Java & Microservices Standards

### Code Style
- Follow standard Java naming conventions (PascalCase for classes, camelCase for methods/variables)
- Use `final` for fields that should not be reassigned
- Prefer constructor injection over field injection (`@Autowired` on fields is discouraged)
- Always add `@Override` annotation when overriding methods
- Keep methods short and single-responsibility
- Maximum line length: 120 characters

### Microservices Patterns
- Each service must be independently deployable — do not create cross-service compile-time dependencies
- Use DTOs for inter-service communication, never expose domain entities directly
- Define all API contracts via interfaces before implementation
- Use `@RestController` + `@RequestMapping` with explicit HTTP method annotations
- Always version your REST APIs (e.g., `/api/v1/...`)

### API Documentation (Swagger / OpenAPI)
- The project uses springdoc-openapi; the API **must** be viewable via the Swagger UI endpoint
  (`/swagger-ui.html`) and the OpenAPI JSON (`/v3/api-docs`). Keep these endpoints reachable —
  do not block them in security/interceptor config.
- **Always** annotate REST controllers and their endpoints with Swagger/OpenAPI annotations:
  - `@Tag` on each controller (name + description)
  - `@Operation(summary = "...", description = "...")` on every endpoint method
  - `@ApiResponses` / `@ApiResponse` documenting the success and error status codes returned
  - `@Parameter` for path/query/header params; `@Schema` on DTO fields for descriptions, examples,
    and required-ness
- Document authentication: reference the relevant `@SecurityScheme` (see `OpenApiConfig`) so the
  Swagger UI shows how to authenticate before calling protected endpoints.
- Any new endpoint is considered incomplete until it is documented and visible in Swagger UI.

### Error Handling
- Use custom exception classes extending `RuntimeException` for business errors
- Always define a `@ControllerAdvice` global exception handler per service
- Never swallow exceptions silently — always log or rethrow
- Return meaningful HTTP status codes (avoid generic 500s)

### Logging
- Use SLF4J with Logback (`private static final Logger log = LoggerFactory.getLogger(ClassName.class)`)
- Never use `System.out.println` — always use the logger
- Log at appropriate levels: DEBUG for internals, INFO for business events, WARN for recoverable issues, ERROR for failures
- Never log sensitive data (passwords, tokens, PII)

### Security
- Never hardcode credentials, secrets, or API keys in source code
- Use environment variables or config server for all secrets
- Validate all inputs at the controller layer
- Use `@Valid` / `@Validated` with Bean Validation annotations on DTOs
- Sanitize all data before persistence

### Dependency Management
- Do not add new Maven/Gradle dependencies without explicitly being asked
- Do not change dependency versions without explicitly being asked
- Do not modify `pom.xml` or `build.gradle` unless instructed

### Database & JPA
- Always define indexes for frequently queried columns
- Use `Optional<T>` for repository methods that may return null
- Never use `FetchType.EAGER` on collections — default to `LAZY`
- Always annotate entity classes with `@Entity` and provide a no-arg constructor

### Testing (Code Only — No Execution)
- Write unit tests using JUnit 5 and Mockito
- Test class naming convention: `ClassNameTest.java`
- Aim for one test class per production class
- Use `@MockBean` for Spring context tests, `@Mock` for plain unit tests
- Do not run tests — only write or modify test files