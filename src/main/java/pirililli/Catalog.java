package pirililli;

import pirililli.Model.Candidate;
import pirililli.Model.Question;
import pirililli.Model.Voter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;

/**
 * Everything configured through files in the data folder:
 * <ul>
 *   <li>candidates   – Number | Name | Party | Picture (file name inside pictures/)</li>
 *   <li>questions    – one column per language (header = language code, e.g. pt | en) | candidates</li>
 *   <li>translations – key | pt | en | ... (UI texts)</li>
 *   <li>voters       – Voter ID | Gringo (yes/no) | Name / note</li>
 * </ul>
 * Each is an .xlsx file ({@code candidates.xlsx}, ...), plus
 * <ul>
 *   <li>sounds/           – pirililli.mp3 and click.mp3 (or .wav/.m4a/.ogg)</li>
 * </ul>
 */
final class Catalog {
    static final List<String> CANDIDATE_HEADER = List.of("Number", "Name", "Party", "Picture");
    static final List<String> VOTER_HEADER = List.of("Voter ID", "Gringo (yes/no)", "Name / note (optional)");
    /** Always a valid voter ID, even if not listed in the voters sheet. Like every ID it can vote once per session. */
    static final String ALWAYS_VALID_VOTER = "000000";
    static final List<String> SOUND_EXTENSIONS = List.of("mp3", "m4a", "aac", "wav", "ogg");

    final Path dataDir;
    final List<Question> questions;
    final Map<String, Candidate> candidates;
    final Map<String, Voter> voters;
    final Translations translations;

    private Catalog(Path dataDir, List<Question> q, Map<String, Candidate> c, Map<String, Voter> v, Translations t) {
        this.dataDir = dataDir;
        this.questions = q;
        this.candidates = c;
        this.voters = v;
        this.translations = t;
    }

    static Catalog load(Settings settings) throws IOException {
        Path dataDir = settings.dataDir();
        Files.createDirectories(dataDir.resolve("pictures"));
        SampleData.createMissing(dataDir);
        installDefaultSounds(dataDir.resolve("sounds"));
        ensureVoters(dataDir, settings.votersCount());

        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (List<String> row : Spreadsheets.read(xlsx(dataDir, "candidates")).rows()) {
            String num = normalize(Spreadsheets.cell(row, 0), 2);
            if (num == null) {
                System.err.println("candidates: skipping row with invalid number: " + row);
                continue;
            }
            candidates.put(num, new Candidate(num, Spreadsheets.cell(row, 1), Spreadsheets.cell(row, 2), Spreadsheets.cell(row, 3)));
        }

        Spreadsheets.Table qt = Spreadsheets.read(xlsx(dataDir, "questions"));
        int candidatesCol = -1;
        Map<Integer, String> langCols = new LinkedHashMap<>();
        for (int c = 0; c < qt.header().size(); c++) {
            String h = qt.header().get(c).trim().toLowerCase(Locale.ROOT);
            if (h.isEmpty()) continue;
            if (h.startsWith("candidat")) candidatesCol = c;
            else langCols.put(c, Spreadsheets.languageCode(h));
        }
        List<Question> questions = new ArrayList<>();
        for (List<String> row : qt.rows()) {
            Map<String, String> texts = new LinkedHashMap<>();
            langCols.forEach((col, lang) -> {
                String t = Spreadsheets.cell(row, col);
                if (!t.isEmpty()) texts.put(lang, t);
            });
            if (texts.isEmpty()) continue;
            List<String> eligible = new ArrayList<>();
            for (String part : Spreadsheets.cell(row, candidatesCol).split("[,;\\s]+")) {
                if (part.isBlank()) continue;
                String n = normalize(part, 2);
                if (n == null || !candidates.containsKey(n)) {
                    System.err.println("questions: unknown candidate '" + part + "' in " + texts.values());
                } else {
                    eligible.add(n);
                }
            }
            questions.add(new Question(questions.size() + 1, Map.copyOf(texts), List.copyOf(eligible)));
        }

        Map<String, Voter> voters = new LinkedHashMap<>();
        for (List<String> row : Spreadsheets.read(xlsx(dataDir, "voters")).rows()) {
            String id = normalize(Spreadsheets.cell(row, 0), 6);
            if (id == null) continue;
            voters.put(id, new Voter(id, truthy(Spreadsheets.cell(row, 1)), Spreadsheets.cell(row, 2)));
        }
        // can be listed in the voters sheet to set its gringo flag; otherwise it uses the default language
        voters.putIfAbsent(ALWAYS_VALID_VOTER, new Voter(ALWAYS_VALID_VOTER, false, "always valid"));

        Spreadsheets.Table tt = addMissingTranslationKeys(dataDir, Spreadsheets.read(xlsx(dataDir, "translations")));
        Translations translations = new Translations(tt, settings.defaultLanguage());

        System.out.printf("Loaded %d candidates, %d questions, %d voters, languages %s%n",
                candidates.size(), questions.size(), voters.size(), new LinkedHashSet<>(langCols.values()));
        return new Catalog(dataDir, List.copyOf(questions), candidates, voters, translations);
    }

    /** Appends built-in keys that are missing from the translations sheet (e.g. after an update) so they can be edited. */
    private static Spreadsheets.Table addMissingTranslationKeys(Path dataDir, Spreadsheets.Table table) throws IOException {
        Set<String> present = new HashSet<>();
        for (List<String> row : table.rows()) present.add(Spreadsheets.cell(row, 0));
        List<List<String>> rows = new ArrayList<>(table.rows());
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, String[]> e : Translations.DEFAULTS.entrySet()) {
            if (present.contains(e.getKey())) continue;
            List<String> row = new ArrayList<>();
            row.add(e.getKey());
            for (int c = 1; c < table.header().size(); c++) {
                String lang = Spreadsheets.languageCode(table.header().get(c));
                row.add(lang.equals("pt") ? e.getValue()[0] : lang.equals("en") ? e.getValue()[1] : "");
            }
            rows.add(row);
            added.add(e.getKey());
        }
        if (added.isEmpty() || table.header().isEmpty()) return table;
        Spreadsheets.write(xlsx(dataDir, "translations"), "Translations", table.header(), rows);
        System.out.println("Added new keys to " + xlsx(dataDir, "translations") + ": " + added);
        return new Spreadsheets.Table(table.header(), rows);
    }

    /** data/&lt;base&gt;.xlsx */
    static Path xlsx(Path dataDir, String base) {
        return dataDir.resolve(base + ".xlsx");
    }

    /** Copies the bundled sounds unless a file with that name (any supported extension) already exists. */
    private static void installDefaultSounds(Path dir) throws IOException {
        Files.createDirectories(dir);
        for (String name : List.of("pirililli", "click")) {
            if (soundFile(dir, name) != null) continue;
            try (InputStream in = Catalog.class.getResourceAsStream("/defaults/sounds/" + name + ".mp3")) {
                Files.copy(Objects.requireNonNull(in), dir.resolve(name + ".mp3"));
            }
        }
    }

    /** File name (relative to data/sounds) of the sound with this base name, or null. */
    static String soundFile(Path dir, String baseName) {
        for (String ext : SOUND_EXTENSIONS) {
            if (Files.isRegularFile(dir.resolve(baseName + "." + ext))) return baseName + "." + ext;
        }
        return null;
    }

    /** Creates the voters sheet or appends freshly generated IDs until it holds {@code count} voters. */
    private static void ensureVoters(Path dataDir, int count) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        if (Files.exists(xlsx(dataDir, "voters"))) {
            for (List<String> row : Spreadsheets.read(xlsx(dataDir, "voters")).rows()) {
                String id = normalize(Spreadsheets.cell(row, 0), 6);
                if (id != null) ids.add(id);
                rows.add(new ArrayList<>(List.of(id == null ? Spreadsheets.cell(row, 0) : id,
                        Spreadsheets.cell(row, 1), Spreadsheets.cell(row, 2))));
            }
        }
        if (ids.size() >= count) return;
        SecureRandom rnd = new SecureRandom();
        int added = 0;
        while (ids.size() < count) {
            String id = String.valueOf(100000 + rnd.nextInt(900000));
            if (ids.add(id)) {
                rows.add(List.of(id, "no", ""));
                added++;
            }
        }
        Spreadsheets.write(xlsx(dataDir, "voters"), "Voters", VOTER_HEADER, rows);
        System.out.println("Generated " + added + " new voter IDs in " + xlsx(dataDir, "voters").toAbsolutePath());
    }

    /** Digits only, left padded with zeros to {@code len}; null if not a valid number of that length. */
    static String normalize(String s, int len) {
        if (s == null) return null;
        s = s.trim();
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        if (s.isEmpty() || !s.chars().allMatch(Character::isDigit) || s.length() > len) return null;
        return "0".repeat(len - s.length()) + s;
    }

    private static boolean truthy(String s) {
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "yes", "y", "true", "1", "x", "sim", "s", "ja", "j", "gringo" -> true;
            default -> false;
        };
    }
}
