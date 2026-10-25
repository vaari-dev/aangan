package dev.vaari.aangan;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The first-run welcome: three cards that finish setting up (open with the Home button, the
 * city, the family's days) and a button to start. Shown once, the first time Aangan opens on a
 * TV where pressing Home doesn't yet bring it up.
 */
public class WelcomeActivity extends Activity {

    private static final int LABEL = 0xFFF5F5F7;
    private static final int SECONDARY = 0x99EBEBF5;
    private static final int DONE = 0xFF30D158;

    private SharedPreferences prefs;
    private LinearLayout cards;

    static boolean needed(SharedPreferences prefs) {
        return !prefs.getBoolean("welcomed", false);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("aangan", MODE_PRIVATE);
        // Leaving by any route counts as seen; the "Set up Aangan" tile stays until Home is set up.
        prefs.edit().putBoolean("welcomed", true).apply();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(48), dp(28), dp(48), dp(20));
        root.setClipChildren(false);
        root.setClipToPadding(false);

        ImageView art = new ImageView(this);
        art.setImageResource(R.drawable.banner);
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        art.setClipToOutline(true);
        GradientDrawable round = new GradientDrawable();
        round.setCornerRadius(dp(14));
        art.setBackground(round);
        root.addView(art, new LinearLayout.LayoutParams(dp(144), dp(81)));

        TextView title = text(32, 300, LABEL, "Welcome to Aangan");
        LinearLayout.LayoutParams titleLp = wrap();
        titleLp.topMargin = dp(16);
        root.addView(title, titleLp);
        TextView sub = text(15, 400, SECONDARY, "A calm home screen for your TV. Three quick things and you're ready.");
        LinearLayout.LayoutParams subLp = wrap();
        subLp.topMargin = dp(8);
        root.addView(sub, subLp);

        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setClipChildren(false);
        LinearLayout.LayoutParams cardsLp = wrap();
        cardsLp.topMargin = dp(24);
        root.addView(cards, cardsLp);

        TextView start = text(16, 600, LABEL, "Start using Aangan");
        start.setGravity(Gravity.CENTER);
        start.setPadding(dp(28), dp(12), dp(28), dp(12));
        start.setFocusable(true);
        start.setClickable(true);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x26FFFFFF);
        pill.setCornerRadius(dp(24));
        start.setBackground(pill);
        // Focus fills the button rather than scaling it, so the label stays crisp.
        start.setOnFocusChangeListener((v, has) -> {
            pill.setColor(has ? 0xFFF5F5F7 : 0x26FFFFFF);
            start.setTextColor(has ? 0xFF1C1B1A : LABEL);
        });
        start.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams startLp = wrap();
        startLp.topMargin = dp(24);
        root.addView(start, startLp);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        View focused = getCurrentFocus();
        int index = focused != null ? cards.indexOfChild(focused) : -1;
        cards.removeAllViews();
        boolean homeOn = HomeButton.isOn(this);
        card("Open with Home", homeOn
                ? "Done. The Home button now brings you to Aangan."
                : "Choose Turn on, then Aangan, and switch it on. Android shows its usual warning; Aangan only acts on the Home button.",
                homeOn ? "On" : "Turn on", homeOn, v -> openAccessibility());
        card("Your city", "Weather and air quality for " + Place.load(prefs).name + ". Change it if you live elsewhere.",
                "Change", false, v -> startActivity(new Intent(this, SetupActivity.class)));
        int days = People.load(prefs).size();
        card("Family days", days > 0
                ? days + (days == 1 ? " birthday or anniversary" : " birthdays and anniversaries") + " added."
                : "Add birthdays and anniversaries from your phone. Aangan celebrates them on the day.",
                days > 0 ? "Add more" : "Add", days > 0, v -> startActivity(new Intent(this, SetupActivity.class)));
        View target = cards.getChildAt(index >= 0 ? index : (homeOn ? 1 : 0));
        target.requestFocus();
    }

    private void openAccessibility() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (ActivityNotFoundException | SecurityException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void card(String title, String body, String action, boolean done, View.OnClickListener onClick) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(20), dp(22), dp(20));
        card.setFocusable(true);
        card.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1F2026);
        bg.setCornerRadius(dp(18));
        bg.setStroke(Math.max(1, dp(0.75f)), 0x1FFFFFFF);
        card.setBackground(bg);
        // Focus brightens the card and outlines it; no scaling, which would blur the text.
        card.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xFF34353F : 0xFF1F2026);
            bg.setStroke(dp(has ? 2.5f : 0.75f), has ? 0xF2FFFFFF : 0x1FFFFFFF);
            v.setElevation(has ? dp(12) : 0);
        });
        card.setOnClickListener(onClick);

        card.addView(text(19, 600, LABEL, title));
        TextView b = text(13.5f, 400, SECONDARY, body);
        b.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        bLp.topMargin = dp(8);
        card.addView(b, bLp);
        TextView a = text(14, 600, done ? DONE : 0xFFFFB86B, done ? "✓  " + action : action + "  ›");
        card.addView(a);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(250), dp(168));
        lp.leftMargin = dp(10);
        lp.rightMargin = dp(10);
        cards.addView(card, lp);
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private TextView text(float sp, int weight, int color, String s) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, weight, false));
        t.setTextColor(color);
        t.setText(s);
        return t;
    }
}
