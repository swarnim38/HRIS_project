package model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

import static model.Scope.required;

public record Employee(Scope.Key key, String departmentId, String fullName, String demographic,
                       String job, String level, LocalDate joiningDate, Status status, Compensation compensation) {
    public enum Status {
        ACTIVE, PROBATION, NOTICE_PERIOD, EXITED;

        public boolean employed() {
            return this != EXITED;
        }

        public static Status parse(String value) {
            try {
                return valueOf(value.strip().replace(' ', '_').toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown employment status");
            }
        }
    }

    public enum PayBasis { HOURLY, MONTHLY, ANNUAL }

    public record Compensation(BigDecimal amount, Currency currency, PayBasis basis, BigDecimal fte) {
        public Compensation {
            if (amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("Basic pay must be a nonnegative number");
            }
            if (fte != null && (fte.signum() <= 0 || fte.compareTo(BigDecimal.ONE) > 0)) {
                throw new IllegalArgumentException("FTE must be greater than zero and at most one");
            }
            amount = amount.stripTrailingZeros();
            if (fte != null) {
                fte = fte.stripTrailingZeros();
            }
        }
    }

    public Employee {
        Objects.requireNonNull(key);
        departmentId = required(departmentId, "department ID");
        fullName = required(fullName, "full name");
        job = required(job, "job");
        demographic = Scope.present(demographic) ? demographic.strip() : null;
        level = Scope.present(level) ? level.strip() : null;
        Objects.requireNonNull(joiningDate);
        Objects.requireNonNull(status);
    }
}
