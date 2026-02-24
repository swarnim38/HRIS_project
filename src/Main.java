import java.util.Scanner;
import service.HRSystem;
import ui.Menu;

public class Main {
    public static void main(String[] args) {

        Scanner sharedScanner = new Scanner(System.in);

        HRSystem hris = new HRSystem(sharedScanner);

        System.out.println("Booting up TalentCompass Core System...");
        hris.loadData("startup.csv");

        Menu dashboard = new Menu(hris, sharedScanner);

        dashboard.start();

        sharedScanner.close();
    }
}