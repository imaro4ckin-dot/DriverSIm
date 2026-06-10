# DriveSimUI — Sim Racing Garage Manager

A JavaFX desktop application for logging, analysing, and exporting sim racing lap times.  
Supports GT3 and Formula car types with per-car setup tracking, an AI race engineer, and CSV/PDF export.

---

## Features

- **Lap logging** — record lap times (in ms) per track and car
- **Per-track, per-car-type tabs** — GT3 and Formula laps are kept in separate tables on the same track tab
- **Dynamic setup sliders** — essential controls (ABS/TC for GT3, wing angles for Formula) always visible; advanced settings (brake bias, suspension, tyre pressure, fuel / ERS, DRS, differential) hidden behind an expandable section
- **Live delta splits** — each lap shows time gained/lost vs the previous lap, coloured green/red
- **Lap trend chart** — line chart per track/car-type showing lap time over the session
- **Column editor** — choose exactly which setup columns appear in the table and in exports
- **Export to CSV or PDF** — one click via a native save dialog; PDF is landscape A4 with a header row
- **AI race engineer** — sends all lap and setup data (including advanced fields) to a local [Ollama](https://ollama.com) LLM and returns 3 bullet points of setup advice
- **Auto-save / auto-load** — session data is saved to `telemetry_save.csv` on close and restored on next launch

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 25 |
| JavaFX | 21.0.6 |
| Maven | 3.8+ (wrapper included) |
| Ollama (optional) | any — only needed for AI analysis |

---

## Running

```bash
./mvnw javafx:run
```

On Windows:

```bat
mvnw.cmd javafx:run
```

The first run will build all dependencies automatically.

---

## AI Race Engineer (optional)

The AI feature requires [Ollama](https://ollama.com) running locally with the `llama3` model:

```bash
# Install Ollama, then:
ollama pull llama3
ollama serve
```

Once running, click **Analyze Session** on any track/car tab to get setup advice.  
If Ollama is not running the button returns a "Radio failure" message and the rest of the app is unaffected.

---

## Project Structure

```
src/main/java/org/example/drivesimui/
├── Launcher.java        — JavaFX Application entry point, all UI logic
├── Vehicle.java         — Abstract base class for all car types
├── GT3Car.java          — GT3 implementation (ABS, TC, brake bias, suspension, tyres, fuel)
├── FormulaCar.java      — Formula implementation (wings, ERS, DRS, differential, fuel)
├── LapTime.java         — Single lap record (track, time, car, delta)
├── GarageManger.java    — In-memory lap collection and fastest-lap query
├── garageIO.java        — CSV persistence (save/load all laps)
├── LapExporter.java     — CSV and PDF export with user-selected columns
├── AIClient.java        — HTTP client for local Ollama API
└── DataExport.java      — Interface: toCSV() / toJSON()
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

## OOP Design

- `Vehicle` is abstract — no casting anywhere in the codebase
- Each subclass declares its own column headers, values, essential column count, and CSV serialisation format via abstract methods
- `LapExporter` resolves columns purely through `Vehicle.getColumnHeaders()` / `getColumnValues()`
- `garageIO` persistence is generic — it stores the car type string and delegates setup parsing back to the subclass via `applySetupFromCSV(List<String>)`

---

## Data Files

| File | Description |
|---|---|
| `telemetry_save.csv` | Auto-saved session data (gitignored — generated at runtime) |
| Exported `.csv` / `.pdf` | User-chosen location via save dialog (gitignored) |
