package dev.vaari.aangan;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Everything the family has set, as one small file: the city, birthdays and anniversaries,
 * festival changes, the dock and the hidden apps. The phone page exports it to the phone and
 * imports it on a new TV; it goes nowhere else. Aangan doesn't use Android's backup, so this is
 * the only way the settings can outlive the TV.
 *
 * Importing replaces each part the file holds, and leaves alone any part it doesn't. Every part is
 * checked first: a damaged or foreign file changes nothing, and a bad entry inside a good file is
 * left out rather than stored.
 */
final class Backup {

    /** Raised when the format changes in a way older versions can't read. */
    static final int FORMAT = 1;
    private static final String APP = "Aangan";
    /** Package names, as Android allows them, and Aangan's own tiles such as "__settings__". */
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z0-9_.]{1,255}");
    /** A backup nests three levels deep; anything far deeper isn't one, and could exhaust the parser's stack. */
    private static final int MAX_DEPTH = 16;

    private Backup() {
    }

    /** The backup as JSON, laid out to be readable. */
    static String export(SharedPreferences prefs) {
        try {
            JSONObject o = new JSONObject()
                    .put("app", APP)
                    .put("format", FORMAT)
                    .put("exported", Dates.today().toString());
            if (prefs.contains(Place.KEY)) o.put(Place.KEY, Prefs.object(prefs, Place.KEY));
            o.put(People.KEY, Prefs.array(prefs, People.KEY));
            o.put(Festival.EDITS, Prefs.object(prefs, Festival.EDITS));
            if (prefs.contains(Dock.KEY)) o.put(Dock.KEY, Prefs.array(prefs, Dock.KEY));
            o.put(HomeActivity.HIDDEN, new JSONArray(Prefs.list(prefs, HomeActivity.HIDDEN)));
            return o.toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Restores a backup; false, changing nothing, if {@code json} isn't one Aangan can read. */
    static boolean restore(SharedPreferences prefs, String json) {
        if (depth(json) > MAX_DEPTH) return false;
        JSONObject o;
        try {
            o = new JSONObject(json);
        } catch (JSONException e) {
            return false;
        }
        if (!APP.equals(o.optString("app")) || o.optInt("format") != FORMAT) return false;

        Place place = null;
        if (o.has(Place.KEY)) {
            JSONObject p = o.optJSONObject(Place.KEY);
            place = p == null ? null : Place.fromJsonOrNull(p);
            if (place == null) return false;
        }

        SharedPreferences.Editor edit = prefs.edit();
        JSONArray people = o.optJSONArray(People.KEY);
        if (people != null) edit.putString(People.KEY, days(people).toString());
        JSONObject edits = o.optJSONObject(Festival.EDITS);
        if (edits != null) edit.putString(Festival.EDITS, festivalEdits(edits).toString());
        JSONArray dock = o.optJSONArray(Dock.KEY);
        if (dock != null) edit.putString(Dock.KEY, dock(dock).toString());
        JSONArray hidden = o.optJSONArray(HomeActivity.HIDDEN);
        if (hidden != null) edit.putString(HomeActivity.HIDDEN, String.join(",", packages(hidden)));
        edit.apply();
        if (place != null) {
            place.save(prefs);
            Weather.clear(prefs);
        }
        return true;
    }

    /**
     * The family's days, each as the TV or phone page could have entered it: at most {@link People#MAX},
     * with names cut to {@link People#MAX_NAME}.
     */
    private static JSONArray days(JSONArray people) {
        JSONArray out = new JSONArray();
        try {
            for (int i = 0; i < people.length() && out.length() < People.MAX; i++) {
                People.Day d = People.Day.fromJson(people.optJSONObject(i));
                if (d == null) continue;
                String who = d.who.length() > People.MAX_NAME
                        ? d.who.substring(0, d.who.offsetByCodePoints(0, d.who.codePointCount(0, People.MAX_NAME))) : d.who;
                out.put(new JSONObject().put("m", d.month).put("d", d.day).put("birthday", d.birthday).put("who", who.trim()));
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    /**
     * The festival changes worth keeping: hidden, or moved to a valid date within
     * {@link Festival#MOVE_DAYS} of the calendar's (which the change's key begins with).
     */
    private static JSONObject festivalEdits(JSONObject edits) {
        JSONObject out = new JSONObject();
        try {
            for (Iterator<String> keys = edits.keys(); keys.hasNext(); ) {
                String key = keys.next();
                JSONObject e = edits.optJSONObject(key);
                int bar = key.indexOf('|');
                if (e == null || bar < 0) continue;
                JSONObject kept = new JSONObject();
                try {
                    LocalDate original = LocalDate.parse(key.substring(0, bar));
                    if (e.has("date")) {
                        LocalDate date = LocalDate.parse(e.optString("date"));
                        if (Math.abs(ChronoUnit.DAYS.between(original, date)) > Festival.MOVE_DAYS) continue;
                        kept.put("date", date.toString());
                    }
                } catch (DateTimeParseException bad) {
                    continue;
                }
                if (e.optBoolean("hidden")) kept.put("hidden", true);
                if (kept.length() > 0) out.put(key, kept);
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    /** The dock's {@link Dock#SIZE} places: a package each, or empty. */
    private static JSONArray dock(JSONArray a) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Dock.SIZE; i++) {
            String s = a.optString(i, Dock.EMPTY);
            out.add(PACKAGE.matcher(s).matches() && !out.contains(s) ? s : Dock.EMPTY);
        }
        return new JSONArray(out);
    }

    private static List<String> packages(JSONArray a) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "");
            if (PACKAGE.matcher(s).matches() && !out.contains(s)) out.add(s);
        }
        return out;
    }

    /** How deeply {@code json}'s objects and arrays nest, counted without parsing it. */
    static int depth(String json) {
        int depth = 0;
        int deepest = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
            } else if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                deepest = Math.max(deepest, ++depth);
            } else if (c == '}' || c == ']') {
                depth--;
            }
        }
        return deepest;
    }
}
