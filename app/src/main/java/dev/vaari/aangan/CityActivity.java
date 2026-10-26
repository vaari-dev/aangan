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
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CityActivity extends Activity {

    private static final long TYPING_PAUSE_MS = 400;
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
    private int asked;
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
        left.addView(ui.text(34, 300, Ui.LABEL, getString(R.string.city_label)));
        TextView now = ui.text(14, 400, Ui.SECONDARY, getString(R.string.city_now,
                current.name + (current.region.isEmpty() ? "" : ", " + current.region)));
        left.addView(now, ui.fill(8));

        query = new EditText(this);
        query.setHint(getString(R.string.city_hint));
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
        TextView hint = ui.text(13, 400, Ui.TERTIARY, getString(R.string.city_privacy));
        left.addView(hint, ui.fill(14));
        root.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout right = ui.column();
        status = ui.heading("");
        status.setPadding(ui.dp(16), ui.dp(8), 0, ui.dp(6));
        right.addView(status);
        results = ui.column();
        results.setPadding(0, 0, 0, ui.dp(24));
        right.addView(ui.scroll(results), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(ui.dp(420), ViewGroup.LayoutParams.MATCH_PARENT);
        rightLp.leftMargin = ui.dp(40);
        root.addView(right, rightLp);

        setContentView(root);
        focusResults = true;
        search();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(search);
        network.shutdownNow();
        super.onDestroy();
    }

    private void search() {
        String q = query.getText().toString().trim();
        int ask = ++asked;
        if (q.length() < MIN_SEARCH) {
            List<Place> popular = new ArrayList<>();
            for (Place p : Place.POPULAR) {
                if (p.name.toLowerCase(Locale.ROOT).startsWith(q.toLowerCase(Locale.ROOT))) popular.add(p);
            }
            show(getString(R.string.city_popular), null, popular.isEmpty() ? Place.POPULAR : popular);
            return;
        }
        status.setText(getString(R.string.city_searching).toUpperCase(Locale.getDefault()));
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
                if (places == null) {
                    show(getString(R.string.city_popular), getString(R.string.search_failed), Place.POPULAR);
                } else if (places.isEmpty()) {
                    show(getString(R.string.city_popular), getString(R.string.no_places, q), Place.POPULAR);
                } else {
                    show(getResources().getQuantityString(R.plurals.places_found, places.size(), places.size()), null, places);
                }
            });
        });
    }

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
        if (focus) (places.isEmpty() ? query : results.getChildAt(results.getChildCount() - places.size())).requestFocus();
    }

    private View row(Place p) {
        LinearLayout row = ui.column();
        row.setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10));
        ui.selectable(row, v -> {
            p.save(prefs);
            finish();
        });
        // By position, as names change: the default "Gurgaon" is the geocoder's "Gurugram".
        boolean here = Math.abs(p.lat - current.lat) < 0.05 && Math.abs(p.lon - current.lon) < 0.05;
        row.addView(ui.text(17, 500, Ui.LABEL, p.name + (here ? "  ✓" : "")));
        TextView where = ui.text(13, 400, Ui.SECONDARY, p.describe(getResources()));
        where.setGravity(Gravity.START);
        row.addView(where, ui.fill(2));
        return row;
    }
}
