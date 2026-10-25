package dev.vaari.aangan;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A festival or a personal day, and the calendar of them. Festivals come from
 * {@code assets/festivals.csv}, built into the app; birthdays and anniversaries from {@link People},
 * stored on the TV. Nothing needs the network.
 */
final class Festival {

    /** How far ahead the home screen mentions a festival. */
    private static final int HORIZON_DAYS = 30;
    /** Days between two entries with the same scene, up to this far apart, share it. */
    private static final int SEASON_DAYS = 9;

    private static List<Festival> festivals;
    private static List<Festival> personal;

    final LocalDate date;
    final String name;
    final String greeting;
    final Festive.Theme theme;
    /** True when the date depends on a moon sighting. */
    final boolean expected;
    final boolean isPersonal;

    private Festival(LocalDate date, String name, String greeting, Festive.Theme theme, boolean expected, boolean isPersonal) {
        this.date = date;
        this.name = name;
        this.greeting = greeting;
        this.theme = theme;
        this.expected = expected;
        this.isPersonal = isPersonal;
    }

    private Festival on(LocalDate day) {
        return new Festival(day, name, greeting, theme, expected, isPersonal);
    }

    // ---- loading ----------------------------------------------------------------------------

    /** Reads the festival calendar once, and the family's days afresh, since they can change. */
    static synchronized void load(Context context, SharedPreferences prefs) {
        if (festivals == null) {
            festivals = new ArrayList<>();
            for (String[] f : read(context, "festivals.csv")) {
                festivals.add(new Festival(LocalDate.parse(f[0]), f[1], f[2], theme(f[3]),
                        f.length > 4 && f[4].equals("expected"), false));
            }
        }
        personal = new ArrayList<>();
        for (People.Day d : People.load(prefs)) {
            // Stored against 2000, a leap year, so 29 February survives; it is moved to each year as needed.
            personal.add(new Festival(LocalDate.of(2000, d.month, d.day),
                    d.who + (d.birthday ? "'s birthday" : "'s anniversary"),
                    (d.birthday ? "Happy birthday, " : "Happy anniversary, ") + d.who,
                    d.birthday ? Festive.Theme.BIRTHDAY : Festive.Theme.ANNIVERSARY, false, true));
        }
    }

    /** Non-comment lines of an asset, split on commas. */
    private static List<String[]> read(Context context, String asset) {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                context.getAssets().open(asset), StandardCharsets.UTF_8))) {
            for (String line; (line = in.readLine()) != null; ) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] fields = line.split("\\s*,\\s*");
                if (fields.length >= 4) rows.add(fields);
            }
        } catch (IOException e) {
            throw new IllegalStateException("festivals.csv is missing from the build", e);
        }
        return rows;
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
        return LocalDate.now();
    }

    /** The personal days as they fall in {@code year} (29 February becomes the 28th in other years). */
    private static List<Festival> personalOn(int year) {
        List<Festival> out = new ArrayList<>();
        for (Festival p : personal) {
            if (p.date.getMonthValue() == 2 && p.date.getDayOfMonth() == 29 && !Year.isLeap(year)) {
                out.add(p.on(LocalDate.of(year, 2, 28)));
            } else {
                out.add(p.on(p.date.withYear(year)));
            }
        }
        return out;
    }

    /**
     * What is being celebrated today: a personal day first, then a festival, then the season one
     * falls in (any day between two entries with the same scene, such as all of Diwali week). Or null.
     */
    static Festival celebrating(LocalDate today) {
        for (Festival p : personalOn(today.getYear())) if (p.date.equals(today)) return p;
        for (Festival f : festivals) if (f.date.equals(today)) return f;
        for (int i = 0; i + 1 < festivals.size(); i++) {
            Festival a = festivals.get(i);
            Festival b = festivals.get(i + 1);
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
        for (Festival f : festivals) if (!f.date.isBefore(today)) all.add(f);
        for (int year = today.getYear(); year <= today.getYear() + 1; year++) {
            for (Festival p : personalOn(year)) if (!p.date.isBefore(today)) all.add(p);
        }
        // Personal days sort ahead of a festival on the same date.
        Collections.sort(all, (a, b) -> a.date.equals(b.date)
                ? Boolean.compare(!a.isPersonal, !b.isPersonal) : a.date.compareTo(b.date));
        return all.size() > max ? new ArrayList<>(all.subList(0, max)) : all;
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

    long daysFrom(LocalDate today) {
        return ChronoUnit.DAYS.between(today, date);
    }

    boolean isToday(LocalDate today) {
        return date.equals(today);
    }

    /** For the home screen: "Happy Diwali", "Diwali tomorrow", "Diwali on Sunday", "Diwali in 14 days". */
    String headline(LocalDate today) {
        long days = daysFrom(today);
        if (days == 0) return greeting;
        if (days == 1) return name + " tomorrow";
        if (days < 7) return name + " on " + date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return name + " in " + days + " days";
    }

    /** "Thu 29 Oct", with a note for moon-dependent dates. */
    String shortDate() {
        String day = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        String month = date.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        return day + " " + date.getDayOfMonth() + " " + month + (expected ? " (expected)" : "");
    }
}
