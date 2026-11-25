package service;

import io.EmployeeImport;
import model.Employee;
import model.Scope;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public final class HRSystem {
    private final Scope scope;
    private final LocalDate asOf;
    private volatile List<Employee> employees;

    public HRSystem(Scope scope, LocalDate asOf) {
        this.scope = scope;
        this.asOf = asOf;
    }

    public EmployeeImport.Preview importEmployees(Path source, String legacyOrg, boolean previewOnly)
            throws IOException {
        var batch = new EmployeeImport().validate(source, scope, legacyOrg, asOf);
        if (!previewOnly && batch.preview().valid()) employees = batch.employees();
        return batch.preview();
    }

    private List<Employee> employees() {
        var current = employees;
        if (current == null) throw new IllegalStateException("Employee snapshot unavailable");
        return current;
    }

    public List<WorkforceReports.DirectoryRow> directory(Scope.Actor actor) {
        scope.requirePeopleAccess(actor);
        return new WorkforceReports(scope).directory(actor, employees());
    }

    public WorkforceReports.Headcount headcount(Scope.Actor actor) {
        scope.requirePeopleAccess(actor);
        return new WorkforceReports(scope).current(actor, employees(), asOf);
    }

    public List<WorkforceReports.TenureFlag> tenureFlags(Scope.Actor actor, int years) {
        scope.requirePeopleAccess(actor);
        return new WorkforceReports(scope).tenureFlags(actor, employees(), asOf, years);
    }

    public List<CompensationReports.PayRow> pay(Scope.Actor actor) {
        scope.requireActor(actor);
        return new CompensationReports(scope).pay(actor, employees());
    }

    public CompensationReports.Comparison payComparison(Scope.Actor actor, Integer minimumGroupSize) {
        scope.requirePeopleAccess(actor);
        return new CompensationReports(scope).compare(actor, employees(), asOf, minimumGroupSize);
    }
}
