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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read on the main thread and the phone page's: each load publishes whole new lists. */
final class Festival {

    private static final int HORIZON_DAYS = 30;
    private static final int SEASON_DAYS = 9;
    private static final int EDIT_MONTHS = 13;
    /** A festival can move this far either side of the calendar's date, so a slip of the thumb can't lose it. */
    static final int MOVE_DAYS = 366;
    static final String EDITS = "festival_edits";

    private static volatile List<Festival> builtIn;
    private static volatile List<Festival> festivals = Collections.emptyList();
    /** Birthdays and anniversaries, dated in 2000, a leap year, so 29 February survives. */
    private static volatile List<Festival> personal = Collections.emptyList();

    final LocalDate date;
    final String name;
    final String greeting;
    final Festive.Theme theme;
    final boolean expected;
    final boolean personalDay;
    final String who;
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

    /** A family day is known by its name. */
    String key() {
        return personalDay ? name : original + "|" + name;
    }

    static synchronized void load(Context context, SharedPreferences prefs) {
        if (builtIn == null) {
            List<String[]> rows;
            try {
                rows = read(context, "festivals.csv", 4);
            } catch (IOException e) {
                throw new IllegalStateException("festivals.csv is missing from the build", e);
            }
            List<Festival> calendar = new ArrayList<>();
            for (String[] f : rows) {
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
            days.add(new Festival(date, name(d),
                    (d.birthday ? "Happy birthday, " : "Happy anniversary, ") + d.who,
                    d.birthday ? Festive.Theme.BIRTHDAY : Festive.Theme.ANNIVERSARY, false, true, d.who, date, false));
        }
        festivals = Collections.unmodifiableList(applied);
        personal = Collections.unmodifiableList(days);
    }

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

    boolean canMoveTo(LocalDate date) {
        return Math.abs(ChronoUnit.DAYS.between(original, date)) <= MOVE_DAYS;
    }

    LocalDate clampMove(LocalDate date) {
        LocalDate earliest = original.minusDays(MOVE_DAYS);
        LocalDate latest = original.plusDays(MOVE_DAYS);
        return date.isBefore(earliest) ? earliest : date.isAfter(latest) ? latest : date;
    }

    static void move(SharedPreferences prefs, Festival f, LocalDate date) {
        LocalDate to = f.clampMove(date);
        edit(prefs, f.key(), "date", to.equals(f.original) ? null : to.toString());
    }

    static void hide(SharedPreferences prefs, Festival f, boolean hidden) {
        edit(prefs, f.key(), "hidden", hidden ? Boolean.TRUE : null);
    }

    /** A day moved out of the window is still listed while its calendar date is in it, so it can be moved back. */
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

    static Festival byKey(String key) {
        for (Festival f : festivals) if (f.key().equals(key)) return f;
        return null;
    }

    static List<Festival> calendar() {
        return festivals;
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

    /** Quoting as Python's csv module, which the website uses, writes it. */
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

    static LocalDate today() {
        return Dates.today();
    }

    private static List<Festival> personalOn(int year) {
        List<Festival> out = new ArrayList<>();
        for (Festival p : personal) out.add(p.on(MonthDay.from(p.date).atYear(year)));
        return out;
    }

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

    static Festival next(LocalDate today) {
        List<Festival> next = upcoming(today, 1);
        return next.isEmpty() || next.get(0).daysFrom(today) > HORIZON_DAYS ? null : next.get(0);
    }

    static String name(People.Day d) {
        return d.who + (d.birthday ? "'s birthday" : "'s anniversary");
    }

    /** The day with this {@link #key()} or name, as though it were today. */
    static Festival preview(String keyOrName) {
        for (List<Festival> days : Arrays.asList(festivals, personal)) {
            for (Festival f : days) {
                if (f.key().equals(keyOrName) || f.name.equalsIgnoreCase(keyOrName)) return f.on(today());
            }
        }
        return null;
    }

    private static String namesLanguage;
    private static Map<String, String[]> names = Collections.emptyMap();

    private static synchronized String[] translated(Context context, String english) {
        String language = Locale.getDefault().getLanguage();
        if (!language.equals(namesLanguage)) {
            Map<String, String[]> read = new HashMap<>();
            if (!language.equals("en")) {
                try {
                    for (String[] row : read(context, "festival-names/" + language + ".csv", 3)) {
                        read.put(row[0], new String[]{row[1], row[2]});
                    }
                } catch (IOException ignored) {
                }
            }
            names = read;
            namesLanguage = language;
        }
        return names.get(english);
    }

    String title(Context context) {
        if (personalDay) {
            return context.getString(theme == Festive.Theme.BIRTHDAY ? R.string.birthday_of : R.string.anniversary_of, who);
        }
        String[] t = translated(context, name);
        return t != null ? t[0] : name;
    }

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

    String shortDate(Resources r) {
        String day = Dates.weekday(date.getDayOfWeek(), TextStyle.SHORT) + " " + Dates.dayMonth(date);
        return expected ? r.getString(R.string.date_expected, day) : day;
    }
}
