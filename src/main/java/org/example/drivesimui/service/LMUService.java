package org.example.drivesimui.service;

import org.example.drivesimui.model.RaceSession;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * LMU (Le Mans Ultimate) / rF2 data service.
 *
 * Supports:
 * 1. File import — XML result files from LMU/rF2 UserData/player/Results/
 * 2. Live API   — optional GET http://localhost:5397/rest/sessions (rF2 REST plugin)
 */
public class LMUService implements PlatformService {

    @Override public String  getPlatformName() { return "LMU"; }
    @Override public boolean isAuthenticated()  { return true; }

    @Override
    public List<RaceSession> fetchRecentRaces() throws Exception {
        return fetchLiveSession("http://localhost:5397");
    }

    // ── Live API ───────────────────────────────────────────────────────────

    public List<RaceSession> fetchLiveSession(String baseUrl) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/rest/sessions"))
                .timeout(Duration.ofSeconds(10))
                .GET().build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new java.io.IOException("LMU API returned HTTP " + resp.statusCode());

        return parseLiveJson(resp.body());
    }

    // ── File import ────────────────────────────────────────────────────────

    public List<RaceSession> importResultFiles(List<File> files) throws Exception {
        List<RaceSession> all = new ArrayList<>();
        for (File f : files) {
            try {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".xml"))  all.addAll(importXmlFile(f));
                else if (name.endsWith(".json")) all.addAll(importJsonFile(f));
                else System.err.println("[LMUService] Unknown file type: " + f.getName());
            } catch (Exception e) {
                System.err.println("[LMUService] Skipping " + f.getName() + ": " + e.getMessage());
            }
        }
        return all;
    }

    // ── XML parsing (rF2/LMU native format) ───────────────────────────────

    private List<RaceSession> importXmlFile(File f) throws Exception {
        List<RaceSession> result = new ArrayList<>();
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(f);
        doc.getDocumentElement().normalize();

        // rF2 XML: root element is <Mod> with nested <Race> or result-style root
        Element root = doc.getDocumentElement();

        RaceSession s = new RaceSession();
        s.setPlatform("LMU");
        s.setRawJson(null);
        s.setExternalSessionId(f.getName());

        // Track name — look for <Track>, <TrackName>, or attribute
        s.setTrackName(firstText(doc, "Track", "TrackName", "track"));

        // Car — first driver's car
        s.setCarName(firstText(doc, "VehicleName", "Car", "car_name"));

        // Session type
        String type = firstText(doc, "RaceType", "SessionType", "session_type");
        s.setSessionType(type != null ? type : "Race");

        // Finish position (first slot = P1)
        s.setFinishPosition(1);

        // Count drivers
        NodeList slots = doc.getElementsByTagName("Slot");
        if (slots.getLength() == 0) slots = doc.getElementsByTagName("Driver");
        s.setTotalDrivers(slots.getLength());

        // Best lap — iterate Slots to find minimum BestLapTime
        long bestMs = Long.MAX_VALUE;
        for (int i = 0; i < slots.getLength(); i++) {
            Element slot = (Element) slots.item(i);
            String lapStr = slot.getAttribute("BestLapTime");
            if (lapStr.isEmpty()) lapStr = getChildText(slot, "BestLapTime");
            if (lapStr != null && !lapStr.isEmpty()) {
                try {
                    double lapSec = Double.parseDouble(lapStr);
                    long lapMs = (long) (lapSec * 1000);
                    if (lapMs > 0 && lapMs < bestMs) bestMs = lapMs;
                } catch (NumberFormatException ignored) {}
            }
        }
        s.setBestLapMs(bestMs == Long.MAX_VALUE ? 0 : bestMs);

        // Date from file name or last modified
        LocalDate date = extractDate(f.getName());
        s.setSessionDate(date != null ? date : LocalDate.ofEpochDay(f.lastModified() / 86_400_000));

        result.add(s);
        return result;
    }

    private List<RaceSession> importJsonFile(File f) throws Exception {
        // Delegate JSON result files to CsvRaceResultImporter or parse directly
        String raw = java.nio.file.Files.readString(f.toPath());
        return parseLiveJson(raw);
    }

    // ── JSON parsing (rF2 REST API response) ──────────────────────────────

    private List<RaceSession> parseLiveJson(String raw) {
        List<RaceSession> result = new ArrayList<>();
        try {
            org.json.JSONObject root = new org.json.JSONObject(raw);
            RaceSession s = new RaceSession();
            s.setPlatform("LMU");
            s.setTrackName(root.optString("TrackName", root.optString("trackName", "Unknown")));
            s.setCarName(null);
            s.setSessionType(root.optString("SessionType", root.optString("session", "Race")));
            s.setFinishPosition(0);
            s.setTotalDrivers(0);
            s.setSessionDate(LocalDate.now());
            s.setRawJson(raw.length() > 4000 ? raw.substring(0, 4000) : raw);

            // Look for vehicle list
            org.json.JSONArray vehicles = root.optJSONArray("Vehicles");
            if (vehicles != null) {
                s.setTotalDrivers(vehicles.length());
                long best = Long.MAX_VALUE;
                for (int i = 0; i < vehicles.length(); i++) {
                    org.json.JSONObject v = vehicles.getJSONObject(i);
                    long lapMs = (long) (v.optDouble("BestLapTime", 0) * 1000);
                    if (lapMs > 0 && lapMs < best) best = lapMs;
                }
                s.setBestLapMs(best == Long.MAX_VALUE ? 0 : best);
            }
            result.add(s);
        } catch (Exception e) {
            System.err.println("[LMUService] parseLiveJson: " + e.getMessage());
        }
        return result;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String firstText(Document doc, String... tags) {
        for (String tag : tags) {
            NodeList nl = doc.getElementsByTagName(tag);
            if (nl.getLength() > 0 && nl.item(0).getTextContent() != null) {
                String t = nl.item(0).getTextContent().trim();
                if (!t.isEmpty()) return t;
            }
        }
        return "Unknown";
    }

    private String getChildText(Element el, String childTag) {
        NodeList nl = el.getElementsByTagName(childTag);
        if (nl.getLength() > 0) return nl.item(0).getTextContent().trim();
        return null;
    }

    private LocalDate extractDate(String fileName) {
        try {
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("(\\d{4})[\\-_](\\d{2})[\\-_](\\d{2})").matcher(fileName);
            if (m.find()) return LocalDate.of(Integer.parseInt(m.group(1)),
                                              Integer.parseInt(m.group(2)),
                                              Integer.parseInt(m.group(3)));
        } catch (Exception ignored) {}
        return null;
    }
}
