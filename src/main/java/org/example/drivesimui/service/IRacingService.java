package org.example.drivesimui.service;

import org.example.drivesimui.model.DriverProfile;
import org.example.drivesimui.model.RaceSession;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * iRacing data service.
 *
 * Auth: POST /auth with base64(sha256(password+email)) per iRacing customer API spec.
 * Data: two-step fetch — initial GET returns a JSON with a "link" pointing to S3.
 */
public class IRacingService implements PlatformService {

    private static final String BASE_URL = "https://members-ng.iracing.com";

    private final HttpClient http;
    private boolean authenticated = false;

    public IRacingService() {
        http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override public String  getPlatformName() { return "iRacing"; }
    @Override public boolean isAuthenticated()  { return authenticated; }

    // ── Authentication ─────────────────────────────────────────────────────

    public void authenticate(String email, String password) throws Exception {
        String hashInput = password + email.toLowerCase();
        byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(
                hashInput.getBytes(StandardCharsets.UTF_8));
        String encoded = Base64.getEncoder().encodeToString(hashBytes);

        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", encoded);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/auth"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Auth failed — HTTP " + resp.statusCode());
        }
        JSONObject json = new JSONObject(resp.body());
        if (json.optBoolean("inactive", false)) {
            throw new IOException("Account inactive or credentials rejected.");
        }
        authenticated = true;
    }

    // ── Data fetch ─────────────────────────────────────────────────────────

    @Override
    public List<RaceSession> fetchRecentRaces() throws Exception {
        ensureAuth();
        String link  = fetchLink(BASE_URL + "/data/stats/member_recent_races");
        String json  = fetchFromLink(link);
        return parseRecentRaces(json);
    }

    public DriverProfile fetchDriverProfile() throws Exception {
        ensureAuth();
        String link  = fetchLink(BASE_URL + "/data/member/profile");
        String json  = fetchFromLink(link);
        return parseDriverProfile(json);
    }

    /** Import a single .ibt binary file (best-effort: extracts car name & lap times from header). */
    public List<RaceSession> importIbtFile(File f) throws Exception {
        return new IbtImporter().importFile(f);
    }

    /** Import a generic iRacing CSV race-result export. */
    public List<RaceSession> importCsvFile(File f) throws Exception {
        return new CsvRaceResultImporter().importFile(f, "IRACING");
    }

    // ── Two-step fetch helpers ─────────────────────────────────────────────

    private String fetchLink(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new IOException("Link fetch failed — HTTP " + resp.statusCode());
        return new JSONObject(resp.body()).getString("link");
    }

    private String fetchFromLink(String link) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(link))
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new IOException("Data fetch failed — HTTP " + resp.statusCode());
        return resp.body();
    }

    // ── JSON parsers ───────────────────────────────────────────────────────

    private List<RaceSession> parseRecentRaces(String json) {
        List<RaceSession> result = new ArrayList<>();
        try {
            JSONObject root   = new JSONObject(json);
            JSONArray  races  = root.optJSONArray("races");
            if (races == null) return result;
            for (int i = 0; i < races.length(); i++) {
                JSONObject r = races.getJSONObject(i);
                RaceSession s = new RaceSession();
                s.setPlatform("IRACING");
                s.setTrackName(r.optString("track_name", "Unknown"));
                s.setCarName(r.optString("car_name", null));
                s.setSessionType(r.optString("event_type", null));
                s.setFinishPosition(r.optInt("finish_position_in_class", 0));
                s.setTotalDrivers(r.optInt("car_class_size", 0));
                s.setBestLapMs(r.optLong("best_lap_time", 0) / 10);  // iRacing uses 1/10000 s
                s.setExternalSessionId(String.valueOf(r.optLong("subsession_id", 0)));
                s.setRawJson(r.toString());
                String dateStr = r.optString("start_time", null);
                if (dateStr != null && dateStr.length() >= 10) {
                    try { s.setSessionDate(LocalDate.parse(dateStr.substring(0, 10))); }
                    catch (Exception ignored) {}
                }
                result.add(s);
            }
        } catch (Exception e) {
            System.err.println("[IRacingService] parseRecentRaces: " + e.getMessage());
        }
        return result;
    }

    private DriverProfile parseDriverProfile(String json) {
        DriverProfile p = new DriverProfile();
        try {
            JSONObject root     = new JSONObject(json);
            JSONObject member   = root.optJSONObject("member");
            JSONObject licenses = root.optJSONObject("licenses");
            if (member == null) member = root;

            p.setPlatform("IRACING");
            p.setDriverName(member.optString("display_name", "Unknown"));
            p.setCustomerId(String.valueOf(member.optLong("cust_id", 0)));

            if (licenses != null) {
                // Get highest license category irating
                for (String cat : new String[]{"oval", "road", "dirt_oval", "dirt_road"}) {
                    JSONObject lic = licenses.optJSONObject(cat);
                    if (lic != null) {
                        int ir = lic.optInt("irating", 0);
                        if (ir > p.getIrating()) {
                            p.setIrating(ir);
                            p.setSafetyRating(lic.optDouble("safety_rating", 0.0));
                            p.setLicenseClass(lic.optString("license_letter", "R"));
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[IRacingService] parseDriverProfile: " + e.getMessage());
        }
        return p;
    }

    private void ensureAuth() throws Exception {
        if (!authenticated) throw new IllegalStateException("Not authenticated — call authenticate() first.");
    }
}
