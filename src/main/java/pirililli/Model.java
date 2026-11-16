package pirililli;

import java.util.List;
import java.util.Map;

/** Plain data types loaded from the spreadsheets. */
final class Model {
    private Model() {}

    record Candidate(String number, String name, String party, String picture) {}

    /**
     * @param texts    question text per language code (e.g. "pt", "en")
     * @param eligible candidate numbers allowed for this question; empty means everyone
     */
    record Question(int no, Map<String, String> texts, List<String> eligible) {
        String text(String lang, String fallbackLang) {
            String t = texts.get(lang);
            if (t == null || t.isBlank()) t = texts.get(fallbackLang);
            if (t == null || t.isBlank()) t = texts.values().stream().filter(s -> !s.isBlank()).findFirst().orElse("?");
            return t;
        }

        boolean allows(String number) {
            return eligible.isEmpty() || eligible.contains(number);
        }
    }

    record Voter(String id, boolean gringo, String label) {}
}
