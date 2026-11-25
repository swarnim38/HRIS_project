package backend;

import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final DirectoryRepository repository;
    private volatile boolean ready;

    public HealthController(DirectoryRepository repository) { this.repository = repository; }

    @EventListener
    public void onReady(ApplicationReadyEvent event) {
        repository.checkDatabase();
        ready = true;
        var context = (org.springframework.boot.web.server.context.WebServerApplicationContext)
                event.getApplicationContext();
        String address = "http://127.0.0.1:" + context.getWebServer().getPort();
        System.out.println("TalentCompass backend ready at " + address + " | Health " + address + "/health");
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        if (ready) {
            try {
                repository.checkDatabase();
                return ResponseEntity.ok(Map.of("status", "UP"));
            } catch (DataAccessException | IllegalStateException ignored) {
                // Report availability without revealing SQL or connection details.
            }
        }
        return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
    }
}
