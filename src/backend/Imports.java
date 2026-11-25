package backend;

import io.Csv;
import io.EmployeeImport;
import model.Employee;
import model.Scope;
import model.Scope.Actor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

import static backend.Operations.*;

@Service
public class Imports {
    public enum Kind { EMPLOYEES, CANDIDATES, REJECTIONS }
    public record Input(Kind kind, String csv, String departmentId, String currency, BigDecimal fte,
                        LocalDate snapshot, String expectedHash) {}
    public record Preview(String hash, int rows, Map<String,BigDecimal> payTotals,
                          List<EmployeeImport.Problem> errors, boolean committed) {}
    private record Legacy(String id, String name, LocalDate applied, LocalDate offered, LocalDate rejected,
                          String reason, String hash) {}
    private record Batch(Preview preview,List<Employee> employees,List<Legacy> recruiting) {}

    private final JdbcClient db;
    private final Operations operations;
    private final LocalIdentity identity;

    public Imports(JdbcClient db,Operations operations,LocalIdentity identity) {
        this.db = db;
        this.operations = operations;
        this.identity = identity;
    }

    public Preview preview(Actor actor, Input input) {
        admin(actor);
        return inspect(actor,input).preview();
    }

    @Transactional
    public Preview commit(Actor actor, Input input) {
        admin(actor);
        db.sql("SELECT org_id FROM organizations WHERE org_id=? FOR UPDATE").param(actor.orgId()).query(String.class).single();
        var batch = inspect(actor,input);
        Preview preview = batch.preview();
        if (!preview.hash().equals(input.expectedHash())) throw new IllegalArgumentException("Preview the current file and mapping before committing");
        if (preview.committed() || !preview.errors().isEmpty()) return preview;
        for (Employee employee : batch.employees()) {
            Employee.Status original = employee.status();
            if (original==Employee.Status.EXITED || !employee.joiningDate().equals(input.snapshot())) {
                db.sql("""
                        INSERT INTO employees(org_id,department_id,employee_id,full_name,job,joined_on,status,job_level,demographic)
                        VALUES (?,?,?,?,?,?,?,?,?)
                        """).params(actor.orgId(),employee.departmentId(),employee.key().id(),employee.fullName(),
                        employee.job(),employee.joiningDate(),original.name(),employee.level(),employee.demographic()).update();
                operations.event(actor,employee.key().id(),employee.departmentId(),input.snapshot(),"SNAPSHOT",original,
                        employee.job(),employee.level());
                operations.invalidateHistory(actor,employee.joiningDate());
            } else {
                operations.insertEmployee(actor,new Operations.EmployeeInput(employee.key().id(),employee.departmentId(),
                        employee.fullName(),employee.job(),employee.level(),employee.demographic(),employee.joiningDate(),original));
            }
            var pay = employee.compensation();
            if (pay!=null) {
                db.sql("INSERT INTO compensation_history VALUES (?,?,?,?,?,?,?,?)")
                        .params(actor.orgId(),employee.key().id(),employee.departmentId(),input.snapshot(),pay.amount(),
                                mappedCurrency(input,pay),pay.basis().name(),mappedFte(input,pay)).update();
            }
        }
        for (Legacy row : batch.recruiting()) {
            if (row.name()!=null) {
                db.sql("""
                        INSERT INTO candidates(org_id,department_id,candidate_id,full_name) VALUES (?,?,?,?)
                        ON DUPLICATE KEY UPDATE candidate_id=candidate_id
                        """).params(actor.orgId(),input.departmentId(),row.id(),row.name()).update();
            }
            db.sql("INSERT INTO legacy_recruiting VALUES (?,?,?,?,?,?,?,?)")
                    .params(actor.orgId(),input.departmentId(),row.hash(),row.id(),row.applied(),row.offered(),
                            row.rejected(),row.reason()).update();
        }
        db.sql("INSERT INTO import_receipts VALUES (?,?,?,?)")
                .params(actor.orgId(),preview.hash(),preview.rows(),encodeTotals(preview.payTotals())).update();
        identity.audit(actor,"CSV_"+input.kind().name()+"_IMPORTED",preview.hash());
        return new Preview(preview.hash(),preview.rows(),preview.payTotals(),List.of(),true);
    }

    private Batch inspect(Actor actor, Input input) {
        if (input.kind()==null || input.csv()==null || input.csv().isBlank() || input.csv().length()>1_000_000)
            throw new IllegalArgumentException("Choose a CSV file up to one million characters");
        String fingerprint = hash(String.join("\u0000","v1",input.kind().name(),String.valueOf(input.departmentId()),
                String.valueOf(input.currency()),String.valueOf(input.fte()),String.valueOf(input.snapshot()),input.csv()));
        var duplicate = db.sql("SELECT row_count,amounts FROM import_receipts WHERE org_id=? AND source_hash=?")
                .params(actor.orgId(),fingerprint).query().listOfRows().stream().findFirst();
        if (duplicate.isPresent()) {
            var row = duplicate.get();
            return new Batch(new Preview(fingerprint,((Number)row.get("row_count")).intValue(),
                    decodeTotals(row.get("amounts").toString()),List.of(),true),List.of(),List.of());
        }
        var departments = operations.departments(actor);
        Scope scope = new Scope(List.of(new Scope.Organization(actor.orgId())),departments);
        if (input.kind()==Kind.EMPLOYEES) return employeeBatch(actor,input,scope,fingerprint);
        scope.requireDepartment(actor.orgId(),field(input.departmentId(),"Department",64));
        return legacyBatch(actor,input,fingerprint);
    }

    private Batch employeeBatch(Actor actor,Input input,Scope scope,String fingerprint) {
        LocalDate snapshot = occurred(input.snapshot());
        var batch = new EmployeeImport().validate(input.csv(),scope,actor.orgId(),snapshot);
        var problems = new ArrayList<>(batch.preview().problems());
        if (batch.preview().validRows()==0 && problems.isEmpty())
            return new Batch(new Preview(fingerprint,0,Map.of(),List.of(new EmployeeImport.Problem(1,"The file has no employee rows")),
                    false),List.of(),List.of());
        var totals = new TreeMap<String,BigDecimal>();
        if (problems.isEmpty()) {
            var sourceRows = Csv.parse(input.csv());
            for (int i=0;i<batch.employees().size();i++) {
                Employee e = batch.employees().get(i);
                try {
                    field(e.key().id(),"Employee number",64);
                    field(e.fullName(),"Full name",200);
                    field(e.job(),"Job title",160);
                    optional(e.level(),64);
                    optional(e.demographic(),64);
                    var used = db.sql("SELECT COUNT(*) FROM employees WHERE org_id=? AND employee_id=?")
                            .params(actor.orgId(),e.key().id()).query(Integer.class).single();
                    if (used>0) throw new IllegalArgumentException("Employee number already exists");
                    var pay = e.compensation();
                    if (pay!=null) {
                        if (pay.basis()==null) throw new IllegalArgumentException("Provide the pay basis in the scoped CSV");
                        mappedFte(input,pay);
                        totals.merge(mappedCurrency(input,pay)+"/"+pay.basis().name(),money(pay.amount()),BigDecimal::add);
                    }
                } catch(IllegalArgumentException problem) {
                    problems.add(new EmployeeImport.Problem(sourceRows.get(i+1).line(),problem.getMessage()));
                }
            }
        }
        return new Batch(new Preview(fingerprint,batch.preview().validRows()-Math.max(0,problems.size()-batch.preview().problems().size()),
                totals,problems,false),batch.employees(),List.of());
    }

    private Batch legacyBatch(Actor actor,Input input,String fingerprint) {
        var rows = Csv.parse(input.csv());
        var problems = new ArrayList<EmployeeImport.Problem>();
        var records = new ArrayList<Legacy>();
        var seen = new HashSet<String>();
        boolean candidate = input.kind()==Kind.CANDIDATES;
        for (var row : rows) {
            try {
                if (row.error()!=null) throw new IllegalArgumentException(row.error());
                var values = row.cells();
                if (values.size()!=(candidate?6:3)) throw new IllegalArgumentException("Unexpected column count for the selected legacy layout");
                String id = field(values.getFirst(),"Candidate code",64);
                LocalDate applied = candidate?parse(values.get(3)):null;
                String offer = candidate?values.get(4).strip():"";
                LocalDate offered = offer.isEmpty() || offer.equalsIgnoreCase("PENDING")?null:parse(offer);
                if (offered!=null && offered.isBefore(applied)) throw new IllegalArgumentException("Offer predates application");
                if (candidate) {
                    field(values.get(2),"Job title",160);
                    String status = field(values.get(5),"Legacy status",24).toUpperCase(Locale.ROOT);
                    if (!List.of("PENDING","APPLIED","ACCEPTED","REJECTED","DECLINED","OFFERED","EXTENDED","HIRED").contains(status))
                        throw new IllegalArgumentException("Legacy status is not recognized");
                }
                String name = candidate?field(values.get(1),"Candidate name",200):null;
                String reason = candidate?null:field(values.get(1),"Rejection reason",160);
                LocalDate rejected = candidate?null:parse(values.get(2));
                String recordHash = hash(input.kind()+"|"+input.departmentId()+"|"+id+"|"+applied+"|"+offered+"|"+rejected+"|"+reason);
                if (!seen.add(candidate?id.toLowerCase(Locale.ROOT):recordHash))
                    throw new IllegalArgumentException("Duplicate row in this file");
                var existing = db.sql("SELECT full_name,department_id FROM candidates WHERE org_id=? AND candidate_id=?")
                        .params(actor.orgId(),id).query().listOfRows().stream().findFirst();
                if (existing.isEmpty() && !candidate) throw new IllegalArgumentException("Import the matching candidate first");
                if (existing.isPresent() && (!input.departmentId().equals(existing.get().get("department_id"))
                        || (candidate && !name.equals(existing.get().get("full_name")))))
                    throw new IllegalArgumentException("Candidate code already belongs to a different name or department");
                if (db.sql("SELECT COUNT(*) FROM legacy_recruiting WHERE org_id=? AND record_hash=?")
                        .params(actor.orgId(),recordHash).query(Integer.class).single()>0)
                    throw new IllegalArgumentException("This legacy record has already been imported");
                records.add(new Legacy(id,name,applied,offered,rejected,reason,recordHash));
            } catch(IllegalArgumentException problem) {
                problems.add(new EmployeeImport.Problem(row.line(),problem.getMessage()));
            }
        }
        if (rows.isEmpty()) problems.add(new EmployeeImport.Problem(1,"The file has no rows"));
        return new Batch(new Preview(fingerprint,records.size(),Map.of(),problems,false),List.of(),records);
    }

    private static LocalDate parse(String value) {
        try { return occurred(LocalDate.parse(value.strip())); }
        catch(java.time.format.DateTimeParseException e) { throw new IllegalArgumentException("Date must be in YYYY-MM-DD format"); }
    }
    private static String mappedCurrency(Input input,Employee.Compensation pay) {
        return pay.currency()==null ? currency(input.currency()) : pay.currency().getCurrencyCode();
    }
    private static BigDecimal mappedFte(Input input,Employee.Compensation pay) {
        BigDecimal fte=pay.fte()==null ? input.fte() : pay.fte();
        if (fte==null || fte.signum()<=0 || fte.compareTo(BigDecimal.ONE)>0 || fte.stripTrailingZeros().scale()>4)
            throw new IllegalArgumentException("Provide FTE above zero and up to one");
        return fte;
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String encodeTotals(Map<String,BigDecimal> totals) {
        return totals.entrySet().stream().map(e -> e.getKey()+"="+e.getValue().toPlainString())
                .collect(java.util.stream.Collectors.joining(","));
    }
    private static Map<String,BigDecimal> decodeTotals(String value) {
        var totals = new TreeMap<String,BigDecimal>();
        if (!value.isEmpty()) for (String pair : value.split(",")) {
            String[] fields = pair.split("=",2);
            totals.put(fields[0],new BigDecimal(fields[1]));
        }
        return totals;
    }
}
