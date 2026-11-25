package service;

import model.Employee;
import model.EmploymentHistory;
import model.EmploymentHistory.Event;
import model.EmploymentHistory.Kind;
import model.EmploymentHistory.Period;
import model.Scope;
import model.Scope.Actor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;

public final class WorkforceReports {
    public record DirectoryRow(String employeeId, String departmentId, String name, String job,
                               LocalDate joiningDate, Employee.Status status) {}

    public record Headcount(LocalDate asOf, Map<String, Integer> byDepartment) {
        public Headcount { byDepartment = Map.copyOf(byDepartment); }
        public int total() { return byDepartment.values().stream().mapToInt(Integer::intValue).sum(); }
    }

    public record TenureFlag(String employeeId, String departmentId, String name, long completedYears) {}

    public record Turnover(Period period, Long separations, OptionalDouble averageHeadcount,
                           OptionalDouble percent, String detail) {}

    private final Scope scope;

    public WorkforceReports(Scope scope) {
        this.scope = scope;
    }

    public List<DirectoryRow> directory(Actor actor, List<Employee> employees) {
        scope.requirePeopleAccess(actor);
        return employees.stream().filter(e -> scope.canReadPeople(actor, e.key().orgId(), e.departmentId()))
                .map(e -> new DirectoryRow(e.key().id(), e.departmentId(), e.fullName(), e.job(),
                        e.joiningDate(), e.status())).toList();
    }

    public Headcount current(Actor actor, List<Employee> employees, LocalDate snapshotDate) {
        Map<String, Integer> counts = new TreeMap<>();
        for (DirectoryRow row : directory(actor, employees)) {
            if (row.status().employed() && !row.joiningDate().isAfter(snapshotDate)) {
                counts.merge(row.departmentId(), 1, Integer::sum);
            }
        }
        return new Headcount(snapshotDate, counts);
    }

    public List<TenureFlag> tenureFlags(Actor actor, List<Employee> employees, LocalDate snapshotDate, int years) {
        if (years < 1) throw new IllegalArgumentException("Tenure threshold must be positive");
        return directory(actor, employees).stream()
                .filter(row -> row.status() == Employee.Status.ACTIVE || row.status() == Employee.Status.NOTICE_PERIOD)
                .filter(row -> !row.joiningDate().isAfter(snapshotDate))
                .map(row -> new TenureFlag(row.employeeId(), row.departmentId(), row.name(),
                        ChronoUnit.YEARS.between(row.joiningDate(), snapshotDate)))
                .filter(row -> row.completedYears() >= years).toList();
    }

    public Turnover turnover(Actor actor, EmploymentHistory history, Period period) {
        var departments = scope.peopleDepartments(actor);
        if (history == null || departments.isEmpty() || departments.stream().anyMatch(department -> {
            Period covered = history.coverage().get(new Scope.Key(actor.orgId(), department));
            return covered == null || !covered.contains(period.from()) || !covered.contains(period.through());
        })) {
            return new Turnover(period, null, OptionalDouble.empty(), OptionalDouble.empty(),
                    "Complete dated employment history is required for every department in scope");
        }

        long personDays = 0;
        long separations = 0;
        LocalDate endExclusive = period.through().plusDays(1);
        for (var entry : history.timelines().entrySet()) {
            if (!entry.getKey().orgId().equals(actor.orgId())) continue;
            List<Event> events = entry.getValue();
            for (int i = 0; i < events.size(); i++) {
                Event event = events.get(i);
                if (!departments.contains(event.departmentId()) || event.date().isAfter(period.through())) continue;
                if (event.kind() == Kind.SEPARATION && period.contains(event.date())) separations++;
                if (event.status().employed()) {
                    LocalDate next = i + 1 < events.size() ? events.get(i + 1).date() : endExclusive;
                    LocalDate start = event.date().isAfter(period.from()) ? event.date() : period.from();
                    LocalDate end = next.isBefore(endExclusive) ? next : endExclusive;
                    if (end.isAfter(start)) personDays += ChronoUnit.DAYS.between(start, end);
                }
            }
        }
        double average = (double) personDays / ChronoUnit.DAYS.between(period.from(), endExclusive);
        return average == 0
                ? new Turnover(period, separations, OptionalDouble.of(0), OptionalDouble.empty(), "Average headcount is zero")
                : new Turnover(period, separations, OptionalDouble.of(average),
                        OptionalDouble.of(separations * 100.0 / average), "");
    }
}
