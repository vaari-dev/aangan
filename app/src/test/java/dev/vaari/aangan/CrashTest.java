package dev.vaari.aangan;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** The note a crash leaves on the TV, and the GitHub link made from it. */
@RunWith(RobolectricTestRunner.class)
public class CrashTest {

    @Test
    public void theNoteSaysWhereButNotWhat() {
        Throwable error = new IllegalStateException("couldn't show Asha's birthday",
                new JSONException("Value Asha at 0 of type java.lang.String"));
        String trace = Crash.trace(error);
        assertTrue(trace, trace.startsWith("java.lang.IllegalStateException\n    at dev.vaari.aangan.CrashTest."));
        assertTrue(trace, trace.contains("Caused by: org.json.JSONException\n"));
        assertFalse("the note quotes what the TV keeps", trace.contains("Asha"));
    }

    @Test
    public void theLinkStaysShortEnoughToOpen() {
        // Devanagari triples in length when encoded.
        String report = "Aangan 1.4 (13) on Android 14\n" + "आशा".repeat(1500);
        String link = Crash.issueLink(report);
        assertTrue(link.length() + " characters", link.length() <= Crash.MAX_LINK);
        assertTrue(link.contains("&body="));
    }
}
