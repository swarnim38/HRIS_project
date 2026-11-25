package backend;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class IdentityController {
    private final LocalIdentity identity;

    public IdentityController(LocalIdentity identity) { this.identity = identity; }

    @PostMapping("/login")
    public Object login(HttpServletRequest request, @RequestBody LocalIdentity.Credentials body) {
        return identity.login(request, body);
    }

    @GetMapping("/session")
    public Object session(HttpServletRequest request) {
        return identity.session(request);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        identity.logout(request);
    }

    @GetMapping("/accounts")
    public Object accounts(HttpServletRequest request) {
        return identity.accounts(identity.require(request));
    }

    @PostMapping("/accounts")
    public void create(HttpServletRequest request, @RequestBody LocalIdentity.AccountInput body) {
        identity.create(identity.require(request), body);
    }

    @PostMapping("/accounts/{username}/deactivate")
    public void deactivate(HttpServletRequest request, @PathVariable String username) {
        identity.deactivate(identity.require(request), username);
    }

    @PutMapping("/accounts/{username}/assignment")
    public void assign(HttpServletRequest request, @PathVariable String username,
                       @RequestBody LocalIdentity.Assignment assignment) {
        identity.assign(identity.require(request), username, assignment);
    }
}
