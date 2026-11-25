package io;

import model.Employee;
import model.Scope;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;

public final class EmployeeImport {
    public static final List<String> LEGACY = List.of("ID", "FullName", "Gender", "Department",
            "Role", "JoiningDate", "BasicSalary", "Status");
    public static final List<String> HEADER = List.of("OrgId", "DepartmentId", "ID", "FullName", "Demographic",
            "Role", "JobLevel", "JoiningDate", "BasicSalary", "Currency", "PayBasis", "FTE", "Status");

    public record Problem(int line, String detail) {}
    public record Preview(int validRows, List<Problem> problems) {
        public Preview { problems = List.copyOf(problems); }
        public boolean valid() { return problems.isEmpty(); }
    }
    public record Batch(Preview preview, List<Employee> employees) {
        public Batch { employees = List.copyOf(employees); }
    }

    public Batch validate(Path source, Scope scope, String legacyOrg, LocalDate asOf) throws IOException {
        return validate(Files.readString(source), scope, legacyOrg, asOf);
    }

    public Batch validate(String source, Scope scope, String legacyOrg, LocalDate asOf) {
        List<Csv.Row> rows = Csv.parse(source);
        List<Problem> errors = new ArrayList<>();
        List<Employee> employees = new ArrayList<>();
        int firstLine = rows.isEmpty() ? 1 : rows.get(0).line();
        if (rows.isEmpty() || rows.get(0).error() != null) {
            return failed(firstLine, rows.isEmpty() ? "Employee CSV header is required" : rows.get(0).error());
        }
        List<String> header = rows.get(0).cells().stream().map(String::strip).toList();
        boolean legacy = header.equals(LEGACY);
        if (!legacy && !header.equals(HEADER)) return failed(firstLine, "Unrecognized employee CSV header");
        if (legacy && !Scope.present(legacyOrg)) return failed(firstLine, "Legacy import requires an explicit organization");
        if (legacy && !scope.hasOrganization(legacyOrg)) return failed(firstLine, "Unknown legacy organization");

        var seen = new HashSet<Scope.Key>();
        for (Csv.Row row : rows.subList(1, rows.size())) {
            try {
                if (row.error() != null) throw new IllegalArgumentException(row.error());
                var cells = row.cells().stream().map(String::strip).toList();
                if (cells.size() != header.size()) throw new IllegalArgumentException("Unexpected employee column count");
                Employee employee = legacy ? legacy(cells, scope, legacyOrg, asOf) : explicit(cells, scope, asOf);
                if (!seen.add(employee.key())) throw new IllegalArgumentException("Duplicate employee ID in organization");
                employees.add(employee);
            } catch (IllegalArgumentException e) {
                errors.add(new Problem(row.line(), e.getMessage()));
            }
        }
        return new Batch(new Preview(employees.size(), errors), errors.isEmpty() ? employees : List.of());
    }

    private static Batch failed(int line, String detail) {
        return new Batch(new Preview(0, List.of(new Problem(line, detail))), List.of());
    }

    private static Employee legacy(List<String> c, Scope scope, String org, LocalDate asOf) {
        String department = scope.legacyDepartment(org, c.get(3));
        var pay = new Employee.Compensation(decimal(c.get(6), "Basic pay"), null, Employee.PayBasis.MONTHLY, null);
        return new Employee(new Scope.Key(org, c.get(0)), department, c.get(1), c.get(2), c.get(4), null,
                joined(c.get(5), asOf), Employee.Status.parse(c.get(7)), pay);
    }

    private static Employee explicit(List<String> c, Scope scope, LocalDate asOf) {
        scope.requireDepartment(c.get(0), c.get(1));
        if (c.get(8).isEmpty() && (!c.get(9).isEmpty() || !c.get(10).isEmpty() || !c.get(11).isEmpty())) {
            throw new IllegalArgumentException("Compensation metadata requires basic pay");
        }
        var pay = c.get(8).isEmpty() ? null : new Employee.Compensation(decimal(c.get(8), "Basic pay"),
                currency(c.get(9)), basis(c.get(10)), c.get(11).isEmpty() ? null : decimal(c.get(11), "FTE"));
        return new Employee(new Scope.Key(c.get(0), c.get(2)), c.get(1), c.get(3), c.get(4), c.get(5), c.get(6),
                joined(c.get(7), asOf), Employee.Status.parse(c.get(12)), pay);
    }

    private static LocalDate joined(String value, LocalDate asOf) {
        LocalDate date;
        try {
            date = LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Joining date must be a valid ISO date");
        }
        if (date.isAfter(asOf)) throw new IllegalArgumentException("Joining date is after the employee snapshot");
        return date;
    }

    private static BigDecimal decimal(String value, String field) {
        if (!value.matches("[+-]?[0-9]+(?:\\.[0-9]+)?")) {
            throw new IllegalArgumentException(field + " must be a decimal number");
        }
        return new BigDecimal(value);
    }

    private static Currency currency(String value) {
        try {
            return value.isEmpty() ? null : Currency.getInstance(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Currency must be a valid ISO currency code");
        }
    }

    private static Employee.PayBasis basis(String value) {
        try {
            return value.isEmpty() ? null : Employee.PayBasis.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Pay basis must be HOURLY, MONTHLY or ANNUAL");
        }
    }
}
