package dev.vaari.aangan;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/**
 * The building blocks every screen shares: units, the type and colour palette, rows and columns,
 * rounded panels, the clock, dialogs, and opening a system settings page. Views are built in code,
 * so this stands in for styles and layouts.
 */
final class Ui {

    // The palette: text on the dark background at three strengths, then hairlines, cards and accents.
    static final int LABEL = 0xFFF5F5F7;
    static final int SECONDARY = 0x99EBEBF5;
    /** Half-strength white: the faintest text that still reads comfortably from the sofa (about 4.5:1). */
    static final int TERTIARY = 0x80EBEBF5;
    static final int HAIRLINE = 0x1FFFFFFF;
    /** Behind a focused row or button. */
    static final int FOCUS_FILL = 0x26FFFFFF;
    /** Settings' phone panel and the welcome cards. */
    static final int CARD = 0xFF1F2026;
    /** Rain chances and "Rain likely" lines. */
    static final int RAIN = 0xFF64D2FF;

    /** True if this TV has the settings page: by its standard action, or as one of {@code screens}. */
    static boolean hasSettings(Context context, String action, ComponentName... screens) {
        if (handled(context, new Intent(action))) return true;
        for (ComponentName screen : screens) if (handled(context, new Intent().setComponent(screen))) return true;
        return false;
    }

    private static boolean handled(Context context, Intent intent) {
        ResolveInfo r = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && !r.activityInfo.packageName.equals(STUBS);
    }

    /** Google's stand-ins for settings pages a TV doesn't have. */
    private static final String STUBS = "com.google.android.tv.frameworkpackagestubs";

    private final Context context;

    Ui(Context context) {
        this.context = context;
    }

    // ---- units and type ---------------------------------------------------------------------

    /** Every screen is laid out for a canvas this many dp wide, which is what most TVs report. */
    private static final int CANVAS_DP = 960;

    /**
     * {@code base}, with its density adjusted so the screen is {@link #CANVAS_DP} wide. Most TVs
     * already are (1080p at 320 dpi, 4K at 640); some boxes run 1080p at 240 dpi or less, which
     * would leave the layout small in one corner. For each screen's attachBaseContext.
     */
    static Context canvas(Context base) {
        DisplayMetrics m = base.getResources().getDisplayMetrics();
        int dpi = Math.round(Math.max(m.widthPixels, m.heightPixels) * (float) DisplayMetrics.DENSITY_DEFAULT / CANVAS_DP);
        Configuration config = base.getResources().getConfiguration();
        if (dpi == config.densityDpi) return base;
        Configuration scaled = new Configuration(config);
        scaled.densityDpi = dpi;
        return base.createConfigurationContext(scaled);
    }

    /** Density-independent pixels to pixels, read afresh each time in case the display changes. */
    int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                context.getResources().getDisplayMetrics()));
    }

    /** The thinnest visible line: three quarters of a dp, but never less than a pixel. */
    int hairline() {
        return Math.max(1, dp(0.75f));
    }

    static Typeface font(int weight) {
        return Typeface.create(Typeface.DEFAULT, weight, false);
    }

    /** Text that wraps, with the font's usual padding: for sentences in Settings and the welcome. */
    TextView text(float sp, int weight, int color, CharSequence s) {
        TextView t = new TextView(context);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(font(weight));
        t.setTextColor(color);
        t.setText(s);
        return t;
    }

    /** One tight line of text, without the font's padding: for the home screen and the Today card. */
    TextView line(float sp, int weight, int color) {
        TextView t = text(sp, weight, color, null);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    TextView line(float sp, int weight, int color, CharSequence s) {
        TextView t = line(sp, weight, color);
        t.setText(s);
        return t;
    }

    /** The time, "5:42" (or "17:42"), in tabular figures. */
    TextClock clock(float sp, int weight, int color) {
        TextClock time = new TextClock(context);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("H:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        time.setTypeface(font(weight));
        time.setTextColor(color);
        time.setLetterSpacing(-0.02f);
        time.setFontFeatureSettings("tnum");
        time.setIncludeFontPadding(false);
        return time;
    }

    /** The date, "Sunday, 25 October". */
    TextClock date(float sp, int weight, int color) {
        TextClock date = new TextClock(context);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        date.setTypeface(font(weight));
        date.setTextColor(color);
        return date;
    }

    // ---- layout -----------------------------------------------------------------------------

    LinearLayout row() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    LinearLayout column() {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        return col;
    }

    static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** Wrapped, {@code top} dp below the view before it. */
    LinearLayout.LayoutParams wrap(int top) {
        LinearLayout.LayoutParams lp = wrap();
        lp.topMargin = dp(top);
        return lp;
    }

    /** Full width, as tall as its content. */
    static LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** Full width, {@code top} dp below the view before it. */
    LinearLayout.LayoutParams fill(int top) {
        LinearLayout.LayoutParams lp = fill();
        lp.topMargin = dp(top);
        return lp;
    }

    /** An equal share of a row's width. */
    static LinearLayout.LayoutParams share(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    // ---- shapes -----------------------------------------------------------------------------

    GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    /** A dark card with a hairline edge. */
    GradientDrawable panel(float radius) {
        GradientDrawable d = rounded(CARD, radius);
        d.setStroke(hairline(), HAIRLINE);
        return d;
    }

    static GradientDrawable dot(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    // ---- system -----------------------------------------------------------------------------

    static AlertDialog.Builder dialog(Context context, String title) {
        return new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(title);
    }

    /**
     * Opens a system settings page: by its standard action, or else the first of {@code screens}
     * this TV has (where makers put the same page under their own name). Failing both, opens the
     * main Settings and returns false.
     */
    static boolean openSettings(Activity activity, String action, ComponentName... screens) {
        if (open(activity, new Intent(action))) return true;
        for (ComponentName screen : screens) if (open(activity, new Intent().setComponent(screen))) return true;
        open(activity, new Intent(Settings.ACTION_SETTINGS));
        return false;
    }

    /**
     * Starts the activity if a real one handles the intent. Some TVs answer settings actions they
     * lack with a Google stub that only says "No application can handle this action"; that counts
     * as nothing.
     */
    private static boolean open(Activity activity, Intent intent) {
        if (!handled(activity, intent)) return false;
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
