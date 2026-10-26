package dev.vaari.aangan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks on the data files themselves: assets/festivals.csv and its translations. These run in
 * CI on every push, so a calendar about to run out, or a festival without a translation, fails
 * the build rather than quietly reaching TVs.
 */
@RunWith(RobolectricTestRunner.class)
public class CalendarDataTest {

    /**
     * How far ahead the calendar must reach on the day CI runs. Settings and the phone page list a
     * year ahead; this leaves time to add the next year and release before TVs run out.
     */
    private static final int MONTHS_AHEAD = 6;
    /** Translations of the festival names, one file per language. */
    private static final String[] LANGUAGES = {"hi"};

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    private List<String[]> rows(String asset) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(context.getAssets().open(asset), StandardCharsets.UTF_8))) {
            for (String line; (line = in.readLine()) != null; ) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) rows.add(Festival.csvFields(line));
            }
        }
        return rows;
    }

    @Test
    public void everyFestivalHasADateANameAGreetingAndAScene() throws IOException {
        Set<String> scenes = new HashSet<>();
        for (Festive.Theme t : Festive.Theme.values()) scenes.add(t.name());
        for (String[] row : rows("festivals.csv")) {
            String line = String.join(",", row);
            assertTrue("too few fields: " + line, row.length >= 4);
            LocalDate.parse(row[0]);
            assertTrue("no name: " + line, !row[1].isEmpty());
            assertTrue("no greeting: " + line, !row[2].isEmpty());
            assertTrue("unknown scene " + row[3] + ": " + line, scenes.contains(row[3]));
            if (row.length > 4) assertEquals("the fifth field can only be 'expected': " + line, "expected", row[4]);
        }
    }

    @Test
    public void datesRunInOrderWithoutRepeats() throws IOException {
        LocalDate last = LocalDate.MIN;
        Set<String> seen = new HashSet<>();
        for (String[] row : rows("festivals.csv")) {
            LocalDate date = LocalDate.parse(row[0]);
            assertTrue(row[0] + " " + row[1] + " is out of order", !date.isBefore(last));
            assertTrue(row[0] + " " + row[1] + " appears twice", seen.add(row[0] + "|" + row[1]));
            last = date;
        }
    }

    @Test
    public void theCalendarReachesFarEnoughAhead() throws IOException {
        List<String[]> rows = rows("festivals.csv");
        LocalDate end = LocalDate.parse(rows.get(rows.size() - 1)[0]);
        LocalDate needed = LocalDate.now().plusMonths(MONTHS_AHEAD);
        if (end.isBefore(needed)) {
            fail("festivals.csv ends on " + end + ", less than " + MONTHS_AHEAD + " months away. Add the next year's "
                    + "festivals (Drik Panchang for New Delhi, and DoPT's holiday list) and release before TVs run out.");
        }
    }

    @Test
    public void everyFestivalIsTranslated() throws IOException {
        Set<String> names = new TreeSet<>();
        for (String[] row : rows("festivals.csv")) names.add(row[1]);
        for (String language : LANGUAGES) {
            Set<String> translated = new TreeSet<>();
            for (String[] row : rows("festival-names/" + language + ".csv")) {
                assertEquals("festival-names/" + language + ".csv: three fields: " + String.join(",", row), 3, row.length);
                translated.add(row[0]);
            }
            Set<String> missing = new TreeSet<>(names);
            missing.removeAll(translated);
            assertTrue("festival-names/" + language + ".csv is missing " + missing, missing.isEmpty());
            Set<String> unknown = new TreeSet<>(translated);
            unknown.removeAll(names);
            assertTrue("festival-names/" + language + ".csv names festivals not in the calendar: " + unknown, unknown.isEmpty());
        }
    }
}
