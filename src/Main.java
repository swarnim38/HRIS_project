import service.HRSystem;

public class Main {
    public static void main(String[] args) {
        HRSystem hris = new HRSystem();
        hris.loadData("TalentCompass_data.csv");
        
        hris.listAllEmployees();
        hris.generateDepartmentReport();
    }
}
