package service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import model.Candidate;
import model.Employee;

public class HRSystem {
    private List<Employee> employeeList = new ArrayList<>();
    private Scanner scanner;

    public HRSystem(Scanner scanner) {
        this.scanner = scanner;
    }

    public void loadData(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            System.err.println("[ERROR] File path cannot be null or empty.");
            return;
        }

        System.out.println("Reading data from: " + filePath);

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNumber = 1;

            br.readLine();

            while ((line = br.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    System.err.println("[WARN] Skipping blank line at row " + lineNumber);
                    continue;
                }

                try {
                    Employee emp = Employee.fromCSV(line);
                    if (emp == null) {
                        System.err.println("[WARN] Null employee returned for row " + lineNumber + ". Skipping.");
                        continue;
                    }
                    employeeList.add(emp);
                } catch (IllegalArgumentException e) {
                    System.err.println("[WARN] Malformed data at row " + lineNumber + ": " + e.getMessage() + ". Skipping record.");
                } catch (Exception e) {
                    System.err.println("[WARN] Unexpected error parsing row " + lineNumber + ": " + e.getMessage() + ". Skipping record.");
                }
            }

            System.out.println("Successfully loaded " + employeeList.size() + " employees.");

        } catch (FileNotFoundException e) {
            System.err.println("[ERROR] File not found: " + filePath + ". Please verify the path and try again.");
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to read file: " + e.getMessage());
        }
    }

    public void listAllEmployees() {
        System.out.println("\n--- EMPLOYEE DIRECTORY ---");

        if (employeeList == null || employeeList.isEmpty()) {
            System.out.println("[INFO] No employee records found. Please load data first.");
            return;
        }

        System.out.printf("%-10s | %-20s | %-15s | %-10s%n", "ID", "Name", "Department", "Tenure");
        System.out.println("------------------------------------------------------------------");

        for (Employee emp : employeeList) {
            try {
                if (emp == null) {
                    System.err.println("[WARN] Encountered a null employee entry. Skipping.");
                    continue;
                }
                System.out.printf("%-10s | %-20s | %-15s | %d Years%n",
                        nullSafe(emp.getId()),
                        nullSafe(emp.getFullName()),
                        nullSafe(emp.getDepartment()),
                        emp.getTenureYears());
            } catch (Exception e) {
                System.err.println("[WARN] Error displaying employee record: " + e.getMessage() + ". Skipping.");
            }
        }
    }

    public void generateDepartmentReport() {
        System.out.println("\n--- Workforce Distribution ---");

        if (employeeList == null || employeeList.isEmpty()) {
            System.out.println("[INFO] No employee data available to generate department report.");
            return;
        }

        java.util.HashMap<String, Integer> deptCount = new java.util.HashMap<>();

        for (Employee emp : employeeList) {
            try {
                if (emp == null || emp.getStatus() == null) {
                    System.err.println("[WARN] Skipping record with null employee or status.");
                    continue;
                }

                if (emp.getStatus().equalsIgnoreCase("Active")) {
                    String dept = (emp.getDepartment() != null && !emp.getDepartment().trim().isEmpty())
                            ? emp.getDepartment().trim()
                            : "Unassigned";
                    deptCount.put(dept, deptCount.getOrDefault(dept, 0) + 1);
                }
            } catch (Exception e) {
                System.err.println("[WARN] Error processing employee for department report: " + e.getMessage() + ". Skipping.");
            }
        }

        if (deptCount.isEmpty()) {
            System.out.println("[INFO] No active employees found for department grouping.");
            return;
        }

        for (java.util.Map.Entry<String, Integer> entry : deptCount.entrySet()) {
            System.out.printf("%-15s: %d Employees%n", entry.getKey(), entry.getValue());
        }
    }

    public void generateAttritionReport() {
        System.out.println("\n--- Attrition Analysis ---");

        if (employeeList == null || employeeList.isEmpty()) {
            System.out.println("[INFO] No employee data available to generate attrition report.");
            return;
        }

        int totalHires = employeeList.size();
        int exitedCount = 0;
        int noticePeriodCount = 0;
        int activeCount = 0;

        for (Employee emp : employeeList) {
            try {
                if (emp == null || emp.getStatus() == null) {
                    System.err.println("[WARN] Skipping record with null employee or status in attrition report.");
                    continue;
                }

                String status = emp.getStatus().trim();

                if (status.equalsIgnoreCase("Exited")) {
                    exitedCount++;
                } else if (status.equalsIgnoreCase("Notice Period")) {
                    noticePeriodCount++;
                } else if (status.equalsIgnoreCase("Active") || status.equalsIgnoreCase("Probation")) {
                    activeCount++;
                } else {
                    System.err.println("[WARN] Unrecognised status '" + status + "' for employee ID: " + nullSafe(emp.getId()) + ". Excluded from counts.");
                }
            } catch (Exception e) {
                System.err.println("[WARN] Error processing employee for attrition report: " + e.getMessage() + ". Skipping.");
            }
        }

        double turnoverRate = 0.0;
        try {
            if (totalHires > 0) {
                turnoverRate = ((double) exitedCount / totalHires) * 100;
            }
        } catch (ArithmeticException e) {
            System.err.println("[ERROR] Failed to calculate turnover rate: " + e.getMessage());
        }

        System.out.printf("Total Historical Hires : %d%n", totalHires);
        System.out.printf("Current Active Staff   : %d%n", activeCount + noticePeriodCount);
        System.out.printf("Employees Exited       : %d%n", exitedCount);
        System.out.printf("Flight Risk Headcount  : %d (Serving Notice Period)%n", noticePeriodCount);
        System.out.println("--------------------------------------------------");
        System.out.printf("Overall Turnover Rate  : %.1f%%%n", turnoverRate);

        double controlLimit = 15.0;
        if (turnoverRate > controlLimit) {
            System.out.println("⚠️ [WARNING] Turnover exceeds " + controlLimit + "% control limit.");
            System.out.println("   -> Root Cause Analysis (DMAIC) required for retention process.");
        } else {
            System.out.println("✅ [STATUS] Retention process is within acceptable control limits.");
        }
    }

    public void generatePayParityReport() {
        System.out.println("\n--- DE&I Analytics: Pay Parity Report ---");

        if (employeeList == null || employeeList.isEmpty()) {
            System.out.println("[INFO] No employee data available to generate pay parity report.");
            return;
        }

        double totalMaleSalary = 0;
        double totalFemaleSalary = 0;
        int maleCount = 0;
        int femaleCount = 0;

        for (Employee emp : employeeList) {
            try {
                if (emp == null || emp.getStatus() == null || emp.getGender() == null) {
                    System.err.println("[WARN] Skipping record with null employee, status, or gender.");
                    continue;
                }

                String status = emp.getStatus().trim();
                if (status.equalsIgnoreCase("Active") || status.equalsIgnoreCase("Probation") || status.equalsIgnoreCase("Notice Period")) {

                    double salary = emp.getBasicSalary();
                    if (salary < 0) {
                        System.err.println("[WARN] Negative salary detected for employee ID: " + nullSafe(emp.getId()) + ". Skipping.");
                        continue;
                    }

                    if (emp.getGender().equalsIgnoreCase("M")) {
                        totalMaleSalary += salary;
                        maleCount++;
                    } else if (emp.getGender().equalsIgnoreCase("F")) {
                        totalFemaleSalary += salary;
                        femaleCount++;
                    } else {
                        System.err.println("[WARN] Unrecognised gender value '" + emp.getGender() + "' for employee ID: " + nullSafe(emp.getId()) + ". Excluded from parity calculation.");
                    }
                }
            } catch (Exception e) {
                System.err.println("[WARN] Error processing employee for pay parity report: " + e.getMessage() + ". Skipping.");
            }
        }

        double avgMaleSalary = (maleCount > 0) ? (totalMaleSalary / maleCount) : 0;
        double avgFemaleSalary = (femaleCount > 0) ? (totalFemaleSalary / femaleCount) : 0;

        double parityRatio = 0.0;
        try {
            if (avgMaleSalary > 0) {
                parityRatio = (avgFemaleSalary / avgMaleSalary) * 100;
            } else {
                System.out.println("[INFO] No male salary data available; parity ratio cannot be computed.");
            }
        } catch (ArithmeticException e) {
            System.err.println("[ERROR] Failed to calculate parity ratio: " + e.getMessage());
        }

        System.out.printf("Male Workforce   : %d employees | Avg Salary: ₹%,.2f%n", maleCount, avgMaleSalary);
        System.out.printf("Female Workforce : %d employees | Avg Salary: ₹%,.2f%n", femaleCount, avgFemaleSalary);
        System.out.println("---------------------------------------------------------");
        System.out.printf("Org-Wide Parity Ratio: %.1f%%%n", parityRatio);

        if (parityRatio >= 98.0 && parityRatio <= 102.0) {
            System.out.println("✅ [EQUITABLE] Pay is balanced across genders.");
        } else if (parityRatio > 0 && parityRatio < 98.0) {
            System.out.println("⚠️ [BIAS ALERT] Women earn roughly " + Math.round(parityRatio) + " cents for every ₹1 earned by men.");
            System.out.println("   -> Recommendation: Conduct a role-by-role compensation audit.");
        } else if (parityRatio > 102.0) {
            System.out.println("⚠️ [BIAS ALERT] Men earn less on average than female counterparts.");
        }
    }

    public void generateGratuityReport() {
        System.out.println("\n--- COMPLIANCE & REWARDS: 5-YEAR TENURE FLAG ---");

        if (employeeList == null || employeeList.isEmpty()) {
            System.out.println("[INFO] No employee data available to generate gratuity report.");
            return;
        }

        System.out.printf("%-10s | %-20s | %-15s | %-12s%n", "ID", "Name", "Joining Date", "Tenure (Yrs)");
        System.out.println("-------------------------------------------------------------------");

        int eligibleCount = 0;

        for (Employee emp : employeeList) {
            try {
                if (emp == null || emp.getStatus() == null) {
                    System.err.println("[WARN] Skipping record with null employee or status in gratuity report.");
                    continue;
                }

                String status = emp.getStatus().trim();

                if (status.equalsIgnoreCase("Active") || status.equalsIgnoreCase("Notice Period")) {
                    long tenureYears = emp.getTenureYears();

                    if (tenureYears < 0) {
                        System.err.println("[WARN] Negative tenure calculated for employee ID: " + nullSafe(emp.getId()) + ". Skipping.");
                        continue;
                    }

                    if (tenureYears >= 5) {
                        System.out.printf("%-10s | %-20s | %-15s | %d Years%n",
                                nullSafe(emp.getId()),
                                nullSafe(emp.getFullName()),
                                nullSafe(emp.getDepartment()),
                                tenureYears);
                        eligibleCount++;
                    }
                }
            } catch (Exception e) {
                System.err.println("[WARN] Error processing employee for gratuity report: " + e.getMessage() + ". Skipping.");
            }
        }

        System.out.println("-------------------------------------------------------------------");
        System.out.println("Total Eligible Employees: " + eligibleCount);

        if (eligibleCount > 0) {
            System.out.println("✅ [ACTION REQUIRED] Notify Finance for gratuity provisioning.");
            System.out.println("✅ [ACTION REQUIRED] Trigger 5-Year Service Award workflows.");
        } else {
            System.out.println("No employees are currently eligible for the 5-year milestone.");
        }
    }

    private String nullSafe(String value) {
        return (value != null) ? value : "N/A";
    }

    public void LogNewCandidate() {
        System.out.println("\n--- CANDIDATE APPLICATION LOGGING ---");
        try {
            System.out.print("Enter Candidate ID: "); 
            String id = scanner.nextLine();

            System.out.print("Enter Full Name: ");
            String fullName = scanner.nextLine();

            System.out.print("Enter Role Applied For: ");
            String role = scanner.nextLine();

            System.out.print("Enter Application Date (YYYY-MM-DD): ");
            String dateInput = scanner.nextLine();

            System.out.print("Enter Offer Date (YYYY-MM-DD): ");
            String offerDateInput = scanner.nextLine();

            System.out.print("Enter Current Offer Status (Accepted/Rejected): ");
            String offerStatus = scanner.nextLine();

            Candidate newCandidate = new Candidate(id, fullName, role, LocalDate.parse(dateInput), LocalDate.parse(offerDateInput), offerStatus);

            try (BufferedWriter writer = new BufferedWriter(new FileWriter("candidates.csv", true))) {
                writer.write(newCandidate.toCSVString());
                writer.newLine();

                System.out.println("\n Candidate Sucessfully saved to DB");

                long CycleTime = newCandidate.getCycleTimeDays();
                if (CycleTime != - 1) {
                    System.out.println("Time to hire: " + CycleTime + "Days");
                    if (CycleTime > 45) {
                        System.out.println("Process bottleneck detected");
                    }
                }
            } catch (java.io.IOException e) {
                System.err.println("Error saving candidate: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Invalid Input. Ensure date is in YYYY-MM-DD format");
        }
    }

    public void logRejection() {
        System.out.println("\n===Log Candidate Rejection===");
        System.out.print("Enter Candidate ID: ");
        String id = scanner.nextLine();

        System.out.println("Select Rejection Reason:");
        System.out.println("[1] Salary Expectations Too High");
        System.out.println("[2] Lacks Technical Skills");
        System.out.println("[3] Poor Culture Fit");
        System.out.println("[4] Candidate Ghosted / Withdrew");
        System.out.println("[5] Other");
        System.out.print("Choice (1-5): ");
        String reasonChoice = scanner.nextLine();

        String reason = "Other";
        switch (reasonChoice) {
            case "1": reason = "Salary Expectations Too High"; break;
            case "2": reason = "Lacks Technical Skills"; break;
            case "3": reason = "Poor Culture Fit"; break;
            case "4": reason = "Candidate Ghosted / Withdrew"; break;
            default: reason = "Other"; break;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("rejections.csv", true))) {
             writer.write(id + "," + reason + "," + LocalDate.now());
            writer.newLine();
            System.out.println("\nRejection reason logged successfully.");
        } catch (IOException e) {
            System.err.println("Error logging rejection: " + e.getMessage());
        }

        generateParetoChart();
    }

    private void generateParetoChart() {
        System.out.println("\n--- PARETO ANALYSIS: PIPELINE BOTTLENECKS ---");
        java.util.HashMap<String, Integer> defectCounts = new java.util.HashMap<>();
        int totalDefects = 0;

        // --- FILE I/O: READING THE LOG ---
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader("rejections.csv"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String reason = parts[1];
                    // Tally up the reasons using HashMap
                    defectCounts.put(reason, defectCounts.getOrDefault(reason, 0) + 1);
                    totalDefects++;
                }
            }
        } catch (java.io.IOException e) {
            System.out.println("No historical rejection data found yet. Start logging!");
            return;
        }

        if (totalDefects == 0) return;

        
        java.util.List<java.util.Map.Entry<String, Integer>> sortedList = new java.util.ArrayList<>(defectCounts.entrySet());
        sortedList.sort((a, b) -> b.getValue().compareTo(a.getValue())); 

        System.out.println("Total Defects Analyzed: " + totalDefects);
        System.out.println("--------------------------------------------------");
        
        for (java.util.Map.Entry<String, Integer> entry : sortedList) {
            double percentage = ((double) entry.getValue() / totalDefects) * 100;
            // %-20s ensures the columns line up perfectly
            System.out.printf("%-20s | Count: %-2d | %5.1f%%%n", entry.getKey(), entry.getValue(), percentage);
        }
        System.out.println("--------------------------------------------------");
        System.out.println("💡 FOCUS AREA: Address the top category to eliminate the largest source of waste.");
    }

    
}