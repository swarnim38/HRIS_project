# TalentCompass release checklist

## Evidence from the Windows checkout

- Baseline before the release changes: 2 backend scope tests, 78 legacy regression checks and a CLI smoke test passed. The initial port 8080 probe found no listener.
- Release: Maven packaged the Java 25 target using JDK 26.0.1. Both backend scope tests, 78 legacy checks, the CLI smoke test, TypeScript and the Next.js production export passed.
- Disposable MySQL 8.4: 67 API assertions passed. Five assertions passed after restart and again after restoration. The restore retained 14 employees, the earlier hire and its audit event, and omitted a post-backup edit.
- Microsoft Edge: desktop and 768 px tablet tests covered login, keyboard focus, required-field errors, employee create/edit/reload, hiring, metric inputs, CSV correction and unchanged source bytes, audit, role differences and tenant isolation. The browser harness checked unexpected application errors and failed API responses.
- Running demo: Main applied Flyway, served the login page on port 8080, returned health UP and authenticated an HR Admin. A second launch detected the occupied port. Existing working database and CSVs were outside the demo project.
- Reconciliation: legacy CLI and the new API agreed on 2 employees and 0% turnover for identical dated synthetic input. The release uses the dated period-rate definition.

| Requirement | Implementation location | Verification evidence | Status |
| --- | --- | --- | --- |
| Important: period turnover and dated hire and exits | WorkforceReports, Metrics, employment_events | API verifies known zero and positive turnover, date and department changes. Legacy comparison agrees | Verified |
| Important: CSV parsing, quoting, validation, preview and retry | io.Csv, EmployeeImport, Imports | API tests line 3, quoted names, retry and USD/monthly totals. Edge corrected a copied file and confirmed the source bytes | Verified |
| Important: recruitment stages and date order | Recruitment.Application, Operations, application_stage_events | API/Edge recorded stages through hire and checked separate duration calculations. Legacy regression covers invalid transitions | Verified |
| Important: authorized and authenticated financial records, logging and access | LocalIdentity, DirectoryRepository, Operations | API verifies anonymous, financial, Manager and tenant denials, CSRF and account deactivation. The app binds to loopback | Verified locally |
| Important: compare pay descriptively by group | CompensationReports, Metrics, employment_events and pay history | February fixture releases USD groups, suppresses the small EUR cohort and confirms an exact mean of 100 | Verified |
| Important: configurable tenure review, with HR label | WorkforceReports, Metrics, metric_settings | January fixture returns one flag at the five-year threshold | Verified |
| Important: validated transactional writes and uniqueness | Flyway V1–V4, Operations, Imports | Hire retry yields one employee and a single hire audit event. CSV retry returns the same receipt | Verified |
| Important: reuse rules outside the CLI | src/service, src/backend, LegacyCli | Two backend scope tests, legacy regressions, and live API/browser suites | Verified |
| Essential: relational persistence, foreign keys, Flyway, pooling, backup/restore | Flyway, JDBC, Compose | Clean migrations and independent restore through the release harness | Verified |
| Essential: employee directory, search/filter, editing, department/level, status, exit and rehire | Operations, employment_events, browser | Edge creates and edits a profile. API checks transfer dates, exit/rehire and imported snapshots | Verified |
| Essential: recruitment, responsibility, candidates, stage history, offers, hires and rejection reasons | Operations, requisitions, applications, application_stage_events, hires | Browser/API flow checks dated accepted offer and hire. API verifies interview by scoped Manager, two forms of rejection, decline, withdrawal and terminal-stage denials | Verified |
| Essential: login, sessions, provisioning, reassignments, deactivation and audit | LocalIdentity, IdentityController, local_accounts, audit_events | Browser verifies sign-in and sign-out for all roles. API verifies provisioning, reassignment during a session, revoked access and persistent audit | Verified |
| Essential: CSV staging/preview, mapping, validation, deduplication and reconciliation | Imports, import_receipts, CSV import screen | Employee, candidate and rejection paths exercised. Preview hashes the source and mapping and commit revalidates | Verified for supported layouts |
| Essential: metrics, data completeness and scope | Metrics, README | Hand-checkable fixture values and missing, zero and suppressed examples | Verified |
| Essential: errors, readiness, local setup, accessibility and responsive layout | Main, HealthController, API errors, frontend, README | Launch and restart, browser validation and keyboard focus, two viewports and port conflict | Verified locally |
| Essential: automated regression and repository/API/UI checks | tests, frontend/tests | Maven, legacy runner, live MySQL and Edge tests | Verified |
| KPI: headcount and workforce distribution | Metrics, employment_events | Two initial employees. Before/after transfer shows ENG 1 and OPS 1, then OPS 2 | Verified |
| KPI: period turnover | Metrics, WorkforceReports | January 0%. March 1 separation over the tested average headcount. Missing historical coverage has its own status | Verified |
| KPI: time to fill, time to hire and legacy application to offer | Metrics, application_stage_events, legacy_recruiting | January fixture 8/6/8 days. Browser April case 4/3 days. Legacy Accepted does not establish an acceptance date | Verified |
| KPI: cost per hire | Metrics, financial_periods, hires | Exact period 1,200 USD per hire, then 900 after a Finance edit. Browser input yields 1,000 USD | Verified |
| KPI: quality of hire | Metrics, post_hire_outcomes, metric_settings | Weighted outcome is 80 through API and 85 after browser input in a separate period | Verified |
| KPI: engagement and satisfaction | Metrics, surveys, survey_responses, metric_settings | Engagement 4/5, no data outside the period, satisfaction suppressed for a small sample. Browser records an anonymous score batch and shows 4/5 | Verified |
| KPI: employee cost and revenue per employee | Metrics, financial_periods, dated workforce | January baseline 5,000 and 15,000 USD per employee, each using 2 people. A matching period and complete denominator are required | Verified |
| KPI: time since last promotion | Metrics, promotions | Case with promotion on January 1 shows 30 days by January 31 | Verified |
| KPI: absenteeism | Metrics, attendance_days | 2 unplanned hours / 16 scheduled gives 12.5%. Corrected input yields 0%, as does a recorded 0-hour absence in the browser case | Verified |
| KPI: pay comparison | CompensationReports, Metrics | Comparable group mean, count, incompatible currency and suppression checked | Verified |
| KPI: rejection analysis and tenure | Metrics, legacy_recruiting, stage events, settings | 1 typed plus 1 legacy rejection and 1 tenure flag | Verified |
| Data: organizations, departments, role and tenant assignments | Flyway V1–V2, LocalIdentity | DEMO-A and DEMO-B share E1/ENG and return different records. Department access and reassignment checked | Verified |
| Data: employment records, dated terms, compensation and promotions | Flyway V1–V4, Operations | Historical transfers and pay cohorts verified. Profile stores job, level, location and employment type. The browser verifies that location and employment type survive editing and reload | Verified |
| Data: requisitions, stages, interviews, offers, hires and rejections | Flyway V2 and Operations | Effective-dated application stage ledger, offer decisions, withdrawals and tenant-referenced hire tested | Verified |
| Data: surveys, questions and responses, attendance, finance, outcomes and audit | Flyway V2 and Metrics | Anonymous response batches and other inputs tested. Each survey version currently has one question | Verified for supported model |
| Migration 1: preserve source CSVs and protect backups | Browser file preview, README | Edge proved exact source bytes persisted unchanged. Synthetic backup restored in separate volume | Verified on synthetic data |
| Migration 2: sanitized cases | tests/ReleaseApi.ps1, frontend/tests/release.mjs, tests/Compare-Legacy.ps1 | Two tenant fixtures with valid, invalid, quoted, duplicate and legacy rows | Verified |
| Migration 3: stable mappings, dates, salary units and ownership | Imports and README | Legacy and scoped employee layouts, candidate and rejection layouts, tenant from session, explicit pay currency/FTE | Verified for supported layouts |
| Migration 4–5: preview and correct a rejected file | Imports and CSV import screen | Edge and API report invalid rows, correct a copy and commit | Verified |
| Migration 6–7: reconcile, retry and compare legacy results | import_receipts, Compare-Legacy.ps1 | Counts, pay total, one receipt per input, and matching headcount/turnover across tools | Verified on synthetic data |
| Migration 8: cutover and rollback | Main, README and tests/Release.ps1 | Browser writes to MySQL. Disposable restore omitted the post-backup edit. Actual organizational cutover needs source verification and approval | Synthetic rehearsal verified |
| Phase 0: decisions, glossary and fixtures | Approved policy, README and demo migrations | Multi-tenant and write roles agreed. Demo policies documented and configurable. Real thresholds require organizational review | Verified for demo |
| Phase 1: backend foundation | Main, Spring Boot 4.1, Flyway, JDBC, OpenAPI | Build, live migration, login page and health, second launch detection | Verified |
| Phase 2: core HR, identity, imports and audit | LocalIdentity, Operations, Imports, browser | API/Edge and restart/persistence suite | Verified for supported models |
| Phase 3: recruitment | Operations and Recruitment.Application | Edge requisition-to-hire. API validates dates, retries, offer decline, withdrawal and rejection counts | Verified |
| Phase 4: dashboard | Metrics and frontend | Hand-checkable scoped values and edge states. Browser verifies post-entry metric changes | Verified |
| Phase 5: Next.js and Fluent UI browser experience | frontend | Type check and export, Edge desktop/tablet, keyboard and form states. Layout uses CSS, rather than Tailwind | Verified locally |
| Phase 6: required KPI collection | Metrics, browser forms | Finance, attendance, survey, outcome, compensation and progression records produce calculated returns | Verified for supported inputs |
| Phase 7: operations and hardening | Main, README, Release.ps1 | Disposable restart, backup/restore and data rollback observed. Internet TLS, rate limiting and a separate security review remain conditions for a network rollout | Verified locally |

## Optional add-ons

Optional ideas include saved views, scheduled exports, payroll integrations, employee self-service, leave approval, document management, learning records, workforce planning and notifications. Survey collection, promotion and compensation are in the release because the required KPIs depend on them.
