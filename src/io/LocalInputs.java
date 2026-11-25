package io;

import model.EmploymentHistory;
import model.Recruitment.LegacyApplication;
import model.Recruitment.LegacyRejection;
import model.Recruitment.Requisition;
import model.Scope;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static model.EmploymentHistory.*;
import static model.Employee.Status;

public final class LocalInputs {
    private LocalInputs() {}

    public static Scope catalog(Path path) throws IOException {
        var departments = read(path, List.of("OrgId", "DepartmentId", "DepartmentName"),
                c -> new Scope.Department(c.get(0), c.get(1), c.get(2)));
        var orgs = departments.stream().map(d -> new Scope.Organization(d.orgId())).distinct().toList();
        return new Scope(orgs, departments);
    }

    public static List<Requisition> requisitions(Path path, Scope scope) throws IOException {
        var seen = new HashSet<Scope.Key>();
        return optional(path, List.of("OrgId", "DepartmentId", "RequisitionId", "Job", "OpenedOn"), c -> {
            scope.requireDepartment(c.get(0), c.get(1));
            var key = new Scope.Key(c.get(0), c.get(2));
            if (!seen.add(key)) throw new IllegalArgumentException("Duplicate requisition ID in organization");
            return new Requisition(key, c.get(1), c.get(3), LocalDate.parse(c.get(4)));
        });
    }

    public static EmploymentHistory history(Path events, Path coverage, Scope scope) throws IOException {
        if (Files.notExists(events) || Files.notExists(coverage)) return null;
        var declarations = read(coverage, List.of("OrgId", "DepartmentId", "From", "Through"),
                c -> new Coverage(new Scope.Key(c.get(0), c.get(1)),
                        new Period(LocalDate.parse(c.get(2)), LocalDate.parse(c.get(3)))));
        var timeline = read(events, List.of("OrgId", "EmployeeId", "DepartmentId", "Date", "Kind", "Status"),
                c -> new Event(new Scope.Key(c.get(0), c.get(1)), c.get(2), LocalDate.parse(c.get(3)),
                        Kind.valueOf(c.get(4)), Status.parse(c.get(5))));
        return new EmploymentHistory(scope, timeline, declarations);
    }

    public static List<LegacyApplication> legacyApplications(Path path, String org, String department, LocalDate asOf)
            throws IOException {
        return optional(path, null, c -> {
            var key = new Scope.Key(org, c.get(0));
            Scope.required(c.get(1), "candidate name");
            Scope.required(c.get(2), "job");
            String status = c.get(5).toUpperCase(Locale.ROOT);
            if (!List.of("ACCEPTED", "REJECTED", "PENDING", "DECLINED", "OFFERED", "EXTENDED").contains(status)) {
                throw new IllegalArgumentException("Unrecognized legacy candidate status");
            }
            var offered = c.get(4).isEmpty() || c.get(4).equalsIgnoreCase("PENDING") ? null : LocalDate.parse(c.get(4));
            return new LegacyApplication(key, department, LocalDate.parse(c.get(3)), offered);
        }, 6).stream().filter(a -> !a.appliedOn().isAfter(asOf)).toList();
    }

    public static List<LegacyRejection> legacyRejections(Path path, String org, String department, LocalDate asOf)
            throws IOException {
        return optional(path, null, c -> new LegacyRejection(new Scope.Key(org, c.get(0)), department,
                LocalDate.parse(c.get(2)), c.get(1)), 3).stream().filter(r -> !r.date().isAfter(asOf)).toList();
    }

    static <T> List<T> optional(Path path, List<String> header, Function<List<String>, T> mapping) throws IOException {
        return optional(path, header, mapping, header.size());
    }

    private static <T> List<T> optional(Path path, List<String> header, Function<List<String>, T> mapping, int width)
            throws IOException {
        return Files.notExists(path) ? List.of() : decode(Csv.parse(Files.readString(path)), header, mapping, width);
    }

    private static <T> List<T> read(Path path, List<String> header, Function<List<String>, T> mapping)
            throws IOException {
        return decode(Csv.parse(Files.readString(path)), header, mapping, header.size());
    }

    static <T> List<T> decode(List<Csv.Row> rows, List<String> header, Function<List<String>, T> mapping, int width)
            throws IOException {
        int first = 0;
        if (header != null) {
            if (rows.isEmpty() || rows.get(0).error() != null || !cells(rows.get(0)).equals(header)) {
                throw new IOException("Missing or unrecognized CSV header");
            }
            first = 1;
        }
        List<T> values = new ArrayList<>();
        for (var row : rows.subList(first, rows.size())) {
            try {
                var cells = cells(row);
                if (cells.size() != width) throw new IllegalArgumentException("Unexpected CSV column count");
                values.add(mapping.apply(cells));
            } catch (DateTimeParseException | IllegalArgumentException e) {
                // The underlying exception may contain a source value.
                throw new IOException("Invalid CSV data at row " + row.line());
            }
        }
        return List.copyOf(values);
    }

    private static List<String> cells(Csv.Row row) {
        if (row.error() != null) throw new IllegalArgumentException(row.error());
        return row.cells().stream().map(String::strip).toList();
    }
}
