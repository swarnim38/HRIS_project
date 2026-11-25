package backend;

import model.Employee.Status;
import model.Recruitment.Stage;
import model.Scope;
import model.Scope.Actor;
import model.Scope.Role;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;

@Service
public class Operations {
    public record EmployeeInput(String id, String departmentId, String name, String job, String level,
                                String demographic, LocalDate effectiveOn, Status status,
                                String workLocation, String employmentType) {
        public EmployeeInput(String id, String departmentId, String name, String job, String level,
                             String demographic, LocalDate effectiveOn, Status status) {
            this(id, departmentId, name, job, level, demographic, effectiveOn, status, null, null);
        }
    }
    public record EmployeeRow(String id, String departmentId, String name, String job, String level,
                              String demographic, LocalDate joinedOn, Status status, String workLocation,
                              String employmentType) {}
    public record PayInput(String employeeId, LocalDate effectiveOn, BigDecimal amount, String currency,
                           String basis, BigDecimal fte) {}
    public record Promotion(String employeeId, LocalDate effectiveOn, String level) {}
    public record Snapshot(LocalDate effectiveOn) {}
    public record Requisition(String id, String departmentId, String job, LocalDate openedOn, String responsible) {}
    public record Application(String id, String requisitionId, String candidateId, String candidateName,
                              LocalDate appliedOn) {}
    public record Transition(Stage stage, LocalDate date, String reason, String employeeId, String jobLevel) {}

    final JdbcClient db;
    private final LocalIdentity identity;
    private final DirectoryRepository directory;

    public Operations(JdbcClient db, LocalIdentity identity, DirectoryRepository directory) {
        this.db = db;
        this.identity = identity;
        this.directory = directory;
    }

    public List<Scope.Department> departments(Actor actor) {
        people(actor);
        return directory.departments(actor.orgId()).stream().filter(d -> visible(actor, d.id())).toList();
    }

    @Transactional
    public void addDepartment(Actor actor, String id, String name) {
        admin(actor);
        db.sql("INSERT INTO departments (org_id,department_id,name) VALUES (?,?,?)")
                .params(actor.orgId(), field(id, "Department code", 64), field(name, "Name", 160)).update();
        identity.audit(actor, "DEPARTMENT_CREATED", id);
    }

    public List<EmployeeRow> employees(Actor actor, String search, String department) {
        people(actor);
        var stmt = scoped(actor, """
                SELECT employee_id,department_id,full_name,job,job_level,demographic,joined_on,status,work_location,employment_type
                FROM employees WHERE org_id=:org
                AND (:dept='' OR department_id=:dept)
                AND (LOCATE(:q,full_name)>0 OR LOCATE(:q,employee_id)>0 OR LOCATE(:q,job)>0 OR :q='')
                """, "department_id", " ORDER BY full_name,employee_id");
        return stmt.param("dept", department).param("q", search).query(Operations::employeeRow).list();
    }

    public List<Map<String, Object>> history(Actor actor, String id) {
        people(actor);
        return scoped(actor, """
                SELECT effective_on,kind,status,department_id FROM employment_events
                WHERE org_id=:org AND employee_id=:id
                """, "department_id", " ORDER BY effective_on").param("id", id).query().listOfRows();
    }

    @Transactional
    public void initializeHistory(Actor actor, String id, Snapshot input) {
        admin(actor);
        EmployeeRow employee = lockEmployee(actor, id);
        LocalDate date = occurred(input.effectiveOn());
        if (date.isBefore(employee.joinedOn())) throw new IllegalArgumentException("Snapshot predates the joining date");
        int previous = db.sql("SELECT COUNT(*) FROM employment_events WHERE org_id=? AND employee_id=?")
                .params(actor.orgId(), id).query(Integer.class).single();
        if (previous != 0) throw new IllegalArgumentException("Employment history already exists");
        event(actor, id, employee.departmentId(), date, "SNAPSHOT", employee.status(), employee.job(), employee.level());
        invalidateHistory(actor, employee.joinedOn());
        identity.audit(actor, "EMPLOYMENT_SNAPSHOT_RECORDED", id);
    }

    @Transactional
    public void createEmployee(Actor actor, EmployeeInput input) {
        admin(actor);
        insertEmployee(actor, input);
        identity.audit(actor, "EMPLOYEE_CREATED", input.id());
    }

    void insertEmployee(Actor actor, EmployeeInput input) {
        LocalDate date = occurred(input.effectiveOn());
        if (input.status() == null || !input.status().employed()) throw new IllegalArgumentException("New employees must have an employed status");
        String id = field(input.id(), "Employee number", 64);
        String dept = field(input.departmentId(), "Department", 64);
        db.sql("""
                INSERT INTO employees(org_id,department_id,employee_id,full_name,job,joined_on,status,job_level,demographic,
                    work_location,employment_type)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """).params(actor.orgId(), dept, id, field(input.name(), "Full name", 200),
                field(input.job(), "Job title", 160), date, input.status().name(),
                optional(input.level(), 64), optional(input.demographic(), 64),
                optional(input.workLocation(), 160), optional(input.employmentType(), 64)).update();
        event(actor, id, dept, date, "HIRE", input.status(), input.job().strip(), optional(input.level(), 64));
    }

    @Transactional
    public void updateEmployee(Actor actor, String id, EmployeeInput input) {
        admin(actor);
        EmployeeRow previous = lockEmployee(actor, id);
        if (input.status() == null) throw new IllegalArgumentException("Choose an employment status");
        String department = field(input.departmentId(), "Department", 64);
        String job = field(input.job(), "Job title", 160);
        String level = optional(input.level(), 64);
        boolean change = !department.equals(previous.departmentId()) || input.status() != previous.status();
        boolean jobChanged = !job.equals(previous.job()) || !java.util.Objects.equals(level, previous.level());
        if (change || jobChanged) {
            LocalDate date = occurred(input.effectiveOn());
            LocalDate latest = db.sql("SELECT MAX(effective_on) FROM employment_events WHERE org_id=? AND employee_id=?")
                    .params(actor.orgId(), id).query(LocalDate.class).optional().orElse(null);
            if (latest == null || !date.isAfter(latest)) throw new IllegalArgumentException(
                    "Employment changes require recorded history and a date after the previous event");
            if (!input.status().employed() && !department.equals(previous.departmentId())) {
                throw new IllegalArgumentException("Record a transfer and an exit separately");
            }
            String kind = !input.status().employed() ? "SEPARATION" : !previous.status().employed() ? "REHIRE" : "CHANGE";
            if (!change && !input.status().employed()) throw new IllegalArgumentException("Rehire the employee before changing job terms");
            event(actor, id, department, date, kind, input.status(), job, level);
        }
        db.sql("""
                UPDATE employees SET full_name=?,job=?,job_level=?,demographic=?,department_id=?,status=?,
                    work_location=?,employment_type=?
                WHERE org_id=? AND employee_id=?
                """).params(field(input.name(), "Full name", 200), job,
                level, optional(input.demographic(), 64), department,
                input.status().name(), optional(input.workLocation(), 160), optional(input.employmentType(), 64),
                actor.orgId(), id).update();
        identity.audit(actor, change || jobChanged ? "EMPLOYMENT_CHANGED" : "PROFILE_EDITED", id);
    }

    void event(Actor actor, String id, String dept, LocalDate date, String kind, Status status, String job, String level) {
        db.sql("INSERT INTO employment_events VALUES (?,?,?,?,?,?,?,?)")
                .params(actor.orgId(), id, dept, date, kind, status.name(), job, level).update();
    }

    void invalidateHistory(Actor actor, LocalDate uncertainFrom) {
        LocalDate lastCertain = uncertainFrom.minusDays(1);
        db.sql("DELETE FROM employment_coverage WHERE org_id=? AND from_date>?")
                .params(actor.orgId(), lastCertain).update();
        db.sql("UPDATE employment_coverage SET through_date=? WHERE org_id=? AND through_date>?")
                .params(lastCertain, actor.orgId(), lastCertain).update();
    }

    EmployeeRow lockEmployee(Actor actor, String id) {
        return db.sql("SELECT * FROM employees WHERE org_id=? AND employee_id=? FOR UPDATE")
                .params(actor.orgId(), id).query(Operations::employeeRow).optional()
                .orElseThrow(() -> new IllegalArgumentException("Employee was not found"));
    }

    String employedDepartment(Actor actor, String id, LocalDate date) {
        return db.sql("""
                SELECT department_id FROM employment_events WHERE org_id=? AND employee_id=? AND effective_on<=?
                AND effective_on=(SELECT MAX(effective_on) FROM employment_events
                    WHERE org_id=? AND employee_id=? AND effective_on<=?) AND status<>'EXITED'
                """).params(actor.orgId(), id, date, actor.orgId(), id, date).query(String.class).optional()
                .orElseThrow(() -> new IllegalArgumentException("No employed department is recorded on that date"));
    }

    @Transactional
    public void recordPay(Actor actor, PayInput body) {
        admin(actor);
        lockEmployee(actor, body.employeeId());
        LocalDate date = occurred(body.effectiveOn());
        String department = employedDepartment(actor, body.employeeId(), date);
        var basis = model.Employee.PayBasis.valueOf(field(body.basis(), "Pay basis", 12));
        if (body.fte() == null || body.fte().signum() <= 0 || body.fte().compareTo(BigDecimal.ONE) > 0
                || body.fte().stripTrailingZeros().scale() > 4) throw new IllegalArgumentException("Enter FTE above zero and up to one");
        db.sql("INSERT INTO compensation_history VALUES (?,?,?,?,?,?,?,?)")
                .params(actor.orgId(), body.employeeId(), department, date, money(body.amount()), currency(body.currency()),
                        basis.name(), body.fte()).update();
        identity.audit(actor, "COMPENSATION_RECORDED", body.employeeId());
    }

    @Transactional
    public void promote(Actor actor, Promotion body) {
        admin(actor);
        EmployeeRow employee = lockEmployee(actor, body.employeeId());
        LocalDate date = occurred(body.effectiveOn());
        String department = employedDepartment(actor, employee.id(), date);
        LocalDate lastEmployment = db.sql("SELECT MAX(effective_on) FROM employment_events WHERE org_id=? AND employee_id=?")
                .params(actor.orgId(), employee.id()).query(LocalDate.class).optional().orElseThrow();
        if (date.isBefore(lastEmployment)) throw new IllegalArgumentException("Promotion predates the latest employment event");
        LocalDate latest = db.sql("SELECT MAX(effective_on) FROM promotions WHERE org_id=? AND employee_id=?")
                .params(actor.orgId(), employee.id()).query(LocalDate.class).optional().orElse(null);
        if (latest != null && !date.isAfter(latest)) throw new IllegalArgumentException("Promotion date must follow the previous promotion");
        String level = field(body.level(), "New job level", 64);
        db.sql("INSERT INTO promotions VALUES (?,?,?,?,?,?)")
                .params(actor.orgId(), employee.id(), department, date, employee.level(), level).update();
        db.sql("UPDATE employees SET job_level=? WHERE org_id=? AND employee_id=?")
                .params(level, actor.orgId(), employee.id()).update();
        identity.audit(actor, "PROMOTION_RECORDED", employee.id());
    }

    public List<Map<String, Object>> promotions(Actor actor) {
        people(actor);
        return scoped(actor, """
                SELECT employee_id,department_id,effective_on,prior_level,new_level FROM promotions WHERE org_id=:org
                """, "department_id", " ORDER BY effective_on DESC").query().listOfRows();
    }

    public List<Map<String, Object>> requisitions(Actor actor) {
        people(actor);
        return scoped(actor, """
                SELECT requisition_id,department_id,job,opened_on,responsible_username FROM requisitions WHERE org_id=:org
                """, "department_id", " ORDER BY opened_on DESC").query().listOfRows();
    }

    @Transactional
    public void open(Actor actor, Requisition body) {
        admin(actor);
        String id = field(body.id(), "Requisition code", 64);
        String dept = field(body.departmentId(), "Department", 64);
        String responsible = field(body.responsible(), "Responsible username", 64);
        int eligible = db.sql("""
                SELECT COUNT(*) FROM local_accounts WHERE org_id=? AND username=? AND active=true
                AND (role='HR_ADMIN' OR (role='MANAGER' AND department_id=?))
                """).params(actor.orgId(), responsible, dept).query(Integer.class).single();
        if (eligible != 1) throw new IllegalArgumentException("Assign an active HR admin or manager in this department");
        db.sql("INSERT INTO requisitions VALUES (?,?,?,?,?,?)")
                .params(actor.orgId(), id, dept, field(body.job(), "Job title", 160), occurred(body.openedOn()), responsible).update();
        identity.audit(actor, "REQUISITION_OPENED", id);
    }

    public List<Map<String, Object>> applications(Actor actor) {
        people(actor);
        return scoped(actor, """
                SELECT a.application_id,a.requisition_id,r.department_id,c.candidate_id,c.full_name,
                s.sequence_no,s.stage,s.effective_on,s.reason,h.employee_id
                FROM applications a JOIN requisitions r USING(org_id,requisition_id)
                JOIN candidates c ON c.org_id=a.org_id AND c.candidate_id=a.candidate_id
                JOIN application_stage_events s ON s.org_id=a.org_id AND s.application_id=a.application_id
                LEFT JOIN hires h ON h.org_id=a.org_id AND h.application_id=a.application_id
                WHERE a.org_id=:org
                """, "r.department_id", " ORDER BY a.application_id,s.sequence_no").query().listOfRows();
    }

    @Transactional
    public void apply(Actor actor, Application body) {
        admin(actor);
        String id = field(body.id(), "Application code", 64);
        String candidate = field(body.candidateId(), "Candidate code", 64);
        var req = db.sql("SELECT * FROM requisitions WHERE org_id=? AND requisition_id=?")
                .params(actor.orgId(), body.requisitionId()).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Requisition was not found"));
        LocalDate date = occurred(body.appliedOn());
        if (date.isBefore(sqlDate(req.get("opened_on")))) throw new IllegalArgumentException("Application precedes the requisition opening");
        String name = field(body.candidateName(), "Candidate name", 200);
        db.sql("""
                INSERT INTO candidates(org_id,department_id,candidate_id,full_name)
                VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE candidate_id=candidate_id
                """).params(actor.orgId(), req.get("department_id"), candidate, name).update();
        var existing = db.sql("SELECT full_name,department_id FROM candidates WHERE org_id=? AND candidate_id=?")
                .params(actor.orgId(), candidate).query().singleRow();
        if (!name.equals(existing.get("full_name")) || !req.get("department_id").equals(existing.get("department_id"))) {
            throw new IllegalArgumentException("Candidate code already belongs to a different name or department");
        }
        db.sql("INSERT INTO applications VALUES (?,?,?,?)")
                .params(actor.orgId(), id, body.requisitionId(), candidate).update();
        stageEvent(actor, id, 1, Stage.APPLIED, date, null);
        identity.audit(actor, "APPLICATION_CREATED", id);
    }

    @Transactional
    public void transition(Actor actor, String id, Transition body) {
        people(actor);
        var application = db.sql("""
                SELECT a.requisition_id,r.department_id,r.job,c.full_name FROM applications a
                JOIN requisitions r USING(org_id,requisition_id) JOIN candidates c ON c.org_id=a.org_id AND c.candidate_id=a.candidate_id
                WHERE a.org_id=? AND a.application_id=? FOR UPDATE
                """).params(actor.orgId(), id).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Application was not found"));
        String dept = application.get("department_id").toString();
        if (!visible(actor, dept)) throw new SecurityException("Application belongs to a different department");
        Stage stage = body.stage();
        if (stage == null || stage == Stage.APPLIED) throw new IllegalArgumentException("Choose a follow-up stage");
        if (actor.role() == Role.MANAGER && List.of(Stage.OFFERED,Stage.ACCEPTED,Stage.DECLINED,Stage.HIRED).contains(stage)) {
            throw new SecurityException("HR Admin records offer decisions and hires");
        }
        LocalDate date = occurred(body.date());
        var previous = db.sql("""
                SELECT stage,effective_on,sequence_no FROM application_stage_events
                WHERE org_id=? AND application_id=? ORDER BY sequence_no DESC LIMIT 1 FOR UPDATE
                """).params(actor.orgId(), id).query().singleRow();
        Stage prior = Stage.valueOf(previous.get("stage").toString());
        if (prior == Stage.HIRED && stage == Stage.HIRED) {
            String existing = db.sql("SELECT employee_id FROM hires WHERE org_id=? AND application_id=?")
                    .params(actor.orgId(), id).query(String.class).single();
            if (existing.equals(body.employeeId()) && date.equals(sqlDate(previous.get("effective_on")))) return;
            throw new IllegalArgumentException("The hire has already been recorded");
        }
        if (!model.Recruitment.Application.allows(prior, stage) || date.isBefore(sqlDate(previous.get("effective_on")))) {
            throw new IllegalArgumentException("Stage transition or date is invalid");
        }
        if (stage == Stage.HIRED) {
            String employeeId = field(body.employeeId(), "Employee number", 64);
            insertEmployee(actor, new EmployeeInput(employeeId, dept, application.get("full_name").toString(),
                    application.get("job").toString(), body.jobLevel(), null, date, Status.ACTIVE));
            db.sql("INSERT INTO hires VALUES (?,?,?,?)")
                    .params(actor.orgId(), id, application.get("requisition_id"), employeeId).update();
        }
        String reason = stage == Stage.REJECTED ? field(body.reason(), "Rejection reason", 160) : optional(body.reason(), 160);
        stageEvent(actor, id, ((Number)previous.get("sequence_no")).intValue()+1, stage, date, reason);
        identity.audit(actor, "APPLICATION_" + stage.name(), id);
    }

    private void stageEvent(Actor actor, String id, int sequence, Stage stage, LocalDate date, String reason) {
        db.sql("INSERT INTO application_stage_events VALUES (?,?,?,?,?,?)")
                .params(actor.orgId(), id, sequence, stage.name(), date, reason).update();
    }

    public List<Map<String, Object>> audit(Actor actor) {
        admin(actor);
        return db.sql("SELECT username,action,entity_id,occurred_at FROM audit_events WHERE org_id=? ORDER BY id DESC LIMIT 200")
                .param(actor.orgId()).query().listOfRows();
    }

    JdbcClient.StatementSpec scoped(Actor actor, String sql, String departmentColumn, String suffix) {
        if (actor.role() == Role.MANAGER) return db.sql(sql+" AND "+departmentColumn+"=:own"+suffix)
                .param("org", actor.orgId()).param("own", actor.departmentId());
        return db.sql(sql+suffix).param("org", actor.orgId());
    }

    static void admin(Actor actor) {
        if (actor.role()!=Role.HR_ADMIN) throw new SecurityException("HR Admin access is required");
    }
    static void people(Actor actor) {
        if (actor.role()==Role.FINANCE) throw new SecurityException("This record requires HR access");
    }
    static boolean visible(Actor actor, String department) {
        return actor.role()!=Role.MANAGER || actor.departmentId().equals(department);
    }
    static LocalDate occurred(LocalDate date) {
        if (date == null || date.isAfter(LocalDate.now()) || date.getYear()<1900)
            throw new IllegalArgumentException("Enter an effective date from 1900 through today");
        return date;
    }
    static LocalDate sqlDate(Object value) {
        return value instanceof java.sql.Date date ? date.toLocalDate() : (LocalDate)value;
    }
    static String field(String value, String name, int max) {
        if (value == null || value.isBlank() || value.strip().length()>max)
            throw new IllegalArgumentException(name+" is required and must be "+max+" characters or fewer");
        return value.strip();
    }
    static String optional(String value, int max) {
        return value == null || value.isBlank() ? null : field(value,"Value",max);
    }
    static BigDecimal money(BigDecimal amount) {
        if (amount==null || amount.signum()<0 || amount.stripTrailingZeros().scale()>2
                || amount.compareTo(new BigDecimal("10000000000000"))>=0)
            throw new IllegalArgumentException("Enter a nonnegative amount with up to two decimal places");
        return amount;
    }
    static String currency(String value) {
        return Currency.getInstance(field(value,"Currency",3)).getCurrencyCode();
    }
    private static EmployeeRow employeeRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new EmployeeRow(rs.getString("employee_id"),rs.getString("department_id"),rs.getString("full_name"),
                rs.getString("job"),rs.getString("job_level"),rs.getString("demographic"),
                rs.getDate("joined_on").toLocalDate(), Status.valueOf(rs.getString("status")),
                rs.getString("work_location"), rs.getString("employment_type"));
    }
}
