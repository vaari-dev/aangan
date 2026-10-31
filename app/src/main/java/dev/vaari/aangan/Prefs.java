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
 * Every write uses apply(): it updates the in-memory copy at once, so the next read anywhere in the
 * app (the phone page's thread included) already sees the change, and the disk write happens in
 * the background instead of on the main thread.
 */
final class Prefs {

    private static final String NAME = "aangan";

    private Prefs() {
    }

    static SharedPreferences of(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static JSONObject object(SharedPreferences prefs, String key) {
        try {
            return new JSONObject(prefs.getString(key, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    static JSONArray array(SharedPreferences prefs, String key) {
        try {
            return new JSONArray(prefs.getString(key, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static List<String> list(SharedPreferences prefs, String key) {
        List<String> out = new ArrayList<>();
        for (String s : prefs.getString(key, "").split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    static void putList(SharedPreferences prefs, String key, List<String> values) {
        prefs.edit().putString(key, TextUtils.join(",", values)).apply();
    }
}
