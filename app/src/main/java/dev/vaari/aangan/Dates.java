package dev.vaari.aangan;

import android.content.res.Resources;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Locale;

/** Dates as Aangan writes them, in the TV's language: "29 Oct", "Thursday", "in 9 days". */
final class Dates {

    /** Anything this close counts as soon: "on Sunday" rather than "in 9 days", and picked out in colour. */
    static final int SOON_DAYS = 7;

    /** A fixed "now" for tests; null for the real time, in the TV's time zone as it is at that moment. */
    static Clock fixed;

    private Dates() {
    }

    /** The clock to read, made afresh each time: the TV's time zone can change while Aangan runs. */
    private static Clock clock() {
        return fixed != null ? fixed : Clock.system(ZoneId.systemDefault());
    }

    static LocalDate today() {
        return LocalDate.now(clock());
    }

    static long nowMillis() {
        return clock().millis();
    }

    static boolean soon(long days) {
        return days < SOON_DAYS;
    }

    /** The twelve month names, "Jan" to "Dec" or "January" to "December". */
    static String[] months(TextStyle style) {
        String[] names = new String[12];
        for (Month m : Month.values()) names[m.ordinal()] = month(m, style);
        return names;
    }

    static String month(Month month, TextStyle style) {
        return month.getDisplayName(style, Locale.getDefault());
    }

    static String weekday(DayOfWeek day, TextStyle style) {
        return day.getDisplayName(style, Locale.getDefault());
    }

    /** "29 Oct". */
    static String dayMonth(LocalDate date) {
        return dayMonth(date.getMonthValue(), date.getDayOfMonth());
    }

    /** "29 Oct", for a day that comes round every year. */
    static String dayMonth(int month, int day) {
        return day + " " + month(Month.of(month), TextStyle.SHORT);
    }

    /** "Today", "Tomorrow", or "in 9 days" ("In 9 days" when {@code capital} is set). */
    static String until(Resources r, long days, boolean capital) {
        if (days == 0) return r.getString(R.string.today);
        if (days == 1) return r.getString(R.string.tomorrow);
        int n = (int) days;
        return r.getQuantityString(capital ? R.plurals.in_days_capital : R.plurals.in_days, n, n);
    }
}
