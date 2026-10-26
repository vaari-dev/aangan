package dev.vaari.aangan;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class People {

    static final String KEY = "people";
    /** More than any family needs, and a limit on what a stranger on the Wi-Fi could pile up. */
    static final int MAX = 200;
    static final int MAX_NAME = 40;

    static final class Day {
        final int month;
        final int day;
        final boolean birthday;
        final String who;

        Day(int month, int day, boolean birthday, String who) {
            this.month = month;
            this.day = day;
            this.birthday = birthday;
            this.who = who;
        }

        static Day fromJson(JSONObject o) {
            if (o == null) return null;
            int month = o.optInt("m");
            int day = o.optInt("d");
            String who = o.optString("who", "").trim();
            if (!valid(month, day) || who.isEmpty()) return null;
            return new Day(month, day, o.optBoolean("birthday", true), who);
        }

        JSONObject toJson() {
            try {
                return new JSONObject().put("m", month).put("d", day).put("birthday", birthday).put("who", who);
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
        }

        static boolean valid(int month, int day) {
            return month >= 1 && month <= 12 && day >= 1 && day <= Month.of(month).maxLength();
        }

        /**
         * Identifies the day by what it is rather than where it sits in the list, so an edit from
         * a phone page loaded before someone else's change still reaches the right day.
         */
        String key() {
            return month + "-" + day + "-" + (birthday ? "b" : "a") + "-" + who;
        }

        LocalDate in(int year) {
            return MonthDay.of(month, day).atYear(year);
        }

        LocalDate next(LocalDate today) {
            LocalDate date = in(today.getYear());
            return date.isBefore(today) ? in(today.getYear() + 1) : date;
        }
    }

    private People() {
    }

    static List<Day> load(SharedPreferences prefs) {
        List<Day> days = new ArrayList<>();
        JSONArray a = Prefs.array(prefs, KEY);
        for (int i = 0; i < a.length(); i++) {
            Day d = Day.fromJson(a.optJSONObject(i));
            if (d != null) days.add(d);
        }
        Collections.sort(days, (x, y) -> x.month != y.month ? x.month - y.month : x.day - y.day);
        return days;
    }

    private static void save(SharedPreferences prefs, List<Day> days) {
        JSONArray a = new JSONArray();
        for (Day d : days) a.put(d.toJson());
        prefs.edit().putString(KEY, a.toString()).apply();
    }

    static boolean add(SharedPreferences prefs, Day day) {
        List<Day> days = load(prefs);
        if (days.size() >= MAX) return false;
        days.add(day);
        save(prefs, days);
        return true;
    }

    static boolean update(SharedPreferences prefs, String key, Day day) {
        return replace(prefs, key, day);
    }

    static boolean remove(SharedPreferences prefs, String key) {
        return replace(prefs, key, null);
    }

    private static boolean replace(SharedPreferences prefs, String key, Day day) {
        List<Day> days = load(prefs);
        for (int i = 0; i < days.size(); i++) {
            if (!days.get(i).key().equals(key)) continue;
            if (day == null) days.remove(i);
            else days.set(i, day);
            save(prefs, days);
            return true;
        }
        return false;
    }
}
