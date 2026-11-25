package service;

import model.Employee;
import model.Scope;
import model.Scope.Actor;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompensationReports {
    public record PayRow(String employeeId, String departmentId, Employee.Compensation pay) {}
    public record Cohort(String departmentId, String job, String level, BigDecimal fte,
                         Employee.PayBasis basis, Currency currency) {}
    public record Group(String category, int sampleSize, BigDecimal mean) {}
    public enum Availability { AVAILABLE, PARTIAL, UNAVAILABLE, SUPPRESSED }
    public record Comparison(LocalDate asOf, Availability availability, int workforce, Integer comparable,
                             Integer withDemographic, Map<Cohort, List<Group>> groups, String detail) {
        public Comparison { groups = Map.copyOf(groups); }
    }

    private final Scope scope;

    public CompensationReports(Scope scope) { this.scope = scope; }

    public List<PayRow> pay(Actor actor, List<Employee> employees) {
        scope.requireActor(actor);
        return employees.stream().filter(e -> scope.canReadFinancial(actor, e.key().orgId(), e.departmentId()))
                .filter(e -> e.compensation() != null)
                .map(e -> new PayRow(e.key().id(), e.departmentId(), e.compensation())).toList();
    }

    public Comparison compare(Actor actor, List<Employee> employees, LocalDate asOf, Integer minimumGroupSize) {
        scope.requirePeopleAccess(actor);
        List<Employee> workforce = employees.stream()
                .filter(e -> scope.canReadPeople(actor, e.key().orgId(), e.departmentId()))
                .filter(e -> e.status().employed() && !e.joiningDate().isAfter(asOf)).toList();
        if (minimumGroupSize == null || minimumGroupSize < 2) {
            return new Comparison(asOf, Availability.UNAVAILABLE, workforce.size(), null, null, Map.of(),
                    "An approved minimum group size is required");
        }
        Map<Cohort, Map<String, List<BigDecimal>>> cohorts = new LinkedHashMap<>();
        int comparable = 0;
        int withDemographic = 0;
        for (Employee employee : workforce) {
            var pay = employee.compensation();
            if (pay == null || employee.level() == null || pay.fte() == null
                    || pay.basis() == null || pay.currency() == null) continue;
            comparable++;
            if (employee.demographic() != null) withDemographic++;
            var cohort = new Cohort(employee.departmentId(), employee.job(), employee.level(),
                    pay.fte(), pay.basis(), pay.currency());
            cohorts.computeIfAbsent(cohort, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(employee.demographic(), ignored -> new ArrayList<>()).add(pay.amount());
        }

        Map<Cohort, List<Group>> published = new LinkedHashMap<>();
        boolean suppressed = false;
        for (var entry : cohorts.entrySet()) {
            var cells = entry.getValue();
            int namedGroups = cells.size() - (cells.containsKey(null) ? 1 : 0);
            if (cells.values().stream().anyMatch(cell -> cell.size() < minimumGroupSize)) {
                suppressed = true;
            } else if (namedGroups >= 2) {
                published.put(entry.getKey(), cells.entrySet().stream().filter(cell -> cell.getKey() != null)
                        .map(cell -> new Group(cell.getKey(), cell.getValue().size(), mean(cell.getValue()))).toList());
            }
        }

        boolean hideCoverage = suppressed || small(workforce.size() - comparable, minimumGroupSize)
                || small(withDemographic, minimumGroupSize) || small(comparable - withDemographic, minimumGroupSize);
        Availability availability;
        String detail;
        if (published.isEmpty()) {
            availability = suppressed ? Availability.SUPPRESSED : Availability.UNAVAILABLE;
            detail = comparable == 0 ? "Comparable job level, FTE, pay basis and currency are required"
                    : suppressed ? "Small groups prevent release" : "At least two disclosed groups per cohort are required";
        } else if (published.size() < cohorts.size() || withDemographic < workforce.size()) {
            availability = Availability.PARTIAL;
            detail = "Some employees or cohorts lack comparable inputs, demographics or releasable groups";
        } else {
            availability = Availability.AVAILABLE;
            detail = "";
        }
        return new Comparison(asOf, availability, workforce.size(), hideCoverage ? null : comparable,
                hideCoverage ? null : withDemographic, published, detail);
    }

    private static boolean small(int count, int minimum) { return count > 0 && count < minimum; }

    private static BigDecimal mean(List<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(amounts.size()), MathContext.DECIMAL128);
    }
}
