# Pirililli 🇧🇷🗳️

A party voting machine styled after the Brazilian *urna eletrônica*, for voting on the "achievements" of the year.
It's a web app (Vaadin + Spring Boot) that runs on your computer and is used from iPads or any browser on the same
Wi-Fi. Votes are stored in PostgreSQL running in Docker.

## Quick start

Requirements: Java 21+ and Docker (Docker Desktop, or Colima + `docker-compose`). The first build downloads
Node.js automatically for the Vaadin frontend build.

```bash
./start.sh
```

This starts the Postgres container, builds the jar if needed and starts the server. The console prints the
addresses to open, e.g. `http://192.168.1.20:8080`.

On first run it creates `config.properties` and a `data/` folder with sample CSV tables, pictures, sounds and voter IDs.

## Setting up an iPad

1. Connect the iPad to the same Wi-Fi as the computer running Pirililli.
2. Open the printed address (`http://<computer-ip>:8080`) in **Safari**.
3. Tap Share → **Add to Home Screen**, then open it from the home screen. It runs fullscreen without the Safari UI.
4. Lock the iPad to the app with **Settings → Accessibility → Guided Access** (triple-click to start/stop).
5. Turn the ring/silent switch off and the volume up. Sounds start working after the first tap (an iOS rule).

The app also asks the browser to keep the screen awake. If the screen still dims, set Settings → Display → Auto-Lock to *Never*.
Several iPads can vote at the same time.

If the computer's firewall asks whether Java may accept incoming connections, allow it.

## Configuration

| File | Content |
|------|---------|
| `data/candidates.csv`   | **Number** (2 digits) · **Name** · **Party** (optional) · **Picture** (file name in `data/pictures/`) |
| `data/questions.csv`    | One column per language, with the **language code as header** (`pt`, `en`, …) · **candidates** (comma separated numbers, empty = all) |
| `data/translations.csv` | All UI texts: **key** · `pt` · `en` · … Edit the texts, or add a column to add a language |
| `data/voters.csv`       | **Voter ID** (6 digits) · **Gringo** (`yes`/`no`) · Name / note (optional) |
| `data/pictures/`         | Candidate photos (png/jpg), cropped to fit the portrait frame |
| `data/sounds/`           | `pirililli.mp3` (played on CONFIRMA) and `click.mp3` (every key press) |
| `config.properties`      | Port, DB connection, `voters-count`, `thanks-seconds`, `default-language`, `gringo-language` |

**Editing the CSV files:** open them with Numbers (right-click → Open With → Numbers), Excel, LibreOffice or any
text editor. In Numbers, save with **File → Export To → CSV…** (UTF-8) and overwrite the original file. A plain
⌘S would create a separate `.numbers` file, which the app ignores. Comma- and semicolon-separated files both work.
Leading zeros that Numbers drops (e.g. `012345` → `12345`) are added back automatically.
Old `.xlsx` files from earlier versions are converted to `.csv` once and moved to `data/old-xlsx/`.

**Languages:** everyone sees `pirililli.default-language` (default `pt`). Voters marked as gringo see
`pirililli.gringo-language` (default `en`). Texts missing in a language fall back to the default language.
The keypad labels and the header plate (`key_*` and `header` in translations.csv) always use the default
language, because they're "printed" on the machine.

**Sounds:** replace `data/sounds/pirililli.mp3` and `data/sounds/click.mp3` with your own recordings.
`.mp3`, `.m4a`, `.aac`, `.wav` and `.ogg` work (e.g. `pirililli.wav`; delete the old file so only one has that name).
Reload the page on the iPads after replacing them. Pictures can also be replaced while it's running.
Changes to the CSV files need a restart.

**Voter IDs:** set `pirililli.voters-count`. On startup, if `voters.csv` has fewer IDs than that, new random 6-digit
IDs are generated and appended. Existing IDs are kept.

**`000000` is always a valid voter ID**, even if it's not in `voters.csv`. Like every ID, it can vote once per
session. Add it to `voters.csv` with `yes` if you want it to be a gringo.

Questions are asked in the order of the rows.

## Voting

| Action | Tap / click | Keyboard |
|--------|-------------|----------|
| Type a digit | number keys | `0`–`9` / numpad |
| **CONFIRMA** | green | `Enter` |
| **CORRIGE** (clear) | orange | `Backspace` / `Delete` |

1. Enter a valid 6-digit voter ID and confirm. Each ID can vote once per session; it's used up as soon as the ballot starts.
2. Each question gets its own ballot. Type a 2-digit candidate number to see their photo, name and party.
   Blank votes are not possible (the BRANCO key does nothing).
   A number that doesn't exist or isn't eligible for the question shows **NÚMERO ERRADO / VOTO NULO** and counts as a null vote, just like the real machine.
3. CONFIRMA records the vote and plays the *pirililili*. After the last question you get **FIM**, and after `thanks-seconds` it returns to the login screen.

## Sessions and results

Every start of the server opens a **new voting session**. Votes and "already voted" markers start fresh, and all
earlier sessions stay in the database (in the `pirililli-data` Docker volume). Votes are stored without the voter ID.

- **Live results page:** `http://<computer-ip>:8080/results` (refreshes every 5 s), or `/results?session=3` for an older session.
- **Command line:**
  ```bash
  java -jar target/pirililli.jar --sessions       # list sessions
  java -jar target/pirililli.jar --results 3      # results of session 3
  java -jar target/pirililli.jar --results        # results of all sessions
  ```
- **SQL:** `docker exec -it pirililli-db psql -U pirililli -c "select * from results"`

The database listens on `localhost:5433` (user/password/db: `pirililli`).
