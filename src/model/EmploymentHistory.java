package model;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class EmploymentHistory {
    public record Period(LocalDate from, LocalDate through) {
        public Period {
            Objects.requireNonNull(from);
            Objects.requireNonNull(through);
            if (from.isAfter(through) || through.equals(LocalDate.MAX)) {
                throw new IllegalArgumentException("Invalid reporting period");
            }
        }

        public boolean contains(LocalDate date) {
            return date != null && !date.isBefore(from) && !date.isAfter(through);
        }
    }

    public enum Kind { HIRE, REHIRE, CHANGE, SEPARATION, SNAPSHOT }

    public record Event(Scope.Key employee, String departmentId, LocalDate date, Kind kind, Employee.Status status) {
        public Event {
            Objects.requireNonNull(employee);
            departmentId = Scope.required(departmentId, "department ID");
            Objects.requireNonNull(date);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(status);
            if (kind != Kind.SNAPSHOT && (kind == Kind.SEPARATION) == status.employed()) {
                throw new IllegalArgumentException("Employment event and status disagree");
            }
        }
    }

    public record Coverage(Scope.Key department, Period period) {
        public Coverage {
            Objects.requireNonNull(department);
            Objects.requireNonNull(period);
        }
    }

    private final Map<Scope.Key, List<Event>> timelines;
    private final Map<Scope.Key, Period> coverage;

    public EmploymentHistory(Scope scope, List<Event> events, List<Coverage> coverage) {
        Map<Scope.Key, Period> covered = new HashMap<>();
        for (Coverage declaration : coverage) {
            Scope.Key department = declaration.department();
            scope.requireDepartment(department.orgId(), department.id());
            if (covered.putIfAbsent(department, declaration.period()) != null) {
                throw new IllegalArgumentException("Duplicate employment coverage declaration");
            }
        }
        this.coverage = Map.copyOf(covered);
        this.timelines = Map.copyOf(events.stream().collect(Collectors.groupingBy(Event::employee,
                Collectors.collectingAndThen(Collectors.toList(), timeline -> validate(scope, timeline)))));
    }

    private static List<Event> validate(Scope scope, List<Event> timeline) {
        List<Event> sorted = timeline.stream().sorted(Comparator.comparing(Event::date)).toList();
        Event previous = null;
        for (Event event : sorted) {
            scope.requireDepartment(event.employee().orgId(), event.departmentId());
            boolean valid = switch (event.kind()) {
                case HIRE, SNAPSHOT -> previous == null;
                case REHIRE -> previous != null && !previous.status().employed();
                case CHANGE -> previous != null && previous.status().employed();
                case SEPARATION -> previous != null && previous.status().employed()
                        && previous.departmentId().equals(event.departmentId());
            };
            if (!valid || (previous != null && !event.date().isAfter(previous.date()))) {
                throw new IllegalArgumentException("Invalid employment timeline or repeated effective date");
            }
            previous = event;
        }
        return sorted;
    }

    public Map<Scope.Key, List<Event>> timelines() { return timelines; }
    public Map<Scope.Key, Period> coverage() { return coverage; }
}
