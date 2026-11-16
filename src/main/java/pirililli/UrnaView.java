package pirililli;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import pirililli.Database.VoteType;
import pirililli.Model.Candidate;
import pirililli.Model.Question;
import pirililli.Model.Voter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The voting machine. Each browser tab / iPad gets its own instance, so several machines can run at once.
 * Keys can be tapped, clicked or typed (digits, Enter = CONFIRMA, Backspace/Delete = CORRIGE, Space/B = BRANCO).
 */
@Route("")
@PageTitle("Pirililli")
@JavaScript("context://js/urna.js")
public class UrnaView extends Div {

    enum UrnaKey {
        D0, D1, D2, D3, D4, D5, D6, D7, D8, D9, BRANCO, CORRIGE, CONFIRMA;

        static UrnaKey digit(int d) { return values()[d]; }
        boolean isDigit() { return ordinal() <= 9; }
        char ch() { return (char) ('0' + ordinal()); }
    }

    private enum Mode { LOGIN, BALLOT, THANKS }

    private final Catalog catalog;
    private final Database db;
    private final Settings settings;
    private final Div screen = new Div();

    private Mode mode = Mode.LOGIN;
    private String typed = "";
    private String loginMessageKey;
    private Voter voter;
    private String lang;
    private int questionIndex;
    private int thanksToken;

    public UrnaView(Catalog catalog, Database db, Settings settings) {
        this.catalog = catalog;
        this.db = db;
        this.settings = settings;
        this.lang = settings.defaultLanguage();
        addClassName("stage");

        Div machine = new Div();
        machine.addClassName("machine");
        Div urna = new Div(machine);
        urna.addClassName("urna");
        add(urna);

        machine.add(div("case"));
        for (int x = 70; x < 1000; x += 78) machine.add(at(div("ridge"), x, null));
        machine.add(div("bezel"));
        screen.addClassName("screen");
        machine.add(screen);
        machine.add(div("panel"), div("plate"));
        Div seal = div("seal");
        seal.setText("★");
        Div plateText = div("plate-text");
        plateText.getElement().setProperty("innerHTML",
                escape(tr("header")).replaceFirst(" ", "<br>"));
        machine.add(seal, plateText, div("well"));

        int x0 = 1095, y0 = 285, dx = 132, dy = 98;
        for (int d = 1; d <= 9; d++) {
            machine.add(key(UrnaKey.digit(d), String.valueOf(d), "digit", x0 + (d - 1) % 3 * dx, y0 + (d - 1) / 3 * dy, 112, 78));
        }
        machine.add(key(UrnaKey.D0, "0", "digit", x0 + dx, y0 + 3 * dy, 112, 78));
        machine.add(key(UrnaKey.BRANCO, tr("key_blank"), "action blank", 1075, 700, 132, 84));
        machine.add(key(UrnaKey.CORRIGE, tr("key_correct"), "action correct", 1222, 700, 132, 84));
        machine.add(key(UrnaKey.CONFIRMA, tr("key_confirm"), "action confirm", 1369, 700, 132, 84));

        registerKeyboard();
        render();
    }

    @Override
    protected void onAttach(AttachEvent event) {
        Path dir = catalog.dataDir.resolve("sounds");
        getElement().executeJs("window.urna && window.urna.init($0, $1)", soundUrl(dir, "pirililli"), soundUrl(dir, "click"));
    }

    private static String soundUrl(Path dir, String name) {
        String file = Catalog.soundFile(dir, name);
        if (file == null) return "";
        long version = 0;
        try {
            version = Files.getLastModifiedTime(dir.resolve(file)).toMillis();
        } catch (Exception ignored) {
        }
        return "media/sounds/" + URLEncoder.encode(file, StandardCharsets.UTF_8) + "?v=" + version;
    }

    private void registerKeyboard() {
        Key[] digits = {Key.DIGIT_0, Key.DIGIT_1, Key.DIGIT_2, Key.DIGIT_3, Key.DIGIT_4,
                Key.DIGIT_5, Key.DIGIT_6, Key.DIGIT_7, Key.DIGIT_8, Key.DIGIT_9};
        Key[] numpad = {Key.NUMPAD_0, Key.NUMPAD_1, Key.NUMPAD_2, Key.NUMPAD_3, Key.NUMPAD_4,
                Key.NUMPAD_5, Key.NUMPAD_6, Key.NUMPAD_7, Key.NUMPAD_8, Key.NUMPAD_9};
        for (int d = 0; d <= 9; d++) {
            UrnaKey k = UrnaKey.digit(d);
            Shortcuts.addShortcutListener(this, () -> press(k), digits[d]);
            Shortcuts.addShortcutListener(this, () -> press(k), numpad[d]);
        }
        Shortcuts.addShortcutListener(this, () -> press(UrnaKey.CONFIRMA), Key.ENTER);
        Shortcuts.addShortcutListener(this, () -> press(UrnaKey.CORRIGE), Key.BACKSPACE);
        Shortcuts.addShortcutListener(this, () -> press(UrnaKey.CORRIGE), Key.DELETE);
        Shortcuts.addShortcutListener(this, () -> press(UrnaKey.BRANCO), Key.SPACE);
        Shortcuts.addShortcutListener(this, () -> press(UrnaKey.BRANCO), Key.KEY_B);
    }

    // ------------------------------------------------------------------ input

    private void press(UrnaKey k) {
        switch (mode) {
            case LOGIN -> loginKey(k);
            case BALLOT -> ballotKey(k);
            case THANKS -> { }
        }
        render();
    }

    private void loginKey(UrnaKey k) {
        loginMessageKey = null;
        if (k.isDigit()) {
            if (typed.length() < 6) typed += k.ch();
        } else if (k == UrnaKey.CORRIGE) {
            typed = "";
        } else if (k == UrnaKey.CONFIRMA && typed.length() == 6) {
            Voter v = catalog.voters.get(typed);
            typed = "";
            if (v == null) {
                loginMessageKey = "invalid_voter";
            } else if (!db.claimVoter(v.id())) {
                loginMessageKey = "already_voted";
            } else {
                startBallot(v);
            }
        }
    }

    private void startBallot(Voter v) {
        voter = v;
        lang = settings.languageFor(v);
        questionIndex = 0;
        typed = "";
        if (catalog.questions.isEmpty()) finish();
        else mode = Mode.BALLOT;
    }

    private Question question() {
        return catalog.questions.get(questionIndex);
    }

    /** The selected candidate, or null if the number is incomplete or not eligible for this question. */
    private Candidate selected() {
        if (typed.length() != 2 || !question().allows(typed)) return null;
        return catalog.candidates.get(typed);
    }

    private void ballotKey(UrnaKey k) {
        // BRANCO is intentionally ignored: blank votes are not allowed
        if (k.isDigit()) {
            if (typed.length() < 2) typed += k.ch();
        } else if (k == UrnaKey.CORRIGE) {
            typed = "";
        } else if (k == UrnaKey.CONFIRMA) {
            if (typed.length() != 2) return; // nothing to confirm yet
            Question q = question();
            String stored = q.text(settings.defaultLanguage(), settings.defaultLanguage());
            Candidate c = selected();
            if (c != null) db.recordVote(q.no(), stored, VoteType.CANDIDATE, c.number(), c.name());
            else db.recordVote(q.no(), stored, VoteType.NULL, typed, null);
            getElement().executeJs("window.urna && window.urna.play('confirm')");
            typed = "";
            questionIndex++;
            if (questionIndex >= catalog.questions.size()) finish();
        }
    }

    private void finish() {
        mode = Mode.THANKS;
        int token = ++thanksToken;
        // countdown runs in the browser, then calls back to return to the login screen
        getElement().executeJs("""
                const host = this, token = $0, template = $1;
                let left = $2;
                const tick = () => {
                  const el = host.querySelector('.countdown');
                  if (el) el.textContent = template.replace('%d', left);
                  if (left <= 0) { host.$server.thanksDone(token); return; }
                  left--; setTimeout(tick, 1000);
                };
                setTimeout(tick, 0);""", token, tr("returning"), settings.thanksSeconds());
    }

    @ClientCallable
    private void thanksDone(int token) {
        if (mode != Mode.THANKS || token != thanksToken) return;
        mode = Mode.LOGIN;
        voter = null;
        lang = settings.defaultLanguage();
        typed = "";
        render();
    }

    // ------------------------------------------------------------------ rendering

    private void render() {
        screen.removeAll();
        switch (mode) {
            case LOGIN -> renderLogin();
            case BALLOT -> renderBallot();
            case THANKS -> renderThanks();
        }
    }

    private void renderLogin() {
        screen.add(centered(tr("login_title"), 70, 44, true));
        screen.add(centered(tr("login_hint"), 140, 24, false));
        int bw = 72, gap = 12;
        screen.add(boxes(typed, 6, (880 - (6 * bw + 5 * gap)) / 2, 215, bw, 96, gap));
        if (loginMessageKey != null) {
            Span msg = centered(tr(loginMessageKey), 390, 34, true);
            msg.addClassName("message");
            screen.add(msg);
        }
        screen.add(footer(tr("press_key"), tr("green_confirm_short"), tr("orange_correct_short")));
    }

    private void renderBallot() {
        Question q = question();
        screen.add(text(tr("your_vote_for"), 30, 24, 24, true));
        Span progress = text((questionIndex + 1) + "/" + catalog.questions.size(), 0, 27, 20, false);
        progress.getStyle().remove("left").set("right", u(30));
        screen.add(progress);

        String qText = q.text(lang, settings.defaultLanguage());
        Div question = new Div();
        question.addClassName("question");
        question.setText(qText);
        int len = qText.length();
        question.getStyle().set("font-size", u(len <= 30 ? 42 : len <= 48 ? 36 : len <= 70 ? 30 : 26));
        screen.add(question);

        Div picture = div("picture");
        Candidate c = selected();
        if (c != null && !c.picture().isBlank()) {
            picture.add(new Image("media/pictures/" + URLEncoder.encode(c.picture(), StandardCharsets.UTF_8).replace("+", "%20"), c.name()));
        }
        screen.add(picture);

        screen.add(text(tr("number"), 30, 268, 26, false));
        screen.add(boxes(typed, 2, 160, 235, 74, 96, 10));

        if (c != null) {
            Div details = div("details");
            details.add(span("label", tr("name")), span("value", c.name()));
            if (!c.party().isBlank()) details.add(span("label", tr("party")), span("value", c.party()));
            screen.add(details);
        } else if (typed.length() == 2) {
            screen.add(text(tr("wrong_number"), 30, 362, 30, true));
            screen.add(centeredIn(tr("null_vote"), 0, 580, 430, 52, true));
        }
        if (typed.length() == 2) {
            screen.add(footer(tr("press_key"), tr("green_confirm"), tr("orange_correct")));
        }
    }

    private void renderThanks() {
        screen.add(centered(tr("end"), 105, 220, true));
        screen.add(centered(tr("thanks"), 395, 40, true));
        Span countdown = centered(tr("returning").replace("%d", String.valueOf(settings.thanksSeconds())), 585, 20, false);
        countdown.addClassName("countdown");
        countdown.getStyle().set("color", "#555");
        screen.add(countdown);
    }

    // ------------------------------------------------------------------ helpers

    private String tr(String key) {
        return catalog.translations.get(lang, key);
    }

    static String u(double v) {
        return "calc(var(--u) * " + v + ")";
    }

    private static Div div(String cls) {
        Div d = new Div();
        d.addClassName(cls);
        return d;
    }

    private static Span span(String cls, String text) {
        Span s = new Span(text);
        s.addClassName(cls);
        return s;
    }

    private static <T extends Component & com.vaadin.flow.component.HasStyle> T at(T c, Integer x, Integer y) {
        if (x != null) c.getStyle().set("left", u(x));
        if (y != null) c.getStyle().set("top", u(y));
        return c;
    }

    private static Span text(String s, int x, int y, int size, boolean bold) {
        Span t = span("t", s);
        if (bold) t.addClassName("b");
        t.getStyle().set("left", u(x)).set("top", u(y)).set("font-size", u(size));
        return t;
    }

    private static Span centered(String s, int y, int size, boolean bold) {
        return centeredIn(s, 0, 880, y, size, bold);
    }

    private static Span centeredIn(String s, int x, int w, int y, int size, boolean bold) {
        Span t = text(s, x, y, size, bold);
        t.addClassName("center");
        t.getStyle().set("width", u(w));
        return t;
    }

    private Div key(UrnaKey k, String label, String classes, int x, int y, int w, int h) {
        Div d = new Div();
        d.setText(label);
        d.addClassName("key");
        for (String c : classes.split(" ")) d.addClassName(c);
        d.getStyle().set("left", u(x)).set("top", u(y)).set("width", u(w)).set("height", u(h));
        d.addClickListener(e -> press(k));
        return d;
    }

    private static Div boxes(String digits, int count, int x, int y, int w, int h, int gap) {
        Div wrap = new Div();
        for (int i = 0; i < count; i++) {
            Div b = div("box");
            b.getStyle().set("left", u(x + i * (w + gap))).set("top", u(y)).set("width", u(w)).set("height", u(h))
                    .set("font-size", u(h * 0.8));
            if (i < digits.length()) b.setText(String.valueOf(digits.charAt(i)));
            else if (i == digits.length()) b.addClassName("cursor");
            wrap.add(b);
        }
        return wrap;
    }

    private static Div footer(String press, String green, String orange) {
        Div f = div("footer");
        Div l2 = new Div(new Span(green));
        l2.addClassName("indent");
        Div l3 = new Div(new Span(orange));
        l3.addClassName("indent");
        f.add(new Div(new Span(press)), l2, l3);
        return f;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
