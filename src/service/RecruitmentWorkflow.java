package service;

import model.Candidate;
import model.Recruitment.Application;
import model.Recruitment.Requisition;
import model.Recruitment.Stage;
import model.Recruitment.StageEvent;
import model.Scope;
import model.Scope.Actor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RecruitmentWorkflow {
    private final Scope scope;

    public RecruitmentWorkflow(Scope scope) {
        this.scope = scope;
    }

    public Application submit(Actor actor, String applicationId, String candidateId, String name,
                              Requisition requisition, LocalDate applied, LocalDate asOf) {
        scope.requireRecruitmentWrite(actor, requisition.key().orgId(), requisition.departmentId());
        requireOccurred(applied, asOf);
        return new Application(new Scope.Key(actor.orgId(), applicationId),
                new Candidate(new Scope.Key(actor.orgId(), candidateId), name), requisition,
                List.of(new StageEvent(Stage.APPLIED, applied)), null);
    }

    public Application advance(Actor actor, Application current, Stage stage, LocalDate date,
                               String reason, LocalDate asOf) {
        scope.requireRecruitmentWrite(actor, current.key().orgId(), current.requisition().departmentId());
        requireOccurred(date, asOf);
        if (stage == Stage.APPLIED) throw new IllegalArgumentException("Use create for a new application");
        String normalized = Scope.present(reason) ? reason.strip() : null;
        if (date.equals(current.date(stage)) && Objects.equals(
                stage == Stage.REJECTED ? current.rejectionReason() : null, normalized)) return current;
        List<StageEvent> events = new ArrayList<>(current.events());
        events.add(new StageEvent(stage, date));
        return new Application(current.key(), current.candidate(), current.requisition(), events, normalized);
    }

    private static void requireOccurred(LocalDate date, LocalDate asOf) {
        if (date == null || date.isAfter(asOf)) throw new IllegalArgumentException("Event requires a date by the snapshot");
    }
}
