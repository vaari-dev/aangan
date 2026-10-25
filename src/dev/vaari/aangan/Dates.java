package dev.vaari.aangan;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

/** Dates as Aangan writes them, always in English: "29 Oct", "Thursday", "in 9 days". */
final class Dates {

    /** Anything this close counts as soon: "on Sunday" rather than "in 9 days", and picked out in colour. */
    static final int SOON_DAYS = 7;

    private Dates() {
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
        return month.getDisplayName(style, Locale.ENGLISH);
    }

    static String weekday(DayOfWeek day, TextStyle style) {
        return day.getDisplayName(style, Locale.ENGLISH);
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
    static String until(long days, boolean capital) {
        if (days == 0) return "Today";
        if (days == 1) return "Tomorrow";
        return (capital ? "In " : "in ") + days + " days";
    }
}
