# DriverSim — Sim Racing Telemetry & Analysis

![Java](https://img.shields.io/badge/Java-21%2B-orange?style=flat-square&logo=openjdk)
![JavaFX](https://img.shields.io/badge/JavaFX-21.0.6-blue?style=flat-square)
![Maven](https://img.shields.io/badge/Maven-3.8%2B-red?style=flat-square&logo=apachemaven)
![SQLite](https://img.shields.io/badge/Storage-SQLite-lightblue?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)
![AI](https://img.shields.io/badge/AI-Ollama%20%28optional%29-purple?style=flat-square)

A JavaFX 21 desktop application for sim racers to log, analyse, and optimise their performance across **iRacing**, **Assetto Corsa Competizione**, and **Le Mans Ultimate** — plus a full manual lap-logging garage. Dark racing-cockpit UI, local SQLite storage, and an optional AI race engineer.

---

## Features

### Live Dashboard
- **Stopwatch / lap timer** — Start/Stop/Reset with live `m:ss.mmm` display; stopping auto-fills the lap time input in the Garage tab
- **KPI bar** — always-visible header strip showing Best Lap, Last Lap, Total Laps, and current session key
- **Stat cards** — Best Lap (red accent), Last Lap, Average Lap, Consistency (±std deviation in seconds)
- **Track State badge** — GREEN / YELLOW / RED driven by lap-to-lap standard deviation
- **Recent laps table** — newest first, fastest lap highlighted in gold, delta splits colour-coded green/red
- **Session stats panel** — total laps, cumulative track time, gap to best, live wall clock
- **Race events log** — auto-populated on every lap: personal bests in amber, faster laps in green, slow laps flagged

### Garage — Manual Lap Logging
- **Flexible time input** — accepts `1:24.500`, `1.24.500`, or raw milliseconds (`84500`)
- **Sector times** — optional S1/S2/S3 per lap; best sector column highlighted in purple; **Theoretical Best** calculated automatically
- **Weather/condition tagging** — DRY / DAMP / WET / COLD / HOT with colour-coded pill badges
- **Per-track, per-car tabs** — GT3 and Formula laps kept in separate tables per track; unlimited tracks
- **Dynamic setup sliders** — essential controls always visible, advanced settings in an expandable section
- **Lap trend chart** — animated line chart per track/car combination
- **Column editor** — choose exactly which setup columns appear in the table and exports
- **Export** — CSV or landscape A4 PDF via native save dialog
- **AI Race Engineer** — sends all lap + setup data to a local Ollama LLM; returns setup advice in bullet points

### Lap Analysis
- **Select any 2 laps** from any track, car type, or platform via dropdown
- **Side-by-side diff table** — every setup parameter compared with colour coding:
  - Green = value on the faster lap
  - Red = value on the slower lap
  - Gray = identical
- **Plain-English verdict** — *"Lap A was faster by 0.831s. Key differences: ABS (4 vs 8), TC (3 vs 6)."*
- Works across GT3 and Formula and platform-imported laps

### Fuel Strategy Calculator
- **Inputs** — race laps, tank capacity, fuel per lap, safety car laps, planned pit stops
- **Fuel per lap auto-estimated** from current session data when available
- **Result cards** — total fuel required, fuel per stint, number of stints, safety margin (colour-coded)
- **Visual fuel bar** — green → amber → red as load approaches tank capacity
- **Stint plan table** — start lap, end lap, pit lap, fuel load at stint start, fuel at pit window, action
- **Warnings** if fuel deficit detected, with number of extra pit stops required

### Platforms — iRacing / ACC / LMU
Connect your sim-racing accounts or import local result files to pull race data directly into DriverSim.

#### iRacing
- **Account login** — SHA-256 password hashing per iRacing customer API specification; session cookie persisted for the run
- **Sync recent races** — fetches via the two-step iRacing data API (initial link → S3 data)
- **Driver stats panel** — iRating, Safety Rating, license class, total races, wins, podiums, average finish
- **Import `.ibt` telemetry** — extracts car name, track, and session info from binary header
- **Import CSV export** — generic iRacing race-result CSV

#### Assetto Corsa Competizione
- No public API — file import only
- **Import single session** — parses `*.json` result files from `~/Documents/Assetto Corsa Competizione/Results/`
- **Scan results folder** — batch-imports all JSON files in the directory
- **Import MoTeC CSV** — channel export from MoTeC i2 / i2 Pro
- Extracts track, car model, session type, finish position, best lap, and sector bests

#### Le Mans Ultimate / rF2
- **Import result files** — XML (native rF2 format) and JSON; multi-file selection supported
- **Live API fetch** — optional GET against `http://localhost:5397/rest/sessions` (rF2 REST plugin, when game is running)
- Parses track name, car, session type, driver count, and best lap times from XML slot data

All imported sessions are stored in SQLite and displayed in session/lap tables within each platform tab.

---

## Storage — SQLite

All data is persisted to `~/.drivesim/drivesim.db` (created automatically on first run).

**One-time CSV migration**: if `telemetry_save.csv` exists and the database is empty, all laps are migrated automatically. The CSV is renamed to `telemetry_save.csv.migrated` afterwards.

### Schema

| Table | Contents |
|---|---|
| `laps` | Every logged lap — track, time, sectors, weather, vehicle FK, platform |
| `vehicles` | Car make, model, type, year, setup JSON |
| `sessions` | Platform sessions — track, car, date, type, finish position, best lap, sector bests |
| `driver_stats` | iRating, safety rating, license class, race results per platform |
| `platform_accounts` | Saved email / display name per platform (no passwords stored) |
| `car_setups` | Key-value setup params imported from platform files |

---

## Architecture — MVC with FXML

The codebase follows a clean MVC pattern. Each tab is an independent FXML + controller pair; `MainController` wires them together via the `fx:include` nested-controller pattern.

```
src/main/java/org/example/drivesimui/
├── Launcher.java                             — FXMLLoader entry point (~30 lines)
├── model/
│   ├── LapTime.java                          — Single lap record (id, track, time, sectors, delta, weather)
│   ├── Vehicle.java                          — Abstract base for all car types
│   ├── GT3Car.java                           — ABS, TC, brake bias, suspension, tyre pressures, fuel
│   ├── FormulaCar.java                       — Wings, ERS, DRS, differential, fuel
│   ├── RaceSession.java                      — Platform session container (track, car, date, results)
│   ├── DriverProfile.java                    — iRating, safety rating, race stats per platform
│   ├── PlatformCredentials.java              — Platform enum + email (no password field)
│   └── CarSetup.java                         — Generic key-value setup from platform imports
├── controller/
│   ├── MainController.java                   — Root controller; owns KPI bar; wires sub-controllers
│   ├── GarageController.java                 — Lap logging, setup sliders, charts, AI engineer
│   ├── DashboardController.java              — Stopwatch, stat cards, events log, table
│   ├── LapComparisonController.java          — Side-by-side diff table and verdict
│   ├── FuelStrategyController.java           — Stint planner and fuel bar
│   └── platforms/
│       ├── PlatformsController.java          — Outer platforms tab; injects repos into sub-controllers
│       ├── IRacingController.java            — iRacing connect, sync, import
│       ├── ACCController.java                — ACC file import
│       └── LMUController.java                — LMU file import + live API
├── service/
│   ├── PlatformService.java                  — Common interface
│   ├── IRacingService.java                   — Cookie auth, two-step data fetch, ibt/CSV import
│   ├── ACCService.java                       — JSON session parser, folder scanner, MoTeC CSV
│   ├── LMUService.java                       — XML/JSON parser, rF2 REST client
│   ├── IbtImporter.java                      — iRacing .ibt binary header reader
│   └── CsvRaceResultImporter.java            — Generic CSV lap/session importer
├── db/
│   ├── DatabaseManager.java                  — SQLite singleton; creates schema on first run
│   ├── LapRepository.java                    — insert, loadAll, findByTrackAndCar, count
│   └── SessionRepository.java               — insert, loadByPlatform, savePlatformAccount, loadPlatformEmail
└── util/
    ├── TimeFormatter.java                    — formatMs(long) → "m:ss.mmm"; parseTimeInput(String) → ms
    └── AIClient.java                         — HTTP client for local Ollama API

src/main/resources/org/example/drivesimui/
├── dark-theme.css
└── fxml/
    ├── main.fxml                             — BorderPane + KPI bar + TabPane with fx:include per tab
    ├── dashboard.fxml
    ├── garage.fxml
    ├── lap-comparison.fxml
    ├── fuel-strategy.fxml
    └── platforms/
        ├── platforms.fxml
        ├── iracing.fxml
        ├── acc.fxml
        └── lmu.fxml
```

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| JavaFX | 21.0.6 (pulled by Maven) |
| Maven | 3.8+ (wrapper included) |
| Ollama | any — optional, only for AI analysis |

---

## Running

```bash
./mvnw javafx:run
```

Windows:

```bat
mvnw.cmd javafx:run
```

The first run downloads all dependencies automatically. The SQLite database is created at `~/.drivesim/drivesim.db`.

---

## AI Race Engineer (optional)

```bash
# Install Ollama from https://ollama.com, then:
ollama pull llama3
ollama serve
```

Click **Analyze Session** on any track tab in the Garage. If Ollama is not running, the button shows a "Radio failure" message and the rest of the app is unaffected.

---

## Car Setup Fields

### GT3
| Field | Range | Default |
|---|---|---|
| ABS | 0 – 11 | 6 |
| Traction Control | 0 – 11 | 6 |
| Brake Bias % | 50 – 65 | 56.5 |
| Front Suspension | 1 – 10 | 5 |
| Rear Suspension | 1 – 10 | 5 |
| Tyre Pressure Front | 25 – 32 PSI | 27.5 |
| Tyre Pressure Rear | 25 – 32 PSI | 27.5 |
| Fuel Load | 0 – 120 L | 60 |

### Formula
| Field | Range | Default |
|---|---|---|
| Front Wing Angle | 0 – 15° | 10 |
| Rear Wing Angle | 0 – 15° | 10 |
| ERS Deploy Mode | 0 (None) – 4 (Overtake) | 3 (High) |
| DRS | On / Off | On |
| Differential Entry | 50 – 100 % | 60 |
| Differential Exit | 50 – 100 % | 70 |
| Fuel Load | 0 – 110 kg | 50 |

---

## Platform Integration Notes

### iRacing
- Password is never stored. The SHA-256 hash of `password + lowercase(email)` is sent to the iRacing auth endpoint per their published API spec.
- The session cookie is held in memory for the duration of the app session only.
- Data fetch uses iRacing's two-step pattern: the first GET returns a signed S3 link; the second GET fetches the actual data.

### ACC
- There is no public ACC API. The "account" section stores a display name only.
- Result JSON files use the naming convention `TrackName_YYYYMMDD_HHMMSS.json`. The track name and date are extracted automatically.
- Sector bests are read from `sessionResult.leaderBoardLines[0].timing.bestSplits`.

### LMU / rF2
- XML result files use the standard rF2 `<Slot BestLapTime="...">` format. Best lap is the minimum across all driver slots.
- The live REST API (`/rest/sessions`) requires the rF2 REST plugin to be active while the game is running. The default URL `http://localhost:5397` can be changed in the LMU tab.

---

## Data Files

| Location | Description |
|---|---|
| `~/.drivesim/drivesim.db` | SQLite database — all laps, sessions, driver stats |
| `telemetry_save.csv.migrated` | Renamed after one-time CSV → SQLite migration |
| Exported `.csv` / `.pdf` | User-chosen location via save dialog |
