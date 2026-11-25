package backend;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import model.Candidate;
import model.Employee;
import model.Scope;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DirectoryRepository {
    public record Pay(String employeeId, String departmentId, LocalDate effectiveOn, BigDecimal amount,
                      String currency, String basis, BigDecimal fte) {}
    public record CandidateRow(String departmentId, Candidate candidate) {}

    private final JdbcClient jdbc;

    public DirectoryRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public List<Scope.Department> departments(String org) {
        return jdbc.sql("SELECT * FROM departments WHERE org_id = :org").param("org", org)
                .query((rs, row) -> new Scope.Department(org, rs.getString("department_id"), rs.getString("name")))
                .list();
    }

    public List<Employee> employees(Scope.Actor actor) {
        return scoped("""
                SELECT emp.* FROM employees emp
                WHERE emp.org_id = :org
                """, "emp.department_id", actor, " ORDER BY emp.employee_id")
                .query((rs, row) -> new Employee(new Scope.Key(rs.getString("org_id"), rs.getString("employee_id")),
                        rs.getString("department_id"), rs.getString("full_name"), null, rs.getString("job"), null,
                        rs.getDate("joined_on").toLocalDate(), Employee.Status.valueOf(rs.getString("status")), null))
                .list();
    }

    public List<Pay> compensation(Scope.Actor actor) {
        return scoped("""
                SELECT pay.employee_id, pay.department_id, pay.effective_on, pay.amount,
                       pay.currency, pay.pay_basis, pay.fte
                FROM compensation_history pay
                WHERE pay.org_id = :org
                """, "pay.department_id", actor, " ORDER BY pay.employee_id, pay.effective_on")
                .query((rs, row) -> new Pay(rs.getString("employee_id"), rs.getString("department_id"),
                        rs.getDate("effective_on").toLocalDate(), rs.getBigDecimal("amount"), rs.getString("currency"),
                        rs.getString("pay_basis"), rs.getBigDecimal("fte"))).list();
    }

    public List<CandidateRow> candidates(Scope.Actor actor) {
        return scoped("SELECT * FROM candidates WHERE org_id = :org", "department_id", actor,
                " ORDER BY department_id, candidate_id")
                .query((rs, row) -> new CandidateRow(rs.getString("department_id"),
                        new Candidate(new Scope.Key(rs.getString("org_id"), rs.getString("candidate_id")),
                                rs.getString("full_name")))).list();
    }

    public void checkDatabase() {
        jdbc.sql("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '1' AND success = TRUE
                """).query(Integer.class).optional().filter(count -> count == 1)
                .orElseThrow(() -> new IllegalStateException("Foundation migration is unavailable"));
    }

    private JdbcClient.StatementSpec scoped(String sql, String departmentColumn, Scope.Actor actor, String order) {
        if (actor.role() == Scope.Role.MANAGER) {
            return jdbc.sql(sql + " AND " + departmentColumn + " = :department" + order)
                    .param("org", actor.orgId()).param("department", actor.departmentId());
        }
        return jdbc.sql(sql + order).param("org", actor.orgId());
    }
}
