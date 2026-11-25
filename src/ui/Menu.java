package ui;

import io.ApplicationCsv;
import io.LocalInputs;
import model.EmploymentHistory.Period;
import model.Recruitment.Stage;
import model.Scope;
import model.Scope.Actor;
import model.Scope.Role;
import service.HRSystem;
import service.OtherMetrics;
import service.RecruitmentReports;
import service.WorkforceReports;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Scanner;

public final class Menu {
    private final Scanner input;
    private final Scope scope;
    private final HRSystem hris;
    private final LocalDate asOf;
    private final int tenureYears;
    private final Integer minimumGroup;
    private final String legacyOrg;
    private final String legacyDepartment;

    public Menu(Scanner input, Scope scope, HRSystem hris, LocalDate asOf, int tenureYears, Integer minimumGroup,
                String legacyOrg, String legacyDepartment) {
        this.input = input;
        this.scope = scope;
        this.hris = hris;
        this.asOf = asOf;
        this.tenureYears = tenureYears;
        this.minimumGroup = minimumGroup;
        this.legacyOrg = legacyOrg;
        this.legacyDepartment = legacyDepartment;
    }

    public void start() {
        try {
            Actor actor = actor();
            while (true) {
                options(actor);
                String selection = prompt("Choice");
                if (selection.equals("0")) return;
                try {
                    run(actor, selection);
                } catch (DateTimeParseException e) {
                    System.out.println("Use a valid ISO date (YYYY-MM-DD).");
                } catch (IOException e) {
                    System.out.println("Local CSV operation failed. Check file format and access.");
                } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
                    System.out.println(e.getMessage());
                }
            }
        } catch (NoSuchElementException ignored) {
            // End of terminal input.
        } catch (SecurityException e) {
            System.out.println("Access denied. Check the simulated actor's organization, role and department.");
        }
    }

    private Actor actor() {
        System.out.println("Choose a synthetic actor for this session.");
        String org = prompt("Organization ID");
        String role = prompt("Role (1 HR Admin, 2 Manager, 3 Finance)");
        return scope.requireActor(switch (role) {
            case "1" -> new Actor("local-hr", org, Role.HR_ADMIN, null);
            case "2" -> new Actor("local-manager", org, Role.MANAGER, prompt("Department ID"));
            case "3" -> new Actor("local-finance", org, Role.FINANCE, null);
            default -> throw new SecurityException("Unknown role");
        });
    }

    private static void options(Actor actor) {
        System.out.println("\nTalentCompass reports");
        if (actor.role() != Role.FINANCE) {
            System.out.println("""
                    [1] Employee directory
                    [2] Current headcount and workforce distribution
                    [3] Period turnover
                    [4] Descriptive pay comparison
                    [5] Tenure flag for HR review
                    [6] Recruitment applications, events and hiring durations
                    [7] Record pre-offer rejection or view rejection reasons""");
        }
        System.out.println("""
                [8] Compensation records
                [9] Other metric availability
                [0] Exit""");
    }

    private void run(Actor actor, String choice) throws IOException {
        switch (choice) {
            case "1" -> {
                var rows = hris.directory(actor);
                System.out.println("Employee directory | Rows: " + rows.size());
                rows.forEach(row -> System.out.printf("%s | %s | %s | %s | %s | %s%n", row.employeeId(),
                        row.departmentId(), row.name(), row.job(), row.joiningDate(), row.status()));
            }
            case "2" -> {
                var result = hris.headcount(actor);
                System.out.printf("Headcount as of %s | Total: %d%n", result.asOf(), result.total());
                result.byDepartment().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                        .forEach(row -> System.out.printf("%s | %d%n", row.getKey(), row.getValue()));
            }
            case "3" -> turnover(actor);
            case "4" -> comparison(actor);
            case "5" -> {
                var flags = hris.tenureFlags(actor, tenureYears);
                System.out.printf("Tenure flag for HR review | %d completed years | %d flagged%n", tenureYears, flags.size());
                flags.forEach(row -> System.out.printf("%s | %s | %s | %d years%n",
                        row.employeeId(), row.departmentId(), row.name(), row.completedYears()));
            }
            case "6" -> recruitment(actor);
            case "7" -> rejections(actor);
            case "8" -> {
                var rows = hris.pay(actor);
                System.out.println("Compensation records | Rows: " + rows.size());
                rows.forEach(row -> {
                    var pay = row.pay();
                    System.out.printf("%s | %s | %s | %s | %s | FTE %s%n", row.employeeId(), row.departmentId(),
                            pay.amount().toPlainString(), known(pay.currency()), known(pay.basis()), known(pay.fte()));
                });
            }
            case "9" -> new OtherMetrics(scope).availability(actor, period()).forEach(metric ->
                    System.out.printf("%s | Unavailable | %s%n", metric.name(), metric.detail()));
            default -> System.out.println("Choose a menu option.");
        }
    }

    private void turnover(Actor actor) throws IOException {
        scope.requirePeopleAccess(actor);
        var period = period();
        var history = LocalInputs.history(Path.of("employment-events.csv"), Path.of("employment-coverage.csv"), scope);
        var result = new WorkforceReports(scope).turnover(actor, history, period);
        System.out.printf("All-cause period turnover | %s through %s%n", period.from(), period.through());
        if (result.separations() != null) {
            System.out.printf("Separations: %d | Average daily headcount: %.2f%n",
                    result.separations(), result.averageHeadcount().orElseThrow());
        }
        if (result.percent().isPresent()) System.out.printf("Turnover: %.2f%%%n", result.percent().getAsDouble());
        else System.out.println("Unavailable | " + result.detail());
    }

    private void comparison(Actor actor) {
        var result = hris.payComparison(actor, minimumGroup);
        System.out.printf("Descriptive pay comparison | %s | %s | Workforce: %d%n",
                result.asOf(), result.availability(), result.workforce());
        if (!result.detail().isEmpty()) System.out.println(result.detail());
        if (result.comparable() != null) {
            System.out.printf("Comparable pay: %d | Comparable pay with demographics: %d%n",
                    result.comparable(), result.withDemographic());
        }
        result.groups().forEach((cohort, groups) -> {
            System.out.printf("%s | %s | %s | FTE %s | %s | %s%n", cohort.departmentId(), cohort.job(), cohort.level(),
                    cohort.fte().toPlainString(), cohort.basis(), cohort.currency());
            groups.forEach(group -> System.out.printf("  %s | n=%d | Mean %s%n", group.category(),
                    group.sampleSize(), group.mean().stripTrailingZeros().toPlainString()));
        });
    }

    private void recruitment(Actor actor) throws IOException {
        scope.requirePeopleAccess(actor);
        var csv = applications();
        switch (prompt("Recruitment (1 list applications, 2 create application, 3 record stage, 4 hiring durations)")) {
            case "1" -> {
                var rows = csv.applications(actor, asOf);
                System.out.println("Applications | Rows: " + rows.size());
                rows.forEach(app -> System.out.printf("%s | %s | %s | %s%n", app.key().id(),
                        app.requisition().departmentId(), app.requisition().key().id(), app.events().getLast()));
            }
            case "2" -> {
                var requisitions = csv.requisitions(actor, asOf);
                requisitions.forEach(req -> System.out.printf("%s | %s | %s%n", req.key().id(), req.departmentId(), req.job()));
                if (requisitions.isEmpty()) {
                    System.out.println("No requisitions in scope. Provide requisitions.csv to log new applications.");
                    return;
                }
                String requisition = prompt("Requisition ID");
                String id = prompt("Application ID");
                String candidate = prompt("Candidate ID");
                String name = prompt("Candidate name");
                saved(csv.create(actor, id, candidate, name, requisition, date("Application date"), asOf));
            }
            case "3" -> {
                String id = prompt("Application ID");
                String raw = prompt("Stage (SCREENING, INTERVIEW, OFFERED, DECLINED, ACCEPTED, HIRED)");
                Stage stage;
                try {
                    stage = Stage.valueOf(raw.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Unknown stage");
                }
                if (stage == Stage.REJECTED) throw new IllegalArgumentException("Use the pre-offer rejection menu");
                saved(csv.advance(actor, id, stage, date("Event date"), null, asOf));
            }
            case "4" -> {
                var period = period();
                var legacy = scopedLegacy(actor) ? LocalInputs.legacyApplications(Path.of("candidates.csv"),
                        legacyOrg, legacyDepartment, asOf) : List.<model.Recruitment.LegacyApplication>of();
                var result = new RecruitmentReports(scope).durations(actor, csv.applications(actor, asOf), legacy, period);
                duration("SHRM time-to-fill", result.timeToFill());
                duration("Time-to-hire", result.timeToHire());
                duration("Legacy application-to-offer duration", result.applicationToOffer());
                if (result.legacyCoverageGap()) {
                    System.out.println("Acceptance coverage is incomplete for scoped legacy applications.");
                }
                if (!scopedLegacy(actor)) System.out.println("Legacy durations require an explicit file assignment in scope.");
            }
            default -> System.out.println("Choose a recruitment option.");
        }
    }

    private void rejections(Actor actor) throws IOException {
        scope.requirePeopleAccess(actor);
        var csv = applications();
        switch (prompt("Rejections (1 record pre-offer rejection, 2 report by reason)")) {
            case "1" -> {
                String id = prompt("Application ID");
                LocalDate date = date("Rejection date");
                saved(csv.advance(actor, id, Stage.REJECTED, date, prompt("Rejection reason"), asOf));
            }
            case "2" -> {
                var period = period();
                var legacy = scopedLegacy(actor) ? LocalInputs.legacyRejections(Path.of("rejections.csv"),
                        legacyOrg, legacyDepartment, asOf) : List.<model.Recruitment.LegacyRejection>of();
                var result = new RecruitmentReports(scope).rejections(actor, csv.applications(actor, asOf), legacy, period);
                System.out.printf("Rejections | %s through %s | Total: %d%n", period.from(), period.through(), result.total());
                result.reasons().forEach(row -> System.out.printf("%s | %d | %.1f%% | Cumulative %.1f%%%n",
                        row.name(), row.count(), row.sharePercent(), row.cumulativePercent()));
                if (!scopedLegacy(actor)) System.out.println("Legacy rejections require an explicit file assignment in scope.");
            }
            default -> System.out.println("Choose a rejection option.");
        }
    }

    private ApplicationCsv applications() throws IOException {
        return new ApplicationCsv(Path.of("applications.csv"), scope,
                LocalInputs.requisitions(Path.of("requisitions.csv"), scope));
    }

    private boolean scopedLegacy(Actor actor) {
        return scope.canReadPeople(actor, legacyOrg, legacyDepartment);
    }

    private Period period() {
        var period = new Period(date("From"), date("Through (inclusive)"));
        if (period.through().isAfter(asOf)) throw new IllegalArgumentException("Period ends after the reporting snapshot");
        return period;
    }

    private LocalDate date(String label) { return LocalDate.parse(prompt(label + " (YYYY-MM-DD)")); }

    private String prompt(String label) {
        System.out.print(label + "> ");
        return input.nextLine().strip();
    }

    private static String known(Object value) { return value == null ? "unknown" : value.toString(); }

    private static void saved(boolean changed) { System.out.println(changed ? "Saved to local CSV." : "Already recorded."); }

    private static void duration(String name, RecruitmentReports.Duration value) {
        if (value.medianDays().isPresent()) {
            System.out.printf("%s | Median %.1f days | n=%d%n", name, value.medianDays().getAsDouble(), value.sampleSize());
        } else {
            System.out.printf("%s | Unavailable | %s%n", name, value.detail());
        }
    }
}
