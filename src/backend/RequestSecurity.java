package backend;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestSecurity extends OncePerRequestFilter {
    private final LocalIdentity identity;

    public RequestSecurity(LocalIdentity identity) {
        this.identity = identity;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", "frame-ancestors 'none'; object-src 'none'; base-uri 'self'");
        response.setHeader("X-Content-Type-Options", "nosniff");
        String path = request.getServletPath();
        if (path.startsWith("/api/")) response.setHeader("Cache-Control", "no-store");
        if (path.startsWith("/api/v1/") && !path.equals("/api/v1/login")) {
            try {
                identity.require(request);
            } catch (LocalIdentity.LoginRequired e) {
                reject(response, 401, "Sign in to continue");
                return;
            } catch (SecurityException e) {
                reject(response, 403, "Session confirmation required");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + message + "\"}");
    }
}
