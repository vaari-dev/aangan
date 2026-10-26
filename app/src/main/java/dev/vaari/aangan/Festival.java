package dev.vaari.aangan;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A festival or a personal day, and the calendar of them. Festivals come from
 * {@code assets/festivals.csv}, built into the app, with any dates the family has moved or hidden
 * on top; birthdays and anniversaries from {@link People}, stored on the TV. Nothing needs the network.
 *
 * The calendar's English names identify festivals everywhere (the family's changes are keyed by
 * them). What the TV shows comes from {@code assets/festival-names/<language>.csv} when the TV's
 * language has one, and is the English otherwise.
 *
 * The calendar is read on the main thread and on the phone page's thread. Each load builds new
 * lists and publishes them whole, so a reader always sees one complete calendar.
 */
final class Festival {

    /** How far ahead the home screen mentions a festival. */
    private static final int HORIZON_DAYS = 30;
    /** Days between two entries with the same scene, up to this far apart, share it. */
    private static final int SEASON_DAYS = 9;
    /** How far ahead Settings and the phone page list festivals for editing. */
    private static final int EDIT_MONTHS = 13;
    /** A festival can move this far either side of the calendar's date, so a slip of the thumb can't lose it. */
    static final int MOVE_DAYS = 366;
    static final String EDITS = "festival_edits";

    /** The calendar as built into the app. */
    private static volatile List<Festival> builtIn;
    /** The calendar with the family's changes: what the home screen shows. */
    private static volatile List<Festival> festivals = Collections.emptyList();
    /** Birthdays and anniversaries, dated in 2000, a leap year, so 29 February survives. */
    private static volatile List<Festival> personal = Collections.emptyList();

    final LocalDate date;
    /** The English name, from the calendar; see {@link #title} for what to show. */
    final String name;
    /** The English greeting; see {@link #greeting(Context)}. */
    final String greeting;
    final Festive.Theme theme;
    /** True when the date depends on a moon sighting. */
    final boolean expected;
    final boolean personalDay;
    /** For a personal day, who it belongs to: "Asha", or "Asha & Rohan". */
    final String who;
    /** The date the calendar gives, before any change; with the name, it identifies a festival day. */
    final LocalDate original;
    final boolean hidden;

    private Festival(LocalDate date, String name, String greeting, Festive.Theme theme, boolean expected,
                     boolean personalDay, String who, LocalDate original, boolean hidden) {
        this.date = date;
        this.name = name;
        this.greeting = greeting;
        this.theme = theme;
        this.expected = expected;
        this.personalDay = personalDay;
        this.who = who;
        this.original = original;
        this.hidden = hidden;
    }

    private Festival on(LocalDate day) {
        return new Festival(day, name, greeting, theme, expected, personalDay, who, original, hidden);
    }

    boolean moved() {
        return !date.equals(original);
    }

    /** Identifies a festival day across edits, for the phone page's forms. */
    String key() {
        return original + "|" + name;
    }

    // ---- loading ----------------------------------------------------------------------------

    /** Reads the festival calendar once, and the family's changes and days afresh, since they can change. */
    static synchronized void load(Context context, SharedPreferences prefs) {
        if (builtIn == null) {
            List<Festival> calendar = new ArrayList<>();
            for (String[] f : read(context, "festivals.csv")) {
                LocalDate date = LocalDate.parse(f[0]);
                calendar.add(new Festival(date, f[1], f[2], theme(f[3]),
                        f.length > 4 && f[4].equals("expected"), false, null, date, false));
            }
            builtIn = calendar;
        }
        JSONObject edits = Prefs.object(prefs, EDITS);
        List<Festival> applied = new ArrayList<>();
        for (Festival f : builtIn) {
            JSONObject e = edits.optJSONObject(f.key());
            if (e == null) {
                applied.add(f);
                continue;
            }
            LocalDate date = e.has("date") ? LocalDate.parse(e.optString("date")) : f.date;
            // A date the family chose is no longer a guess.
            applied.add(new Festival(date, f.name, f.greeting, f.theme, f.expected && !e.has("date"), false, null,
                    f.original, e.optBoolean("hidden")));
        }
        Collections.sort(applied, (a, b) -> a.date.compareTo(b.date));

        List<Festival> days = new ArrayList<>();
        for (People.Day d : People.load(prefs)) {
            LocalDate date = d.in(2000);
            days.add(new Festival(date,
                    d.who + (d.birthday ? "'s birthday" : "'s anniversary"),
                    (d.birthday ? "Happy birthday, " : "Happy anniversary, ") + d.who,
                    d.birthday ? Festive.Theme.BIRTHDAY : Festive.Theme.ANNIVERSARY, false, true, d.who, date, false));
        }
        festivals = Collections.unmodifiableList(applied);
        personal = Collections.unmodifiableList(days);
    }

    // ---- the family's changes ---------------------------------------------------------------

    /** Changes keyed by {@link #key()}: {"date": "2026-11-09"} to move a day, {"hidden": true} to hide it. */
    private static void edit(SharedPreferences prefs, String key, String field, Object value) {
        JSONObject all = Prefs.object(prefs, EDITS);
        try {
            JSONObject e = all.optJSONObject(key);
            if (e == null) e = new JSONObject();
            if (value == null) e.remove(field);
            else e.put(field, value);
            if (e.length() == 0) all.remove(key);
            else all.put(key, e);
        } catch (JSONException ex) {
            throw new IllegalStateException(ex);
        }
        prefs.edit().putString(EDITS, all.toString()).apply();
    }

    /** True if {@code date} is within {@link #MOVE_DAYS} of the calendar's date. */
    boolean canMoveTo(LocalDate date) {
        return Math.abs(ChronoUnit.DAYS.between(original, date)) <= MOVE_DAYS;
    }

    /** The nearest date to {@code date} that the festival can move to. */
    LocalDate clampMove(LocalDate date) {
        LocalDate earliest = original.minusDays(MOVE_DAYS);
        LocalDate latest = original.plusDays(MOVE_DAYS);
        return date.isBefore(earliest) ? earliest : date.isAfter(latest) ? latest : date;
    }

    /** Moves a festival day, within {@link #MOVE_DAYS}; moving it back to the calendar's date undoes the change. */
    static void move(SharedPreferences prefs, Festival f, LocalDate date) {
        LocalDate to = f.clampMove(date);
        edit(prefs, f.key(), "date", to.equals(f.original) ? null : to.toString());
    }

    static void hide(SharedPreferences prefs, Festival f, boolean hidden) {
        edit(prefs, f.key(), "hidden", hidden ? Boolean.TRUE : null);
    }

    /**
     * Festival days from {@code today} for about a year, hidden ones included, for editing. A day
     * moved out of the window is still listed while its calendar date is in it, so it can be moved back.
     */
    static List<Festival> editable(LocalDate today) {
        LocalDate end = today.plusMonths(EDIT_MONTHS);
        List<Festival> out = new ArrayList<>();
        for (Festival f : festivals) {
            if (within(f.date, today, end) || within(f.original, today, end)) out.add(f);
        }
        return out;
    }

    private static boolean within(LocalDate date, LocalDate from, LocalDate until) {
        return !date.isBefore(from) && date.isBefore(until);
    }

    /** The festival day with this {@link #key()}, or null. */
    static Festival byKey(String key) {
        for (Festival f : festivals) if (f.key().equals(key)) return f;
        return null;
    }

    /** Every festival day in the calendar, with the family's moves and hidden days applied, in date order: for tests and checks. */
    static List<Festival> calendar() {
        return festivals;
    }

    /**
     * Non-comment lines of an asset, split on commas. A field in double quotes may contain commas,
     * and "" inside it stands for one quote mark, as in Python's csv module (which the website uses).
     */
    private static List<String[]> read(Context context, String asset) {
        try {
            return read(context, asset, 4);
        } catch (IOException e) {
            throw new IllegalStateException("festivals.csv is missing from the build", e);
        }
    }

    private static List<String[]> read(Context context, String asset, int minFields) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                context.getAssets().open(asset), StandardCharsets.UTF_8))) {
            for (String line; (line = in.readLine()) != null; ) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] fields = csvFields(line);
                if (fields.length >= minFields) rows.add(fields);
            }
        }
        return rows;
    }

    /** One CSV line's fields, each trimmed. A quote mark only opens a quoted field at its very start. */
    static String[] csvFields(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c != '"') {
                    field.append(c);
                } else if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = false;
                }
            } else if (c == '"' && field.length() == 0) {
                quoted = true;
            } else if (c == ',') {
                fields.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString().trim());
        return fields.toArray(new String[0]);
    }

    private static Festive.Theme theme(String name) {
        try {
            return Festive.Theme.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Festive.Theme.PLAIN;
        }
    }

    // ---- the calendar -----------------------------------------------------------------------

    static LocalDate today() {
        return Dates.today();
    }

    /** The personal days as they fall in {@code year} (29 February becomes the 28th in other years). */
    private static List<Festival> personalOn(int year) {
        List<Festival> out = new ArrayList<>();
        for (Festival p : personal) out.add(p.on(MonthDay.from(p.date).atYear(year)));
        return out;
    }

    /**
     * What is being celebrated today: a personal day first, then a festival, then the season one
     * falls in (any day between two entries with the same scene, such as all of Diwali week). Or null.
     */
    static Festival celebrating(LocalDate today) {
        for (Festival p : personalOn(today.getYear())) if (p.date.equals(today)) return p;
        List<Festival> shown = shown();
        for (Festival f : shown) if (f.date.equals(today)) return f;
        for (int i = 0; i + 1 < shown.size(); i++) {
            Festival a = shown.get(i);
            Festival b = shown.get(i + 1);
            if (a.theme != Festive.Theme.PLAIN && a.theme == b.theme
                    && a.date.isBefore(today) && b.date.isAfter(today)
                    && ChronoUnit.DAYS.between(a.date, b.date) <= SEASON_DAYS) {
                return a.on(today);
            }
        }
        return null;
    }

    /** Up to {@code max} festivals and personal days from today onwards, soonest first. */
    static List<Festival> upcoming(LocalDate today, int max) {
        List<Festival> all = new ArrayList<>();
        for (Festival f : shown()) if (!f.date.isBefore(today)) all.add(f);
        for (int year = today.getYear(); year <= today.getYear() + 1; year++) {
            for (Festival p : personalOn(year)) if (!p.date.isBefore(today)) all.add(p);
        }
        // Personal days sort ahead of a festival on the same date.
        Collections.sort(all, (a, b) -> a.date.equals(b.date)
                ? Boolean.compare(!a.personalDay, !b.personalDay) : a.date.compareTo(b.date));
        return all.size() > max ? new ArrayList<>(all.subList(0, max)) : all;
    }

    private static List<Festival> shown() {
        List<Festival> out = new ArrayList<>();
        for (Festival f : festivals) if (!f.hidden) out.add(f);
        return out;
    }

    /** Today's festival, or the next one within the horizon, or null. */
    static Festival next(LocalDate today) {
        List<Festival> next = upcoming(today, 1);
        return next.isEmpty() || next.get(0).daysFrom(today) > HORIZON_DAYS ? null : next.get(0);
    }

    /** The festival or personal day called {@code name}, as though it were today, for previewing its look. */
    static Festival preview(String name) {
        for (Festival f : festivals) if (f.name.equalsIgnoreCase(name)) return f.on(today());
        for (Festival p : personal) if (p.name.equalsIgnoreCase(name)) return p.on(today());
        return null;
    }

    // ---- wording ----------------------------------------------------------------------------

    /** The festival names for the language they were read in: English name to {name, greeting}. */
    private static String namesLanguage;
    private static Map<String, String[]> names = Collections.emptyMap();

    /** The festival's name and greeting in the TV's language, or null to use the English. */
    private static synchronized String[] translated(Context context, String english) {
        String language = Locale.getDefault().getLanguage();
        if (!language.equals(namesLanguage)) {
            Map<String, String[]> read = new HashMap<>();
            if (!language.equals("en")) {
                try {
                    for (String[] row : read(context, "festival-names/" + language + ".csv", 3)) {
                        read.put(row[0], new String[]{row[1], row[2]});
                    }
                } catch (IOException e) {
                    // No translation for this language: the English names stand.
                }
            }
            names = read;
            namesLanguage = language;
        }
        return names.get(english);
    }

    /** What to call the day: "Diwali", or "Asha's birthday". */
    String title(Context context) {
        if (personalDay) {
            return context.getString(theme == Festive.Theme.BIRTHDAY ? R.string.birthday_of : R.string.anniversary_of, who);
        }
        String[] t = translated(context, name);
        return t != null ? t[0] : name;
    }

    /** What to say on the day: "Happy Diwali", or "Happy birthday, Asha". */
    String greeting(Context context) {
        if (personalDay) {
            return context.getString(theme == Festive.Theme.BIRTHDAY ? R.string.happy_birthday : R.string.happy_anniversary, who);
        }
        String[] t = translated(context, name);
        return t != null ? t[1] : greeting;
    }

    long daysFrom(LocalDate today) {
        return ChronoUnit.DAYS.between(today, date);
    }

    boolean isToday(LocalDate today) {
        return date.equals(today);
    }

    /** For the home screen: "Happy Diwali", "Diwali tomorrow", "Diwali on Sunday", "Diwali in 14 days". */
    String headline(Context context, LocalDate today) {
        long days = daysFrom(today);
        if (days == 0) return greeting(context);
        String title = title(context);
        if (days == 1) return context.getString(R.string.festival_tomorrow, title);
        if (Dates.soon(days)) {
            return context.getString(R.string.festival_on_day, title, Dates.weekday(date.getDayOfWeek(), TextStyle.FULL));
        }
        int n = (int) days;
        return context.getResources().getQuantityString(R.plurals.festival_in_days, n, title, n);
    }

    /** "Thu 29 Oct", with a note for moon-dependent dates. */
    String shortDate(Resources r) {
        String day = Dates.weekday(date.getDayOfWeek(), TextStyle.SHORT) + " " + Dates.dayMonth(date);
        return expected ? r.getString(R.string.date_expected, day) : day;
    }
}
