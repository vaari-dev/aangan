package dev.vaari.aangan;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Choosing the city on the TV: a search box on the left, and on the right either popular cities
 * (before anything is typed) or places matching what's typed so far, updated as you type.
 * Choosing one saves it and returns to Settings.
 */
public class CityActivity extends Activity {

    /** Typing pauses this long before a search goes out, so each letter doesn't send one. */
    private static final long TYPING_PAUSE_MS = 400;
    /** Shorter searches show the popular cities that start with what's typed. */
    private static final int MIN_SEARCH = 2;

    private Ui ui;
    private SharedPreferences prefs;
    private Place current;
    private EditText query;
    private TextView status;
    private LinearLayout results;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Runnable search = this::search;
    /** Counts searches, so a slow answer to an older one is ignored. */
    private int asked;
    /** Set by the keyboard's Search key: focus the first result once this search's results arrive. */
    private boolean focusResults;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.canvas(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        prefs = Prefs.of(this);
        current = Place.load(prefs);

        LinearLayout root = ui.row();
        root.setPadding(ui.dp(48), ui.dp(36), ui.dp(48), ui.dp(24));

        LinearLayout left = ui.column();
        left.addView(ui.text(34, 300, Ui.LABEL, "Your city"));
        TextView now = ui.text(14, 400, Ui.SECONDARY, "Now: " + current.name
                + (current.region.isEmpty() ? "" : ", " + current.region));
        left.addView(now, ui.fill(8));

        query = new EditText(this);
        query.setHint("Type a city or town");
        query.setHintTextColor(Ui.TERTIARY);
        query.setTextColor(Ui.LABEL);
        query.setTextSize(18);
        query.setSingleLine(true);
        query.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        // Keep the keyboard below the screen rather than over it, so results show as you type.
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        query.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
        query.setBackground(ui.panel(12));
        query.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable bg = ui.panel(12);
            if (hasFocus) bg.setStroke(ui.dp(2), Ui.LABEL);
            v.setBackground(bg);
        });
        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(search);
                if (s.toString().trim().length() < MIN_SEARCH) search();
                else handler.postDelayed(search, TYPING_PAUSE_MS);
            }
        });
        // Search on the keyboard: put the keyboard away and go to the first result.
        query.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH && (event == null || event.getKeyCode() != KeyEvent.KEYCODE_ENTER)) {
                return false;
            }
            handler.removeCallbacks(search);
            focusResults = true;
            search();
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(v.getWindowToken(), 0);
            return true;
        });
        left.addView(query, ui.fill(28));
        TextView hint = ui.text(13, 400, Ui.TERTIARY, "Only what you type is sent, to Open-Meteo's place search. "
                + "If your town isn't listed, choose the nearest large one.");
        left.addView(hint, ui.fill(14));
        root.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout right = ui.column();
        status = ui.text(12, 600, Ui.TERTIARY, "");
        status.setLetterSpacing(0.08f);
        status.setPadding(ui.dp(16), ui.dp(8), 0, ui.dp(6));
        right.addView(status);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(ui.dp(32));
        results = ui.column();
        results.setPadding(0, 0, 0, ui.dp(24));
        scroll.addView(results);
        right.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(ui.dp(420), ViewGroup.LayoutParams.MATCH_PARENT);
        rightLp.leftMargin = ui.dp(40);
        root.addView(right, rightLp);

        setContentView(root);
        // Start on the list, so a popular city is one press away; Left reaches the search box.
        focusResults = true;
        search();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(search);
        network.shutdownNow();
        super.onDestroy();
    }

    /** Popular cities for a short query (all of them if none starts with it); otherwise asks the geocoder. */
    private void search() {
        String q = query.getText().toString().trim();
        int ask = ++asked;
        if (q.length() < MIN_SEARCH) {
            List<Place> popular = new ArrayList<>();
            for (Place p : Place.POPULAR) {
                if (p.name.toLowerCase(Locale.ROOT).startsWith(q.toLowerCase(Locale.ROOT))) popular.add(p);
            }
            show("Popular cities", null, popular.isEmpty() ? Place.POPULAR : popular);
            return;
        }
        status.setText("SEARCHING…");
        network.execute(() -> {
            List<Place> found;
            try {
                found = Place.search(q, current.country);
            } catch (Exception e) {
                found = null;
            }
            List<Place> places = found;
            runOnUiThread(() -> {
                if (ask != asked || isFinishing() || isDestroyed()) return;
                // When the search fails or finds nothing, say so plainly, and offer the popular cities instead.
                if (places == null) {
                    show("Popular cities", "Couldn't search right now. Check the TV's internet connection.", Place.POPULAR);
                } else if (places.isEmpty()) {
                    show("Popular cities", "No places called “" + q + "”. Try the nearest large town.", Place.POPULAR);
                } else {
                    show(places.size() == 1 ? "1 place" : places.size() + " places", null, places);
                }
            });
        });
    }

    /** Shows {@code places} under {@code heading}, after {@code note} (a sentence, such as an error) if any. */
    private void show(String heading, String note, List<Place> places) {
        status.setText(heading.toUpperCase(Locale.getDefault()));
        boolean focus = results.findFocus() != null || focusResults;
        focusResults = false;
        results.removeAllViews();
        if (note != null) {
            TextView t = ui.text(15, 400, Ui.SECONDARY, note);
            t.setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(12));
            results.addView(t);
        }
        for (Place p : places) results.addView(row(p));
        View first = null;
        for (int i = 0; i < results.getChildCount() && first == null; i++) {
            if (results.getChildAt(i).isFocusable()) first = results.getChildAt(i);
        }
        if (focus && first != null) first.requestFocus();
        else if (focus) query.requestFocus();
    }

    private View row(Place p) {
        LinearLayout row = ui.column();
        row.setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10));
        row.setFocusable(true);
        row.setClickable(true);
        GradientDrawable focused = ui.rounded(Ui.FOCUS_FILL, 12);
        row.setOnFocusChangeListener((v, hasFocus) -> v.setBackground(hasFocus ? focused : null));
        row.setOnClickListener(v -> choose(p));
        // By position, as names change: the default "Gurgaon" is the geocoder's "Gurugram".
        boolean here = Math.abs(p.lat - current.lat) < 0.05 && Math.abs(p.lon - current.lon) < 0.05;
        row.addView(ui.text(17, 500, Ui.LABEL, p.name + (here ? "  ✓" : "")));
        TextView where = ui.text(13, 400, Ui.SECONDARY, p.describe());
        where.setGravity(Gravity.START);
        row.addView(where, ui.fill(2));
        return row;
    }

    private void choose(Place p) {
        p.save(prefs);
        Weather.clear(prefs);
        finish();
    }
}
