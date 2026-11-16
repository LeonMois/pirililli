package pirililli;

import pirililli.Database.SessionInfo;
import pirililli.Database.Tally;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/** --sessions / --results [id] */
final class Cli {
    private Cli() {}

    /** Runs without Spring/Vaadin: plain JDBC using the datasource settings from config.properties. */
    static void run(Path config, String[] args) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        DriverManagerDataSource ds = new DriverManagerDataSource(
                p.getProperty("spring.datasource.url", "jdbc:postgresql://localhost:5433/pirililli"),
                p.getProperty("spring.datasource.username", "pirililli"),
                p.getProperty("spring.datasource.password", "pirililli"));
        run(new Database(new JdbcTemplate(ds)), args);
    }

    static void run(Database db, String[] args) {
        if (args[0].equals("--sessions")) {
            System.out.printf("%-6s %-25s %-25s %8s %8s%n", "ID", "Started", "Ended", "Voters", "Votes");
            for (SessionInfo s : db.sessions()) {
                System.out.printf("%-6d %-25s %-25s %8d %8d%n", s.id(), s.startedAt(),
                        s.endedAt() == null ? "-" : s.endedAt(), s.voters(), s.votes());
            }
            return;
        }
        Integer only = args.length > 1 ? Integer.parseInt(args[1]) : null;
        for (SessionInfo s : db.sessions()) {
            if (only != null && s.id() != only) continue;
            System.out.printf("%n=== Session #%d  (%s, %d voters) ===%n", s.id(), s.startedAt(), s.voters());
            List<Tally> tallies = db.results(s.id());
            int lastQ = -1;
            for (Tally t : tallies) {
                if (t.questionNo() != lastQ) {
                    System.out.printf("%n  %d. %s%n", t.questionNo(), t.question());
                    lastQ = t.questionNo();
                }
                String who = switch (t.type()) {
                    case CANDIDATE -> t.number() + " " + t.name();
                    case BLANK -> "(em branco / blank)";
                    case NULL -> "(nulo / null: " + t.number() + ")";
                };
                System.out.printf("     %4d  %s%n", t.votes(), who);
            }
            if (tallies.isEmpty()) System.out.println("  (no votes)");
        }
    }
}
