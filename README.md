# DriverSim — Sim Racing Garage Manager

![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)
![JavaFX](https://img.shields.io/badge/JavaFX-21.0.6-blue?style=flat-square)
![Maven](https://img.shields.io/badge/Maven-3.8%2B-red?style=flat-square&logo=apachemaven)
![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)
![AI](https://img.shields.io/badge/AI-Ollama%20%28optional%29-purple?style=flat-square)

A JavaFX desktop application for sim racers to log, analyse, and optimise lap times.  
Features a live race dashboard, lap comparison tool, fuel strategy calculator, AI race engineer, and full CSV/PDF export — all in a dark racing-cockpit UI.

---

## Features

### Live Dashboard
- **Stopwatch / lap timer** — Start/Stop/Reset with live `m:ss.mmm` display; stopping auto-fills the lap time input
- **KPI cards** — Best Lap (red), Last Lap, Average Lap, Consistency (±std deviation)
- **Track State badge** — GREEN / YELLOW / RED based on lap-to-lap consistency
- **Recent laps table** — newest first, fastest lap highlighted in gold, split deltas green/red
- **Session stats panel** — total laps, track time, gap to best, live wall clock
- **Race events log** — auto-populated on every lap: personal bests in amber, faster laps in green

### Garage (Lap Logging)
- **Flexible time input** — accepts `1.24.500`, `1:24.500`, or raw `84500` ms
- **Per-track, per-car tabs** — GT3 and Formula laps kept in separate tables per track
- **Dynamic setup sliders** — essential controls always visible; advanced settings behind expandable section
- **Live delta splits** — time gained/lost vs previous lap, colour coded
- **Lap trend chart** — animated line chart per track/car with glowing accent line
- **Column editor** — choose exactly which setup columns appear in table and exports
- **Export to CSV or PDF** — native save dialog; PDF is landscape A4

### Lap Analysis (Comparison Tool)
- **Select any 2 laps** from any track or car type via dropdown
- **Side-by-side diff table** — every setup parameter compared with colour coding:
  - 🟢 Green = value on the faster lap
  - 🔴 Red = value on the slower lap
  - ⚪ Gray = identical
- **Plain-English verdict** — *"Lap A was faster by 0.831s. Key differences: ABS (4 vs 8), TC (3 vs 6)."*
- Works across GT3 and Formula via polymorphic `Vehicle` API — no car-type-specific code

### Fuel Strategy Calculator
- **Inputs** — race laps, tank capacity, fuel per lap, safety car laps, planned pit stops
- **Fuel per lap auto-estimated** from logged session data
- **Result cards** — total fuel, fuel per stint, stints, safety margin (colour coded)
- **Visual fuel bar** — green → amber → red as load approaches tank limit
- **Stint plan table** — start lap, end lap, pit lap, fuel load, fuel at pit window, action
- **Warnings** if fuel deficit detected, with suggested fix

### AI Race Engineer (optional)
- Sends all lap + setup data as JSON to a local [Ollama](https://ollama.com) LLM
- Returns 3 bullet points of setup advice
- Graceful "Radio failure" fallback if Ollama is not running

### Data & Persistence
- **Auto-save / auto-load** — session restored automatically on next launch (`telemetry_save.csv`)
- **Export** — CSV or PDF with user-selected columns

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 25 |
| JavaFX | 21.0.6 |
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

The first run downloads all dependencies automatically.

---

## AI Race Engineer Setup (optional)

```bash
# Install Ollama from https://ollama.com, then:
ollama pull llama3
ollama serve
```

Click **◉ Analyze Session** on any track tab. If Ollama is not running the app continues normally — the button shows a "Radio failure" message.

---

## Project Structure

```
src/main/java/org/example/drivesimui/
├── Launcher.java               — JavaFX entry point, main UI wiring
├── DashboardController.java    — Live dashboard: stopwatch, KPIs, events log
├── LapComparisonController.java— Lap Analysis: side-by-side setup diff tool
├── FuelStrategyController.java — Fuel Strategy: stint planner and calculator
├── Vehicle.java                — Abstract base class for all car types
├── GT3Car.java                 — GT3 implementation (ABS, TC, brake bias, suspension, tyres, fuel)
├── FormulaCar.java             — Formula implementation (wings, ERS, DRS, differential, fuel)
├── LapTime.java                — Single lap record (track, time, car, delta)
├── GarageManger.java           — In-memory lap collection
├── garageIO.java               — CSV persistence (save/load)
├── LapExporter.java            — CSV and PDF export engine
├── AIClient.java               — HTTP client for local Ollama API
└── DataExport.java             — Interface: toCSV() / toJSON()

src/main/resources/org/example/drivesimui/
└── dark-theme.css              — Full racing-cockpit dark theme
```

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

## Architecture

- `Vehicle` is abstract — **no casting anywhere** in the codebase
- Each subclass declares its own column headers, values, essential column count, and CSV serialisation via abstract methods
- `LapComparisonController` and `LapExporter` resolve all setup columns purely through `Vehicle.getColumnHeaders()` / `getColumnValues()` — adding a new car type requires no changes to UI or analysis code
- `DashboardController`, `LapComparisonController`, `FuelStrategyController` are self-contained modules; `Launcher` wires them together and calls `onNewLap()` on each
- `garageIO` persistence is generic — stores car type string and delegates setup parsing back to the subclass via `applySetupFromCSV(List<String>)`

---

## Data Files

| File | Description |
|---|---|
| `telemetry_save.csv` | Auto-saved session data (gitignored — generated at runtime) |
| Exported `.csv` / `.pdf` | User-chosen location via save dialog (gitignored) |
