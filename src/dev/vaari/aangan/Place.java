package dev.vaari.aangan;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The city whose weather, air and time Aangan shows. Chosen in Settings (or from a phone) and
 * stored on the TV. Searching asks Open-Meteo's geocoder, sending only what was typed.
 */
final class Place {

    private static final String KEY = "place";
    private static final int MAX_RESULTS = 8;
    /** Longer than any place's name; a search or a name beyond it is cut or refused. */
    private static final int MAX_TEXT = 100;
    /** Recent searches and their results, so typing back over a word doesn't search again. */
    private static final Map<String, List<Place>> RECENT = new LinkedHashMap<String, List<Place>>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Place>> eldest) {
            return size() > 32;
        }
    };
    private static final List<String> NOT_TOWNS = Arrays.asList("PPLX", "PPLQ", "PPLW", "PPLH", "PPLCH");
    static final Place DEFAULT = new Place("Gurgaon", "Haryana, India", 28.46, 77.03, "Asia/Kolkata", "IN");

    /** Large Indian cities, to choose without typing. Coordinates as Open-Meteo's geocoder gives them. */
    static final List<Place> POPULAR = Arrays.asList(
            india("Delhi", "Delhi", 28.65195, 77.23149),
            india("Mumbai", "Maharashtra", 19.07283, 72.88261),
            india("Bengaluru", "Karnataka", 12.97194, 77.59369),
            india("Hyderabad", "Telangana", 17.38405, 78.45636),
            india("Chennai", "Tamil Nadu", 13.08784, 80.27847),
            india("Kolkata", "West Bengal", 22.56263, 88.36304),
            india("Pune", "Maharashtra", 18.51957, 73.85535),
            india("Ahmedabad", "Gujarat", 23.02579, 72.58727),
            india("Jaipur", "Rajasthan", 26.91962, 75.78781),
            india("Lucknow", "Uttar Pradesh", 26.83928, 80.92313),
            india("Chandigarh", "Chandigarh", 30.73629, 76.7884),
            india("Gurugram", "Haryana", 28.4601, 77.02635),
            india("Noida", "Uttar Pradesh", 28.58, 77.33),
            india("Kochi", "Kerala", 9.93988, 76.26022));

    /**
     * Older or everyday names the geocoder doesn't know, or matches to a smaller place elsewhere
     * ("Bombay" finds one in New York). Searching for one also searches for the official name.
     */
    private static final Map<String, String> ALSO_KNOWN_AS = new HashMap<>();

    static {
        String[][] names = {
                {"bombay", "Mumbai"}, {"bangalore", "Bengaluru"}, {"calcutta", "Kolkata"}, {"madras", "Chennai"},
                {"gurgaon", "Gurugram"}, {"poona", "Pune"}, {"baroda", "Vadodara"}, {"trivandrum", "Thiruvananthapuram"},
                {"cochin", "Kochi"}, {"ernakulam", "Kochi"}, {"mysore", "Mysuru"}, {"benares", "Varanasi"},
                {"banaras", "Varanasi"}, {"simla", "Shimla"}, {"pondicherry", "Puducherry"}, {"mangalore", "Mangaluru"},
                {"calicut", "Kozhikode"}, {"allahabad", "Prayagraj"}, {"belgaum", "Belagavi"}, {"vizag", "Visakhapatnam"},
                {"quilon", "Kollam"}, {"alleppey", "Alappuzha"}, {"trichy", "Tiruchirappalli"}, {"tanjore", "Thanjavur"},
                {"cawnpore", "Kanpur"}, {"jubbulpore", "Jabalpur"}, {"new delhi", "Delhi"},
        };
        for (String[] n : names) ALSO_KNOWN_AS.put(n[0], n[1]);
    }

    final String name;
    /** State and country, for telling apart places with the same name. */
    final String region;
    final double lat;
    final double lon;
    final String timezone;
    /** ISO country code; "IN" selects India's CPCB air-quality scale. */
    final String country;
    /** For telling search results apart; 0 when unknown, and not stored. */
    final long population;

    Place(String name, String region, double lat, double lon, String timezone, String country) {
        this(name, region, lat, lon, timezone, country, 0);
    }

    private Place(String name, String region, double lat, double lon, String timezone, String country, long population) {
        this.name = name;
        this.region = region;
        this.lat = lat;
        this.lon = lon;
        this.timezone = timezone;
        this.country = country;
        this.population = population;
    }

    /** A place from the phone page's form, or null unless every part is sensible. */
    static Place of(String name, String region, String lat, String lon, String timezone, String country) {
        try {
            double la = Double.parseDouble(lat);
            double lo = Double.parseDouble(lon);
            if (name == null || name.trim().isEmpty() || name.length() > MAX_TEXT || region == null || region.length() > MAX_TEXT
                    || !(Math.abs(la) <= 90) || !(Math.abs(lo) <= 180) || !ZoneId.getAvailableZoneIds().contains(timezone)
                    || country == null || !country.matches("[A-Z]{0,2}")) {
                return null;
            }
            return new Place(name.trim(), region, la, lo, timezone, country);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Place india(String name, String state, double lat, double lon) {
        return new Place(name, state + ", India", lat, lon, "Asia/Kolkata", "IN");
    }

    /** "Haryana, India · 8.9 lakh people" (or "2.7 million" outside India): where it is, and how big, to tell same-named places apart. */
    String describe() {
        if (population <= 0) return region;
        // Lakh and crore in India, millions elsewhere.
        boolean india = "IN".equals(country);
        String people;
        if (india && population >= 10_000_000) people = String.format(Locale.ROOT, "%.1f crore", population / 1e7);
        else if (india && population >= 100_000) people = String.format(Locale.ROOT, "%.1f lakh", population / 1e5);
        else if (!india && population >= 1_000_000) people = String.format(Locale.ROOT, "%.1f million", population / 1e6);
        else people = String.format(Locale.ROOT, "%,d", population);
        return region + "  ·  " + people.replace(".0 ", " ") + " people";
    }

    static Place load(SharedPreferences prefs) {
        if (!prefs.contains(KEY)) return DEFAULT;
        try {
            return fromJson(Prefs.object(prefs, KEY));
        } catch (JSONException e) {
            return DEFAULT;
        }
    }

    void save(SharedPreferences prefs) {
        prefs.edit().putString(KEY, toJson().toString()).apply();
    }

    private JSONObject toJson() {
        try {
            return new JSONObject().put("name", name).put("region", region).put("lat", lat).put("lon", lon)
                    .put("timezone", timezone).put("country", country);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Place fromJson(JSONObject o) throws JSONException {
        return new Place(o.getString("name"), o.optString("region", ""), o.getDouble("lat"), o.getDouble("lon"),
                o.getString("timezone"), o.optString("country", ""));
    }

    /**
     * Towns and cities matching {@code query}: those in {@code country} first, then the largest.
     * Airports, districts and the like are left out, as are less-known spellings of a place
     * already listed. Blocking; call off the main thread.
     */
    static List<Place> search(String query, String country) throws IOException, JSONException {
        String q = query.trim();
        if (q.length() > MAX_TEXT) q = q.substring(0, MAX_TEXT);
        String key = country + "|" + q.toLowerCase(Locale.ROOT);
        synchronized (RECENT) {
            List<Place> known = RECENT.get(key);
            if (known != null) return new ArrayList<>(known);
        }
        List<Place> places = new ArrayList<>();
        String official = ALSO_KNOWN_AS.get(q.toLowerCase(Locale.ROOT));
        // Just the city itself: the official name's search also finds its neighbourhoods.
        if (official != null) {
            for (Place p : lookUp(official)) if (plain(p.name).equals(plain(official))) places.add(p);
        }
        // Leave out what's already listed: another spelling of the same place, or, for an older name,
        // parts of the city it now means ("Bombay" also finds Bandra).
        for (Place p : lookUp(q)) {
            boolean seen = false;
            for (Place other : places) {
                seen |= other.region.equals(p.region) && plain(other.name).equals(plain(p.name))
                        || official != null && Math.abs(other.lat - p.lat) < 0.5 && Math.abs(other.lon - p.lon) < 0.5;
            }
            if (!seen) places.add(p);
        }
        // A stable sort: the geocoder's own order breaks ties.
        Collections.sort(places, (a, b) -> {
            boolean ha = a.country.equals(country), hb = b.country.equals(country);
            if (ha != hb) return ha ? -1 : 1;
            return Long.compare(b.population, a.population);
        });
        if (places.size() > MAX_RESULTS) places = new ArrayList<>(places.subList(0, MAX_RESULTS));
        synchronized (RECENT) {
            RECENT.put(key, new ArrayList<>(places));
        }
        return places;
    }

    private static List<Place> lookUp(String name) throws IOException, JSONException {
        String body = Weather.get("https://geocoding-api.open-meteo.com/v1/search?count=20&language=en&format=json&name="
                + URLEncoder.encode(name, "UTF-8"));
        List<Place> places = new ArrayList<>();
        JSONArray results = new JSONObject(body).optJSONArray("results");
        if (results == null) return places;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            // Towns and cities only (feature codes PPL, PPLA, PPLC...): no airports, rivers or districts,
            // and no neighbourhoods (PPLX) or places that are abandoned, destroyed or historical.
            String code = r.optString("feature_code", "");
            if (!r.has("timezone") || !code.startsWith("PPL") || NOT_TOWNS.contains(code)) continue;
            String admin = r.optString("admin1", "");
            String nation = r.optString("country", "");
            places.add(new Place(r.getString("name"), admin.isEmpty() ? nation : admin + ", " + nation,
                    r.getDouble("latitude"), r.getDouble("longitude"), r.getString("timezone"),
                    r.optString("country_code", ""), r.optLong("population", 0)));
        }
        return places;
    }

    /** "Vāranāsi" and "Varanasi" alike: lower case, without accents. */
    private static String plain(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
