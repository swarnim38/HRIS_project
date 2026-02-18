import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class Employee {
    private  String id;
    private String fullName;
    private String gender;
    private String department;
    private String role;
    private LocalDate joiningDate;
    private Double basicSalary;
    private String Status;

    public Employee(String id, String   fullName, String gender, String department, String role, LocalDate joiningDate, Double basicSalary, String status) {
        this.id = id;
        this.fullName = fullName;
        this.gender = gender;
        this.department = department;
        this.role = role;
        this.joiningDate = joiningDate;
        this.basicSalary = basicSalary;
        this.Status = status;    
}

public static Employee fromCSV(String  csvLine) {
    String[] parts = csvLine.split(",");

    String id = parts[0];
    String fullName = parts[1]; 
    String gender = parts[2];
    String department = parts[3];
    String role = parts[4];
    LocalDate joiningDate = LocalDate.parse(parts[5]);
    Double Salary = Double.parseDouble(parts[6]);
    String status = parts[7];

    return new Employee(id, fullName, gender, department, role, joiningDate, Salary, status);
}

public long getTenureYears() {
    return ChronoUnit.YEARS.between(joiningDate, LocalDate.now());
}

public double getAnnualCTC() {
    return (this.basicSalary * 12) * 1.20;
}

public String getId() {
    return id;}
public String getFullName() {
    return fullName;}
public String getGender() {
    return gender;}
public String getDepartment() {
    return department;}
public String getRole() {       
    return role;}
public LocalDate getJoiningDate() {
    return joiningDate;}
public Double getBasicSalary() {
    return basicSalary;}
public String getStatus() {
    return Status;}

@Override
public String toString() {
    return String.format("ID: %s | Name: %S | Dept: %s | Tenure: %d Years", id, fullName, department, getTenureYears());
}
}
