package pirililli;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** UI texts per language, from the translations sheet (key | pt | en | ...), with built-in fallbacks. */
final class Translations {
    /** key → {pt, en}. Also used to create the translations sheet and to fill keys missing from it. */
    static final Map<String, String[]> DEFAULTS = new LinkedHashMap<>();

    static {
        def("header", "JUSTIÇA ELEITORAL", "JUSTIÇA ELEITORAL");
        def("login_title", "TÍTULO DE ELEITOR", "VOTER ID");
        def("login_hint", "Digite o número do seu título", "Type your voter ID number");
        def("invalid_voter", "TÍTULO INVÁLIDO", "INVALID VOTER ID");
        def("already_voted", "ESTE TÍTULO JÁ VOTOU", "THIS VOTER ID HAS ALREADY VOTED");
        def("your_vote_for", "SEU VOTO PARA", "YOUR VOTE FOR");
        def("number", "Número:", "Number:");
        def("name", "Nome:", "Name:");
        def("party", "Partido:", "Party:");
        def("press_key", "Aperte a tecla:", "Press the key:");
        def("green_confirm", "VERDE para CONFIRMAR este voto", "GREEN to CONFIRM this vote");
        def("orange_correct", "LARANJA para CORRIGIR este voto", "ORANGE to CORRECT this vote");
        def("green_confirm_short", "VERDE para CONFIRMAR", "GREEN to CONFIRM");
        def("orange_correct_short", "LARANJA para CORRIGIR", "ORANGE to CORRECT");
        def("wrong_number", "NÚMERO ERRADO", "WRONG NUMBER");
        def("null_vote", "VOTO NULO", "NULL VOTE");
        def("blank_vote", "VOTO EM BRANCO", "BLANK VOTE");
        def("end", "FIM", "END");
        def("thanks", "Obrigado por votar!", "Thank you for voting!");
        def("returning", "Voltando ao início em %d s", "Returning to start in %d s");
        def("key_blank", "BRANCO", "BLANK");
        def("key_correct", "CORRIGE", "CORRECT");
        def("key_confirm", "CONFIRMA", "CONFIRM");
        def("results", "RESULTADOS", "RESULTS");
        def("session", "Sessão", "Session");
        def("blank_votes", "Brancos", "Blank");
        def("null_votes", "Nulos", "Null");
        def("total", "Total", "Total");
        def("no_votes", "Nenhum voto ainda", "No votes yet");
    }

    private static void def(String key, String pt, String en) {
        DEFAULTS.put(key, new String[]{pt, en});
    }

    private final Map<String, Map<String, String>> byLang = new HashMap<>();
    private final String fallbackLang;

    Translations(Spreadsheets.Table table, String fallbackLang) {
        this.fallbackLang = fallbackLang;
        List<String> header = table.header();
        for (List<String> row : table.rows()) {
            String key = Spreadsheets.cell(row, 0);
            if (key.isEmpty()) continue;
            for (int c = 1; c < header.size(); c++) {
                String lang = Spreadsheets.languageCode(header.get(c));
                String text = Spreadsheets.cell(row, c);
                if (!lang.isEmpty() && !text.isEmpty()) byLang.computeIfAbsent(lang, l -> new HashMap<>()).put(key, text);
            }
        }
    }

    String get(String lang, String key) {
        String t = byLang.getOrDefault(lang, Map.of()).get(key);
        if (t == null) t = byLang.getOrDefault(fallbackLang, Map.of()).get(key);
        if (t == null && DEFAULTS.containsKey(key)) t = DEFAULTS.get(key)["en".equals(lang) ? 1 : 0];
        return t == null ? key : t;
    }
}
