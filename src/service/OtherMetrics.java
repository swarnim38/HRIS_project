package service;

import model.EmploymentHistory.Period;
import model.Recruitment.Application;
import model.Scope;
import model.Scope.Actor;
import model.Scope.Role;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Currency;
import java.util.List;

public final class OtherMetrics {
    public record Metric(String name, Period period, BigDecimal value, Currency currency, BigDecimal denominator,
                         String detail) {}

    // Upstream input contains approved totals for a complete, explicitly identified period.
    public record RecruitingCosts(String orgId, String departmentId, Period period, Currency currency,
                                  BigDecimal internal, BigDecimal external) {
        public RecruitingCosts {
            orgId = Scope.required(orgId, "organization ID");
            if (departmentId != null) departmentId = Scope.required(departmentId, "department ID");
            if (period == null || currency == null || internal == null || external == null
                    || internal.signum() < 0 || external.signum() < 0) {
                throw new IllegalArgumentException("Approved recruiting costs require a period, currency and nonnegative totals");
            }
        }
    }

    public record RevenueInputs(String orgId, String departmentId, Period period, Currency currency,
                                BigDecimal revenue, BigDecimal averageHeadcount) {
        public RevenueInputs {
            orgId = Scope.required(orgId, "organization ID");
            if (departmentId != null) departmentId = Scope.required(departmentId, "department ID");
            if (period == null || currency == null || revenue == null || averageHeadcount == null
                    || averageHeadcount.signum() < 0) {
                throw new IllegalArgumentException("Revenue inputs require approved revenue and matching average headcount");
            }
        }
    }

    private final Scope scope;

    public OtherMetrics(Scope scope) { this.scope = scope; }

    public Metric costPerHire(Actor actor, Period period, RecruitingCosts costs, List<Application> applications,
                              boolean completeHireRegister) {
        scope.requireActor(actor);
        String name = "Cost per hire";
        if (costs != null) requireFinancialScope(actor, costs.orgId(), costs.departmentId());
        if (costs == null || !period.equals(costs.period()) || !completeHireRegister) {
            return unavailable(name, period, "Requires approved internal and external costs and a complete hire register");
        }
        long hires = applications.stream().filter(app -> scope.canReadFinancial(actor, app.key().orgId(),
                        app.requisition().departmentId()))
                .filter(app -> costs.departmentId() == null || costs.departmentId().equals(app.requisition().departmentId()))
                .filter(app -> app.hire() != null && period.contains(app.hire().date())).count();
        return ratio(name, period, costs.internal().add(costs.external()), costs.currency(),
                BigDecimal.valueOf(hires), "No hires in period");
    }

    public Metric revenuePerEmployee(Actor actor, Period period, RevenueInputs inputs) {
        scope.requireActor(actor);
        String name = "Revenue per employee";
        if (inputs != null) requireFinancialScope(actor, inputs.orgId(), inputs.departmentId());
        if (inputs == null || !period.equals(inputs.period())) {
            return unavailable(name, period, "Requires period-aligned approved revenue and average headcount");
        }
        return ratio(name, period, inputs.revenue(), inputs.currency(), inputs.averageHeadcount(),
                "Average headcount is zero");
    }

    private void requireFinancialScope(Actor actor, String orgId, String departmentId) {
        if (!actor.orgId().equals(orgId) || (departmentId == null && actor.role() == Role.MANAGER)
                || (departmentId != null && !scope.canReadFinancial(actor, orgId, departmentId))) {
            throw new SecurityException("Financial inputs are outside the actor's scope");
        }
    }

    private static Metric ratio(String name, Period period, BigDecimal numerator, Currency currency,
                                BigDecimal denominator, String zeroDetail) {
        if (denominator.signum() == 0) return new Metric(name, period, null, currency, denominator, zeroDetail);
        return new Metric(name, period, numerator.divide(denominator, MathContext.DECIMAL128),
                currency, denominator, "");
    }

    public List<Metric> availability(Actor actor, Period period) {
        scope.requireActor(actor);
        var financial = List.of(costPerHire(actor, period, null, List.of(), false),
                unavailable("Cost per employee", period, "Requires period-aligned approved costs and an agreed headcount denominator"),
                revenuePerEmployee(actor, period, null));
        if (actor.role() == Role.FINANCE) return financial;
        var results = new java.util.ArrayList<>(financial);
        results.add(unavailable("Quality of hire", period, "Requires post-hire outcomes and an agreed scoring method"));
        results.add(unavailable("Engagement / satisfaction", period, "Requires dated survey responses and anonymity rules"));
        results.add(unavailable("Time since last promotion", period, "Requires effective-dated promotion history"));
        results.add(unavailable("Absenteeism", period, "Requires scheduled time, recorded absences and a leave inclusion policy"));
        return List.copyOf(results);
    }

    private static Metric unavailable(String name, Period period, String detail) {
        return new Metric(name, period, null, null, null, detail);
    }
}
