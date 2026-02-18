package service;

import java.io.BufferedReader; // Import the blueprint we just made
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import model.Employee;

public class HRSystem {
    // This List acts as our "In-Memory Database"
    // We load data from the file into this list once, then work with the list.
    private List<Employee> employeeList = new ArrayList<>();

    // METHOD 1: The Loader (File I/O)
    public void loadData(String filePath) {
        System.out.println("Reading data from: " + filePath);

        // Try-with-resources (Auto-closes the file to prevent memory leaks)
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;

            // Skip the Header Row (We don't want "EmployeeID,FullName..." as an object)
            br.readLine();

            while ((line = br.readLine()) != null) {
                // Use the static method we wrote in Employee.java
                Employee emp = Employee.fromCSV(line);

                // Add to our memory list
                employeeList.add(emp);
            }
            System.out.println("Successfully loaded " + employeeList.size() + " employees.");

        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
    }

    // METHOD 2: The Health Check (Simple Display)
    public void listAllEmployees() {
        System.out.println("\n--- EMPLOYEE DIRECTORY ---");
        // The "Techno-Functional" formatted print
        System.out.printf("%-10s | %-20s | %-15s | %-10s%n", "ID", "Name", "Department", "Tenure");
        System.out.println("------------------------------------------------------------------");

        for (Employee emp : employeeList) {
            // Using getters to access private data
            System.out.printf("%-10s | %-20s | %-15s | %d Years%n",
                    emp.getId(),
                    emp.getFullName(),
                    emp.getDepartment(),
                    emp.getTenureYears());
        }
    }
}