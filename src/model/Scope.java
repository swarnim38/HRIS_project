package model;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class Scope {
    public record Organization(String id) {
        public Organization { id = required(id, "organization ID"); }
    }

    public record Department(String orgId, String id, String name) {
        public Department {
            orgId = required(orgId, "organization ID");
            id = required(id, "department ID");
            name = required(name, "department name");
        }
    }

    public record Key(String orgId, String id) {
        public Key {
            orgId = required(orgId, "organization ID");
            id = required(id, "record ID");
        }
    }

    public enum Role { HR_ADMIN, MANAGER, FINANCE }

    public record Actor(String id, String orgId, Role role, String departmentId) {}

    private final Set<String> organizations;
    private final Map<Key, Department> departments;

    public Scope(Collection<Organization> organizations, Collection<Department> departments) {
        this.organizations = Set.copyOf(organizations.stream().map(Organization::id).toList());
        Map<Key, Department> byKey = new HashMap<>();
        for (Department department : departments) {
            if (!this.organizations.contains(department.orgId())
                    || byKey.putIfAbsent(new Key(department.orgId(), department.id()), department) != null) {
                throw new IllegalArgumentException("Unknown organization or duplicate department");
            }
        }
        this.departments = Map.copyOf(byKey);
    }

    public void requireDepartment(String orgId, String departmentId) {
        if (!departments.containsKey(new Key(orgId, departmentId))) {
            throw new IllegalArgumentException("Unknown department in organization");
        }
    }

    public boolean hasOrganization(String orgId) {
        return orgId != null && organizations.contains(orgId);
    }

    public String legacyDepartment(String orgId, String label) {
        // Legacy labels resolve only through the explicitly configured import catalog.
        var matches = departments.values().stream()
                .filter(d -> d.orgId().equals(orgId) && d.name().equals(label)).toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Legacy department needs one configured mapping");
        }
        return matches.get(0).id();
    }

    public Actor requireActor(Actor actor) {
        if (actor == null || !present(actor.id()) || !present(actor.orgId())
                || actor.role() == null || !organizations.contains(actor.orgId())
                || (actor.role() == Role.MANAGER && !known(actor.orgId(), actor.departmentId()))) {
            throw new SecurityException("Invalid actor or scope");
        }
        return actor;
    }

    public void requirePeopleAccess(Actor actor) {
        if (requireActor(actor).role() == Role.FINANCE) {
            throw new SecurityException("This report requires HR Admin or Manager access");
        }
    }

    public boolean canReadPeople(Actor actor, String orgId, String departmentId) {
        return requireActor(actor).role() != Role.FINANCE && inScope(actor, orgId, departmentId);
    }

    public boolean canReadFinancial(Actor actor, String orgId, String departmentId) {
        return inScope(requireActor(actor), orgId, departmentId);
    }

    public void requireRecruitmentWrite(Actor actor, String orgId, String departmentId) {
        Role role = requireActor(actor).role();
        if ((role != Role.HR_ADMIN && role != Role.MANAGER) || !inScope(actor, orgId, departmentId)) {
            throw new SecurityException("Recruitment write is outside the actor's scope");
        }
    }

    private boolean inScope(Actor actor, String orgId, String departmentId) {
        return actor.orgId().equals(orgId) && known(orgId, departmentId)
                && (actor.role() != Role.MANAGER || actor.departmentId().equals(departmentId));
    }

    public Set<String> peopleDepartments(Actor actor) {
        requirePeopleAccess(actor);
        return departments.values().stream().filter(d -> canReadPeople(actor, d.orgId(), d.id()))
                .map(Department::id).collect(Collectors.toUnmodifiableSet());
    }

    private boolean known(String orgId, String departmentId) {
        return present(orgId) && present(departmentId) && departments.containsKey(new Key(orgId, departmentId));
    }

    public static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    public static String required(String value, String field) {
        if (!present(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
