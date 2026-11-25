package backend;

import jakarta.servlet.http.HttpServletRequest;
import model.Scope.Actor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
public class ApiController {
    private final LocalIdentity identity;
    private final Operations operations;
    private final DirectoryService directory;
    private final Metrics metrics;
    private final Imports imports;

    public record DepartmentInput(String id,String name) {}

    public ApiController(LocalIdentity identity,Operations operations,DirectoryService directory,
                         Metrics metrics,Imports imports) {
        this.identity=identity;
        this.operations=operations;
        this.directory=directory;
        this.metrics=metrics;
        this.imports=imports;
    }

    @GetMapping("/employees")
    public Object employees(HttpServletRequest r,@RequestParam(defaultValue="") String search,
                            @RequestParam(defaultValue="") String department) {
        return operations.employees(actor(r),search,department);
    }

    @PostMapping("/employees")
    public void employee(HttpServletRequest r,@RequestBody Operations.EmployeeInput body) {
        operations.createEmployee(actor(r),body);
    }

    @PutMapping("/employees/{id}")
    public void employee(HttpServletRequest r,@PathVariable String id,@RequestBody Operations.EmployeeInput body) {
        operations.updateEmployee(actor(r),id,body);
    }

    @GetMapping("/employees/{id}/history")
    public Object history(HttpServletRequest r,@PathVariable String id) {
        return operations.history(actor(r),id);
    }

    @PostMapping("/employees/{id}/history")
    public void initializeHistory(HttpServletRequest r,@PathVariable String id,@RequestBody Operations.Snapshot body) {
        operations.initializeHistory(actor(r),id,body);
    }

    @GetMapping("/departments")
    public Object departments(HttpServletRequest r) { return operations.departments(actor(r)); }

    @PostMapping("/departments")
    public void department(HttpServletRequest r,@RequestBody DepartmentInput body) {
        operations.addDepartment(actor(r),body.id(),body.name());
    }

    @GetMapping("/compensation")
    public Object compensation(HttpServletRequest r) { return directory.compensation(actor(r)); }

    @PostMapping("/compensation")
    public void compensation(HttpServletRequest r,@RequestBody Operations.PayInput body) {
        operations.recordPay(actor(r),body);
    }

    @GetMapping("/promotions")
    public Object promotions(HttpServletRequest r) { return operations.promotions(actor(r)); }

    @PostMapping("/promotions")
    public void promote(HttpServletRequest r,@RequestBody Operations.Promotion body) { operations.promote(actor(r),body); }

    @GetMapping("/candidates")
    public Object candidates(HttpServletRequest r) {
        return directory.candidates(actor(r));
    }

    @GetMapping("/requisitions")
    public Object requisitions(HttpServletRequest r) { return operations.requisitions(actor(r)); }

    @PostMapping("/requisitions")
    public void open(HttpServletRequest r,@RequestBody Operations.Requisition body) { operations.open(actor(r),body); }

    @GetMapping("/applications")
    public Object applications(HttpServletRequest r) { return operations.applications(actor(r)); }

    @PostMapping("/applications")
    public void apply(HttpServletRequest r,@RequestBody Operations.Application body) { operations.apply(actor(r),body); }

    @PostMapping("/applications/{id}/stage")
    public void stage(HttpServletRequest r,@PathVariable String id,@RequestBody Operations.Transition body) {
        operations.transition(actor(r),id,body);
    }

    @GetMapping("/dashboard")
    public Object dashboard(HttpServletRequest r,@RequestParam LocalDate from,@RequestParam LocalDate through) {
        return metrics.dashboard(actor(r),from,through);
    }

    @GetMapping("/headcount")
    public Object headcount(HttpServletRequest r) {
        Actor who=actor(r);
        Operations.people(who);
        var day=LocalDate.now();
        return metrics.dashboard(who,day,day).departments();
    }

    @GetMapping("/finance")
    public Object finance(HttpServletRequest r) { return metrics.financePeriods(actor(r)); }

    @PostMapping("/finance")
    public void finance(HttpServletRequest r,@RequestBody Metrics.FinanceInput body) { metrics.finance(actor(r),body); }

    @PostMapping("/attendance")
    public void attendance(HttpServletRequest r,@RequestBody Metrics.Attendance body) { metrics.attendance(actor(r),body); }

    @GetMapping("/surveys")
    public Object surveys(HttpServletRequest r) { return metrics.surveys(actor(r)); }

    @PostMapping("/surveys")
    public void survey(HttpServletRequest r,@RequestBody Metrics.Survey body) { metrics.survey(actor(r),body); }

    @PostMapping("/survey-responses")
    public void responses(HttpServletRequest r,@RequestBody Metrics.Response body) { metrics.responses(actor(r),body); }

    @PostMapping("/outcomes")
    public void outcome(HttpServletRequest r,@RequestBody Metrics.Outcome body) { metrics.outcome(actor(r),body); }

    @GetMapping("/metric-settings")
    public Object settings(HttpServletRequest r) { return metrics.settings(actor(r)); }

    @PutMapping("/metric-settings")
    public void settings(HttpServletRequest r,@RequestBody Metrics.Settings body) { metrics.settings(actor(r),body); }

    @PutMapping("/history-coverage")
    public void coverage(HttpServletRequest r,@RequestBody Metrics.Coverage body) { metrics.coverage(actor(r),body); }

    @PostMapping("/imports/preview")
    public Object preview(HttpServletRequest r,@RequestBody Imports.Input body) { return imports.preview(actor(r),body); }

    @PostMapping("/imports/commit")
    public Object commit(HttpServletRequest r,@RequestBody Imports.Input body) { return imports.commit(actor(r),body); }

    @GetMapping("/audit")
    public Object audit(HttpServletRequest r) { return operations.audit(actor(r)); }

    private Actor actor(HttpServletRequest r) { return identity.require(r); }
}
