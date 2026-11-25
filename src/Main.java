import backend.Backend;
import org.springframework.boot.SpringApplication;

import java.io.IOException;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

public final class Main {
    public static void main(String[] args) {
        try {
            Properties local = loadLocalConfiguration();
            String password = setting(local, "HRIS_DB_PASSWORD");
            if (password == null || password.isBlank()) {
                throw new IllegalStateException("Set HRIS_DB_PASSWORD or create talentcompass.local.properties. See the setup in readme.MD.");
            }
            String port = setting(local,"HRIS_PORT");
            if (isListening(port==null ? 8080 : Integer.parseInt(port))) {
                throw new IllegalStateException("The backend port is in use. Close the existing instance or use a different HRIS_PORT.");
            }
            if ("true".equalsIgnoreCase(setting(local,"HRIS_START_MYSQL"))) startMysql(local,password);
            SpringApplication.run(Backend.class,args);
        } catch (IllegalStateException failure) {
            System.err.println(failure.getMessage());
            System.exit(1);
        } catch (RuntimeException failure) {
            boolean flywayFailure = false;
            for (Throwable cause=failure; cause!=null; cause=cause.getCause()) {
                if (cause instanceof java.sql.SQLException sql) {
                    System.err.printf("Database startup failed (SQL state %s). Check docker compose ps, JDBC host, port, credentials and migration permissions.%n",
                            sql.getSQLState());
                    System.exit(1);
                }
                flywayFailure |= cause.getClass().getName().startsWith("org.flywaydb.");
            }
            System.err.println(flywayFailure
                    ? "Flyway could not apply or validate migrations. Run docker compose ps and check the database and migration history."
                    : "Backend startup failed. Check the port and configuration. Enable SpringApplication logging for diagnostics.");
            System.exit(1);
        }
    }

    private static Properties loadLocalConfiguration() {
        Properties config = new Properties();
        Path file = Path.of("talentcompass.local.properties");
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                config.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read talentcompass.local.properties");
            }
            config.stringPropertyNames().stream().filter(key -> key.startsWith("HRIS_") || key.equals("SPRING_PROFILES_ACTIVE"))
                    .filter(key -> System.getenv(key)==null && System.getProperty(key)==null)
                    .forEach(key -> System.setProperty(key,config.getProperty(key)));
        }
        String profile = setting(config,"SPRING_PROFILES_ACTIVE");
        if (profile!=null && System.getProperty("spring.profiles.active")==null) {
            System.setProperty("spring.profiles.active",profile);
        }
        String mysqlPort = setting(config,"HRIS_MYSQL_PORT");
        if (mysqlPort!=null && setting(config,"HRIS_DB_URL")==null) {
            System.setProperty("HRIS_DB_URL","jdbc:mysql://127.0.0.1:"+mysqlPort+"/talentcompass");
        }
        return config;
    }

    private static void startMysql(Properties config,String password) {
        String project = setting(config,"HRIS_COMPOSE_PROJECT");
        String port = setting(config,"HRIS_MYSQL_PORT");
        ProcessBuilder process = new ProcessBuilder("docker","compose","up","-d","--wait","--wait-timeout","90");
        if (project!=null) {
            process.command().add(2,"-p");
            process.command().add(3,project);
        }
        Map<String,String> env=process.environment();
        env.put("HRIS_DB_PASSWORD",password);
        if (port!=null) env.put("HRIS_MYSQL_PORT",port);
        try {
            if (process.inheritIO().start().waitFor()!=0) throw new IllegalStateException(
                    "MySQL did not start. Check Docker Desktop, then run docker compose up -d --wait with the configured password.");
        } catch (IOException e) {
            throw new IllegalStateException("Docker is unavailable. Open Docker Desktop and ensure docker is on PATH.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Database startup was interrupted");
        }
    }

    private static boolean isListening(int port) {
        if (port==0) return false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1",port),500);
            return true;
        } catch(IOException e) { return false; }
    }

    private static String setting(Properties config,String key) {
        String property=System.getProperty(key);
        if (property!=null) return property;
        String environment=System.getenv(key);
        return environment==null ? config.getProperty(key) : environment;
    }
}
