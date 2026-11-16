package pirililli;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import pirililli.Database.Tally;
import pirililli.Database.VoteType;
import pirililli.Model.Question;

import java.util.ArrayList;
import java.util.List;

/** Live results for the operator: /results (current session) or /results?session=3. Refreshes every 5 s. */
@Route("results")
@PageTitle("Pirililli – Resultados")
public class ResultsView extends Div implements BeforeEnterObserver {
    private final Catalog catalog;
    private final Database db;
    private final Settings settings;
    private int session;

    public ResultsView(Catalog catalog, Database db, Settings settings) {
        this.catalog = catalog;
        this.db = db;
        this.settings = settings;
        addClassName("results-page");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        session = event.getLocation().getQueryParameters().getSingleParameter("session")
                .map(Integer::parseInt).orElse(db.sessionId());
        event.getUI().setPollInterval(5000);
        event.getUI().addPollListener(e -> render());
        render();
    }

    private String tr(String key) {
        return catalog.translations.get(settings.defaultLanguage(), key);
    }

    private void render() {
        removeAll();
        List<Tally> tallies = db.results(session);
        add(new H1(tr("results")));
        Div sub = new Div(new Span(tr("session") + " #" + session));
        sub.addClassName("sub");
        add(sub);

        Div grid = new Div();
        grid.addClassName("results-grid");
        add(grid);

        // questions as recorded in this session (falls back to current spreadsheet for unanswered ones)
        List<Integer> numbers = new ArrayList<>();
        for (Question q : catalog.questions) numbers.add(q.no());
        for (Tally t : tallies) if (!numbers.contains(t.questionNo())) numbers.add(t.questionNo());

        for (int no : numbers) {
            List<Tally> rows = tallies.stream().filter(t -> t.questionNo() == no).toList();
            String title = rows.isEmpty()
                    ? catalog.questions.get(no - 1).text(settings.defaultLanguage(), settings.defaultLanguage())
                    : rows.get(0).question();
            Div card = new Div(new H2(title));
            card.addClassName("result-card");
            List<Tally> cands = rows.stream().filter(t -> t.type() == VoteType.CANDIDATE).toList();
            int max = cands.stream().mapToInt(Tally::votes).max().orElse(1);
            boolean first = true;
            for (Tally t : cands) {
                Span who = new Span(t.number() + "  " + t.name());
                who.addClassName("who");
                Div bar = new Div();
                bar.addClassName("bar");
                bar.getStyle().set("width", (100.0 * t.votes() / max) + "%");
                Div row = new Div(who, new Div(bar), new Span(String.valueOf(t.votes())));
                row.addClassName("result-row");
                if (first && t.votes() == max) row.addClassName("lead");
                first = false;
                card.add(row);
            }
            if (cands.isEmpty()) card.add(new Div(new Span(tr("no_votes"))));
            int blank = rows.stream().filter(t -> t.type() == VoteType.BLANK).mapToInt(Tally::votes).sum();
            int nul = rows.stream().filter(t -> t.type() == VoteType.NULL).mapToInt(Tally::votes).sum();
            int total = rows.stream().mapToInt(Tally::votes).sum();
            Div foot = new Div(new Span(tr("blank_votes") + ": " + blank + "   ·   " + tr("null_votes") + ": " + nul
                    + "   ·   " + tr("total") + ": " + total));
            foot.addClassName("result-foot");
            card.add(foot);
            grid.add(card);
        }
    }
}
