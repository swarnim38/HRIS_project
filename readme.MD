# TalentCompass HRIS v1.0

A lightweight, terminal-based Human Resource Information System (HRIS) built in Java. TalentCompass Core is designed to give HR teams fast, no-frills access to workforce analytics and candidate pipeline management — all from the command line, with no database or internet connection required.

---

## Project Scope

TalentCompass is a single-user, local-run HRIS intended for small to mid-sized teams that need structured HR reporting without the overhead of enterprise software. The system covers two core domains:

**Analytics** — Read-only reporting on the existing workforce loaded from a CSV file at startup. This includes headcount distribution, attrition metrics, pay parity analysis, and gratuity compliance flagging.

**Data Entry** — Append-only logging for recruitment activity (new candidates and their hiring cycle time) and candidate rejection tracking with Pareto-style pipeline analysis.

The system is not a full HRMS. It does not support real-time database writes to the employee master, user authentication, or multi-user access.

---

## Features

**Analytics Modules**

- **Master Employee Directory** — Tabular view of all employees with ID, name, department, and tenure.
- **Workforce Distribution** — Active headcount grouped by department.
- **Attrition & Risk Report** — Turnover rate calculation with a Six Sigma-style control limit flag (⚠️ triggered above 15%). Breaks down active, notice period, and exited headcounts.
- **Gender Pay Parity Report** — Average salary comparison across male and female employees, with a parity ratio and bias alert if the gap exceeds ±2%.
- **5-Year Gratuity Compliance Report** — Lists all active and notice-period employees who have crossed the 5-year tenure threshold and are eligible for statutory gratuity.

**Data Entry Modules**

- **Log New Candidate** — Records candidate ID, name, applied role, application date, offer date, and offer status to `candidates.csv`. Automatically calculates hiring cycle time in days and flags if it exceeds 45 days.
- **Log Rejection & Pareto Chart** — Records a candidate rejection with a standardised reason code to `rejections.csv`, then immediately renders a ranked Pareto analysis of all rejection reasons to help identify the top pipeline bottleneck.

---

## Limitations

- **No live editing of employee records** — The employee master (`startup.csv`) is read once at boot. You cannot add, update, or delete employee records from within the application.
- **No authentication or access control** — Any user who can run the application has full access to all modules.
- **Flat-file storage only** — All data is stored in CSV files. There is no relational database, so there are no joins, foreign key constraints, or transactional safety.
- **Single-user only** — Concurrent access from multiple users will cause data corruption in the CSV log files.
- **No data validation on CSV input** — Malformed rows in `startup.csv` are skipped with a warning, but there is no pre-import validation tool.
- **No export functionality** — Report output is printed to the terminal only. There is no option to export reports to PDF, Excel, or any other format.
- **Gender binary assumption** — The pay parity report currently only computes averages for `M` and `F` gender values. Non-binary or undisclosed entries are excluded from the calculation with a warning.
- **Date parsing is strict** — All dates must be entered in `YYYY-MM-DD` format exactly. Any other format will throw an error and abort the operation.

---

## Roadmap

The following enhancements are planned or under consideration for future versions:

- **Employee record management** — Add the ability to onboard new employees, update existing records, and flag exits directly from the dashboard, with changes written back to the master CSV.
- **Role-based access control** — Introduce a login layer with at least two roles (e.g., HR Admin and Viewer) to restrict data entry operations.
- **Database backend** — Migrate from flat CSV files to an embedded database (e.g., SQLite via JDBC) for safer concurrent reads, proper indexing, and transactional writes.
- **Report export** — Allow any generated report to be saved as a `.txt`, `.csv`, or `.pdf` file with a timestamped filename.
- **Configurable thresholds** — Move hardcoded values like the 15% attrition control limit and 45-day cycle time threshold into an external `config.properties` file so they can be adjusted without recompiling.
- **Full gender inclusivity in pay parity** — Extend the pay parity report to handle non-binary and undisclosed gender categories.
- **Candidate pipeline dashboard** — A dedicated view showing all candidates logged, their current status, and average cycle times by role.
- **Automated gratuity notifications** — Generate a printable or email-ready notification list for Finance when employees cross the 5-year milestone.
- **Unit test coverage** — Introduce JUnit tests for the `Employee`, `Candidate`, and `HRSystem` classes to guard against regressions.

---

## Installation & Setup

### Prerequisites

| Requirement | Version |
|---|---|
| Java Development Kit (JDK) | 11 or higher |
| A terminal / command prompt | Any |

No third-party libraries or build tools (Maven, Gradle) are required. The project compiles with standard `javac`.

### Project Structure

```
HRIS_project/
├── Main.java                  # Entry point
├── startup.csv                # Employee master data (required at launch)
├── candidates.csv             # Auto-created on first candidate log
├── rejections.csv             # Auto-created on first rejection log
├── model/
│   ├── Employee.java
│   └── Candidate.java
├── service/
│   └── HRSystem.java
└── ui/
    └── Menu.java
```

### Data Files

**`startup.csv`** (required) — The employee master file. Must be placed in the same directory as `Main.java` before running. The application will fail to load data if this file is missing. Expected column order, with a header row:

```
ID, FullName, Gender, Department, Role, JoiningDate, BasicSalary, Status
```

- `Gender`: `M` or `F`
- `JoiningDate`: `YYYY-MM-DD` format
- `BasicSalary`: numeric (monthly, no currency symbols)
- `Status`: `Active`, `Probation`, `Notice Period`, or `Exited`

**`candidates.csv`** — Auto-generated in the working directory when the first candidate is logged. Do not manually edit this file while the application is running.

**`rejections.csv`** — Auto-generated in the working directory when the first rejection is logged. This file feeds the Pareto chart; clearing it will reset the analysis history.

### Compilation

From the project root directory:

```bash
javac -d . model/Employee.java model/Candidate.java service/HRSystem.java ui/Menu.java Main.java
```

### Running the Application

```bash
java Main
```

The system will boot, attempt to load `startup.csv`, and display the main dashboard menu.