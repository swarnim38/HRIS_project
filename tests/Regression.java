import io.ApplicationCsv;
import io.Csv;
import io.EmployeeImport;
import io.LocalInputs;
import model.Candidate;
import model.Employee;
import model.EmploymentHistory;
import model.Scope;
import service.CompensationReports;
import service.HRSystem;
import service.OtherMetrics;
import service.RecruitmentReports;
import service.WorkforceReports;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import static model.EmploymentHistory.*;
import static model.Recruitment.*;
import static model.Scope.*;

public class Regression {
    private static final Scope SCOPE = new Scope(List.of(new Organization("A"), new Organization("B")),
            List.of(new Department("A", "eng", "Research, Inc."), new Department("A", "ops", "Ops"),
                    new Department("A", "empty", "Empty"), new Department("B", "eng", "Research, Inc.")));
    private static final Actor HR = new Actor("a1", "A", Role.HR_ADMIN, null);
    private static final Actor MANAGER = new Actor("a2", "A", Role.MANAGER, "eng");
    private static final Actor FINANCE = new Actor("a3", "A", Role.FINANCE, null);
    private static final Actor OTHER = new Actor("b1", "B", Role.HR_ADMIN, null);
    private static final LocalDate SNAPSHOT = LocalDate.parse("2025-01-31");
    private static final Period JANUARY = new Period(day(1), SNAPSHOT);
    private static final Currency USD = Currency.getInstance("USD");
    private static int checks;

    public static void main(String[] args) throws Exception {
        var fixture = Files.createTempDirectory("talentcompass-regression-");
        employeeImport(fixture);
        workforce();
        compensation();
        recruitment(fixture);
        System.out.printf("Passed %d regression checks.%n", checks);
    }

    private static void employeeImport(Path dir) throws Exception {
        Path source = dir.resolve("startup.csv");
        var hris = new HRSystem(SCOPE, SNAPSHOT);
        String header = Csv.encode(EmployeeImport.LEGACY) + "\r\n";
        String row = "E1,\"Sample \"\"Q\"\", Name\",,\"Research, Inc.\",Dev,2015-01-01,5000,Active\r\n";
        Files.writeString(source, "\uFEFF" + header + row);
        expect(hris.importEmployees(source, "A", true).valid(), "valid preview");
        fails(IllegalStateException.class, () -> hris.directory(HR));
        expect(!hris.importEmployees(source, null, false).valid(), "explicit legacy assignment required");
        expect(hris.importEmployees(source, "A", false).valid(), "quoted legacy import");
        expect(hris.directory(HR).getFirst().name().equals("Sample \"Q\", Name"), "escaped quote and comma");
        expect(hris.importEmployees(source, "A", false).valid() && hris.directory(HR).size() == 1, "safe success retry");
        String invalid = header + row + row + "E2,Secret,,Ops,Dev,2025-02-30,-1,Active\r\n"
                + "E3,Secret,,Ops,Dev,2020-01-01,25,Surprise\r\n";
        Files.writeString(source, invalid);
        for (int attempt = 0; attempt < 2; attempt++) {
            var preview = hris.importEmployees(source, "A", false);
            expect(preview.problems().size() == 3 && preview.problems().getFirst().line() == 3, "row errors");
            expect(preview.problems().stream().noneMatch(p -> p.detail().contains("Secret")), "sanitized errors");
            expect(Files.readString(source).equals(invalid) && hris.directory(HR).size() == 1, "failed batch is atomic");
        }
        String extendedHeader = Csv.encode(EmployeeImport.HEADER) + "\n";
        String extended = extendedHeader + "A,eng,E1,Trusted,,Dev,,2020-01-01,,,,,Active\n"
                + "B,eng,E1,CrossTenant,,Dev,,2020-01-01,,,,,Exited\n";
        Files.writeString(source, extended);
        expect(hris.importEmployees(source, null, false).valid(), "employee IDs repeat across tenants");
        expect(hris.directory(HR).getFirst().name().equals("Trusted") && hris.directory(OTHER).size() == 1,
                "employee import keeps tenant ownership");
        Files.writeString(source, extendedHeader + "B,ops,ID,Secret,,Dev,,2020-01-01,,,,,Active\n");
        expect(!hris.importEmployees(source, null, false).valid(), "department must belong to row tenant");
        Files.writeString(source, extendedHeader + "A,eng,ID,Secret,,Dev,,2020-01-01,NaN,,,,Active\n");
        expect(!hris.importEmployees(source, null, false).valid(), "invalid pay rejected");
        var parsed = Csv.parse(Csv.encode(List.of("x", "line 1\r\nline \"2\"", "")));
        expect(parsed.getFirst().cells().equals(List.of("x", "line 1\r\nline \"2\"", "")), "multiline and trailing cells");
        expect(Csv.parse("a,b,\"bad\"extra\nx,y,z").getFirst().error() != null, "malformed quotes rejected");
    }

    private static void workforce() throws Exception {
        var reports = new WorkforceReports(SCOPE);
        var roster = List.of(employee("A", "eng", "1", "X", Employee.Status.ACTIVE, USD),
                employee("A", "ops", "2", null, Employee.Status.PROBATION, USD),
                employee("A", "ops", "3", null, Employee.Status.NOTICE_PERIOD, USD),
                employee("A", "eng", "4", null, Employee.Status.EXITED, USD),
                employee("B", "eng", "1", "Y", Employee.Status.ACTIVE, USD));
        expect(reports.directory(HR, roster).size() == 4 && reports.directory(MANAGER, roster).size() == 2,
                "directory organization and department scope");
        expect(reports.current(HR, roster, SNAPSHOT).total() == 3, "active, probation and notice are employed");
        expect(reports.current(MANAGER, roster, SNAPSHOT).total() == 1, "scoped headcount");
        fails(SecurityException.class, () -> reports.directory(FINANCE, roster));
        fails(SecurityException.class, () -> reports.directory(null, roster));
        fails(SecurityException.class, () -> reports.directory(new Actor("x", "A", Role.MANAGER, null), roster));
        fails(SecurityException.class, () -> reports.directory(new Actor("x", "A", Role.MANAGER, "bogus"), roster));
        fails(SecurityException.class, () -> reports.directory(new Actor("x", "unknown", Role.HR_ADMIN, null), roster));
        expect(reports.tenureFlags(HR, roster, SNAPSHOT, 5).size() == 2
                && reports.tenureFlags(HR, roster, SNAPSHOT, 20).isEmpty(), "configurable HR review outcome");

        var events = List.of(event("A", "eng", "1", 1, Kind.HIRE),
                event("A", "eng", "1", 6, Kind.SEPARATION), event("A", "eng", "2", 1, Kind.HIRE),
                event("A", "ops", "2", 8, Kind.CHANGE), event("A", "ops", "3", 1, Kind.HIRE),
                event("B", "eng", "1", 1, Kind.HIRE), event("B", "eng", "1", 2, Kind.SEPARATION));
        var coverage = List.of(coverage("A", "eng"), coverage("A", "ops"), coverage("A", "empty"), coverage("B", "eng"));
        var history = new EmploymentHistory(SCOPE, events, coverage);
        var period = new Period(day(1), day(10));
        var manager = reports.turnover(MANAGER, history, period);
        expect(manager.separations() == 1 && near(manager.averageHeadcount().orElseThrow(), 1.2)
                && near(manager.percent().orElseThrow(), 100.0 / 1.2), "period turnover uses scoped person-days");
        var admin = reports.turnover(HR, history, period);
        expect(admin.separations() == 1 && near(admin.averageHeadcount().orElseThrow(), 2.5),
                "same-tenant transfer preserves organization headcount");
        expect(reports.turnover(HR, history, new Period(day(7), day(10))).percent().orElseThrow() == 0,
                "zero separations with positive headcount");
        var empty = reports.turnover(new Actor("x", "A", Role.MANAGER, "empty"), history, period);
        expect(empty.percent().isEmpty() && empty.averageHeadcount().orElseThrow() == 0, "zero denominator unavailable");
        expect(reports.turnover(HR, null, period).separations() == null, "missing dated history unavailable");
        expect(reports.turnover(HR, new EmploymentHistory(SCOPE, events, coverage.subList(0, 1)), period)
                .percent().isEmpty(), "organization coverage requires every department");
        fails(IllegalArgumentException.class, () -> new EmploymentHistory(SCOPE,
                List.of(event("B", "ops", "1", 1, Kind.HIRE)), coverage));
    }

    private static void compensation() throws Exception {
        var report = new CompensationReports(SCOPE);
        var roster = List.of(employee("A", "eng", "1", "Woman", Employee.Status.ACTIVE, USD),
                employee("A", "eng", "2", "Woman", Employee.Status.ACTIVE, USD),
                employee("A", "eng", "3", "Nonbinary", Employee.Status.ACTIVE, USD),
                employee("A", "eng", "4", "Nonbinary", Employee.Status.ACTIVE, USD),
                employee("A", "ops", "5", "Woman", Employee.Status.ACTIVE, Currency.getInstance("EUR")),
                employee("B", "eng", "1", "Unscoped", Employee.Status.ACTIVE, USD));
        expect(report.pay(FINANCE, roster).size() == 5 && report.pay(MANAGER, roster).size() == 4,
                "financial projection includes permitted employee IDs and compensation only");
        fails(SecurityException.class, () -> report.compare(FINANCE, roster, SNAPSHOT, 2));
        var ready = report.compare(MANAGER, roster, SNAPSHOT, 2);
        expect(ready.availability() == CompensationReports.Availability.AVAILABLE && ready.workforce() == 4
                && ready.comparable() == 4 && ready.withDemographic() == 4, "scoped coverage");
        expect(ready.groups().values().iterator().next().stream().allMatch(g -> g.sampleSize() == 2
                && g.mean().compareTo(BigDecimal.valueOf(100)) == 0), "comparable descriptive groups");
        var partial = report.compare(HR, roster, SNAPSHOT, 2);
        expect(partial.availability() == CompensationReports.Availability.PARTIAL
                && partial.groups().size() == 1 && partial.comparable() == null, "small cohort and coverage are suppressed");
        expect(report.compare(MANAGER, roster, SNAPSHOT, 3).availability() == CompensationReports.Availability.SUPPRESSED,
                "small groups cannot be displayed");
        expect(report.compare(HR, roster, SNAPSHOT, null).availability() == CompensationReports.Availability.UNAVAILABLE,
                "missing suppression rules yield unavailable");
        var missing = List.of(employee("A", "eng", "1", "Woman", Employee.Status.ACTIVE, null));
        expect(report.compare(HR, missing, SNAPSHOT, 2).availability() == CompensationReports.Availability.UNAVAILABLE,
                "unknown currency yields unavailable comparison");
        var splitCurrency = List.of(roster.get(0), roster.get(1),
                employee("A", "eng", "3", "Nonbinary", Employee.Status.ACTIVE, Currency.getInstance("EUR")),
                employee("A", "eng", "4", "Nonbinary", Employee.Status.ACTIVE, Currency.getInstance("EUR")));
        expect(report.compare(HR, splitCurrency, SNAPSHOT, 2).groups().isEmpty(), "currency cohorts cannot be mixed");
    }

    private static void recruitment(Path dir) throws Exception {
        Path path = dir.resolve("applications.csv");
        var req = req("A", "eng", "R");
        var otherReq = req("B", "eng", "R");
        var store = new ApplicationCsv(path, SCOPE,
                List.of(req, req("A", "ops", "O"), req("A", "eng", "P"), otherReq));
        fails(IllegalArgumentException.class, () -> new Application(new Key("A", "bad"),
                new Candidate(new Key("B", "C"), "Synthetic"), req, List.of(new StageEvent(Stage.APPLIED, day(2))), null));
        fails(SecurityException.class, () -> store.create(MANAGER, "X", "C", "Synthetic", "O", day(2), SNAPSHOT));
        fails(SecurityException.class, () -> store.create(FINANCE, "X", "C", "Synthetic", "R", day(2), SNAPSHOT));
        fails(IllegalArgumentException.class, () -> store.create(HR, "X", "C", "Synthetic", "R", day(1).minusDays(1), SNAPSHOT));
        String name = "Synthetic, \"Candidate\"";
        expect(store.create(MANAGER, "X", "C", name, "R", day(2), SNAPSHOT), "manager can create in assigned department");
        expect(!store.create(MANAGER, "X", "C", name, "R", day(2), SNAPSHOT), "create retry does not duplicate");
        expect(store.create(OTHER, "X", "C", "Other", "R", day(2), SNAPSHOT), "application and candidate IDs are tenant scoped");
        expect(store.create(HR, "Y", "C2", "Operations", "O", day(4), SNAPSHOT), "admin can create across departments");
        expect(store.create(HR, "Z", "C3", "Pending", "P", day(4), SNAPSHOT), "pending application recorded");
        fails(IllegalArgumentException.class, () -> store.advance(MANAGER, "Y", Stage.REJECTED, day(5), "Reason", SNAPSHOT));
        var report = new RecruitmentReports(SCOPE);
        var before = Files.readString(path);
        fails(IllegalArgumentException.class, () -> store.advance(MANAGER, "X", Stage.ACCEPTED, day(3), null, SNAPSHOT));
        fails(IllegalArgumentException.class, () -> store.advance(MANAGER, "X", Stage.OFFERED, day(1), null, SNAPSHOT));
        expect(Files.readString(path).equals(before), "invalid transitions preserve original CSV");
        expect(store.advance(MANAGER, "X", Stage.OFFERED, day(5), null, SNAPSHOT), "offer extended");
        expect(store.applications(MANAGER, SNAPSHOT).getFirst().candidate().name().equals(name), "escaped candidate persisted");
        expect(report.durations(HR, store.applications(HR, SNAPSHOT), List.of(), JANUARY)
                .timeToFill().medianDays().isEmpty(), "open offer and pending application excluded");
        fails(IllegalArgumentException.class, () -> store.advance(HR, "X", Stage.REJECTED, day(6), "Reason", SNAPSHOT));
        expect(store.advance(HR, "Y", Stage.REJECTED, day(7), "Timing, \"other\"", SNAPSHOT), "pre-offer rejection persisted");
        expect(store.advance(HR, "X", Stage.ACCEPTED, day(8), null, SNAPSHOT), "acceptance separately recorded");
        expect(store.advance(HR, "X", Stage.HIRED, day(9), null, SNAPSHOT), "hire separately recorded");
        expect(!store.advance(HR, "X", Stage.ACCEPTED, day(8), null, SNAPSHOT), "recorded event retries safely");
        store.advance(OTHER, "X", Stage.OFFERED, day(5), null, SNAPSHOT);
        store.advance(OTHER, "X", Stage.DECLINED, day(6), null, SNAPSHOT);

        Path old = dir.resolve("candidates.csv");
        Files.writeString(old, "OLD,Legacy Candidate,Dev,2025-01-01,2025-01-04,Accepted\n");
        var legacy = LocalInputs.legacyApplications(old, "A", "eng", SNAPSHOT);
        var mixed = new java.util.ArrayList<>(store.applications(HR, SNAPSHOT));
        mixed.addAll(store.applications(OTHER, SNAPSHOT));
        var duration = report.durations(MANAGER, mixed, legacy, JANUARY);
        expect(duration.timeToFill().medianDays().orElseThrow() == 7 && duration.timeToFill().sampleSize() == 1,
                "time-to-fill counts only dated acceptance within scope");
        expect(duration.timeToHire().medianDays().orElseThrow() == 6, "separate time-to-hire endpoints");
        expect(duration.applicationToOffer().medianDays().orElseThrow() == 3 && duration.legacyCoverageGap(),
                "legacy accepted status supplies no acceptance date");
        expect(report.durations(MANAGER, store.applications(MANAGER, day(6)), legacy, new Period(day(1), day(6)))
                .timeToFill().medianDays().isEmpty(), "report snapshot excludes later acceptances");
        expect(report.rejections(MANAGER, mixed, List.of(), JANUARY).total() == 0, "manager excludes other departments");
        expect(report.rejections(HR, mixed, List.of(), JANUARY).reasons().getFirst().name().equals("Timing, \"other\""),
                "rejection reasons survive CSV round-trip and exclude declines");
        Path oldRejections = dir.resolve("rejections.csv");
        String oldContents = "OLD,\"Timing, \"\"other\"\"\",2025-01-05\n";
        Files.writeString(oldRejections, oldContents);
        expect(report.rejections(HR, mixed, LocalInputs.legacyRejections(oldRejections, "A", "eng", SNAPSHOT),
                JANUARY).total() == 2 && Files.readString(oldRejections).equals(oldContents), "legacy rejection compatibility");
        fails(SecurityException.class, () -> store.applications(FINANCE, SNAPSHOT));

        var metrics = new OtherMetrics(SCOPE);
        var cost = new OtherMetrics.RecruitingCosts("A", null, JANUARY, USD, BigDecimal.TEN, BigDecimal.TEN);
        expect(metrics.costPerHire(FINANCE, JANUARY, cost, mixed, true).value().compareTo(BigDecimal.valueOf(20)) == 0,
                "approved cost per hire uses scoped actual hires");
        expect(metrics.costPerHire(FINANCE, JANUARY, cost, mixed, false).value() == null, "missing hire coverage");
        fails(SecurityException.class, () -> metrics.costPerHire(MANAGER, JANUARY, cost, mixed, true));
        var revenue = new OtherMetrics.RevenueInputs("A", null, JANUARY, USD, BigDecimal.valueOf(300), BigDecimal.valueOf(6));
        expect(metrics.revenuePerEmployee(FINANCE, JANUARY, revenue).value().compareTo(BigDecimal.valueOf(50)) == 0,
                "period-aligned revenue divided by average headcount");
        fails(SecurityException.class, () -> metrics.revenuePerEmployee(OTHER, JANUARY, revenue));
        expect(metrics.availability(FINANCE, JANUARY).size() == 3 && metrics.availability(HR, JANUARY).size() == 7,
                "unavailable KPI details respect actor scope");
    }

    private static Requisition req(String org, String dept, String id) {
        return new Requisition(new Key(org, id), dept, "Synthetic role", day(1));
    }

    private static Employee employee(String org, String dept, String id, String category, Employee.Status status,
                                     Currency currency) {
        return new Employee(new Key(org, id), dept, "Employee " + org + id, category, "Dev", "L1",
                LocalDate.parse("2015-01-15"), status,
                new Employee.Compensation(BigDecimal.valueOf(100), currency, Employee.PayBasis.MONTHLY, BigDecimal.ONE));
    }

    private static Coverage coverage(String org, String dept) { return new Coverage(new Key(org, dept), JANUARY); }

    private static Event event(String org, String dept, String id, int day, Kind kind) {
        return new Event(new Key(org, id), dept, day(day), kind,
                kind == Kind.SEPARATION ? Employee.Status.EXITED : Employee.Status.ACTIVE);
    }

    private static LocalDate day(int day) { return LocalDate.of(2025, 1, day); }
    private static boolean near(double a, double b) { return Math.abs(a - b) < 0.00001; }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static void fails(Class<? extends Exception> type, Checked action) throws Exception {
        try {
            action.run();
        } catch (Exception e) {
            if (!type.isInstance(e)) throw e;
            checks++;
            return;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    @FunctionalInterface
    private interface Checked { void run() throws Exception; }
}
