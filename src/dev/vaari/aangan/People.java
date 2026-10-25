package dev.vaari.aangan;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The family's birthdays and anniversaries, kept only on this TV. Added from the TV or from a
 * phone on the same Wi-Fi (see {@link PhoneSetup}); never sent anywhere.
 */
final class People {

    static final class Day {
        final int month;
        final int day;
        final boolean birthday;
        /** "Asha", or "Asha & Rohan" for an anniversary. */
        final String who;

        Day(int month, int day, boolean birthday, String who) {
            this.month = month;
            this.day = day;
            this.birthday = birthday;
            this.who = who;
        }
    }

    private People() {
    }

    /** All days, in calendar order. */
    static List<Day> load(SharedPreferences prefs) {
        List<Day> days = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString("people", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                days.add(new Day(o.getInt("m"), o.getInt("d"), o.getBoolean("birthday"), o.getString("who")));
            }
        } catch (JSONException ignored) {
            // A damaged list reads as empty rather than taking the home screen down.
        }
        Collections.sort(days, (a, b) -> a.month != b.month ? a.month - b.month : a.day - b.day);
        return days;
    }

    static void save(SharedPreferences prefs, List<Day> days) {
        JSONArray a = new JSONArray();
        try {
            for (Day d : days) {
                a.put(new JSONObject().put("m", d.month).put("d", d.day).put("birthday", d.birthday).put("who", d.who));
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        prefs.edit().putString("people", a.toString()).commit();
    }

    static void add(SharedPreferences prefs, Day day) {
        List<Day> days = load(prefs);
        days.add(day);
        save(prefs, days);
    }

    static void remove(SharedPreferences prefs, int index) {
        List<Day> days = load(prefs);
        if (index >= 0 && index < days.size()) days.remove(index);
        save(prefs, days);
    }
}
