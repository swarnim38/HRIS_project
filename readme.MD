# TalentCompass HRIS

TalentCompass is a local, browser-based Human Resource Information System (HRIS). It helps HR teams manage employee records, recruitment, compensation history, and workforce reports. The browser app runs on your own computer, and its demo uses fictional data.

This project also includes an optional terminal menu for older CSV-based reports. Most users should start with the browser app described below.

## What the app can do

- **Employee directory:** Search employee records and review job, department, and employment history.
- **Recruitment:** Track job openings, applications, interviews, offers, acceptances, and hires.
- **Compensation:** Review dated pay records and promotion history, depending on your access.
- **Workforce reports:** Review staffing, hiring, attendance, pay comparisons, and other measures when the required information is available.
- **CSV imports:** Preview employee or hiring records, correct row errors, and then import valid data.
- **User access:** HR Admins, managers, and Finance users see the actions allowed for their role and organization.

The demo runs on this computer and stores its records in a local MySQL database managed by Docker. The included demo accounts and records are fictional.

## Start the browser app on Windows

You do not need VS Code. Use **Windows PowerShell** or **Windows Terminal** and keep its window open while TalentCompass is running.

### What you need

- [Java Development Kit (JDK) 25 or newer](https://adoptium.net/temurin/releases/)
- [Docker Desktop for Windows](https://docs.docker.com/desktop/setup/install/windows-install/), open before starting the app
- [Node.js 20.9 or newer](https://nodejs.org/en/download) and [pnpm](https://pnpm.io/installation)
- [Maven](https://maven.apache.org/install.html), or a Maven copy already available in the local Maven wrapper cache

### First-time demo setup

1. Open Docker Desktop and wait for it to finish starting.
2. In File Explorer, open the `HRIS_project` folder—the folder containing `Start-TalentCompass.ps1`.
3. Click the File Explorer address bar, type `powershell`, and press **Enter**. A PowerShell window opens in the project folder.
4. Build the app and start its demo database and server:

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Start-TalentCompass.ps1 -Setup -Demo
   ```

   This command runs the setup script with a temporary PowerShell policy for that process only. It does not change your computer's saved execution policy. First-time setup installs the browser dependencies, builds the browser pages and Java app, and creates private demo passwords if needed.

5. Wait until the terminal says **TalentCompass backend ready**, then open [TalentCompass](http://127.0.0.1:8080/) in your browser. Leave the terminal open while you use the app.

### Sign in to the demo

Use **DEMO-A** for Organization and **a-admin** for Username. To get the password, open `talentcompass.local.properties` in the project folder and copy the value after `HRIS_DEMO_PASSWORD=`. Do not share or upload this file. The database password in that file is different; it is not the sign-in password.

| Organization | Username | Access overview |
| --- | --- | --- |
| `DEMO-A` | `a-admin` | Administers the demo organization and its HR records. |
| `DEMO-A` | `a-manager` | Views Engineering records and records permitted recruitment updates for that department. |
| `DEMO-A` | `a-finance` | Views financial records and enters permitted financial inputs. |
| `DEMO-B` | `b-admin` | Administers a separate demo organization. |

All demo accounts use the same generated demo password. Each organization has its own records, even when employee numbers are the same.

## Use the app

After signing in, choose a section from the app's menu. The sections available depend on your role.

| Section | Purpose |
| --- | --- |
| **Overview** | Choose a date range and review workforce and hiring measures. |
| **Employees** | Find a person and review their profile and work history. |
| **Recruitment** | Manage openings, applications, and hiring stages. |
| **Compensation** | Review compensation and promotion records. |
| **Metric inputs** | Enter information used by reports, such as recruiting costs and scheduled hours. |
| **CSV import** | Preview a spreadsheet export, correct errors, and confirm valid records. |
| **Administration** | Manage accounts and review the history of recorded actions. |

Reports include both dates in the selected range. **Missing inputs** means the report needs more information. **Zero denominator** means there was nothing to divide by. **Suppressed** means the result is hidden to protect privacy. See [report definitions](#browser-report-inputs) for details.

### Practice with the college demo

On a fresh demo database, these steps show how recording a hire affects the employee list and reports:

1. Sign in as `DEMO-A` / `a-admin`. In **Overview**, select **January 1–31, 2025**. The starting demo shows 2 employees, 0% turnover, 12.5% absenteeism, and engagement of 4 out of 5.
2. In **Recruitment**, open an Engineering job dated **January 2, 2025** and assign it to `a-manager`. Link a candidate who applied **January 4**.
3. Record an interview on **January 5**, an offer on **January 8**, acceptance on **January 10**, and a hire on **January 21**. Give the new hire a unique employee number.
4. Return to **Overview**, use the same date range, and choose **Update reports**. The demo should show 3 employees, 8 days to fill, 6 days to hire, and USD 1,200 per hire.
5. Find the new employee under **Employees** and review the recorded changes under **Administration → Audit history**.

### Import a CSV file

A CSV is a plain-text table that most spreadsheet programs can save or export. Use fictional records for the demo.

1. Sign in as an HR Admin and open **CSV import**.
2. Select the format that matches your file and choose the file to upload.
3. Select **Preview CSV**. Review the row errors, valid row count, and pay totals.
4. Correct the preview and check it again. Your original file on your computer is not changed.
5. Select **Commit validated records** to import the valid records.

See [CSV mapping](#browser-csv-mapping) for the supported columns and values.

## Start the app again

After first-time setup, open Docker Desktop. In File Explorer, open the `HRIS_project` folder, click the address bar, type `powershell`, and press **Enter**. Then run:

```powershell
java -jar .\target\talentcompass-0.2.0.jar
```

When the terminal says **TalentCompass backend ready**, open [http://127.0.0.1:8080/](http://127.0.0.1:8080/). Keep the terminal window open until you finish. Press **Ctrl+C** in that window to stop the app. The demo database keeps its data when the app or MySQL container is stopped.

## Troubleshooting

| What you see | What to do |
| --- | --- |
| The browser cannot connect | Make sure Docker Desktop is running, start the app from PowerShell, and wait for **TalentCompass backend ready**. Open the address printed in the terminal. |
| The terminal says the backend port is in use | Another app instance may already be running. Check [the health page](http://127.0.0.1:8080/health). If it reports `UP`, open the app instead of starting a second copy. Otherwise, close the program using port 8080 or configure a free `HRIS_PORT`. |
| PowerShell says scripts are disabled | Use the first-time setup command above, which applies a temporary policy to the setup process only. On a managed computer, follow your administrator's instructions. See [Microsoft's PowerShell execution policy guide](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.core/about/about_execution_policies). |
| Sign-in says the organization, username, or password is invalid | Check all three fields, even if Organization is highlighted. Use the value after `HRIS_DEMO_PASSWORD=` in `talentcompass.local.properties`. |
| MySQL reports a password error | An existing Docker database keeps the password it was created with. Use that database's original password; changing the local properties file does not change an existing database account. |
| A setup file is missing | Run the first-time demo setup command from the project folder. If Maven is missing, install it and add its `bin` folder to `PATH`. |

## Optional terminal menu

The project also keeps a legacy, CSV-based terminal menu for local reports. It is separate from the browser app and does not use the browser app's MySQL records. To learn about its CSV files and commands, see [the legacy CSV CLI section](#optional-legacy-csv-cli).

## Reference

The sections below explain reporting rules, accepted file formats, and maintenance. The [assessment checklist](ASSESSMENT_CHECKLIST.md) records the release checks and known limits.

### Browser report inputs

All reporting periods are inclusive. The browser shows the period, authenticated scope, refresh time, formula and coverage. HR Admin owns the configured survey/pay minimums, tenure review years and quality weights. Finance or HR Admin owns financial input. Demo settings use 3 survey responses, 2 pay observations per disclosed group, 5 tenure years and quality weights of 50/25/25.

| Measure | Required records and calculation |
| --- | --- |
| Headcount and distribution | Dated employment events and declared coverage on the end date. The latest event on or before that date determines status and department. |
| Turnover | Dated separations during the period divided by the average daily employed headcount in the same scope. Coverage must include the entire period. A departure is counted in the recorded department. |
| Time to fill / time to hire | Median calendar days from requisition opening / application to a recorded accepted offer in the period. |
| Legacy application to offer | Median calendar days from recorded application to offer. Acceptance is unavailable in the legacy layout. |
| Cost per hire | Exact-period recruiting cost in one currency divided by typed hires in the period. |
| Quality of hire | Mean configured weighted performance, manager score and retention. Each hire contributes its latest dated outcome within the period. |
| Engagement / satisfaction | Average 1–5 anonymous scores for complete version groups. A period splitting a version or a small group is suppressed, including in an organization total. |
| Employee cost / revenue per employee | Exact-period cost / revenue divided by average daily headcount with verified coverage. Periods and currencies remain separate. |
| Time since last promotion | Median calendar days from last visible promotion through the reporting end for employees active on that date. Displays observed coverage. |
| Absenteeism | Unplanned absent hours divided by scheduled hours for recorded employee days. Scheduled zero and absent inputs are distinct. Planned leave is excluded from the supplied unplanned hours. |
| Unadjusted pay comparison | Means within matching department, job, level, FTE, pay basis and currency by disclosed category. Small cohorts are suppressed. This is a descriptive base-pay measure. |
| Tenure review | Completed years since original hire for Active or Notice Period employees, using the configured threshold and covered history. |
| Rejections | Dated pre-offer rejections plus imported legacy rejections, grouped by reason. Declines and withdrawals have separate stages. |

Sources for applicable benchmarks include the [SHRM turnover formula](https://www.shrm.org/topics-tools/news/talent-acquisition/data-will-show-employees-leave-stay), [SHRM recruiting benchmark](https://www.shrm.org/content/dam/en/shrm/research/2025-recruiting-benchmarking-report.pdf), and [SHRM HR glossary](https://www.shrm.org/topics-tools/tools/hr-glossary). Attendance, quality weights and privacy thresholds are explicit local reporting policies.

### Browser CSV mapping

See the [accepted employee headers](#department-catalog-and-employee-snapshot) below. Tenant ownership comes from the signed-in account. In the legacy employee layout choose the snapshot date and supply currency and FTE if pay is present. The Department value is matched to the configured department name. Scoped employee rows must match the current organization. Both legacy recruiting layouts have no header. Supply the department, import candidates before their associated rejections, and retain the original source as a read-only copy. If you change the file or its settings, preview it again before committing. It reports valid row counts and totals by currency and pay basis. A snapshot records employment on its effective date. If earlier events are unknown, the affected historical coverage is shortened until HR validates that period.

### Technical overview and local configuration

Next.js and Fluent UI build the browser pages. Spring Boot serves the pages and the API at the same local address. MySQL stores records, and Flyway applies database changes. See the [API specification](http://127.0.0.1:8080/openapi.yaml) while the app is running.

To create an organization outside the demo, use a separate database and a private `talentcompass.local.properties` file. Set `HRIS_DB_PASSWORD`, `HRIS_BOOTSTRAP_ORG`, and `HRIS_BOOTSTRAP_PASSWORD` with a bootstrap password of at least 12 characters. Leave the demo profile unset, run `.\Start-TalentCompass.ps1 -Setup`, and sign in as `admin`.

Set `HRIS_START_MYSQL=true` to start MySQL automatically. Use `HRIS_COMPOSE_PROJECT` and `HRIS_MYSQL_PORT` to select its Docker project and port, or `HRIS_DB_URL` to connect to an existing database. Environment variables override values in the file. Network use requires TLS, secure-cookie configuration, and a separate security review.

### Backup, restore and verification

Take a backup before importing or upgrading. Use `mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF` inside the Compose MySQL container. Copy the dump out with `docker cp` and store it outside Git. For restore, create a separate Compose project on a free port, copy the dump into its MySQL container, and import with `mysql`. Point a separate app launch at the restored JDBC URL and validate health, row counts, reports and audit before switching. Keep the original volume available for rollback. A code rollback across a schema migration needs the matching pre-upgrade dump and application build.

The release harness starts, checks and stops its own disposable databases on ports 33317 and 33318. It uses a separate HTTP port, exercises real MySQL, restarts Main, and checks a report and audit trail against restored data. Edge is used for browser verification. Set `HRIS_EDGE_PATH` if Edge is installed in another location.

~~~powershell
mvn package
pnpm --dir frontend run typecheck
pnpm --dir frontend run build
.\tests\Release.ps1
~~~

See [ASSESSMENT_CHECKLIST.md](ASSESSMENT_CHECKLIST.md) for observed evidence and remaining limits.

### Optional legacy CSV CLI

The original CSV menu is available through LegacyCli. From the repository root run:

~~~powershell
mvn compile
java -cp target/classes LegacyCli --as-of=2026-11-25
java -cp target/classes LegacyCli --as-of=2026-11-25 --validate-employees=startup.csv
~~~

Run LegacyCli from the directory holding the CSVs. The catalog is required. Keep those inputs outside version control. --as-of labels the employee snapshot and bounds reporting periods and newly recorded recruitment dates. It defaults to the local date. All reporting periods include both endpoints. The startup import and validation-only command are local file-operator actions.

### Local CSV inputs for the terminal menu

#### Department catalog and employee snapshot

`departments.csv` declares organizations and department IDs. A department ID can recur in another organization. The department name supports the explicit legacy import mapping.

~~~csv
OrgId,DepartmentId,DepartmentName
A,eng,Engineering
A,ops,Operations
B,eng,Engineering
~~~

`startup.csv` accepts either of these headers. The scoped format can represent multiple organizations.

~~~csv
OrgId,DepartmentId,ID,FullName,Demographic,Role,JobLevel,JoiningDate,BasicSalary,Currency,PayBasis,FTE,Status
ID,FullName,Gender,Department,Role,JoiningDate,BasicSalary,Status
~~~

- For the legacy format, pass `--legacy-org=A` and register an exact, unique catalog name for each legacy department label in that organization. Legacy basic salary is monthly. The file supplies neither currency nor FTE.
- Status values are Active, Probation, Notice Period and Exited. Joining dates must be valid ISO dates on or before the snapshot. Pay must be a nonnegative decimal. The scoped format supports blank demographic, level and pay information. When present, pay metadata uses an ISO currency, a basis of `HOURLY`, `MONTHLY` or `ANNUAL`, and an FTE greater than zero and at most one.
- Validation handles quoted commas, escaped quotes, embedded line breaks and trailing empty cells. The preview returns row numbers and field errors. Successful startup import replaces the in-memory snapshot. An invalid import preserves the prior snapshot and leaves the source file intact.

#### Employment history

Supply both files to enable historical turnover.

| File | Exact header |
| --- | --- |
| `employment-events.csv` | `OrgId,EmployeeId,DepartmentId,Date,Kind,Status` |
| `employment-coverage.csv` | `OrgId,DepartmentId,From,Through` |

Use `HIRE`, `CHANGE`, `SEPARATION` or `REHIRE`. A change can update employment status or move an employee to a different department in the same organization. Record a separation against the employee's department immediately before departure, with status `EXITED`. Each timeline starts with a hire and uses one change in state per effective date. Use a coverage row only when history is complete for that department and date range. Include the earlier events needed to establish who was employed when coverage began.

#### Recruitment

Supply `requisitions.csv` with header `OrgId,DepartmentId,RequisitionId,Job,OpenedOn`. Each requisition represents one opening. The recruitment menu creates an application against a scoped requisition and records later stages. Supporting dated events include screening, interview, offer extension, acceptance, decline and actual hire. The rejection menu records pre-offer rejections. New events and rejection reasons are written to `applications.csv` using the same CSV escaping rules. Exact retries are idempotent. Writes use a sidecar lock and an atomic file replacement on the local filesystem.

The optional legacy inputs are headerless. The original `candidates.csv` layout is candidate ID, name, job, application date, offer date and status. The original `rejections.csv` layout is candidate ID, reason and date. Use `--legacy-org=A --legacy-recruitment-department=eng` only if both legacy sources you supply can be assigned to that one department. Files with unknown or mixed ownership need a verified assignment before use. The application reads those files in their original layouts and saves new records to `applications.csv`.

### Report definitions for the terminal menu

| Report | Calculation and required inputs |
| --- | --- |
| Current headcount and workforce distribution | Counts one person per validated, authorized employee in Active, Probation or Notice Period status on the snapshot date. Exited rows remain visible in the scoped directory. |
| All-cause period turnover | Separations in the selected period divided by average daily headcount for the same period and scope, multiplied by 100. Average headcount is the sum of employed person-days divided by calendar days. An event takes effect on its recorded date. Transfers affect the department denominator and do not count as separations. A period with no separations can report zero when its denominator is positive. Missing coverage or a zero denominator yields unavailable. |
| SHRM time-to-fill | Median calendar days from requisition opening to recorded offer acceptance. The sample is applications whose acceptance date falls in the selected period. |
| Time-to-hire | Median calendar days from recorded application, used here as entry into the talent pipeline, to recorded offer acceptance. Uses the same acceptance-date period filter. |
| Legacy application-to-offer duration | Median calendar days from application to recorded offer extension, grouped into the period by the extension date. Acceptance dates are unknown in this layout, including for rows marked Accepted. |
| Rejection reasons | Counts in-period rejection events by reason with percentage and cumulative percentage. Typed offer declines are separate from pre-offer rejections. Explicitly assigned legacy rejection rows contribute their recorded reasons. |
| Descriptive pay comparison | Mean basic pay by optional demographic category within each department, job, level, FTE, pay basis and currency cohort. Output includes scoped workforce coverage and releasable group sizes. Set `--minimum-group-size` to the approved threshold. A small cell, including an undisclosed group, suppresses its cohort. At least two disclosed groups are needed. Exact coverage counts are withheld when they expose small cells. |
| Tenure flag for HR review | Flags employees in Active or Notice Period status with at least `--tenure-years` completed years since their recorded joining date. The review threshold defaults to five. |
| Cost per hire | Approved internal plus external recruiting costs divided by actual hires in a complete register for the same period and scope. Requires a known currency and positive hire count. Typed service inputs support the calculation. |
| Revenue per employee | Period-aligned approved revenue divided by matching average headcount, with currency. Typed service inputs support the calculation. |

The CLI reports cost per hire and revenue per employee as unavailable pending approved sources. It also identifies the missing inputs for cost per employee, quality of hire, engagement and satisfaction, time since last promotion, and absenteeism. Those metrics need data collection or definition work before they can return values.

SHRM publishes the turnover formula and discusses separation choices. TalentCompass uses all recorded separations and daily headcount as local methodology decisions. The supplied industry benchmark PDF could not be verified, so this pass makes no claim about matching its sampling convention. The 2025 recruiting benchmark uses calendar days and a median for time-to-fill. SHRM's glossary describes pipeline entry to offer acceptance for time-to-hire. The finance definitions use SHRM's recruiting brief and its revenue per employee guidance. [SHRM on turnover](https://www.shrm.org/topics-tools/news/talent-acquisition/data-will-show-employees-leave-stay) · [SHRM 2025 Recruiting Benchmarking](https://www.shrm.org/content/dam/en/shrm/research/2025-recruiting-benchmarking-report.pdf) · [SHRM HR Glossary](https://www.shrm.org/topics-tools/tools/hr-glossary) · [SHRM on revenue per employee](https://www.shrm.org/in/topics-tools/news/blogs/are-your-people-metrics-keeping-pace-with-your-business-growth-)

### Verify the terminal menu with synthetic data

From the repository root, compile both source and tests, then run the dependency-free regression runner and the CLI smoke test.

~~~powershell
mvn test
java -cp 'target/classes;target/test-classes' Regression
.\tests\Smoke.ps1 -Java (Get-Command java).Source -Classes target/classes
git diff --check
~~~

Both runners create temporary synthetic CSV files. The smoke test launches `LegacyCli` from the fixture directory and exercises each role.
