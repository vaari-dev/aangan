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

/**
 * The family's birthdays and anniversaries, kept only on this TV. Added from the TV or from a
 * phone on the same Wi-Fi (see {@link PhoneSetup}); never sent anywhere.
 */
final class People {

    static final String KEY = "people";
    /** More than any family needs, and a limit on what a stranger on the Wi-Fi could pile up. */
    static final int MAX = 200;
    /** The longest name the TV and the phone page let anyone type for a day. */
    static final int MAX_NAME = 40;

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

        /** A day as stored, or null if it isn't a real date with a name. Names entered are capped at {@link #MAX_NAME}. */
        static Day fromJson(JSONObject o) {
            if (o == null) return null;
            int month = o.optInt("m");
            int day = o.optInt("d");
            String who = o.optString("who", "").trim();
            if (!valid(month, day) || who.isEmpty()) return null;
            return new Day(month, day, o.optBoolean("birthday", true), who);
        }

        /** True for a real calendar day; 29 February counts, as it comes round in leap years. */
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

        /** The day as it falls in {@code year}; 29 February becomes the 28th in other years. */
        LocalDate in(int year) {
            return MonthDay.of(month, day).atYear(year);
        }

        /** The next time the day comes round, from {@code today} (today included). */
        LocalDate next(LocalDate today) {
            LocalDate date = in(today.getYear());
            return date.isBefore(today) ? in(today.getYear() + 1) : date;
        }
    }

    private People() {
    }

    /** All days, in calendar order. Anything that isn't a real date with a name is left out. */
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

    static void save(SharedPreferences prefs, List<Day> days) {
        JSONArray a = new JSONArray();
        try {
            for (Day d : days) {
                a.put(new JSONObject().put("m", d.month).put("d", d.day).put("birthday", d.birthday).put("who", d.who));
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        prefs.edit().putString(KEY, a.toString()).apply();
    }

    /** Adds a day; false, changing nothing, once there are {@link #MAX} already. */
    static boolean add(SharedPreferences prefs, Day day) {
        List<Day> days = load(prefs);
        if (days.size() >= MAX) return false;
        days.add(day);
        save(prefs, days);
        return true;
    }

    /** Replaces the day with this {@link Day#key()}; false, changing nothing, if it has gone. */
    static boolean update(SharedPreferences prefs, String key, Day day) {
        List<Day> days = load(prefs);
        int i = indexOf(days, key);
        if (i < 0) return false;
        days.set(i, day);
        save(prefs, days);
        return true;
    }

    /** Removes the day with this {@link Day#key()}; false, changing nothing, if it has gone. */
    static boolean remove(SharedPreferences prefs, String key) {
        List<Day> days = load(prefs);
        int i = indexOf(days, key);
        if (i < 0) return false;
        days.remove(i);
        save(prefs, days);
        return true;
    }

    private static int indexOf(List<Day> days, String key) {
        for (int i = 0; i < days.size(); i++) if (days.get(i).key().equals(key)) return i;
        return -1;
    }
}
