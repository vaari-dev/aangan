package dev.vaari.aangan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class BackupTest {

    private SharedPreferences prefs;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        prefs = Prefs.of(context);
        Festival.load(context, prefs);
    }

    private void setUpATv() {
        new Place("Pune", "Maharashtra, India", 18.52, 73.86, "Asia/Kolkata", "IN").save(prefs);
        People.add(prefs, new People.Day(3, 14, true, "Asha"));
        People.add(prefs, new People.Day(11, 2, false, "Asha & Rohan"));
        Festival lohri = Festival.calendar().stream().filter(f -> f.name.equals("Lohri")).findFirst().get();
        Festival.move(prefs, lohri, lohri.date.plusDays(1));
        Dock.set(prefs, Arrays.asList("com.google.android.youtube.tv", Dock.EMPTY, "__settings__", Dock.EMPTY));
        Prefs.putList(prefs, HomeActivity.HIDDEN, Arrays.asList("com.example.ads"));
    }

    @Test
    public void aBackupPutsEverythingBack() {
        setUpATv();
        String backup = Backup.export(prefs);
        String edits = prefs.getString(Festival.EDITS, "");

        prefs.edit().clear().commit();
        assertTrue(Backup.restore(prefs, backup));

        assertEquals("Pune", Place.load(prefs).name);
        List<People.Day> days = People.load(prefs);
        assertEquals(2, days.size());
        assertEquals("Asha & Rohan", days.get(1).who);
        assertFalse(days.get(1).birthday);
        assertEquals(edits, prefs.getString(Festival.EDITS, ""));
        assertEquals("[\"com.google.android.youtube.tv\",\"\",\"__settings__\",\"\"]", prefs.getString(Dock.KEY, ""));
        assertEquals(Arrays.asList("com.example.ads"), Prefs.list(prefs, HomeActivity.HIDDEN));
    }

    @Test
    public void anythingElseChangesNothing() {
        setUpATv();
        String before = prefs.getAll().toString();
        for (String bad : new String[]{"", "not json", "{}", "[]", "{\"app\":\"Aangan\",\"format\":99}",
                "{\"app\":\"Other\",\"format\":1}",
                "{\"app\":\"Aangan\",\"format\":1,\"place\":{\"name\":\"Nowhere\",\"lat\":200,\"lon\":0,\"timezone\":\"UTC\"}}"}) {
            assertFalse(bad, Backup.restore(prefs, bad));
        }
        assertEquals(before, prefs.getAll().toString());
    }

    @Test
    public void badEntriesAreLeftOut() throws Exception {
        String backup = "{\"app\":\"Aangan\",\"format\":1,"
                + "\"people\":[{\"m\":2,\"d\":31,\"who\":\"Nobody\"},{\"m\":2,\"d\":29,\"who\":\"Leap\"},{\"m\":1,\"d\":1,\"who\":\" \"}],"
                + "\"festival_edits\":{\"2026-11-08|Diwali\":{\"date\":\"soon\"},\"2027-01-13|Lohri\":{\"hidden\":true}},"
                + "\"dock\":[\"com.ok.app\",\"not a package!\",\"com.ok.app\"]}";
        assertTrue(Backup.restore(prefs, backup));
        List<People.Day> days = People.load(prefs);
        assertEquals(1, days.size());
        assertEquals("Leap", days.get(0).who);
        JSONObject edits = new JSONObject(prefs.getString(Festival.EDITS, "{}"));
        assertEquals(1, edits.length());
        assertTrue(edits.getJSONObject("2027-01-13|Lohri").getBoolean("hidden"));
        assertEquals("[\"com.ok.app\",\"\",\"\",\"\"]", prefs.getString(Dock.KEY, ""));
    }

    @Test
    public void whatTheFileLeavesOutStaysAsItIs() {
        setUpATv();
        String dock = prefs.getString(Dock.KEY, "");
        assertTrue(Backup.restore(prefs, "{\"app\":\"Aangan\",\"format\":1,\"people\":[]}"));
        assertTrue(People.load(prefs).isEmpty());
        assertEquals(dock, prefs.getString(Dock.KEY, ""));
        assertEquals("Pune", Place.load(prefs).name);
        assertEquals(Arrays.asList("com.example.ads"), Prefs.list(prefs, HomeActivity.HIDDEN));
    }

    @Test
    public void importsAreHeldToWhatTheTvAllows() throws Exception {
        StringBuilder people = new StringBuilder();
        for (int i = 0; i < People.MAX + 5; i++) people.append(i == 0 ? "" : ",").append("{\"m\":1,\"d\":1,\"who\":\"P").append(i).append("\"}");
        String longName = "A very long name that goes on well past forty characters";
        String backup = "{\"app\":\"Aangan\",\"format\":1,\"people\":[{\"m\":5,\"d\":4,\"who\":\"" + longName + "\"}," + people + "],"
                + "\"festival_edits\":{\"2026-11-08|Diwali\":{\"date\":\"2031-01-01\"},\"2027-01-13|Lohri\":{\"date\":\"2027-01-14\"}}}";
        assertTrue(Backup.restore(prefs, backup));
        assertEquals(People.MAX, People.load(prefs).size());
        assertTrue(People.load(prefs).stream().anyMatch(d -> d.who.equals(longName.substring(0, People.MAX_NAME).trim())));
        JSONObject edits = new JSONObject(prefs.getString(Festival.EDITS, "{}"));
        assertEquals(1, edits.length());
        assertEquals("2027-01-14", edits.getJSONObject("2027-01-13|Lohri").getString("date"));
    }

    @Test
    public void aNameIsCutBetweenCharacters() {
        String name = "A".repeat(People.MAX_NAME - 1) + "\uD83E\uDE94";  // ending in an emoji, two chars
        assertTrue(Backup.restore(prefs, "{\"app\":\"Aangan\",\"format\":1,\"people\":[{\"m\":5,\"d\":4,\"who\":\"" + name + "\"}]}"));
        assertEquals("A".repeat(People.MAX_NAME - 1), People.load(prefs).get(0).who);
    }

    @Test
    public void nestingIsCountedWithoutParsing() {
        assertEquals(0, Backup.depth("\"[[[\""));
        assertEquals(2, Backup.depth("{\"a\":[1,\"]\\\"[\"]}"));
        char[] deep = new char[50_000];
        java.util.Arrays.fill(deep, '[');
        assertFalse(Backup.restore(prefs, new String(deep)));
    }

    @Test
    public void theFileSaysWhatItIs() throws Exception {
        JSONObject o = new JSONObject(Backup.export(prefs));
        assertEquals("Aangan", o.getString("app"));
        assertEquals(Backup.FORMAT, o.getInt("format"));
    }
}
