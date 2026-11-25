package backend;

import model.Employee;
import model.EmploymentHistory;
import model.EmploymentHistory.Period;
import model.Scope;
import model.Scope.Actor;
import model.Scope.Role;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import service.CompensationReports;
import service.WorkforceReports;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static backend.Operations.*;

@Service
public class Metrics {
    public record Metric(String name, String unit, Double value, String status, String formula,
                         Number denominator, String coverage) {}
    public record Dashboard(Period period, String scope, Instant refreshedAt, List<Metric> metrics,
                            Map<String,Integer> departments, List<Map<String,Object>> rejectionReasons,
                            List<?> payCohorts, List<?> tenureFlags) {}
    public record FinanceInput(LocalDate from, LocalDate through, String currency, BigDecimal recruitingCost,
                               BigDecimal employeeCost, BigDecimal revenue) {}
    public record Attendance(String employeeId, LocalDate date, BigDecimal scheduled, BigDecimal absence) {}
    public record Survey(String id, Integer version, String departmentId, String kind, String question) {}
    public record Response(String id, Integer version, LocalDate date, List<Integer> scores) {}
    public record Outcome(String applicationId, LocalDate measuredOn, BigDecimal performance,
                          BigDecimal managerScore, Boolean retained) {}
    public record Settings(int surveyMinimum, int payMinimum, int tenureYears, int performanceWeight,
                           int managerWeight, int retentionWeight) {}
    public record Coverage(String departmentId, LocalDate from, LocalDate through) {}

    private final JdbcClient db;
    private final Operations operations;
    private final LocalIdentity identity;
    private final DirectoryRepository directory;

    public Metrics(JdbcClient db, Operations operations, LocalIdentity identity, DirectoryRepository directory) {
        this.db = db;
        this.operations = operations;
        this.identity = identity;
        this.directory = directory;
    }

    @Transactional
    public void finance(Actor actor, FinanceInput input) {
        if (actor.role() == Role.MANAGER) throw new SecurityException("Financial input requires Finance or HR Admin");
        Period period = period(input.from(), input.through());
        db.sql("""
                INSERT INTO financial_periods VALUES (?,?,?,?,?,?,?) AS incoming
                ON DUPLICATE KEY UPDATE currency=incoming.currency,recruiting_cost=incoming.recruiting_cost,
                employee_cost=incoming.employee_cost,revenue=incoming.revenue
                """).params(actor.orgId(),period.from(),period.through(),currency(input.currency()),
                money(input.recruitingCost()),money(input.employeeCost()),money(input.revenue())).update();
        identity.audit(actor,"FINANCE_RECORDED",period.from()+" / "+period.through());
    }

    public List<Map<String,Object>> financePeriods(Actor actor) {
        if (actor.role() == Role.MANAGER) throw new SecurityException("Financial periods are organization-wide");
        return db.sql("""
                SELECT from_date,through_date,currency,recruiting_cost,employee_cost,revenue
                FROM financial_periods WHERE org_id=? ORDER BY from_date DESC
                """).param(actor.orgId()).query().listOfRows();
    }

    @Transactional
    public void attendance(Actor actor, Attendance input) {
        admin(actor);
        LocalDate date = occurred(input.date());
        String dept = operations.employedDepartment(actor,input.employeeId(),date);
        BigDecimal scheduled = money(input.scheduled());
        BigDecimal absence = money(input.absence());
        if (scheduled.compareTo(BigDecimal.valueOf(24))>0 || absence.compareTo(scheduled)>0)
            throw new IllegalArgumentException("Hours must satisfy 0 ≤ absence ≤ scheduled ≤ 24");
        db.sql("""
                INSERT INTO attendance_days VALUES (?,?,?,?,?,?) AS incoming
                ON DUPLICATE KEY UPDATE department_id=incoming.department_id,scheduled_hours=incoming.scheduled_hours,
                unplanned_absence=incoming.unplanned_absence
                """).params(actor.orgId(),input.employeeId(),dept,date,scheduled,absence).update();
        identity.audit(actor,"ATTENDANCE_RECORDED",input.employeeId());
    }

    @Transactional
    public void survey(Actor actor, Survey input) {
        admin(actor);
        String kind = field(input.kind(),"Survey category",16);
        if (input.version()==null || input.version()<1 || !List.of("ENGAGEMENT","SATISFACTION").contains(kind))
            throw new IllegalArgumentException("Choose a valid survey version and category");
        db.sql("INSERT INTO surveys VALUES (?,?,?,?,?,?)")
                .params(actor.orgId(),field(input.id(),"Survey code",64),input.version(),
                        field(input.departmentId(),"Department",64),kind,field(input.question(),"Question",250)).update();
        identity.audit(actor,"SURVEY_CREATED",input.id());
    }

    public List<Map<String,Object>> surveys(Actor actor) {
        people(actor);
        return operations.scoped(actor, """
                SELECT survey_id,version_no,department_id,kind,question FROM surveys WHERE org_id=:org
                ""","department_id"," ORDER BY survey_id,version_no").query().listOfRows();
    }

    @Transactional
    public void responses(Actor actor, Response input) {
        admin(actor);
        LocalDate date = occurred(input.date());
        if (input.version()==null || input.scores()==null || input.scores().isEmpty() || input.scores().size()>500
                || input.scores().stream().anyMatch(s -> s==null || s<1 || s>5))
            throw new IllegalArgumentException("Enter between 1 and 500 anonymous scores from 1 through 5");
        for (int score : input.scores()) {
            db.sql("""
                    INSERT INTO survey_responses(org_id,survey_id,version_no,answered_on,score) VALUES (?,?,?,?,?)
                    """).params(actor.orgId(),input.id(),input.version(),date,score).update();
        }
        identity.audit(actor,"SURVEY_BATCH_ENTERED",input.id());
    }

    @Transactional
    public void outcome(Actor actor, Outcome input) {
        admin(actor);
        LocalDate day = occurred(input.measuredOn());
        LocalDate hired = db.sql("""
                SELECT s.effective_on FROM hires h JOIN application_stage_events s USING(org_id,application_id)
                WHERE h.org_id=? AND h.application_id=? AND s.stage='HIRED'
                """).params(actor.orgId(),input.applicationId()).query(LocalDate.class).optional()
                .orElseThrow(() -> new IllegalArgumentException("Record a hire first"));
        if (day.isBefore(hired) || input.retained()==null)
            throw new IllegalArgumentException("Outcome date must be on or after hire and include retention");
        BigDecimal performance = money(input.performance());
        BigDecimal score = money(input.managerScore());
        if (performance.compareTo(BigDecimal.valueOf(100))>0 || score.compareTo(BigDecimal.valueOf(100))>0)
            throw new IllegalArgumentException("Scores must be between 0 and 100");
        db.sql("""
                INSERT INTO post_hire_outcomes VALUES (?,?,?,?,?,?) AS incoming
                ON DUPLICATE KEY UPDATE performance=incoming.performance,manager_score=incoming.manager_score,retained=incoming.retained
                """).params(actor.orgId(),input.applicationId(),day,performance,score,input.retained()).update();
        identity.audit(actor,"OUTCOME_RECORDED",input.applicationId());
    }

    @Transactional
    public void settings(Actor actor, Settings s) {
        admin(actor);
        if (s.surveyMinimum()<3 || s.payMinimum()<2 || s.tenureYears()<1 || s.tenureYears()>100
                || s.performanceWeight()<0 || s.managerWeight()<0 || s.retentionWeight()<0
                || (long)s.performanceWeight()+s.managerWeight()+s.retentionWeight()!=100)
            throw new IllegalArgumentException("Use survey minimum ≥3, pay minimum ≥2, tenure 1–100 and weights totaling 100");
        db.sql("""
                INSERT INTO metric_settings VALUES (?,?,?,?,?,?,?) AS incoming ON DUPLICATE KEY UPDATE
                survey_minimum=incoming.survey_minimum,pay_minimum=incoming.pay_minimum,tenure_years=incoming.tenure_years,
                performance_weight=incoming.performance_weight,manager_weight=incoming.manager_weight,retention_weight=incoming.retention_weight
                """).params(actor.orgId(),s.surveyMinimum(),s.payMinimum(),s.tenureYears(),s.performanceWeight(),
                s.managerWeight(),s.retentionWeight()).update();
        identity.audit(actor,"METRIC_SETTINGS_CHANGED",actor.orgId());
    }

    @Transactional
    public void coverage(Actor actor, Coverage input) {
        admin(actor);
        Period p = period(input.from(),input.through());
        db.sql("""
                INSERT INTO employment_coverage VALUES (?,?,?,?) AS incoming
                ON DUPLICATE KEY UPDATE from_date=incoming.from_date,through_date=incoming.through_date
                """).params(actor.orgId(),input.departmentId(),p.from(),p.through()).update();
        identity.audit(actor,"HISTORY_COVERAGE_CONFIRMED",input.departmentId());
    }

    public Settings settings(Actor actor) {
        admin(actor);
        return configuredSettings(actor);
    }

    private Settings configuredSettings(Actor actor) {
        return db.sql("SELECT * FROM metric_settings WHERE org_id=?").param(actor.orgId())
                .query((r,n) -> new Settings(r.getInt("survey_minimum"),r.getInt("pay_minimum"),
                        r.getInt("tenure_years"),r.getInt("performance_weight"),r.getInt("manager_weight"),
                        r.getInt("retention_weight"))).optional().orElse(null);
    }

    @Transactional(readOnly=true)
    public Dashboard dashboard(Actor actor, LocalDate from, LocalDate through) {
        Period period = period(from,through);
        var cards = new ArrayList<Metric>();
        var departments = directory.departments(actor.orgId());
        var scope = new Scope(List.of(new Scope.Organization(actor.orgId())),departments);
        var workforce = new WorkforceReports(scope);
        var history = history(actor,scope);
        Actor denominatorActor = actor.role()==Role.FINANCE
                ? new Actor(actor.id(),actor.orgId(),Role.HR_ADMIN,null) : actor;
        var turnover = workforce.turnover(denominatorActor,history,period);
        if (actor.role()!=Role.MANAGER) financialMetrics(actor,period,turnover.averageHeadcount(),cards);
        String label = actor.orgId()+(actor.role()==Role.MANAGER ? " / "+actor.departmentId() : "");
        if (actor.role()==Role.FINANCE)
            return new Dashboard(period,label,Instant.now(),cards,Map.of(),List.of(),List.of(),List.of());

        var snapshot = snapshot(actor,through);
        var distribution = workforce.current(actor,snapshot,through);
        boolean covered = covers(scope,actor,history,new Period(through,through));
        cards.add(covered ? metric("Headcount","people",distribution.total(),"Employees active on the end date",null,
                "Dated employment records") : missing("Headcount","people","Employees active on the end date",
                "Complete history is required for every department in scope"));
        cards.add(turnover.percent().isPresent()
                ? metric("Turnover","%",turnover.percent().getAsDouble(),"Separations ÷ average daily headcount × 100",
                        turnover.averageHeadcount().getAsDouble(),turnover.separations()+" separations")
                : unavailableIfZero("Turnover","%","Separations ÷ average daily headcount × 100",
                        turnover.averageHeadcount(),turnover.detail()));
        recruitmentMetrics(actor,period,cards);
        var settings = configuredSettings(actor);
        surveyMetric(actor,period,"ENGAGEMENT","Engagement",settings,cards);
        surveyMetric(actor,period,"SATISFACTION","Satisfaction",settings,cards);
        quality(actor,period,settings,cards);
        attendanceMetric(actor,period,cards);
        promotionMetric(actor,through,snapshot,cards);

        var comparison = new CompensationReports(scope).compare(actor,snapshot,through,settings==null ? null : settings.payMinimum());
        cards.add(new Metric("Unadjusted pay comparison","cohorts",comparison.groups().isEmpty() ? null :
                (double)comparison.groups().size(),comparison.availability().name(),
                "Mean base pay by department, job, level, FTE, basis, currency and disclosed group",
                comparison.withDemographic(),comparison.detail()));
        var cohorts = comparison.groups().entrySet().stream().map(e -> Map.of("cohort",e.getKey(),"groups",e.getValue())).toList();
        var flags = covered && settings!=null ? workforce.tenureFlags(actor,snapshot,through,settings.tenureYears()) : List.of();
        cards.add(!covered || settings==null ? missing("Tenure flags","people","Completed years since recorded hire",
                "Complete history and an approved review threshold are required")
                : metric("Tenure flags","people",flags.size(),"Employees eligible for the configured "+settings.tenureYears()
                +" year HR review",null,"Years since original hire"));
        var reasons = rejections(actor,period);
        cards.add(metric("Rejections","applications",reasons.stream().mapToInt(r -> ((Number)r.get("count")).intValue()).sum(),
                "Recorded pre-offer rejections during the period",null,"Recorded typed and imported rejections"));
        return new Dashboard(period,label,Instant.now(),cards,covered ? distribution.byDepartment() : Map.of(),reasons,cohorts,flags);
    }

    private EmploymentHistory history(Actor actor, Scope scope) {
        var events = db.sql("SELECT * FROM employment_events WHERE org_id=?").param(actor.orgId())
                .query((r,n) -> new EmploymentHistory.Event(new Scope.Key(actor.orgId(),r.getString("employee_id")),
                        r.getString("department_id"),r.getDate("effective_on").toLocalDate(),
                        EmploymentHistory.Kind.valueOf(r.getString("kind")),Employee.Status.valueOf(r.getString("status")))).list();
        var coverage = db.sql("SELECT * FROM employment_coverage WHERE org_id=?").param(actor.orgId())
                .query((r,n) -> new EmploymentHistory.Coverage(new Scope.Key(actor.orgId(),r.getString("department_id")),
                        new Period(r.getDate("from_date").toLocalDate(),r.getDate("through_date").toLocalDate()))).list();
        return new EmploymentHistory(scope,events,coverage);
    }

    private List<Employee> snapshot(Actor actor, LocalDate date) {
        return operations.scoped(actor, """
                WITH dated AS (
                    SELECT e.*,ROW_NUMBER() OVER (PARTITION BY employee_id ORDER BY effective_on DESC) AS n
                    FROM employment_events e WHERE org_id=:org AND effective_on<=:date
                )
                SELECT d.department_id,d.status,emp.employee_id,emp.full_name,COALESCE(d.job,emp.job) AS job,
                    (d.job IS NOT NULL) AS dated_job,emp.demographic,emp.joined_on,
                    COALESCE((SELECT new_level FROM promotions j WHERE j.org_id=emp.org_id AND j.employee_id=emp.employee_id
                        AND j.effective_on BETWEEN d.effective_on AND :date ORDER BY j.effective_on DESC LIMIT 1),
                        d.job_level) AS job_level,
                    p.amount,p.currency,p.pay_basis,p.fte
                FROM dated d JOIN employees emp USING(org_id,employee_id)
                LEFT JOIN compensation_history p ON p.org_id=d.org_id AND p.employee_id=d.employee_id
                  AND p.department_id=d.department_id AND p.effective_on=(SELECT MAX(c.effective_on) FROM compensation_history c
                    WHERE c.org_id=d.org_id AND c.employee_id=d.employee_id AND c.department_id=d.department_id AND c.effective_on<=:date)
                WHERE d.n=1
                ""","d.department_id","").param("date",date)
                .query((r,n) -> {
                    BigDecimal amount=r.getBigDecimal("amount");
                    var pay = amount==null || !r.getBoolean("dated_job") ? null : new Employee.Compensation(amount,Currency.getInstance(r.getString("currency")),
                            Employee.PayBasis.valueOf(r.getString("pay_basis")),r.getBigDecimal("fte"));
                    return new Employee(new Scope.Key(actor.orgId(),r.getString("employee_id")),r.getString("department_id"),
                            r.getString("full_name"),r.getString("demographic"),r.getString("job"),r.getString("job_level"),
                            r.getDate("joined_on").toLocalDate(),Employee.Status.valueOf(r.getString("status")),pay);
                }).list();
    }

    private void financialMetrics(Actor actor, Period p, OptionalDouble average, List<Metric> out) {
        var row = db.sql("""
                SELECT * FROM financial_periods WHERE org_id=? AND from_date=? AND through_date=?
                """).params(actor.orgId(),p.from(),p.through()).query().listOfRows().stream().findFirst();
        String unit = row.map(r -> r.get("currency").toString()).orElse("currency");
        int hires = dates(actor,p,"HIRED").size();
        financeRatio(out,row,"recruiting_cost","Cost per hire",unit,"Recruiting costs ÷ recorded hires",OptionalDouble.of(hires));
        financeRatio(out,row,"employee_cost","Cost per employee",unit,"Employee costs ÷ average daily headcount",average);
        financeRatio(out,row,"revenue","Revenue per employee",unit,"Revenue ÷ average daily headcount",average);
    }

    private static void financeRatio(List<Metric> out, Optional<Map<String,Object>> row, String key,
                                     String name,String unit,String formula,OptionalDouble denominator) {
        if (row.isEmpty()) out.add(missing(name,unit,formula,"An approved financial record must exactly match the selected period"));
        else if (denominator.isEmpty() || denominator.getAsDouble()==0)
            out.add(unavailableIfZero(name,unit,formula,denominator,"A positive covered denominator is required"));
        else out.add(metric(name,unit,number(row.get(),key)/denominator.getAsDouble(),formula,
                denominator.getAsDouble(),"Exact period and a single currency"));
    }

    private List<Map<String,Object>> dates(Actor actor, Period p, String stage) {
        return ranged(operations.scoped(actor,"""
                SELECT r.opened_on, s.effective_on,
                   (SELECT started.effective_on FROM application_stage_events started
                    WHERE started.org_id=a.org_id AND started.application_id=a.application_id AND started.sequence_no=1) AS applied_on
                FROM applications a JOIN requisitions r USING(org_id,requisition_id)
                JOIN application_stage_events s USING(org_id,application_id)
                WHERE a.org_id=:org AND s.stage=:stage AND s.effective_on BETWEEN :from AND :through
                ""","r.department_id","").param("stage",stage),p).query().listOfRows();
    }

    private void recruitmentMetrics(Actor actor, Period p, List<Metric> out) {
        var accepts = dates(actor,p,"ACCEPTED");
        out.add(median("Time to fill","days","Median calendar days from requisition opening to accepted offer",
                durations(accepts,"opened_on","effective_on")));
        out.add(median("Time to hire","days","Median calendar days from application to accepted offer",
                durations(accepts,"applied_on","effective_on")));
        var legacy = ranged(operations.scoped(actor,"""
                SELECT applied_on,offered_on FROM legacy_recruiting WHERE org_id=:org
                AND offered_on BETWEEN :from AND :through AND applied_on IS NOT NULL
                ""","department_id",""),p).query().listOfRows();
        out.add(median("Legacy application to offer","days","Median calendar days from application to recorded offer",
                durations(legacy,"applied_on","offered_on")));
    }

    private List<Map<String,Object>> rejections(Actor actor, Period p) {
        var current = ranged(operations.scoped(actor,"""
                SELECT s.reason FROM application_stage_events s JOIN applications a USING(org_id,application_id)
                JOIN requisitions r USING(org_id,requisition_id)
                WHERE a.org_id=:org AND s.stage='REJECTED' AND s.effective_on BETWEEN :from AND :through
                ""","r.department_id",""),p).query(String.class).list();
        var legacy = ranged(operations.scoped(actor,"""
                SELECT reason FROM legacy_recruiting WHERE org_id=:org AND rejected_on BETWEEN :from AND :through
                ""","department_id",""),p).query(String.class).list();
        Map<String,Integer> counts = new TreeMap<>();
        java.util.stream.Stream.concat(current.stream(),legacy.stream()).forEach(reason -> counts.merge(reason,1,Integer::sum));
        return counts.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed())
                .<Map<String,Object>>map(e -> Map.of("reason",e.getKey(),"count",e.getValue())).toList();
    }

    private void surveyMetric(Actor actor,Period p,String kind,String name,Settings settings,List<Metric> out) {
        String formula = "Mean anonymous 1–5 scores for the complete survey versions answered in the period";
        if (settings==null) {
            out.add(missing(name,"/5",formula,"HR Admin must configure the minimum group size"));
            return;
        }
        var rows = ranged(operations.scoped(actor,"""
                SELECT SUM(a.score) AS score_sum,COUNT(*) AS n,
                  SUM(a.answered_on BETWEEN :from AND :through) AS in_period
                FROM survey_responses a JOIN surveys s USING(org_id,survey_id,version_no)
                WHERE s.org_id=:org AND s.kind=:kind
                ""","s.department_id"," GROUP BY a.survey_id,a.version_no HAVING in_period>0")
                .param("kind",kind),p).query().listOfRows();
        if (rows.isEmpty()) {
            out.add(missing(name,"/5",formula,"No responses in the selected period"));
            return;
        }
        if (rows.stream().anyMatch(r -> number(r,"n")<settings.surveyMinimum() || number(r,"n")!=number(r,"in_period"))) {
            out.add(new Metric(name,"/5",null,"SUPPRESSED",formula,null,"A group is small or the period splits a survey version"));
            return;
        }
        int n = rows.stream().mapToInt(r -> (int)number(r,"n")).sum();
        double sum = rows.stream().mapToDouble(r -> number(r,"score_sum")).sum();
        out.add(metric(name,"/5",sum/n,formula,n,"Complete survey versions only"));
    }

    private void quality(Actor actor,Period p,Settings settings,List<Metric> out) {
        String formula = "Mean weighted performance, manager score and retention, using each hire's latest period outcome";
        var rows = ranged(operations.scoped(actor,"""
                SELECT x.performance,x.manager_score,x.retained FROM post_hire_outcomes x
                JOIN applications a USING(org_id,application_id) JOIN requisitions r USING(org_id,requisition_id)
                WHERE x.org_id=:org AND x.measured_on BETWEEN :from AND :through
                AND x.measured_on=(SELECT MAX(y.measured_on) FROM post_hire_outcomes y
                  WHERE y.org_id=x.org_id AND y.application_id=x.application_id AND y.measured_on BETWEEN :from AND :through)
                ""","r.department_id",""),p).query().listOfRows();
        if (settings==null || rows.isEmpty()) out.add(missing("Quality of hire","/100",formula,"Weights and post-hire outcomes are required"));
        else {
            double weighted = rows.stream().mapToDouble(r ->
                    (settings.performanceWeight()*number(r,"performance")+settings.managerWeight()*number(r,"manager_score")
                            +(Boolean.TRUE.equals(r.get("retained")) ? 100*settings.retentionWeight() : 0))/100).average().orElseThrow();
            out.add(metric("Quality of hire","/100",weighted,formula,rows.size(),"Hires with measured outcomes"));
        }
    }

    private void attendanceMetric(Actor actor,Period p,List<Metric> out) {
        var row = ranged(operations.scoped(actor,"""
                SELECT COUNT(*) AS n,SUM(scheduled_hours) AS scheduled,SUM(unplanned_absence) AS absence
                FROM attendance_days WHERE org_id=:org AND work_date BETWEEN :from AND :through
                ""","department_id",""),p).query().singleRow();
        String formula = "Unplanned absent hours ÷ scheduled hours × 100 for recorded employee days";
        if (number(row,"n")==0) out.add(missing("Absenteeism","%",formula,"No scheduled days recorded in scope"));
        else if (number(row,"scheduled")==0)
            out.add(unavailableIfZero("Absenteeism","%",formula,OptionalDouble.of(0),"Recorded schedule has zero hours"));
        else out.add(metric("Absenteeism","%",100*number(row,"absence")/number(row,"scheduled"),formula,
                number(row,"scheduled"),(int)number(row,"n")+" recorded employee days"));
    }

    private void promotionMetric(Actor actor,LocalDate through,List<Employee> snapshot,List<Metric> out) {
        var rows = operations.scoped(actor,"""
                SELECT employee_id,MAX(effective_on) AS latest FROM promotions
                WHERE org_id=:org AND effective_on<=:date
                ""","department_id"," GROUP BY employee_id").param("date",through).query().listOfRows();
        Set<String> active = new HashSet<>();
        snapshot.stream().filter(e -> e.status().employed()).forEach(e -> active.add(e.key().id()));
        var days = rows.stream().filter(r -> active.contains(r.get("employee_id")))
                .map(r -> ChronoUnit.DAYS.between(sqlDate(r.get("latest")),through)).toList();
        Metric m = median("Time since last promotion","days","Median calendar days since the last visible promotion",days);
        out.add(new Metric(m.name(),m.unit(),m.value(),m.status(),m.formula(),m.denominator(),
                days.size()+" of "+active.size()+" active employees have recorded promotions in scope"));
    }

    private static boolean covers(Scope scope,Actor actor,EmploymentHistory h,Period p) {
        var departments = scope.peopleDepartments(actor);
        return !departments.isEmpty() && departments.stream().allMatch(id -> {
            var c = h.coverage().get(new Scope.Key(actor.orgId(),id));
            return c!=null && c.contains(p.from()) && c.contains(p.through());
        });
    }
    static Period period(LocalDate from,LocalDate through) { return new Period(occurred(from),occurred(through)); }
    private static JdbcClient.StatementSpec ranged(JdbcClient.StatementSpec stmt,Period p) {
        return stmt.param("from",p.from()).param("through",p.through());
    }
    private static double number(Map<String,Object> row,String field) { return ((Number)row.get(field)).doubleValue(); }
    private static List<Long> durations(List<Map<String,Object>> rows,String start,String end) {
        return rows.stream().map(r -> ChronoUnit.DAYS.between(sqlDate(r.get(start)),sqlDate(r.get(end)))).toList();
    }
    private static Metric median(String name,String unit,String formula,List<Long> values) {
        if (values.isEmpty()) return missing(name,unit,formula,"No usable dated records in scope");
        var sorted = values.stream().sorted().toList();
        int n=sorted.size();
        return metric(name,unit,(sorted.get((n-1)/2)+sorted.get(n/2))/2.0,formula,n,"Records with known dates");
    }
    private static Metric unavailableIfZero(String name,String unit,String formula,OptionalDouble denominator,String detail) {
        return denominator.isPresent() ? new Metric(name,unit,null,"ZERO_DENOMINATOR",formula,0,detail)
                : missing(name,unit,formula,detail);
    }
    private static Metric missing(String name,String unit,String formula,String detail) {
        return new Metric(name,unit,null,"MISSING_INPUTS",formula,null,detail);
    }
    private static Metric metric(String name,String unit,double value,String formula,Number denominator,String detail) {
        return new Metric(name,unit,value,"AVAILABLE",formula,denominator,detail);
    }
}
