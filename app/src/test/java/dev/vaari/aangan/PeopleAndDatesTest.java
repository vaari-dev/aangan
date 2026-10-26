package dev.vaari.aangan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class PeopleAndDatesTest {

    private SharedPreferences prefs;
    private Resources r;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        prefs = Prefs.of(context);
        r = context.getResources();
    }

    @Test
    public void onlyRealDaysAreValid() {
        assertTrue(People.Day.valid(2, 29));
        assertFalse(People.Day.valid(2, 30));
        assertFalse(People.Day.valid(4, 31));
        assertFalse(People.Day.valid(13, 1));
        assertFalse(People.Day.valid(0, 0));
    }

    @Test
    public void aStoredImpossibleDayIsLeftOut() {
        prefs.edit().putString(People.KEY, "[{\"m\":2,\"d\":31,\"who\":\"X\"},{\"m\":12,\"d\":1,\"who\":\"Y\"},{\"m\":1,\"d\":5,\"who\":\"Z\"}]").commit();
        List<People.Day> days = People.load(prefs);
        assertEquals(2, days.size());
        assertEquals("Z", days.get(0).who);
        assertEquals("Y", days.get(1).who);
    }

    @Test
    public void editsFindTheirDayByWhatItIs() {
        People.add(prefs, new People.Day(6, 1, true, "Asha"));
        People.add(prefs, new People.Day(1, 1, true, "Rohan"));
        String asha = new People.Day(6, 1, true, "Asha").key();
        assertTrue(People.update(prefs, asha, new People.Day(6, 2, true, "Asha")));
        assertFalse("the old key is gone", People.update(prefs, asha, new People.Day(6, 3, true, "Asha")));
        assertTrue(People.remove(prefs, new People.Day(6, 2, true, "Asha").key()));
        assertEquals(1, People.load(prefs).size());
    }

    @Test
    public void nextTimeRoundIncludesToday() {
        People.Day d = new People.Day(10, 25, true, "A");
        assertEquals(LocalDate.of(2026, 10, 25), d.next(LocalDate.of(2026, 10, 25)));
        assertEquals(LocalDate.of(2027, 10, 25), d.next(LocalDate.of(2026, 10, 26)));
    }

    @Test
    public void untilInEnglish() {
        assertEquals("Today", Dates.until(r, 0, true));
        assertEquals("Tomorrow", Dates.until(r, 1, false));
        assertEquals("in 9 days", Dates.until(r, 9, false));
        assertEquals("In 9 days", Dates.until(r, 9, true));
        assertEquals("29 Oct", Dates.dayMonth(10, 29));
    }

    @Test
    @Config(qualifiers = "hi")
    public void untilInHindi() {
        assertEquals("आज", Dates.until(r, 0, true));
        assertEquals("9 दिन में", Dates.until(r, 9, false));
    }

    @Test
    public void todayFollowsTheTvsTimeZoneAsItChanges() {
        java.util.TimeZone was = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Kiritimati"));
            LocalDate east = Dates.today();
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Pago_Pago"));
            LocalDate west = Dates.today();
            // 25 hours apart: never the same day.
            assertTrue(west.isBefore(east));
        } finally {
            java.util.TimeZone.setDefault(was);
        }
    }
}
