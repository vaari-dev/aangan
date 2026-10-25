package dev.vaari.aangan;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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

    /** A finished step's tick. */
    private static final int DONE = 0xFF30D158;
    /** A step still to do. */
    private static final int TO_DO = 0xFFFFB86B;
    private static final int CARD_FOCUSED = 0xFF34353F;
    /** The start button when focused: filled white, with dark text. */
    private static final int BUTTON_FOCUSED = Ui.LABEL;
    private static final int BUTTON_FOCUSED_TEXT = 0xFF1C1B1A;
    private static final String WELCOMED = "welcomed";

    private Ui ui;
    private SharedPreferences prefs;
    private LinearLayout cards;

    static boolean needed(SharedPreferences prefs) {
        return !prefs.getBoolean(WELCOMED, false);
    }

    static void markSeen(SharedPreferences prefs) {
        prefs.edit().putBoolean(WELCOMED, true).apply();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.canvas(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        prefs = Prefs.of(this);
        // Leaving by any route counts as seen; the "Set up Aangan" tile stays until Home is set up.
        markSeen(prefs);

        LinearLayout root = ui.column();
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(ui.dp(48), ui.dp(28), ui.dp(48), ui.dp(20));
        root.setClipChildren(false);
        root.setClipToPadding(false);

        ImageView art = new ImageView(this);
        art.setImageResource(R.drawable.banner);
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        art.setClipToOutline(true);
        art.setBackground(ui.rounded(Color.TRANSPARENT, 14));
        root.addView(art, new LinearLayout.LayoutParams(ui.dp(144), ui.dp(81)));

        root.addView(ui.text(32, 300, Ui.LABEL, "Welcome to Aangan"), ui.wrap(16));
        root.addView(ui.text(15, 400, Ui.SECONDARY, "A calm home screen for your TV. Three quick things and you're ready."),
                ui.wrap(8));

        cards = ui.row();
        cards.setClipChildren(false);
        root.addView(cards, ui.wrap(24));

        TextView start = ui.text(16, 600, Ui.LABEL, "Start using Aangan");
        start.setGravity(Gravity.CENTER);
        start.setPadding(ui.dp(28), ui.dp(12), ui.dp(28), ui.dp(12));
        start.setFocusable(true);
        start.setClickable(true);
        GradientDrawable pill = ui.rounded(Ui.FOCUS_FILL, 24);
        start.setBackground(pill);
        // Focus fills the button rather than scaling it, so the label stays crisp.
        start.setOnFocusChangeListener((v, hasFocus) -> {
            pill.setColor(hasFocus ? BUTTON_FOCUSED : Ui.FOCUS_FILL);
            start.setTextColor(hasFocus ? BUTTON_FOCUSED_TEXT : Ui.LABEL);
        });
        start.setOnClickListener(v -> finish());
        root.addView(start, ui.wrap(24));

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
                homeOn ? "On" : "Turn on", homeOn, v -> HomeButton.askToTurnOn(this));
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

    private void card(String title, String body, String action, boolean done, View.OnClickListener onClick) {
        LinearLayout card = ui.column();
        card.setPadding(ui.dp(22), ui.dp(20), ui.dp(22), ui.dp(20));
        card.setFocusable(true);
        card.setClickable(true);
        GradientDrawable bg = ui.panel(18);
        card.setBackground(bg);
        // Focus brightens the card and outlines it; no scaling, which would blur the text.
        card.setOnFocusChangeListener((v, hasFocus) -> {
            bg.setColor(hasFocus ? CARD_FOCUSED : Ui.CARD);
            bg.setStroke(hasFocus ? ui.dp(2.5f) : ui.dp(0.75f), hasFocus ? 0xF2FFFFFF : Ui.HAIRLINE);
            v.setElevation(hasFocus ? ui.dp(12) : 0);
        });
        card.setOnClickListener(onClick);

        card.addView(ui.text(19, 600, Ui.LABEL, title));
        TextView b = ui.text(13.5f, 400, Ui.SECONDARY, body);
        b.setLineSpacing(ui.dp(2), 1f);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        bLp.topMargin = ui.dp(8);
        card.addView(b, bLp);
        card.addView(ui.text(14, 600, done ? DONE : TO_DO, done ? "✓  " + action : action + "  ›"));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(250), ui.dp(168));
        lp.leftMargin = ui.dp(10);
        lp.rightMargin = ui.dp(10);
        cards.addView(card, lp);
    }
}
