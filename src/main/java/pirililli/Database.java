package pirililli;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pirililli.Model.Voter;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;

/**
 * PostgreSQL storage. Every application start opens a new voting session, so votes and
 * "already voted" markers start fresh while all earlier sessions stay in the database.
 * Votes are stored without the voter ID to keep the ballot secret.
 */
@Component
class Database {
    private static final String[] SCHEMA = {
            """
            CREATE TABLE IF NOT EXISTS voting_session (
                id          SERIAL PRIMARY KEY,
                started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                ended_at    TIMESTAMPTZ
            )""",
            """
            CREATE TABLE IF NOT EXISTS voter (
                voter_id    TEXT PRIMARY KEY,
                gringo      BOOLEAN NOT NULL DEFAULT FALSE,
                label       TEXT
            )""",
            """
            CREATE TABLE IF NOT EXISTS session_voter (
                session_id  INT  NOT NULL REFERENCES voting_session(id),
                voter_id    TEXT NOT NULL REFERENCES voter(voter_id),
                voted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
                PRIMARY KEY (session_id, voter_id)
            )""",
            """
            CREATE TABLE IF NOT EXISTS vote (
                id               BIGSERIAL PRIMARY KEY,
                session_id       INT  NOT NULL REFERENCES voting_session(id),
                question_no      INT  NOT NULL,
                question         TEXT NOT NULL,
                vote_type        TEXT NOT NULL CHECK (vote_type IN ('CANDIDATE', 'BLANK', 'NULL')),
                candidate_number TEXT,
                candidate_name   TEXT,
                cast_at          TIMESTAMPTZ NOT NULL DEFAULT now()
            )""",
            """
            CREATE OR REPLACE VIEW results AS
                SELECT session_id, question_no, question, vote_type, candidate_number, candidate_name, count(*) AS votes
                FROM vote
                GROUP BY session_id, question_no, question, vote_type, candidate_number, candidate_name
                ORDER BY session_id, question_no, votes DESC"""
    };

    enum VoteType { CANDIDATE, BLANK, NULL }

    record Tally(int questionNo, String question, VoteType type, String number, String name, int votes) {}

    record SessionInfo(int id, Timestamp startedAt, Timestamp endedAt, int voters, int votes) {}

    private final JdbcTemplate jdbc;
    private volatile int sessionId = -1;

    Database(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Creates the schema, retrying for a while so the app can start right after `docker compose up`. */
    void init(int timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (true) {
            try {
                for (String sql : SCHEMA) jdbc.execute(sql);
                return;
            } catch (org.springframework.dao.DataAccessException e) {
                if (System.currentTimeMillis() > deadline) throw e;
                System.err.println("Waiting for database (" + e.getMostSpecificCause().getMessage() + ")...");
                Thread.sleep(1000);
            }
        }
    }

    int startSession() {
        sessionId = jdbc.queryForObject("INSERT INTO voting_session DEFAULT VALUES RETURNING id", Integer.class);
        return sessionId;
    }

    int sessionId() {
        return sessionId;
    }

    void endSession() {
        if (sessionId < 0) return;
        try {
            jdbc.update("UPDATE voting_session SET ended_at = now() WHERE id = ?", sessionId);
        } catch (RuntimeException e) {
            System.err.println("Could not close session: " + e.getMessage());
        }
    }

    void syncVoters(Collection<Voter> voters) {
        jdbc.batchUpdate("""
                        INSERT INTO voter (voter_id, gringo, label) VALUES (?, ?, ?)
                        ON CONFLICT (voter_id) DO UPDATE SET gringo = EXCLUDED.gringo, label = EXCLUDED.label""",
                voters.stream().map(v -> new Object[]{v.id(), v.gringo(), v.label()}).toList());
    }

    /** Atomically marks the voter as having voted in this session. Returns false if they already did. */
    boolean claimVoter(String voterId) {
        return jdbc.update("INSERT INTO session_voter (session_id, voter_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                sessionId, voterId) == 1;
    }

    void recordVote(int questionNo, String question, VoteType type, String number, String name) {
        jdbc.update("""
                        INSERT INTO vote (session_id, question_no, question, vote_type, candidate_number, candidate_name)
                        VALUES (?, ?, ?, ?, ?, ?)""",
                sessionId, questionNo, question, type.name(), number, name);
    }

    List<Tally> results(int session) {
        return jdbc.query("""
                        SELECT question_no, question, vote_type, candidate_number, candidate_name, votes
                        FROM results WHERE session_id = ?
                        ORDER BY question_no, votes DESC, candidate_number""",
                (rs, i) -> new Tally(rs.getInt(1), rs.getString(2), VoteType.valueOf(rs.getString(3)),
                        rs.getString(4), rs.getString(5), rs.getInt(6)),
                session);
    }

    List<SessionInfo> sessions() {
        return jdbc.query("""
                        SELECT s.id, s.started_at, s.ended_at,
                               (SELECT count(*) FROM session_voter sv WHERE sv.session_id = s.id),
                               (SELECT count(*) FROM vote v WHERE v.session_id = s.id)
                        FROM voting_session s ORDER BY s.id""",
                (rs, i) -> new SessionInfo(rs.getInt(1), rs.getTimestamp(2), rs.getTimestamp(3), rs.getInt(4), rs.getInt(5)));
    }
}
