package dev.vaari.aangan;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * The city whose weather, air and time Aangan shows. Chosen in Settings (or from a phone) and
 * stored on the TV. Searching asks Open-Meteo's geocoder, sending only what was typed.
 */
final class Place {

    static final Place DEFAULT = new Place("Gurgaon", "Haryana, India", 28.46, 77.03, "Asia/Kolkata", "IN");

    final String name;
    /** State and country, for telling apart places with the same name. */
    final String region;
    final double lat;
    final double lon;
    final String timezone;
    /** ISO country code; "IN" selects India's CPCB air-quality scale. */
    final String country;

    Place(String name, String region, double lat, double lon, String timezone, String country) {
        this.name = name;
        this.region = region;
        this.lat = lat;
        this.lon = lon;
        this.timezone = timezone;
        this.country = country;
    }

    static Place load(SharedPreferences prefs) {
        String json = prefs.getString("place", null);
        if (json == null) return DEFAULT;
        try {
            return fromJson(new JSONObject(json));
        } catch (JSONException e) {
            return DEFAULT;
        }
    }

    void save(SharedPreferences prefs) {
        prefs.edit().putString("place", toJson().toString()).apply();
    }

    JSONObject toJson() {
        try {
            return new JSONObject().put("name", name).put("region", region).put("lat", lat).put("lon", lon)
                    .put("timezone", timezone).put("country", country);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    static Place fromJson(JSONObject o) throws JSONException {
        return new Place(o.getString("name"), o.optString("region", ""), o.getDouble("lat"), o.getDouble("lon"),
                o.getString("timezone"), o.optString("country", ""));
    }

    /** Up to eight places matching {@code query}, most populous first. Blocking; call off the main thread. */
    static List<Place> search(String query) throws IOException, JSONException {
        String body = Weather.get("https://geocoding-api.open-meteo.com/v1/search?count=8&language=en&format=json&name="
                + URLEncoder.encode(query.trim(), "UTF-8"));
        List<Place> places = new ArrayList<>();
        JSONArray results = new JSONObject(body).optJSONArray("results");
        if (results == null) return places;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            if (!r.has("timezone")) continue;
            String admin = r.optString("admin1", "");
            String nation = r.optString("country", "");
            places.add(new Place(r.getString("name"), admin.isEmpty() ? nation : admin + ", " + nation,
                    r.getDouble("latitude"), r.getDouble("longitude"), r.getString("timezone"),
                    r.optString("country_code", "")));
        }
        return places;
    }
}
