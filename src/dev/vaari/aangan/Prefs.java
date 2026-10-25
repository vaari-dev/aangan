package dev.vaari.aangan;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Aangan's one preferences file, and the small formats kept in it: JSON for the city, the family's
 * days, festival changes and the dock, and comma-separated package names for hidden apps.
 *
 * Every write uses apply(): it updates the in-memory copy at once, so the next read anywhere in the
 * app (the home screen, Settings, or the phone page's thread) already sees the change, and the disk
 * write happens in the background instead of on the main thread.
 */
final class Prefs {

    private static final String NAME = "aangan";

    private Prefs() {
    }

    static SharedPreferences of(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    /** The JSON object stored at {@code key}, or an empty one if it is missing or damaged. */
    static JSONObject object(SharedPreferences prefs, String key) {
        try {
            return new JSONObject(prefs.getString(key, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /** The JSON array stored at {@code key}, or an empty one if it is missing or damaged. */
    static JSONArray array(SharedPreferences prefs, String key) {
        try {
            return new JSONArray(prefs.getString(key, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    /** A comma-separated list, such as the hidden apps' package names. */
    static List<String> list(SharedPreferences prefs, String key) {
        List<String> out = new ArrayList<>();
        for (String s : prefs.getString(key, "").split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    static void putList(SharedPreferences prefs, String key, List<String> values) {
        prefs.edit().putString(key, TextUtils.join(",", values)).apply();
    }
}
