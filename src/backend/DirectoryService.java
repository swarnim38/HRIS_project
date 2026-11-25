package backend;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import model.Scope;
import model.Scope.Actor;
import org.springframework.stereotype.Service;
import service.WorkforceReports;

@Service
public class DirectoryService {
    public record CandidateSummary(String candidateId, String departmentId, String name) {}

    private final DirectoryRepository repository;
    private final Clock clock = Clock.systemDefaultZone();

    public DirectoryService(DirectoryRepository repository) {
        this.repository = repository;
    }

    public List<WorkforceReports.DirectoryRow> employees(Actor actor) {
        Scope scope = scope(actor);
        scope.requirePeopleAccess(actor);
        return new WorkforceReports(scope).directory(actor, repository.employees(actor));
    }

    public WorkforceReports.Headcount currentHeadcount(Actor actor) {
        Scope scope = scope(actor);
        scope.requirePeopleAccess(actor);
        return new WorkforceReports(scope).current(actor, repository.employees(actor), LocalDate.now(clock));
    }

    public List<DirectoryRepository.Pay> compensation(Actor actor) {
        Scope scope = scope(actor);
        return repository.compensation(actor).stream()
                .filter(row -> scope.canReadFinancial(actor, actor.orgId(), row.departmentId()))
                .toList();
    }

    public List<CandidateSummary> candidates(Actor actor) {
        Scope scope = scope(actor);
        scope.requirePeopleAccess(actor);
        return repository.candidates(actor).stream()
                .filter(row -> scope.canReadPeople(actor, row.candidate().key().orgId(), row.departmentId()))
                .map(row -> new CandidateSummary(row.candidate().key().id(), row.departmentId(), row.candidate().name()))
                .toList();
    }

    private Scope scope(Actor actor) {
        if (actor == null || actor.orgId() == null) throw new SecurityException("An authenticated account is required");
        Scope scope = new Scope(List.of(new Scope.Organization(actor.orgId())), repository.departments(actor.orgId()));
        scope.requireActor(actor);
        return scope;
    }
}
