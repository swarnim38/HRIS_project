# TalentCompass HRIS: Assessment, Feasibility, and Implementation Plan

**Assessment date:** 21 November 2026  
**Scope:** Review of the repository contents and the proposed move from a Java terminal application backed by CSV files to a web-based HRIS.

## Executive assessment

TalentCompass is a useful prototype for a small set of HR reports. Its current code is a single-user Java command-line application with employee data loaded into memory from `startup.csv`, candidate and rejection events appended to CSV, and analytics printed to the terminal. The code already contains a few reusable concepts, especially the employee and candidate models, tenure calculation, department headcount, rejection reason analysis, and basic recruitment cycle-time calculation.

It is not yet a web application or a database-backed HR system. There is no build descriptor, HTTP API, browser UI, relational schema, login, permissions, employee CRUD, audit trail, migration process, automated tests, or deployment configuration in the repository. The `startup.csv` employee master is described by the README but is not present in the checked-in project files. The project `.gitignore` excludes CSV files, which is appropriate for personal data, but leaves no reproducible, sanitized sample dataset for development.

**Feasibility:** The requested architecture is feasible. Keep Java as the application backend, expose REST endpoints through Spring Boot, use Spring JDBC (`JdbcTemplate` or `JdbcClient`) with MySQL Connector/J, and build the browser client in Next.js with TypeScript. The repository's existing business logic needs to be extracted from `HRSystem` into testable services and repositories before adding dashboard features. Replacing CSV persistence with MySQL is a data migration, not a direct UI change.

## 1. Existing project assessment

### Files and responsibilities found

| File | Current responsibility | Assessment |
|---|---|---|
| `src/Main.java` | Creates a shared `Scanner`, loads `startup.csv`, starts the menu | CLI-only entry point; filesystem path and lifecycle are implicit. |
| `src/ui/Menu.java` | Numbered terminal menu for reports and input | Tightly coupled to console input/output and `HRSystem`; replace as an interface, not as the domain layer. |
| `src/service/HRSystem.java` | CSV file I/O, input prompts, employee listing, report calculations, candidate logging, rejection logging, Pareto output | Main change hotspot: mixes persistence, validation, business rules, presentation, and user interaction in one class. |
| `src/model/Employee.java` | Employee fields, CSV parsing, tenure, annual CTC estimate | Reusable starting point, but lacks validation and uses nullable wrappers and a mutable-looking field naming convention. CSV parsing uses `split(",")`, which fails on quoted commas and does not validate column count before indexing. |
| `src/model/Candidate.java` | Candidate fields and application-to-offer cycle-time calculation | Reusable starting point, but candidate status is limited to a free-text string and `PENDING` is not represented as a proper nullable/database date. No getters or persistence mapping are present. |
| `readme.MD` | Documents scope, features, limitations, setup, and roadmap | Helpful description, but some wording is inconsistent with implementation, such as calling CSV a database and calling a lifetime exit share a turnover rate. It also says no build tools are needed, which will change with the web stack. |
| `.gitignore` | Excludes compiled files and CSV/text data | Protects data files from accidental commits. Add `.env`, build output, frontend dependencies, and local database artifacts as the stack is introduced. |

The only root-level data file found was `rejections.csv`; no `startup.csv` or `candidates.csv` was present. The employee import format and live data therefore could not be verified. Continue migration using a sanitized sample and validate the actual employee CSV before any production import.

### Important: fix first because they affect correctness or data safety

| Priority | Finding | Why it matters | Required action |
|---|---|---|---|
| Important | `generateAttritionReport()` divides all exited employees by all rows currently present in the employee file and labels it overall turnover. | This is a historical exit share, not a period turnover rate. It cannot be compared reliably across periods or teams. | Preserve hire and exit dates and define a time window and denominator, such as average headcount for the same period. Name the metric precisely. |
| Important | `Employee.fromCSV()` indexes fields without checking row length and splits on commas without CSV quoting rules. | A malformed row can be misread or skipped, and names/departments containing commas are not supported. | Use a CSV parser for legacy import, validate required fields, report row-level errors, and make import preview/retry possible. |
| Important | Candidate logging accepts an offer date even for rejected offers and accepts unrestricted status text. | Invalid combinations and negative cycle times can enter reporting. | Model application stage/status explicitly, validate date order and allowed transitions, and define time-to-hire as accepted start date or another explicit business endpoint. |
| Important | Salary and employee details are loaded without authentication or authorization. | A browser migration exposes sensitive HR and pay data over a network unless access control is built in. | Require authenticated sessions, role/permission checks, TLS, restricted database credentials, audit events, and careful handling of logs and exports before network access. |
| Important | Pay parity report groups only `M` and `F`, compares unadjusted averages, and includes a fixed alert threshold. | Categories are excluded and an unadjusted group average can be misread as proof of pay equity or discrimination. | Use inclusive, optional demographic categories, report coverage/sample sizes, compare like-for-like roles/levels, and explain methodology. Keep access to sensitive breakdowns restricted. |
| Important | Gratuity eligibility is hard-coded to active/notice status and five completed years. | Eligibility can depend on applicable law, employment conditions, and organizational policy. The existing output should not be treated as a legal determination. | Label as an HR review flag, make rules configurable, and obtain qualified review before presenting compliance status. |
| Important | User-entered values are written directly to comma-delimited files. | Commas, newlines, quotes, duplicate IDs, and simultaneous writes can corrupt the file or records. | Replace append-only flat files with validated transactional database writes and unique constraints. |
| Important | The UI directly invokes methods that print output and read from a shared scanner. | It is difficult to reuse logic in a REST API or test calculations without console interaction. | Move calculation and workflow logic into application services that return typed results; keep adapters for CLI/API separate if the CLI remains. |

### Essential: required for the requested usable HRIS

- **Relational persistence:** normalized tables, primary/foreign keys, unique constraints, migrations, transaction boundaries, connection pooling, and backup/restore procedures.
- **Employee lifecycle:** create, view, edit, search, filter, deactivate/exit, and record effective-dated changes. Avoid hard deletes for employee history.
- **Recruitment workflow:** requisitions, candidates, applications, stage history, interviews, offers, accepted offers, hires, rejection reasons, and responsible recruiter/hiring manager.
- **Identity and permissions:** login, session handling, password/identity-provider strategy, role and action authorization, user provisioning/deactivation, and audit history.
- **Data quality and migration:** CSV staging/import, preview, field mapping, duplicate detection, validation report, idempotent import, and reconciliation against source row counts and totals.
- **Metric definitions and history:** named formulas, period filters, date semantics, source fields, and data completeness indicators. Store event dates or snapshots needed to reproduce prior-period figures.
- **Operational basics:** configuration outside source control, structured errors/logs, health checks, environment setup, database migration, accessibility, responsive layout, and backups.
- **Verification:** automated unit tests for metric definitions and validation, repository/API integration tests against a disposable database, and UI workflow checks. Tests are a required implementation phase, not present today.

### Add-ons: useful after the core data model is stable

- Drill-down charts, team/role/tenure filters, saved dashboard views, and scheduled report exports.
- CSV/XLSX/PDF export with permission checks and audit logging.
- Configurable metric targets, alerts, and data-quality warnings.
- Employee self-service, leave workflows, onboarding tasks, approvals, and document management.
- Engagement and satisfaction survey collection with response privacy controls.
- Promotion history, skills/learning records, compensation history, and workforce planning.
- Notifications, integrations with payroll/recruiting systems, and anonymized aggregate reports.

## 2. Feasibility and gap analysis

### What can be reused

- Java domain concepts and date calculations from `Employee` and `Candidate`.
- Report ideas for active headcount by department, exit/status counts, pay comparisons, tenure milestones, hiring cycle, and rejection reason Pareto.
- Existing CSV files as an initial import source only, after backup, schema validation, and data cleanup.
- Existing menu documentation as a first-pass list of user stories and acceptance criteria.

### What must be added or redesigned

- Spring Boot application structure, Maven or Gradle build, configuration profiles, REST API, validation, exception handling, and OpenAPI documentation.
- A MySQL schema and versioned migrations; JDBC repositories and transactional service methods.
- Next.js App Router application, TypeScript API types, accessible Fluent UI components, Tailwind layout utilities, charts, navigation, forms, loading/empty/error states, and responsive design.
- Authentication, authorization, audit logging, secure configuration, and production deployment topology.
- Historical event data and source systems for HR metrics beyond current employee and candidate records.

### Metric availability in the current data

| Requested metric | Current status | Additional fields or source needed |
|---|---|---|
| Headcount / workforce distribution | Partially available | Effective-dated employment and status history for historical snapshots; current code counts only exact `Active` status by department. |
| Attrition / turnover | Partially available, current formula unsuitable for period turnover | Hire date, exit date, exit type, period definition, average headcount or beginning/ending headcount, and optional regrettable/voluntary categories. |
| Time to hire | Partial prototype | Current candidate dates are application and offer dates; define metric endpoint. Common variants include requisition-open-to-accepted-offer and application-to-accepted-offer. Add requisition and accepted/start dates. |
| Cost per hire | Not available | Internal recruiter time/cost, agency fees, job advertising, background checks, relocation, and count of hires for period; define included cost categories. |
| Quality of hire | Not available | Explicit composite methodology and post-hire outcomes, such as performance review, retention at a defined period, and hiring manager assessment. Requires careful weighting and bias review. |
| Engagement rating / employee satisfaction | Not available | Survey responses, question/version metadata, dates, participation counts, anonymity thresholds, and access rules. |
| Cost per employee | Not available | Period-aligned HR/operating cost and employee count denominator, with consistent inclusions and currency. Finance source required. |
| Revenue per employee | Not available | Period-aligned revenue from finance and average employee count, with organizational scope and currency defined. |
| Time since last promotion | Not available | Promotion history with effective dates and employee link. Current role alone cannot recover promotion history. |
| Absenteeism | Not available | Scheduled/working hours or days, absence dates, types, and a policy for planned leave and partial days. Leave system integration may be preferable. |
| Pay parity | Partially available | Current basic salary and binary-coded gender are present in the model; historical compensation, job level, hours/FTE, currency, broader demographics, and appropriate comparison cohorts are missing. |

These metrics cannot be responsibly calculated from a dashboard alone. The UI can display them only after the underlying data capture, period definitions, and missing-data rules are agreed and implemented. Every KPI should show its formula, period, denominator, refresh timestamp, and data coverage.

### Metric definition examples to agree before coding

| KPI | Candidate definition to validate with HR/Finance |
|---|---|
| Time to hire | Median calendar days from approved requisition opening to accepted offer, grouped by role and period. |
| Cost per hire | Total agreed recruiting costs in period divided by hires in period. Exclude or include internal recruiter labor consistently. |
| Turnover rate | Exits during period divided by average headcount during period, multiplied by 100. Decide whether this is all-cause, voluntary, or regrettable turnover. |
| Absence rate | Absence hours divided by scheduled work hours for the same period. Define treatment of leave categories. |
| Time since promotion | Current date minus most recent promotion effective date; report median/percentiles and flag missing promotion history. |
| Revenue per employee | Revenue for the period divided by average employee count for the same scope and period. |

These are proposal definitions, not assumptions about the user's organization. They need to be confirmed before implementing the measures.

## 3. Recommended architecture and technology choices

### Application architecture

```text
Browser (Next.js + TypeScript)
  Fluent UI React v9 controls and Fluent 2 tokens
  Tailwind CSS for layout and responsive utilities
  Motion for restrained transitions; Lucide React for icons
  Dashboard charts from API data
          │ HTTPS / JSON REST
          ▼
Spring Boot REST API (Java)
  Controllers → application services → JDBC repositories
  Spring Security + validation + audit events
  Flyway migrations + OpenAPI contract
          │ JDBC / transaction pool
          ▼
MySQL 8.4 / InnoDB
  HR master data + dated events + survey/finance aggregates
```

Keep the API and database on a trusted server/network. The browser must never connect directly to MySQL or receive database credentials. Treat Spring Boot as the system of record and the only application path for reads/writes. Next.js can call the API from server-side code or through a same-origin proxy; avoid duplicating business rules in Next.js route handlers.

### Stack recommendation

| Layer | Recommendation | Why |
|---|---|---|
| Backend | Java 21 LTS + Spring Boot 3.x, Maven | Modern Java baseline and conventional service/API structure. Pick a supported Spring Boot release when implementation starts and pin it in the build. |
| Database access | `spring-boot-starter-jdbc`, `JdbcTemplate`/`JdbcClient`, MySQL Connector/J, HikariCP | Satisfies the JDBC requirement while keeping SQL visible and supporting transaction-managed service methods. Spring Boot configures JDBC support and HikariCP through the JDBC starter. |
| Database | MySQL 8.4 LTS with InnoDB | Best fit with the stated MySQL requirement and familiar relational design. Use foreign keys, constraints, transactions, and indexes. |
| Schema changes | Flyway migrations | Repeatable, reviewed schema history across developer, test, and deployment environments. |
| API security | Spring Security | Central authentication, endpoint authorization, CSRF/session controls as applicable, and role/permission enforcement. |
| Frontend | Next.js App Router + TypeScript | Clear route/layout structure, strong typing, and server/client component options. Consume the Java API, with no direct SQL from the frontend. |
| UI | Fluent UI React v9 (`@fluentui/react-components`) + Fluent 2 theme; Tailwind CSS | Microsoft recommends Fluent UI React v9 for Fluent 2 React experiences. Use Fluent controls for core widgets and Tailwind mainly for grid/spacing/layout to limit competing styling systems. |
| Charts / interaction | Recharts or another accessible chart library; Motion; Lucide React | Charts should encode definitions and accessible text summaries. Use animation only to clarify state changes, not decorate every interaction. |
| API contract | OpenAPI/Swagger | Makes frontend/backend payloads explicit and reduces duplicated or drifting types. |
| Test layers | JUnit 5, Spring integration tests, frontend component/e2e checks | Protect calculations, import rules, permissions, and main workflows. |
| Local operations | Docker Compose for local MySQL and app dependencies | Gives contributors a reproducible development setup without committing personal data. |

**JDBC note:** JDBC is the Java standard interface to a relational database. Spring's `JdbcTemplate` / `JdbcClient` handles repetitive connection and exception work; repository classes still contain SQL. This is different from having Next.js use a MySQL driver, and preferable here because Java owns the existing business logic and permissions.

### DBMS comparison

| DBMS | Fit for this project | Trade-off |
|---|---|---|
| MySQL | **Recommended for this scope** | Directly matches the requested backend and supports relational integrity and transactional writes with InnoDB. Strong fit for typical HR CRUD and dashboard queries. |
| PostgreSQL | Also an excellent choice | Strong relational and analytics capabilities; PostgreSQL supports materialized views when precomputed reporting is useful. It is not necessary to switch from the explicitly requested MySQL for this project's expected scale. |
| MongoDB | Not recommended as the primary store | Employee, department, requisition, candidate, compensation, survey, and event relationships need consistency, joins, constraints, and time-based reporting. Document storage may be appropriate later for uploaded files, but not as the core HR database. |

The decision is based on the repository and stated product goals, not a benchmark. If deployment constraints, existing operational expertise, or future integrations favor PostgreSQL, the repository is small enough that this choice can still be revisited before creating production data.

## 4. Proposed data model (first version)

Start with normalized operational records and explicit dated events. Avoid putting every value in a wide employee table or attempting to compute historical metrics from current values alone.

- `users`, `roles`, `user_roles`: identities and application permissions. Prefer a managed identity provider if available; never store plaintext passwords.
- `employees`: stable employee identity and current profile attributes; unique employee number, department, job/level, location, employment type, and status.
- `employment_events`: hire, status change, leave, exit, rehire, with effective date and actor/source.
- `compensation_history`: effective-dated salary/CTC, currency, pay frequency, and approved changes; restricted access.
- `promotion_events`: employee, prior/new role or level, effective date, approval reference.
- `requisitions`, `candidates`, `applications`, `application_stage_events`, `offers`, `hires`, `rejection_events`: recruitment history and cycle-time inputs.
- `surveys`, `survey_questions`, `survey_responses`: survey version and dated response records, with aggregation/privacy thresholds.
- `absence_records` (or an integration): absence category, time range, duration, and source.
- `financial_periods` / `financial_metrics`: approved aggregate finance inputs for recruiting cost, operating cost, and revenue; define currency and organizational scope.
- `audit_events`: actor, action, entity, timestamp, and minimal change metadata. Do not copy full sensitive records into logs.

Use database constraints for identity and referential integrity. Keep metric SQL or service methods centralized and versioned. For dashboards, return small, typed aggregate DTOs, not every employee row. Sensitive breakdowns require authorization checks and minimum-group rules.

## 5. Data migration plan

1. Back up the source CSVs securely and keep the originals read-only during migration.
2. Create sanitized examples representing valid records and edge cases. Do not commit real employee information.
3. Define a versioned import mapping from the CSV header/columns to the relational schema. Confirm salary units, status vocabulary, date formats, IDs, and whether exited employees remain in the file.
4. Parse CSV with a library and load to staging tables. Produce a validation report for missing/duplicate IDs, invalid dates, salary values, unknown statuses, and malformed records.
5. Resolve errors with the data owner. Do not silently discard or auto-correct ambiguous HR records.
6. Import with stable identifiers and idempotency safeguards; reconcile row counts, current headcount by status/department, salary totals/averages, and candidate/rejection counts.
7. Run old and new reports side-by-side on the same fixture data. Document metric differences, especially the corrected turnover definition.
8. Cut over writes to the API/database, retain a protected rollback copy, and remove CSV runtime writes only after reconciliation is signed off.

## 6. Phased implementation plan

| Phase | Work | Completion evidence |
|---|---|---|
| 0. Product/data decisions | Confirm users and roles; choose single organization or multi-tenant; agree KPI definitions, periods, sources, privacy rules, salary basis/currency, and deployment environment. Inventory and sanitize source CSVs. | Approved glossary, data map, sanitized fixtures, and initial acceptance criteria. |
| 1. Backend foundation | Introduce Maven, Spring Boot, configuration profiles, MySQL Connector/J, JDBC repositories, Flyway, health endpoint, API error format, validation, and OpenAPI. Extract current logic from `HRSystem` into services. | App starts against local database; migrations apply cleanly; core service and repository coverage exists. |
| 2. Core HR and import | Employee directory CRUD/search, departments, employment event history, CSV staging/import, data-quality report, audit events, authentication/authorization. | Validated import reconciles; unauthorized access is rejected; employee lifecycle works from API. |
| 3. Recruitment | Requisitions, candidate applications, stage changes, offer/acceptance, rejection reasons, cycle-time metrics. | Workflow state transitions and time-to-hire calculations match agreed examples. |
| 4. Dashboard v1 | Headcount, department mix, hiring funnel/time-to-hire, turnover/attrition, rejection reasons, and pay reporting where data/access permit. Add filters, period labels, definitions, and coverage warnings. | Dashboard values reconcile to explicit fixtures and each KPI shows its formula/period. |
| 5. Frontend | Next.js TypeScript shell, Fluent 2 layout/navigation/forms/tables, Tailwind responsive layout, accessible charts, loading/empty/error states, restrained Motion, Lucide icons. | Main user journeys usable on desktop and tablet with keyboard navigation and clear error states. |
| 6. New metric inputs | Add promotion, absence, survey, finance cost/revenue, and post-hire quality data sources as separately approved modules. | Each metric has a confirmed definition, source, privacy rule, period handling, and test fixtures. |
| 7. Hardening and release | Security review, integration/e2e tests, backups and restore rehearsal, deployment configuration, monitoring, accessibility review, import reconciliation, release and rollback runbook. | Rehearsed release and restore; documented operations; no secrets or real HR data in Git. |

Recommended order: complete the decisions and backend data model before investing heavily in dashboard visuals. The dashboard can only be trusted after the source events, formulas, authorization, and historical capture are implemented.

## 7. Decisions needed before implementation

The current architecture can be planned, but these product questions materially affect schema and permissions:

1. Is this for one organization, or should it support multiple organizations/tenants?
2. Which roles are needed at launch (for example HR Admin, Recruiter, Manager, Finance, Viewer), and which roles may see salary, demographic, survey, and individual employee records?
3. What is the intended source and definition for each dashboard KPI, particularly quality of hire, cost per hire, engagement, satisfaction, absenteeism, and revenue per employee?
4. Is there a sanitized `startup.csv` and `candidates.csv` available for import design? The current repository does not contain the employee master file.
5. Where should the app run: local/self-hosted, a VPS/cloud service, or a managed platform? This affects authentication, database networking, backups, and deployment.

## References

- [Spring Boot SQL database support](https://docs.spring.io/spring-boot/reference/data/sql.html)
- [Spring Framework JDBC core](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html)
- [Next.js App Router](https://nextjs.org/docs/app)
- [Next.js Route Handlers](https://nextjs.org/docs/app/getting-started/route-handlers)
- [Microsoft Fluent 2 development guide](https://fluent2.microsoft.design/get-started/develop/)
- [Fluent UI React v9 repository and package](https://github.com/microsoft/fluentui)
- [Spring Security request authorization](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
- [MySQL 8.4 InnoDB overview](https://dev.mysql.com/doc/refman/8.4/en/innodb-introduction.html)
- [MySQL foreign key constraints](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html)
- [PostgreSQL materialized views](https://www.postgresql.org/docs/current/rules-materializedviews.html)
