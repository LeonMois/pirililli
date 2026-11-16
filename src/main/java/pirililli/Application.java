package pirililli;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Viewport;
import com.vaadin.flow.server.AppShellSettings;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pirililli – a party voting machine in the style of the Brazilian urna eletrônica, served as a web app.
 *
 * <pre>
 *   java -jar pirililli.jar                 start the voting machine (new session)
 *   java -jar pirililli.jar --sessions      list all voting sessions stored in the DB
 *   java -jar pirililli.jar --results [id]  print the results of one session (default: all)
 * </pre>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@Viewport("width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover")
public class Application implements AppShellConfigurator {

    private static Database database;

    public static void main(String[] args) throws IOException {
        Path config = Path.of("config.properties");
        if (!Files.exists(config)) {
            try (InputStream in = Application.class.getResourceAsStream("/defaults/config.properties")) {
                Files.copy(Objects.requireNonNull(in), config);
            }
            System.out.println("Created default " + config.toAbsolutePath());
        }

        if (args.length > 0 && (args[0].equals("--results") || args[0].equals("--sessions"))) {
            Cli.run(config, args);
            return;
        }
        ConfigurableApplicationContext ctx = SpringApplication.run(Application.class, args);
        String port = ctx.getEnvironment().getProperty("local.server.port", "8080");
        System.out.println("\nPirililli is running. Open on the iPad / browser:");
        for (String ip : lanAddresses()) System.out.println("   http://" + ip + ":" + port);
        System.out.println("   http://localhost:" + port + "   (results: /results)\n");
    }

    /** Loaded once at startup; also starts a new voting session (web mode only). */
    @Bean
    Catalog catalog(Settings settings, Database db) throws Exception {
        Catalog catalog = Catalog.load(settings);
        db.init(30);
        database = db;
        return catalog;
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
    SessionStarter sessionStarter(Catalog catalog, Database db) {
        return new SessionStarter(catalog, db);
    }

    record SessionStarter(Catalog catalog, Database db) implements org.springframework.boot.ApplicationRunner {
        @Override
        public void run(org.springframework.boot.ApplicationArguments args) {
            int session = db.startSession();
            db.syncVoters(catalog.voters.values());
            System.out.println("Started voting session #" + session);
        }
    }

    @PreDestroy
    void shutdown() {
        if (database != null) database.endSession();
    }

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.setPageTitle("Pirililli");
        // "Add to Home Screen" on iPad → opens fullscreen without the Safari UI
        settings.addMetaTag("apple-mobile-web-app-capable", "yes");
        settings.addMetaTag("mobile-web-app-capable", "yes");
        settings.addMetaTag("apple-mobile-web-app-status-bar-style", "default");
        settings.addMetaTag("apple-mobile-web-app-title", "Urna");
        settings.addMetaTag("theme-color", "#ffffff");
        settings.addLink("apple-touch-icon", "icons/icon.png");
        settings.addLink("manifest", "manifest.webmanifest");
        settings.addLink("stylesheet", "styles/urna.css");
    }

    private static List<String> lanAddresses() {
        try {
            return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                    .filter(n -> {
                        try {
                            return n.isUp() && !n.isLoopback() && !n.isVirtual();
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .flatMap(n -> Collections.list(n.getInetAddresses()).stream())
                    .filter(a -> a instanceof Inet4Address)
                    .map(a -> a.getHostAddress())
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
