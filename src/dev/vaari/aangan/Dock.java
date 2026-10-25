package dev.vaari.aangan;

import android.app.Activity;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The four large apps at the top of the home screen. The first time it's needed, the dock is filled
 * from a few popular streaming apps (or the apps pinned in Aangan 1.2 and earlier), then the rest in
 * alphabetical order, and saved. From then on only the person changes it: nothing reorders it, and
 * an app that's uninstalled leaves its slot empty rather than shifting the others along.
 */
final class Dock {

    static final int SIZE = 4;
    /** An empty slot. */
    static final String EMPTY = "";

    private static final String KEY = "dock";
    /** Aangan 1.2's pinned row, which the dock replaces. */
    private static final String OLD_PINS = "pins";
    private static final List<String> DEFAULTS = Arrays.asList(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "in.startv.hotstar",
            "com.apple.atve.sony.appletv",
            "com.apple.atve.androidtv.appletv",
            "com.google.android.youtube.tvkids");

    private Dock() {
    }

    /** The dock's packages, {@link #SIZE} of them, with {@link #EMPTY} for an empty slot. */
    static List<String> load(SharedPreferences prefs, Map<String, Apps.App> apps, List<String> hidden) {
        List<String> dock = new ArrayList<>();
        if (prefs.contains(KEY)) {
            JSONArray a = Prefs.array(prefs, KEY);
            for (int i = 0; i < SIZE; i++) dock.add(a.optString(i, EMPTY));
            return dock;
        }
        List<String> first = prefs.contains(OLD_PINS) ? Prefs.list(prefs, OLD_PINS) : DEFAULTS;
        for (String pkg : first) if (dock.size() < SIZE && apps.containsKey(pkg)) dock.add(pkg);
        for (Apps.App app : Apps.sorted(apps.values())) {
            if (dock.size() < SIZE && !dock.contains(app.pkg) && !hidden.contains(app.pkg)) dock.add(app.pkg);
        }
        while (dock.size() < SIZE) dock.add(EMPTY);
        set(prefs, dock);
        prefs.edit().remove(OLD_PINS).apply();
        return dock;
    }

    /**
     * Puts {@code pkg} in {@code slot}. If it was already elsewhere in the dock, the two places swap,
     * so nothing else drops out.
     */
    static void put(SharedPreferences prefs, List<String> dock, int slot, String pkg) {
        int was = pkg.equals(EMPTY) ? -1 : dock.indexOf(pkg);
        if (was >= 0) dock.set(was, dock.get(slot));
        dock.set(slot, pkg);
        set(prefs, dock);
    }

    static void swap(SharedPreferences prefs, List<String> dock, int a, int b) {
        String s = dock.get(a);
        dock.set(a, dock.get(b));
        dock.set(b, s);
        set(prefs, dock);
    }

    /**
     * Asks which app goes in {@code slot}, from every app on the TV, or to leave it empty. Choosing
     * one already in the dock swaps the two places. {@code done} gets the chosen package.
     */
    static void choose(Activity activity, Map<String, Apps.App> apps, List<String> dock, List<String> hidden, int slot,
                       Consumer<String> done) {
        List<String> choices = new ArrayList<>();
        List<String> names = new ArrayList<>();
        if (!dock.get(slot).equals(EMPTY)) {
            choices.add(EMPTY);
            names.add("Empty");
        }
        for (Apps.App app : Apps.sorted(apps.values())) {
            int at = dock.indexOf(app.pkg);
            choices.add(app.pkg);
            names.add(app.label + (at == slot ? "  ·  Here now" : at >= 0 ? "  ·  In place " + (at + 1)
                    : hidden.contains(app.pkg) ? "  ·  Hidden" : ""));
        }
        Ui.dialog(activity, "Place " + (slot + 1) + " in the dock")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    String pkg = choices.get(which);
                    put(Prefs.of(activity), dock, slot, pkg);
                    done.accept(pkg);
                })
                .show();
    }

    /** Forgets the person's choices; the next {@link #load} fills the dock afresh. */
    static void reset(SharedPreferences prefs) {
        prefs.edit().remove(KEY).apply();
    }

    static void set(SharedPreferences prefs, List<String> dock) {
        prefs.edit().putString(KEY, new JSONArray(dock).toString()).apply();
    }
}
