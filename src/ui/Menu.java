package ui;

import java.util.NoSuchElementException;
import java.util.Scanner;
import service.HRSystem;

public class Menu {
    
    private HRSystem hris;
    private Scanner scanner;

    public Menu(HRSystem hris, Scanner scanner) {
        this.hris = hris;
        this.scanner = scanner;
    }

    public void start() {
        boolean isSystemRunning = true;

        while (isSystemRunning) {
            System.out.println("\n=========================================");
            System.out.println("      TALENT COMPASS - HRIS v1.0         ");
            System.out.println("=========================================");
            System.out.println("--- ANALYTICS MODULES ---");
            System.out.println("[1] View Master Employee Directory");
            System.out.println("[2] Workforce Distribution (Headcount)");
            System.out.println("[3] Six Sigma Attrition & Risk Report");
            System.out.println("[4] DE&I Gender Pay Parity Report");
            System.out.println("[5] Legal Compliance (5-Year Gratuity)");
            System.out.println("");
            System.out.println("--- DATA ENTRY MODULES ---");
            System.out.println("[6] Recruitment: Log New Candidate & Cycle Time");
            System.out.println("[7] Quality Control: Log Rejection & View Pareto Chart");
            System.out.println("");
            System.out.println("[0] Exit System");
            System.out.println("=========================================");
            System.out.print("Enter your choice (0-7): ");

            String choice = "";
            try {
                choice = scanner.nextLine();
            } catch (NoSuchElementException e) {
                isSystemRunning = false;
                break;
            }

            switch (choice) {
                case "1":
                    hris.listAllEmployees();
                    break;
                case "2":
                    hris.generateDepartmentReport();
                    break;
                case "3":
                    hris.generateAttritionReport();
                    break;
                case "4":
                    hris.generatePayParityReport();
                    break;
                case "5":
                    hris.generateGratuityReport();
                    break;
                case "6":
                    hris.LogNewCandidate();
                    break;
                case "7":
                    hris.logRejection();
                    break;
                case "0":
                    System.out.println("\nShutting down TalentCompass... Data securely saved.");
                    isSystemRunning = false;
                    break;
                default:
                    System.out.println("\nInvalid command. Please type a number between 0 and 7.");
            }

            if (isSystemRunning) {
                System.out.print("\nPress [ENTER] to return to the Main Menu...");
                try {
                    scanner.nextLine();
                } catch (NoSuchElementException e) {
                    isSystemRunning = false;
                }
            }
        }

    }
}