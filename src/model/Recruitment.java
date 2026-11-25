package model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static model.Scope.required;

public final class Recruitment {
    private Recruitment() {}

    public enum Stage { APPLIED, SCREENING, INTERVIEW, OFFERED, REJECTED, DECLINED, ACCEPTED, HIRED, WITHDRAWN }
    public enum OfferStatus { EXTENDED, DECLINED, ACCEPTED }

    public record Requisition(Scope.Key key, String departmentId, String job, LocalDate openedOn) {
        public Requisition {
            Objects.requireNonNull(key);
            departmentId = required(departmentId, "department ID");
            job = required(job, "job");
            Objects.requireNonNull(openedOn);
        }
    }

    public record StageEvent(Stage stage, LocalDate date) {
        public StageEvent {
            Objects.requireNonNull(stage);
            Objects.requireNonNull(date);
        }
    }

    public record Offer(LocalDate extendedOn, OfferStatus status, LocalDate decidedOn) {}
    public record Hire(Scope.Key application, LocalDate date) {}
    public record Rejection(Scope.Key application, LocalDate date, String reason) {}

    public record Application(Scope.Key key, Candidate candidate, Requisition requisition,
                              List<StageEvent> events, String rejectionReason) {
        public Application {
            Objects.requireNonNull(key);
            Objects.requireNonNull(candidate);
            Objects.requireNonNull(requisition);
            if (!key.orgId().equals(candidate.key().orgId()) || !key.orgId().equals(requisition.key().orgId())) {
                throw new IllegalArgumentException("Cross-organization recruitment link");
            }
            events = List.copyOf(events);
            if (events.isEmpty() || events.get(0).stage() != Stage.APPLIED
                    || events.get(0).date().isBefore(requisition.openedOn())) {
                throw new IllegalArgumentException("Application must start on or after the requisition opens");
            }
            for (int i = 1; i < events.size(); i++) {
                StageEvent previous = events.get(i - 1);
                StageEvent next = events.get(i);
                if (next.date().isBefore(previous.date()) || !allows(previous.stage(), next.stage())) {
                    throw new IllegalArgumentException("Invalid application stage transition or date order");
                }
            }
            if (events.get(events.size() - 1).stage() == Stage.REJECTED) {
                rejectionReason = required(rejectionReason, "rejection reason");
            } else if (Scope.present(rejectionReason)) {
                throw new IllegalArgumentException("Rejection reason requires a pre-offer rejection");
            } else {
                rejectionReason = null;
            }
        }

        public static boolean allows(Stage before, Stage after) {
            if (after == Stage.WITHDRAWN) {
                return before == Stage.APPLIED || before == Stage.SCREENING || before == Stage.INTERVIEW
                        || before == Stage.OFFERED;
            }
            return switch (before) {
                case APPLIED -> after == Stage.SCREENING || after == Stage.INTERVIEW
                        || after == Stage.OFFERED || after == Stage.REJECTED;
                case SCREENING -> after == Stage.INTERVIEW || after == Stage.OFFERED || after == Stage.REJECTED;
                case INTERVIEW -> after == Stage.OFFERED || after == Stage.REJECTED;
                case OFFERED -> after == Stage.ACCEPTED || after == Stage.DECLINED;
                case ACCEPTED -> after == Stage.HIRED;
                case HIRED, DECLINED, REJECTED, WITHDRAWN -> false;
            };
        }

        public LocalDate date(Stage stage) {
            return events.stream().filter(e -> e.stage() == stage).map(StageEvent::date).findFirst().orElse(null);
        }

        public Offer offer() {
            LocalDate extendedOn = date(Stage.OFFERED);
            if (extendedOn == null) return null;
            if (date(Stage.ACCEPTED) != null) return new Offer(extendedOn, OfferStatus.ACCEPTED, date(Stage.ACCEPTED));
            if (date(Stage.DECLINED) != null) return new Offer(extendedOn, OfferStatus.DECLINED, date(Stage.DECLINED));
            return new Offer(extendedOn, OfferStatus.EXTENDED, null);
        }

        public Hire hire() {
            return date(Stage.HIRED) == null ? null : new Hire(key, date(Stage.HIRED));
        }

        public Rejection rejection() {
            return rejectionReason == null ? null : new Rejection(key, date(Stage.REJECTED), rejectionReason);
        }
    }

    public record LegacyApplication(Scope.Key candidate, String departmentId, LocalDate appliedOn,
                                    LocalDate offeredOn) {
        public LegacyApplication {
            Objects.requireNonNull(candidate);
            departmentId = required(departmentId, "department ID");
            Objects.requireNonNull(appliedOn);
            if (offeredOn != null && offeredOn.isBefore(appliedOn)) {
                throw new IllegalArgumentException("Offer date precedes application date");
            }
        }
    }

    public record LegacyRejection(Scope.Key candidate, String departmentId, LocalDate date, String reason) {
        public LegacyRejection {
            Objects.requireNonNull(candidate);
            departmentId = required(departmentId, "department ID");
            Objects.requireNonNull(date);
            reason = required(reason, "rejection reason");
        }
    }
}
