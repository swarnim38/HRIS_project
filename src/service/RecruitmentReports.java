package service;

import model.EmploymentHistory.Period;
import model.Recruitment;
import model.Recruitment.Application;
import model.Recruitment.LegacyApplication;
import model.Recruitment.LegacyRejection;
import model.Recruitment.Stage;
import model.Scope;
import model.Scope.Actor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;

public final class RecruitmentReports {
    public record Duration(OptionalDouble medianDays, int sampleSize, String detail) {}
    public record Durations(Period period, Duration timeToFill, Duration timeToHire, Duration applicationToOffer,
                            boolean legacyCoverageGap) {}
    public record Reason(String name, int count, double sharePercent, double cumulativePercent) {}
    public record Rejections(Period period, int total, List<Reason> reasons) {
        public Rejections { reasons = List.copyOf(reasons); }
    }

    private final Scope scope;

    public RecruitmentReports(Scope scope) { this.scope = scope; }

    public List<Application> applications(Actor actor, List<Application> applications) {
        scope.requirePeopleAccess(actor);
        return applications.stream().filter(app -> scope.canReadPeople(actor, app.key().orgId(),
                app.requisition().departmentId())).toList();
    }

    public Durations durations(Actor actor, List<Application> applications, List<LegacyApplication> legacy,
                               Period period) {
        List<Long> fill = new ArrayList<>();
        List<Long> hire = new ArrayList<>();
        List<Long> extension = new ArrayList<>();
        for (Application app : applications(actor, applications)) {
            LocalDate applied = app.date(Stage.APPLIED);
            LocalDate accepted = app.date(Stage.ACCEPTED);
            if (period.contains(accepted)) {
                fill.add(ChronoUnit.DAYS.between(app.requisition().openedOn(), accepted));
                hire.add(ChronoUnit.DAYS.between(applied, accepted));
            }
        }
        boolean legacyGap = false;
        for (LegacyApplication app : legacy) {
            if (scope.canReadPeople(actor, app.candidate().orgId(), app.departmentId())) {
                if (!app.appliedOn().isAfter(period.through())) legacyGap = true;
                if (period.contains(app.offeredOn())) {
                    extension.add(ChronoUnit.DAYS.between(app.appliedOn(), app.offeredOn()));
                }
            }
        }
        return new Durations(period, median(fill, "No recorded offer acceptances in period"),
                median(hire, "No recorded offer acceptances in period"),
                median(extension, "No dated legacy offer extensions in period"), legacyGap);
    }

    public Rejections rejections(Actor actor, List<Application> applications, List<LegacyRejection> legacy,
                                 Period period) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Application app : applications(actor, applications)) {
            Recruitment.Rejection rejection = app.rejection();
            if (rejection != null && period.contains(rejection.date())) {
                counts.merge(rejection.reason(), 1, Integer::sum);
            }
        }
        for (LegacyRejection row : legacy) {
            if (scope.canReadPeople(actor, row.candidate().orgId(), row.departmentId()) && period.contains(row.date())) {
                counts.merge(row.reason(), 1, Integer::sum);
            }
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        int cumulative = 0;
        List<Reason> results = new ArrayList<>();
        for (var entry : counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList()) {
            cumulative += entry.getValue();
            results.add(new Reason(entry.getKey(), entry.getValue(), entry.getValue() * 100.0 / total,
                    cumulative * 100.0 / total));
        }
        return new Rejections(period, total, results);
    }

    private static Duration median(List<Long> days, String unavailable) {
        if (days.isEmpty()) return new Duration(OptionalDouble.empty(), 0, unavailable);
        days.sort(Long::compare);
        int middle = days.size() / 2;
        double median = (days.get((days.size() - 1) / 2) + days.get(middle)) / 2.0;
        return new Duration(OptionalDouble.of(median), days.size(), "");
    }
}
