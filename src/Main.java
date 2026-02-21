import service.HRSystem;

public class Main {
    public static void main(String[] args) {
        HRSystem hris = new HRSystem();
        hris.loadData("startup.csv");
        
        hris.listAllEmployees();
        hris.generateDepartmentReport();
        hris.generateAttritionReport();
    }
}
