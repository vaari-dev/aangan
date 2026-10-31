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
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Locale;

final class Ui {

    static final int LABEL = 0xFFF5F5F7;
    static final int SECONDARY = 0x99EBEBF5;
    static final int TERTIARY = 0x80EBEBF5;
    static final int HAIRLINE = 0x1FFFFFFF;
    static final int FOCUS_FILL = 0x26FFFFFF;
    static final int CARD = 0xFF1F2026;
    static final int RAIN = 0xFF64D2FF;

    /** Google's stand-ins for settings pages a TV doesn't have. */
    private static final String STUBS = "com.google.android.tv.frameworkpackagestubs";
    private static final int CANVAS_DP = 960;

    private final Context context;

    Ui(Context context) {
        this.context = context;
    }

    /** Some boxes run 1080p at 240 dpi or less, which would leave the layout small in one corner. */
    static Context canvas(Context base) {
        DisplayMetrics m = base.getResources().getDisplayMetrics();
        int dpi = Math.round(Math.max(m.widthPixels, m.heightPixels) * (float) DisplayMetrics.DENSITY_DEFAULT / CANVAS_DP);
        Configuration config = base.getResources().getConfiguration();
        if (dpi == config.densityDpi) return base;
        Configuration scaled = new Configuration(config);
        scaled.densityDpi = dpi;
        return base.createConfigurationContext(scaled);
    }

    int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                context.getResources().getDisplayMetrics()));
    }

    int hairline() {
        return Math.max(1, dp(0.75f));
    }

    static Typeface font(int weight) {
        return Typeface.create(Typeface.DEFAULT, weight, false);
    }

    TextView text(float sp, int weight, int color, CharSequence s) {
        TextView t = new TextView(context);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(font(weight));
        t.setTextColor(color);
        t.setText(s);
        return t;
    }

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

    TextView heading(String s) {
        TextView t = text(12, 600, TERTIARY, s.toUpperCase(Locale.getDefault()));
        t.setLetterSpacing(0.08f);
        return t;
    }

    TextClock date(float sp, int weight, int color) {
        TextClock date = new TextClock(context);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        date.setTypeface(font(weight));
        date.setTextColor(color);
        return date;
    }

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

    ScrollView scroll(View content) {
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(32));
        scroll.addView(content);
        return scroll;
    }

    void selectable(View row, View.OnClickListener action) {
        row.setFocusable(true);
        row.setClickable(true);
        GradientDrawable focused = rounded(FOCUS_FILL, 12);
        row.setOnFocusChangeListener((v, hasFocus) -> v.setBackground(hasFocus ? focused : null));
        row.setOnClickListener(action);
    }

    static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    LinearLayout.LayoutParams wrap(int top) {
        LinearLayout.LayoutParams lp = wrap();
        lp.topMargin = dp(top);
        return lp;
    }

    LinearLayout.LayoutParams leftOf(float left) {
        LinearLayout.LayoutParams lp = wrap();
        lp.leftMargin = dp(left);
        return lp;
    }

    View divider() {
        View v = new View(context);
        v.setBackgroundColor(HAIRLINE);
        return v;
    }

    static LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    LinearLayout.LayoutParams fill(int top) {
        LinearLayout.LayoutParams lp = fill();
        lp.topMargin = dp(top);
        return lp;
    }

    static LinearLayout.LayoutParams share(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

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

    static AlertDialog.Builder dialog(Context context, String title) {
        return new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(title);
    }

    static boolean hasSettings(Context context, String action, ComponentName... screens) {
        if (handled(context, new Intent(action))) return true;
        for (ComponentName screen : screens) if (handled(context, new Intent().setComponent(screen))) return true;
        return false;
    }

    /** {@code screens} are where makers put the same page under their own name; false if none opened. */
    static boolean openSettings(Activity activity, String action, ComponentName... screens) {
        if (open(activity, new Intent(action))) return true;
        for (ComponentName screen : screens) if (open(activity, new Intent().setComponent(screen))) return true;
        open(activity, new Intent(Settings.ACTION_SETTINGS));
        return false;
    }

    /** Some TVs answer settings actions they lack with a stub that says "No application can handle this action". */
    private static boolean handled(Context context, Intent intent) {
        ResolveInfo r = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && !r.activityInfo.packageName.equals(STUBS);
    }

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
