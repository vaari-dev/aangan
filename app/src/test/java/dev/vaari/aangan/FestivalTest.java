package dev.vaari.aangan;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class FestivalTest {

    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        prefs = Prefs.of(context);
        Festival.load(context, prefs);
    }

    @After
    public void tearDown() {
        Testing.resetClock();
    }

    private Festival festival(String name) {
        for (Festival f : Festival.calendar()) if (f.name.equals(name)) return f;
        throw new AssertionError(name + " is not in the calendar");
    }

    private void reload() {
        Festival.load(context, prefs);
    }

    // ---- the calendar file ------------------------------------------------------------------

    @Test
    public void csvFieldsHandleQuotesAndCommas() {
        assertArrayEquals(new String[]{"2026-01-01", "A, B", "Say \"hi\"", "PLAIN"},
                Festival.csvFields("2026-01-01,\"A, B\" ,\"Say \"\"hi\"\"\", PLAIN"));
    }

    // ---- what is celebrated -----------------------------------------------------------------

    @Test
    public void aFestivalIsCelebratedOnItsDay() {
        Festival diwali = festival("Diwali");
        Festival today = Festival.celebrating(diwali.date);
        assertNotNull(today);
        assertEquals("Diwali", today.name);
    }

    @Test
    public void daysBetweenTwoEntriesOfOneSceneShareIt() {
        List<Festival> all = Festival.calendar();
        for (int i = 0; i + 1 < all.size(); i++) {
            Festival a = all.get(i);
            Festival b = all.get(i + 1);
            long gap = ChronoUnit.DAYS.between(a.date, b.date);
            if (a.theme != Festive.Theme.PLAIN && a.theme == b.theme && gap >= 2 && gap <= 9) {
                Festival between = Festival.celebrating(a.date.plusDays(1));
                assertNotNull(between);
                assertEquals(a.theme, between.theme);
                return;
            }
        }
        throw new AssertionError("no scene spans several days in the calendar");
    }

    @Test
    public void aHiddenFestivalIsNotCelebratedButCanStillBeEdited() {
        Festival holi = festival("Holi");
        Festival.hide(prefs, holi, true);
        reload();
        Festival today = Festival.celebrating(holi.date);
        assertTrue(today == null || !today.name.equals("Holi"));
        assertTrue(Festival.editable(holi.date.minusDays(1)).stream().anyMatch(f -> f.name.equals("Holi") && f.hidden));

        Festival.hide(prefs, Festival.byKey(holi.key()), false);
        reload();
        assertEquals("Holi", Festival.celebrating(holi.date).name);
    }

    // ---- the family's changes ---------------------------------------------------------------

    @Test
    public void movingAFestivalAndMovingItBack() {
        Festival lohri = festival("Lohri");
        Festival.move(prefs, lohri, lohri.date.plusDays(1));
        reload();
        Festival moved = Festival.byKey(lohri.key());
        assertEquals(lohri.date.plusDays(1), moved.date);
        assertTrue(moved.moved());
        assertFalse("a date the family chose is no longer a guess", moved.expected);

        Festival.move(prefs, moved, moved.original);
        reload();
        assertFalse(Festival.byKey(lohri.key()).moved());
        assertEquals("{}", prefs.getString(Festival.EDITS, "{}"));
    }

    @Test
    public void aMoveStaysWithinAYearOfTheCalendarsDate() {
        Festival lohri = festival("Lohri");
        Festival.move(prefs, lohri, lohri.date.plusYears(5));
        reload();
        assertEquals(lohri.original.plusDays(Festival.MOVE_DAYS), Festival.byKey(lohri.key()).date);
    }

    @Test
    public void aFestivalMovedIntoThePastCanStillBeEdited() {
        Festival christmas = festival("Christmas");
        LocalDate today = christmas.date.minusDays(10);
        Festival.move(prefs, christmas, today.minusDays(30));
        reload();
        assertTrue(Festival.editable(today).stream().anyMatch(f -> f.key().equals(christmas.key())));
    }

    // ---- family days ------------------------------------------------------------------------

    @Test
    public void aBirthdayComesBeforeAFestivalOnTheSameDay() {
        Festival diwali = festival("Diwali");
        People.add(prefs, new People.Day(diwali.date.getMonthValue(), diwali.date.getDayOfMonth(), true, "Asha"));
        reload();
        assertTrue(Festival.celebrating(diwali.date).personalDay);
        List<Festival> next = Festival.upcoming(diwali.date, 2);
        assertTrue(next.get(0).personalDay);
        assertEquals("Diwali", next.get(1).name);
    }

    @Test
    public void aLeapDayBirthdayFallsOnThe28thInOtherYears() {
        People.add(prefs, new People.Day(2, 29, true, "Rohan"));
        reload();
        Festival day = Festival.celebrating(LocalDate.of(2027, 2, 28));
        assertNotNull(day);
        assertEquals("Rohan", day.who);
        Festival march = Festival.celebrating(LocalDate.of(2027, 3, 1));
        assertTrue(march == null || !march.personalDay);
    }

    // ---- wording ----------------------------------------------------------------------------

    @Test
    public void headlinesCountDown() {
        Festival diwali = festival("Diwali");
        LocalDate day = diwali.date;
        assertEquals("Happy Diwali", diwali.headline(context, day));
        assertEquals("Diwali tomorrow", diwali.headline(context, day.minusDays(1)));
        assertEquals("Diwali on " + day.getDayOfWeek().getDisplayName(TextStyle.FULL, java.util.Locale.ENGLISH),
                diwali.headline(context, day.minusDays(3)));
        assertEquals("Diwali in 14 days", diwali.headline(context, day.minusDays(14)));
    }

    @Test
    public void personalDaysAreNamed() {
        People.add(prefs, new People.Day(5, 4, false, "Asha & Rohan"));
        reload();
        Festival day = Festival.celebrating(LocalDate.of(2027, 5, 4));
        assertEquals("Asha & Rohan's anniversary", day.title(context));
        assertEquals("Happy anniversary, Asha & Rohan", day.greeting(context));
    }

    @Test
    @Config(qualifiers = "hi")
    public void inHindi() {
        Festival diwali = festival("Diwali");
        assertEquals("दिवाली", diwali.title(context));
        assertEquals("दिवाली की शुभकामनाएँ", diwali.headline(context, diwali.date));
        assertEquals("14 दिन में दिवाली", diwali.headline(context, diwali.date.minusDays(14)));

        People.add(prefs, new People.Day(1, 9, true, "आशा"));
        reload();
        assertEquals("आशा का जन्मदिन", Festival.celebrating(LocalDate.of(2027, 1, 9)).title(context));
    }
}
