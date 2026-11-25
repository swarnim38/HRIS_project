import io.EmployeeImport;
import io.LocalInputs;
import model.Scope;
import service.HRSystem;
import ui.Menu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class LegacyCli {
    private static final Set<String> OPTIONS = Set.of("as-of", "legacy-org", "legacy-recruitment-department",
            "tenure-years", "minimum-group-size", "validate-employees");

    public static void main(String[] args) {
        int status = run(args);
        if (status != 0) System.exit(status);
    }

    private static int run(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            var options = options(args);
            var asOf = options.containsKey("as-of") ? LocalDate.parse(options.get("as-of")) : LocalDate.now();
            var catalog = LocalInputs.catalog(Path.of("departments.csv"));
            var hris = new HRSystem(catalog, asOf);
            String legacyOrg = options.get("legacy-org");
            String legacyDept = options.get("legacy-recruitment-department");
            if (legacyOrg != null && !catalog.hasOrganization(legacyOrg)) {
                throw new IllegalArgumentException("Unknown legacy organization");
            }
            if (legacyDept != null) catalog.requireDepartment(legacyOrg, legacyDept);
            if (options.containsKey("validate-employees")) {
                var preview = hris.importEmployees(Path.of(options.get("validate-employees")), legacyOrg, true);
                showImport(preview, true);
                return preview.valid() ? 0 : 1;
            }

            System.out.println("TalentCompass HRIS | Local role simulation | Snapshot " + asOf);
            var employees = Path.of("startup.csv");
            if (Files.notExists(employees)) {
                System.out.println("Employee snapshot unavailable. Place a validated startup.csv in the working directory.");
            } else {
                showImport(hris.importEmployees(employees, legacyOrg, false), false);
            }
            int tenure = positive(options.getOrDefault("tenure-years", "5"), 1, "Tenure years");
            Integer minimumGroup = options.containsKey("minimum-group-size")
                    ? positive(options.get("minimum-group-size"), 2, "Minimum group size") : null;
            var menu = new Menu(scanner, catalog, hris, asOf, tenure, minimumGroup, legacyOrg, legacyDept);
            menu.start();
            return 0;
        } catch (DateTimeParseException e) {
            System.err.println("Use a valid ISO date (YYYY-MM-DD).");
        } catch (IOException e) {
            // A filesystem exception may contain a user path.
            System.err.println("Could not read the local CSV. Check departments.csv and the requested source.");
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
        }
        return 1;
    }

    private static void showImport(io.EmployeeImport.Preview preview, boolean validationOnly) {
        if (validationOnly) {
            System.out.printf("Validation only | Valid rows: %d | Errors: %d%n", preview.validRows(),
                    preview.problems().size());
        } else {
            System.out.println(preview.valid() ? "Employee snapshot loaded." : "Employee snapshot rejected. Run validation for row details.");
        }
        if (validationOnly) {
            preview.problems().forEach(problem -> System.out.printf("Row %d | %s%n", problem.line(), problem.detail()));
        }
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            int equals = arg.indexOf('=');
            if (!arg.startsWith("--") || equals <= 2 || !OPTIONS.contains(arg.substring(2, equals))
                    || equals == arg.length() - 1 || options.putIfAbsent(arg.substring(2, equals),
                    arg.substring(equals + 1)) != null) {
                throw new IllegalArgumentException("Use supported, unique --name=value options. See readme.MD.");
            }
        }
        return options;
    }

    private static int positive(String value, int minimum, String field) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed >= minimum) return parsed;
        } catch (NumberFormatException ignored) {
            // Report a field-level message without echoing the input.
        }
        throw new IllegalArgumentException(field + " must be an integer of at least " + minimum);
    }
}
