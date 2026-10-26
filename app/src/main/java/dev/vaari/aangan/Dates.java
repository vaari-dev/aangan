package dev.vaari.aangan;

import android.content.res.Resources;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Locale;

final class Dates {

    private static final int SOON_DAYS = 7;

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

    static String dayMonth(LocalDate date) {
        return dayMonth(date.getMonthValue(), date.getDayOfMonth());
    }

    static String dayMonth(int month, int day) {
        return day + " " + month(Month.of(month), TextStyle.SHORT);
    }

    static String until(Resources r, long days, boolean capital) {
        if (days == 0) return r.getString(R.string.today);
        if (days == 1) return r.getString(R.string.tomorrow);
        int n = (int) days;
        return r.getQuantityString(capital ? R.plurals.in_days_capital : R.plurals.in_days, n, n);
    }
}
