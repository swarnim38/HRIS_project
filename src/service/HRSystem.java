package service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import model.Employee;

public class HRSystem {
    private List<Employee> employeeList = new ArrayList<>();

    public void loadData(String filePath) {
        System.out.println("Reading data from: " + filePath);

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;

            br.readLine();

            while ((line = br.readLine()) != null) {
                Employee emp = Employee.fromCSV(line);

                employeeList.add(emp);
            }
            System.out.println("Successfully loaded " + employeeList.size() + " employees.");

        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
    }

    public void listAllEmployees() {
        System.out.println("\n--- EMPLOYEE DIRECTORY ---");
        // The "Techno-Functional" formatted print
        System.out.printf("%-10s | %-20s | %-15s | %-10s%n", "ID", "Name", "Department", "Tenure");
        System.out.println("------------------------------------------------------------------");

        for (Employee emp : employeeList) {
            System.out.printf("%-10s | %-20s | %-15s | %d Years%n",
                    emp.getId(),
                    emp.getFullName(),
                    emp.getDepartment(),
                    emp.getTenureYears());
        }
    }

    public void generateDepartmentReport() {
        System.out.println("\n--- Workforce Distribution---");
        java.util.HashMap<String, Integer> deptCount = new java.util.HashMap<>();

        for (Employee emp : employeeList) {
            if (emp.getStatus().equalsIgnoreCase("Active")) {
                String dept = emp.getDepartment();
                deptCount.put(dept, deptCount.getOrDefault(dept, 0) + 1);
            }
        }

        for (java.util.Map.Entry<String, Integer> entry : deptCount.entrySet()) {
            System.out.printf("%-15s: %d Employees%n", entry.getKey(), entry.getValue());
        }

    }
}