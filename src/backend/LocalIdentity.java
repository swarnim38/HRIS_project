package backend;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import model.Scope.Actor;
import model.Scope.Role;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class LocalIdentity implements ApplicationRunner {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final java.util.concurrent.Semaphore passwordChecks = new java.util.concurrent.Semaphore(2);
    private final JdbcClient db;
    private final Environment environment;

    public record Credentials(String organization, String username, String password) {}
    public record AccountInput(String username, String password, Role role, String departmentId) {}
    public record Assignment(Role role, String departmentId) {}
    public record SessionView(String organization, String username, Role role, String departmentId, String csrf) {}
    private record SessionKey(String organization, String username, String csrf) {}
    private record Account(Actor actor, String hash) {}

    public LocalIdentity(JdbcClient db, Environment environment) {
        this.db = db;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String demo = environment.getProperty("HRIS_DEMO_PASSWORD");
        if (environment.acceptsProfiles(Profiles.of("demo")) && demo != null && !demo.isBlank()) {
            password(demo);
            var actors = db.sql("SELECT org_id, actor_id, role, department_id FROM demo_actor_assignments")
                    .query((rs, row) -> new Actor(rs.getString("actor_id"), rs.getString("org_id"),
                            Role.valueOf(rs.getString("role")), rs.getString("department_id"))).list();
            for (Actor actor : actors) {
                if (!exists(actor.orgId(), actor.id())) insert(actor, demo);
            }
        }

        String org = environment.getProperty("HRIS_BOOTSTRAP_ORG");
        String secret = environment.getProperty("HRIS_BOOTSTRAP_PASSWORD");
        if (org != null && secret != null && !exists(org, "admin")) {
            password(secret);
            db.sql("INSERT INTO organizations (org_id,name) VALUES (?,?) ON DUPLICATE KEY UPDATE org_id=org_id")
                    .params(org, org).update();
            insert(new Actor("admin", org, Role.HR_ADMIN, null), secret);
        }
    }

    public SessionView login(HttpServletRequest request, Credentials credentials) {
        if (credentials == null || credentials.password() == null || credentials.password().length() > 256) {
            throw new LoginRequired("Invalid organization, username or password");
        }
        if (!passwordChecks.tryAcquire()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        }
        Account account;
        boolean valid;
        try {
            account = find(credentials.organization(), credentials.username());
            valid = account == null
                    ? MessageDigest.isEqual(derive(credentials.password(), new byte[24]), new byte[32])
                    : matches(credentials.password(), account.hash());
        } finally {
            passwordChecks.release();
        }
        if (account == null || !valid) {
            throw new LoginRequired("Invalid organization, username or password");
        }
        HttpSession old = request.getSession(false);
        if (old != null) old.invalidate();
        HttpSession session = request.getSession();
        var key = new SessionKey(account.actor().orgId(), account.actor().id(), UUID.randomUUID().toString());
        session.setAttribute(SessionKey.class.getName(), key);
        return view(account.actor(), key);
    }

    public SessionView session(HttpServletRequest request) {
        SessionKey key = sessionKey(request);
        return view(actor(key), key);
    }

    public Actor require(HttpServletRequest request) {
        SessionKey key = sessionKey(request);
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())
                && !key.csrf().equals(request.getHeader("X-CSRF-Token"))) {
            throw new SecurityException("The session confirmation token is missing or expired");
        }
        return actor(key);
    }

    public void logout(HttpServletRequest request) {
        require(request);
        request.getSession(false).invalidate();
    }

    @Transactional
    public void create(Actor actor, AccountInput input) {
        Operations.admin(actor);
        String username = Operations.field(input.username(), "Username", 64).toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9][a-z0-9._-]*") || input.role() == null) {
            throw new IllegalArgumentException("Enter a valid username and role");
        }
        String department = input.role() == Role.MANAGER
                ? Operations.field(input.departmentId(), "Department", 64) : null;
        password(input.password());
        insert(new Actor(username, actor.orgId(), input.role(), department), input.password());
        audit(actor, "ACCOUNT_CREATED", username);
    }

    @Transactional
    public void deactivate(Actor actor, String username) {
        Operations.admin(actor);
        if (actor.id().equalsIgnoreCase(username)) throw new IllegalArgumentException("You cannot deactivate your own account");
        int count = db.sql("UPDATE local_accounts SET active=false WHERE org_id=? AND username=? AND active=true")
                .params(actor.orgId(), username).update();
        if (count == 0) throw new IllegalArgumentException("Active account was not found");
        audit(actor, "ACCOUNT_DEACTIVATED", username);
    }

    public List<java.util.Map<String, Object>> accounts(Actor actor) {
        Operations.admin(actor);
        return db.sql("SELECT username,role,department_id,active FROM local_accounts WHERE org_id=? ORDER BY username")
                .param(actor.orgId()).query().listOfRows();
    }

    @Transactional
    public void assign(Actor actor, String username, Assignment assignment) {
        Operations.admin(actor);
        if (actor.id().equalsIgnoreCase(username)) throw new IllegalArgumentException("You cannot change your own assignment");
        if (assignment.role() == null) throw new IllegalArgumentException("Choose a role");
        String department = assignment.role() == Role.MANAGER
                ? Operations.field(assignment.departmentId(), "Department", 64) : null;
        int count = db.sql("UPDATE local_accounts SET role=?,department_id=? WHERE org_id=? AND username=? AND active=true")
                .params(assignment.role().name(), department, actor.orgId(), username).update();
        if (count == 0) throw new IllegalArgumentException("Active account was not found");
        audit(actor, "ACCOUNT_ASSIGNMENT_CHANGED", username);
    }

    public void audit(Actor actor, String action, String id) {
        db.sql("INSERT INTO audit_events(org_id,username,action,entity_id) VALUES (?,?,?,?)")
                .params(actor.orgId(), actor.id(), action, id).update();
    }

    private SessionKey sessionKey(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(SessionKey.class.getName()) instanceof SessionKey key)) {
            throw new LoginRequired("Sign in to continue");
        }
        return key;
    }

    private Actor actor(SessionKey key) {
        var account = find(key.organization(), key.username());
        if (account == null) throw new LoginRequired("Your account is inactive");
        return account.actor();
    }

    private SessionView view(Actor actor, SessionKey key) {
        return new SessionView(actor.orgId(), actor.id(), actor.role(), actor.departmentId(), key.csrf());
    }

    private Account find(String org, String username) {
        if (org == null || username == null || org.length() > 64 || username.length() > 64) return null;
        return db.sql("SELECT * FROM local_accounts WHERE org_id=? AND username=? AND active=true")
                .params(org.strip(), username.strip()).query((rs, row) -> new Account(
                        new Actor(rs.getString("username"), rs.getString("org_id"), Role.valueOf(rs.getString("role")),
                                rs.getString("department_id")), rs.getString("password_hash"))).optional().orElse(null);
    }

    private boolean exists(String org, String username) {
        return db.sql("SELECT COUNT(*) FROM local_accounts WHERE org_id=? AND username=?")
                .params(org, username).query(Integer.class).single() != 0;
    }

    private void insert(Actor actor, String password) {
        byte[] salt = new byte[24];
        RANDOM.nextBytes(salt);
        String encoded = Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derive(password, salt));
        db.sql("INSERT INTO local_accounts(org_id,username,password_hash,role,department_id) VALUES (?,?,?,?,?)")
                .params(actor.orgId(), actor.id(), encoded, actor.role().name(), actor.departmentId()).update();
    }

    private static boolean matches(String password, String hash) {
        String[] parts = hash.split("\\$", 2);
        return MessageDigest.isEqual(derive(password, Base64.getDecoder().decode(parts[0])),
                Base64.getDecoder().decode(parts[1]));
    }

    private static byte[] derive(String password, byte[] salt) {
        var spec = new PBEKeySpec(password.toCharArray(), salt, 600_000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static void password(String password) {
        if (password == null || password.length() < 12 || password.length() > 256) {
            throw new IllegalArgumentException("Use a password between 12 and 256 characters");
        }
    }

    public static class LoginRequired extends RuntimeException {
        public LoginRequired(String message) { super(message); }
    }
}
