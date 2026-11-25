import backend.DirectoryRepository;
import backend.DirectoryService;
import model.Employee;
import model.Scope;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackendScopeTest {
    private final DirectoryRepository repository = mock(DirectoryRepository.class);

    private DirectoryService service() {
        when(repository.departments("A")).thenReturn(List.of(
                new Scope.Department("A", "ENG", "Engineering"), new Scope.Department("A", "OPS", "Operations")));
        return new DirectoryService(repository);
    }

    @Test
    void managerAndAdminScopeIncludesOnlyAuthorizedRows() {
        var admin = new Scope.Actor("admin", "A", Scope.Role.HR_ADMIN, null);
        var manager = new Scope.Actor("manager", "A", Scope.Role.MANAGER, "ENG");
        var rows = List.of(employee("A", "ENG"), employee("A", "OPS"), employee("B", "ENG"));
        when(repository.employees(admin)).thenReturn(rows);
        when(repository.employees(manager)).thenReturn(rows);
        DirectoryService directory = service();
        assertEquals(2, directory.employees(admin).size());
        assertEquals(1, directory.employees(manager).size());
        assertEquals(1, directory.currentHeadcount(manager).total());
    }

    @Test
    void financeCannotReadPeopleAndAnActorIsRequired() {
        var finance = new Scope.Actor("finance", "A", Scope.Role.FINANCE, null);
        DirectoryService directory = service();
        assertThrows(SecurityException.class, () -> directory.employees(finance));
        assertThrows(SecurityException.class, () -> directory.candidates(finance));
        assertThrows(SecurityException.class, () -> directory.compensation(null));
        verify(repository, never()).employees(finance);
    }

    private static Employee employee(String org, String department) {
        return new Employee(new Scope.Key(org, department), department, "Synthetic", null, "Engineer", null,
                LocalDate.of(2020, 1, 1), Employee.Status.ACTIVE, null);
    }
}
