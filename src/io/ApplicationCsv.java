package io;

import model.Candidate;
import model.Recruitment.Application;
import model.Recruitment.Requisition;
import model.Recruitment.Stage;
import model.Recruitment.StageEvent;
import model.Scope;
import model.Scope.Actor;
import service.RecruitmentWorkflow;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ApplicationCsv {
    private static final List<String> HEADER = List.of("OrgId", "ApplicationId", "CandidateId", "CandidateName",
            "RequisitionId", "Events", "RejectionReason");
    private final Path path;
    private final Map<Scope.Key, Requisition> requisitions;
    private final Scope scope;
    private final RecruitmentWorkflow workflow;

    public ApplicationCsv(Path path, Scope scope, List<Requisition> requisitions) {
        this.path = path.toAbsolutePath().normalize();
        this.scope = scope;
        this.workflow = new RecruitmentWorkflow(scope);
        this.requisitions = requisitions.stream().collect(Collectors.toUnmodifiableMap(Requisition::key, r -> {
            scope.requireDepartment(r.key().orgId(), r.departmentId());
            return r;
        }));
    }

    public List<Requisition> requisitions(Actor actor, LocalDate asOf) {
        scope.requirePeopleAccess(actor);
        return requisitions.values().stream()
                .filter(r -> scope.canReadPeople(actor, r.key().orgId(), r.departmentId()))
                .filter(r -> !r.openedOn().isAfter(asOf)).toList();
    }

    public List<Application> applications(Actor actor, LocalDate asOf) throws IOException {
        scope.requirePeopleAccess(actor);
        return read().values().stream()
                .filter(a -> scope.canReadPeople(actor, a.key().orgId(), a.requisition().departmentId()))
                .filter(a -> !a.date(Stage.APPLIED).isAfter(asOf))
                .map(a -> {
                    var events = a.events().stream().filter(event -> !event.date().isAfter(asOf)).toList();
                    String reason = events.getLast().stage() == Stage.REJECTED ? a.rejectionReason() : null;
                    return new Application(a.key(), a.candidate(), a.requisition(), events, reason);
                }).toList();
    }

    public boolean create(Actor actor, String id, String candidateId, String name, String requisitionId,
                          LocalDate applied, LocalDate asOf) throws IOException {
        scope.requirePeopleAccess(actor);
        var req = requisitions.get(new Scope.Key(actor.orgId(), requisitionId));
        if (req == null) throw new IllegalArgumentException("Unknown requisition in scope");
        var application = workflow.submit(actor, id, candidateId, name, req, applied, asOf);
        var key = application.key();
        return change(applications -> {
            Application existing = applications.get(key);
            if (existing != null) {
                scope.requireRecruitmentWrite(actor, existing.key().orgId(), existing.requisition().departmentId());
                if (existing.candidate().equals(application.candidate())
                        && existing.requisition().equals(req) && existing.date(Stage.APPLIED).equals(applied)) return false;
                throw new IllegalArgumentException("Application ID is already in use in this organization");
            }
            applications.put(key, application);
            return true;
        });
    }

    public boolean advance(Actor actor, String id, Stage stage, LocalDate date, String reason, LocalDate asOf)
            throws IOException {
        scope.requirePeopleAccess(actor);
        var key = new Scope.Key(actor.orgId(), id);
        return change(applications -> {
            Application existing = applications.get(key);
            if (existing == null || !scope.canReadPeople(actor, key.orgId(), existing.requisition().departmentId())) {
                throw new IllegalArgumentException("Unknown application in scope");
            }
            Application updated = workflow.advance(actor, existing, stage, date, reason, asOf);
            if (updated == existing) return false;
            applications.put(key, updated);
            return true;
        });
    }

    private Map<Scope.Key, Application> read() throws IOException {
        List<Application> rows = LocalInputs.optional(path, HEADER, c -> {
            var requisition = requisitions.get(new Scope.Key(c.get(0), c.get(4)));
            if (requisition == null) throw new IllegalArgumentException("Unknown requisition");
            List<StageEvent> events = new ArrayList<>();
            for (String value : c.get(5).split(";", -1)) {
                String[] event = value.split("@", -1);
                if (event.length != 2) throw new IllegalArgumentException("Invalid stage event");
                // A future event relative to the report date can follow a valid historical event in storage.
                events.add(new StageEvent(Stage.valueOf(event[0]), LocalDate.parse(event[1])));
            }
            return new Application(new Scope.Key(c.get(0), c.get(1)),
                    new Candidate(new Scope.Key(c.get(0), c.get(2)), c.get(3)), requisition, events, c.get(6));
        });
        Map<Scope.Key, Application> applications = new LinkedHashMap<>();
        for (Application row : rows) {
            if (applications.putIfAbsent(row.key(), row) != null) throw new IOException("Duplicate application ID");
        }
        try {
            validateRelationships(applications.values());
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage());
        }
        return applications;
    }

    private static void validateRelationships(Iterable<Application> apps) {
        Map<Scope.Key, Candidate> identities = new LinkedHashMap<>();
        var filled = new java.util.HashSet<Scope.Key>();
        for (Application app : apps) {
            Candidate previous = identities.putIfAbsent(app.candidate().key(), app.candidate());
            if (previous != null && !previous.equals(app.candidate())) {
                throw new IllegalArgumentException("Candidate ID has conflicting candidate details in organization");
            }
            if (app.date(Stage.ACCEPTED) != null && !filled.add(app.requisition().key())) {
                throw new IllegalArgumentException("A single-opening requisition can have only one accepted offer");
            }
        }
    }

    // Serialize in-process callers as well as local instances that use this sidecar lock.
    private boolean change(Function<Map<Scope.Key, Application>, Boolean> mutation) throws IOException {
        synchronized (ApplicationCsv.class) {
            Path lock = path.resolveSibling(path.getFileName() + ".lock");
            try (var channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var fileLock = channel.lock()) {
                if (!fileLock.isValid()) throw new IOException("Application file lock is unavailable");
                var applications = read();
                if (!mutation.apply(applications)) return false;
                validateRelationships(applications.values());
                Path pending = Files.createTempFile(path.getParent(), "talentcompass-", ".tmp");
                try {
                    List<String> lines = new ArrayList<>();
                    lines.add(Csv.encode(HEADER));
                    for (Application a : applications.values()) lines.add(encode(a));
                    Files.write(pending, lines);
                    Files.move(pending, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(pending);
                }
                return true;
            }
        }
    }

    private static String encode(Application a) {
        String events = a.events().stream().map(e -> e.stage() + "@" + e.date()).collect(Collectors.joining(";"));
        return Csv.encode(List.of(a.key().orgId(), a.key().id(), a.candidate().key().id(), a.candidate().name(),
                a.requisition().key().id(), events, a.rejectionReason() == null ? "" : a.rejectionReason()));
    }
}
