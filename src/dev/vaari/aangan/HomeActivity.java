package dev.vaari.aangan;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A calm, offline-first home screen in the spirit of the Apple TV 4K: clock, date, weather and
 * air quality across the top, a row of pinned apps, and every other app in a uniform grid.
 * The focused app casts a soft light in its own colour; press Up to the weather and OK for
 * the day's forecast and air. Hold OK on any tile for options. Laid out on tvOS proportions
 * for a 960x540dp canvas.
 */
public class HomeActivity extends Activity {

    private static final String[] DEFAULT_PINS = {
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.google.android.youtube.tv",
            "com.google.android.youtube.tvkids",
            "com.apple.atve.sony.appletv",
            "com.apple.atve.androidtv.appletv",
            "in.startv.hotstar",
    };
    private static final String SETTINGS_KEY = "__settings__";
    private static final String AANGAN_KEY = "__aangan__";

    // Layout, in dp. Six 16:9 tiles with 20dp gaps span 868dp, centred on the 960dp canvas.
    private static final int COLUMNS = 6;
    private static final int TILE_W = 128;
    private static final int TILE_H = 72;
    private static final int GAP = 20;
    private static final int ROW_PITCH = 124;
    private static final int TOP = 30;
    private static final int HEADER_H = 86;
    private static final int FIRST_ROW = 132;
    /** Grid rows are scrolled so the focused one never sits lower than this. */
    private static final int LOWEST_ROW = 380;
    private static final int RADIUS = 8;
    private static final int SHADOW_BLUR = 20;
    private static final int SHADOW_DROP = 10;

    // The ambient light's strength behind the top row, the grid, and apps with no strong colour.
    private static final float GLOW_PINNED = 0.24f;
    private static final float GLOW_GRID = 0.16f;
    private static final float GLOW_NEUTRAL = 0.10f;
    private static final int NEUTRAL = 0xFFB4C3E6;

    // Motion.
    private static final float FOCUS_SCALE = 1.15f;
    /** Tiles other than the focused one recede slightly, under a dark veil (they stay opaque). */
    private static final int UNFOCUSED_VEIL = 0x2E000000;
    private static final float PRESS_SCALE = 0.96f;
    private static final long FOCUS_IN_MS = 180;
    private static final long FOCUS_OUT_MS = 220;
    private static final long LABEL_MS = 150;

    // Colour.
    private static final int LABEL = 0xFFF5F5F7;
    private static final int SECONDARY = 0x99EBEBF5;
    private static final int TERTIARY = 0x4DEBEBF5;
    /** Festivals: a warm marigold, for festivals coming up soon. */
    static final int FESTIVE = 0xFFFFC56B;
    /** A festival's opening plays again only after this long away from the home screen. */
    private static final long FESTIVE_REPLAY_MS = 3 * 60 * 60_000L;

    /** How often to check, while visible, whether weather or air quality is due a refresh. */
    private static final long WEATHER_CHECK_MS = 5 * 60_000L;

    private static final class App {
        String pkg;
        String label;
        Intent intent;
        Drawable banner;
        Drawable icon;
    }

    private SharedPreferences prefs;
    private FrameLayout root;
    private Ambient ambient;
    private ScrollView scroll;
    private View header;
    private GridLayout pinnedGrid;
    private GridLayout appGrid;
    private Bitmap shadow;
    private String signature = "";
    private boolean resumed;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Runnable weatherCheck = this::maybeRefreshWeather;
    private Weather weather;
    private boolean fetching;
    private int failures;
    private long retryAt;
    private int glow;

    private View pill;
    private WeatherGlyph glyph;
    private TextView tempView;
    private TextView conditionView;
    private TextView feelsView;
    private View aqiGroup;
    private View aqiDot;
    private TextView aqiView;
    private TextView aqiCategoryView;
    private TextView credit;
    private TextView festivalView;
    private Festive festive;
    /** A festival to show instead of today's, set with: am start -n dev.vaari.aangan/.HomeActivity --es festival Holi */
    private String preview;
    private long festivePlayedAt;
    private GradientDrawable glass;
    private GradientDrawable glassFocused;
    private View today;
    private final Map<String, Integer> brand = new HashMap<>();

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (resumed) refresh();
            else signature = "";
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("aangan", MODE_PRIVATE);
        Festival.load(this, prefs);
        if (!prefs.contains("pins")) {
            prefs.edit().putString("pins", TextUtils.join(",", DEFAULT_PINS)).apply();
        }
        weather = Weather.load(prefs);
        shadow = shadowBitmap();
        buildFrame();
        showWeather();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        network.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        // Settings may have changed the city or the family's days while we were away.
        weather = Weather.load(prefs);
        Festival.load(this, prefs);
        refresh();
        showFestival();
        maybeRefreshWeather();
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(weatherCheck);
        super.onPause();
    }

    /** Pressing Home while already home returns to the top row. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // A plain Home press clears any preview.
        preview = intent.getStringExtra("festival");
        if (today != null) closeToday();
        goToTop();
    }

    /** Back never leaves the home screen; it returns to the top row instead. */
    @Override
    public void onBackPressed() {
        if (today != null) closeToday();
        else goToTop();
    }

    // ---- type and units ---------------------------------------------------------------------

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private static Typeface font(int weight) {
        return Typeface.create(Typeface.DEFAULT, weight, false);
    }

    private TextView text(float sp, int weight, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(font(weight));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    // ---- layout -----------------------------------------------------------------------------

    private void buildFrame() {
        // Focus changes drive scrolling (see scrollToRow); the default "just enough" scroll is disabled.
        scroll = new ScrollView(this) {
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
                return 0;
            }
        };
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        // The header fades away over the first row's worth of scrolling.
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) ->
                header.setAlpha(1f - Math.min(1f, y / (float) dp(ROW_PITCH))));

        int side = (getResources().getDisplayMetrics().widthPixels - dp(COLUMNS * TILE_W + (COLUMNS - 1) * GAP)) / 2;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(side, dp(TOP), side, dp(220));
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        header = buildHeader();
        content.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(HEADER_H)));

        pinnedGrid = newGrid();
        LinearLayout.LayoutParams pinnedLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pinnedLp.topMargin = dp(FIRST_ROW - TOP - HEADER_H);
        content.addView(pinnedGrid, pinnedLp);

        appGrid = newGrid();
        content.addView(appGrid);

        // The light sits behind the scrolling content, so tiles always draw over it.
        root = new FrameLayout(this);
        ambient = new Ambient(this);
        festive = new Festive(this);
        root.addView(festive, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(ambient, new FrameLayout.LayoutParams(dp(Ambient.WIDTH), dp(Ambient.HEIGHT)));
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        paintBackdrop(glowColor());
    }

    private void paintBackdrop(int color) {
        glow = color;
        DisplayMetrics m = getResources().getDisplayMetrics();
        getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(),
                Backdrop.paint(m.widthPixels, m.heightPixels, m.density, color)));
    }

    /** Time and date on the left; a frosted pill with weather and air quality on the right. */
    private View buildHeader() {
        FrameLayout frame = new FrameLayout(this);
        // The weather pill grows when focused; let it draw past its container's edges.
        frame.setClipChildren(false);

        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setBaselineAligned(true);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("H:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        time.setTypeface(font(300));
        time.setTextColor(LABEL);
        time.setLetterSpacing(-0.02f);
        time.setFontFeatureSettings("tnum");
        time.setIncludeFontPadding(false);
        timeRow.addView(time);
        TextClock meridiem = new TextClock(this);
        meridiem.setFormat12Hour("a");
        meridiem.setFormat24Hour("");
        meridiem.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        meridiem.setTypeface(font(500));
        meridiem.setTextColor(SECONDARY);
        meridiem.setAllCaps(true);
        LinearLayout.LayoutParams meridiemLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        meridiemLp.leftMargin = dp(5);
        timeRow.addView(meridiem, meridiemLp);
        clock.addView(timeRow);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        date.setTypeface(font(400));
        date.setTextColor(SECONDARY);
        // The next festival rides on the date line: "Sunday, 25 October  ·  Diwali in 14 days".
        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.addView(date);
        festivalView = text(15, 500, FESTIVE);
        dateRow.addView(festivalView);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateLp.topMargin = dp(4);
        clock.addView(dateRow, dateLp);
        frame.addView(clock, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.TOP));

        LinearLayout right = new LinearLayout(this);
        right.setClipChildren(false);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        pill = buildPill();
        right.addView(pill, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        // Open-Meteo data is CC BY 4.0; air quality comes from Copernicus CAMS.
        credit = text(9, 400, TERTIARY);
        credit.setText("Open-Meteo · Copernicus CAMS");
        LinearLayout.LayoutParams creditLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        creditLp.topMargin = dp(7);
        creditLp.rightMargin = dp(14);
        right.addView(credit, creditLp);
        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP);
        rightLp.topMargin = dp(12);
        frame.addView(right, rightLp);
        return frame;
    }

    private View buildPill() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x17FFFFFF, 0x0DFFFFFF});
        glass.setCornerRadius(dp(20));
        glass.setStroke(Math.max(1, dp(0.75f)), 0x1FFFFFFF);
        glassFocused = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33FFFFFF, 0x1FFFFFFF});
        glassFocused.setCornerRadius(dp(20));
        glassFocused.setStroke(Math.max(1, dp(0.75f)), 0x59FFFFFF);
        row.setBackground(glass);

        // Up from the top row reaches the pill; OK opens the day's forecast and air.
        row.setFocusable(true);
        row.setClickable(true);
        DecelerateInterpolator ease = new DecelerateInterpolator(1.5f);
        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.setPivotX(v.getWidth());
            v.setPivotY(v.getHeight() / 2f);
            v.setBackground(hasFocus ? glassFocused : glass);
            float scale = hasFocus ? 1.06f : 1f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(hasFocus ? FOCUS_IN_MS : FOCUS_OUT_MS)
                    .setInterpolator(ease).start();
            if (hasFocus) {
                ambient.hide();
                scroll.smoothScrollTo(0, 0);
            }
        });
        row.setOnClickListener(v -> openToday());

        glyph = new WeatherGlyph(this);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(22), dp(22)));

        tempView = text(22, 500, LABEL);
        tempView.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams tempLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tempLp.leftMargin = dp(8);
        row.addView(tempView, tempLp);

        LinearLayout lines = new LinearLayout(this);
        lines.setOrientation(LinearLayout.VERTICAL);
        conditionView = text(12, 500, 0xD9F5F5F7);
        feelsView = text(11.5f, 400, SECONDARY);
        lines.addView(conditionView);
        LinearLayout.LayoutParams feelsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        feelsLp.topMargin = dp(2);
        lines.addView(feelsView, feelsLp);
        LinearLayout.LayoutParams linesLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        linesLp.leftMargin = dp(10);
        row.addView(lines, linesLp);

        LinearLayout aqi = new LinearLayout(this);
        aqi.setOrientation(LinearLayout.HORIZONTAL);
        aqi.setGravity(Gravity.CENTER_VERTICAL);
        View divider = new View(this);
        divider.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(Math.max(1, dp(0.75f)), dp(20));
        dividerLp.leftMargin = dp(14);
        dividerLp.rightMargin = dp(14);
        aqi.addView(divider, dividerLp);
        aqiDot = new View(this);
        aqi.addView(aqiDot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        aqiView = text(15, 500, LABEL);
        aqiView.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams aqiLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aqiLp.leftMargin = dp(6);
        aqi.addView(aqiView, aqiLp);
        aqiCategoryView = text(12, 500, LABEL);
        LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        catLp.leftMargin = dp(6);
        aqi.addView(aqiCategoryView, catLp);
        aqiGroup = aqi;
        row.addView(aqi);
        return row;
    }

    private GridLayout newGrid() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(COLUMNS);
        grid.setUseDefaultMargins(false);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        return grid;
    }

    // ---- weather ----------------------------------------------------------------------------

    private void showWeather() {
        long now = System.currentTimeMillis();
        Festival festival = preview != null ? Festival.preview(preview) : Festival.next(Festival.today());
        festivalView.setText(festival == null ? "" : "  ·  " + festival.headline(Festival.today()));
        if (festival != null) festivalView.setTextColor(festival.theme.accent);
        boolean any = weather.hasWeather() || weather.hasAir();
        pill.setVisibility(any ? View.VISIBLE : View.INVISIBLE);
        credit.setVisibility(any ? View.VISIBLE : View.INVISIBLE);
        if (glowColor() != glow) paintBackdrop(glowColor());
        if (!any) return;

        boolean showSky = weather.hasWeather();
        glyph.setVisibility(showSky ? View.VISIBLE : View.GONE);
        tempView.setVisibility(showSky ? View.VISIBLE : View.GONE);
        ((View) conditionView.getParent()).setVisibility(showSky ? View.VISIBLE : View.GONE);
        if (showSky) {
            glyph.setGlyph(weather.glyph());
            tempView.setText(Weather.degrees(weather.temp));
            conditionView.setText(weather.condition());
            // An imminent shower matters more than the feels-like temperature.
            String rain = weather.rainSoon();
            feelsView.setText(rain != null ? rain : "Feels " + Weather.degrees(weather.feels));
            feelsView.setTextColor(rain != null ? 0xFF64D2FF : SECONDARY);
        }

        aqiGroup.setVisibility(weather.hasAir() ? View.VISIBLE : View.GONE);
        if (weather.hasAir()) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(weather.aqiColor());
            aqiDot.setBackground(dot);
            aqiView.setText("AQI " + weather.aqi);
            aqiCategoryView.setText(weather.aqiCategory());
            aqiCategoryView.setTextColor(weather.aqiColor());
        }
        // Old readings stay visible but dimmed, so a lost connection is obvious.
        pill.setAlpha(weather.isStale(now) ? 0.5f : 1f);
    }

    /** The sky's colour, except during a festival, when the room takes on the festival's colour. */
    private int glowColor() {
        Festival festival = celebrating();
        return festival != null ? festival.theme.glow : weather.glowColor();
    }

    private Festival celebrating() {
        return preview != null ? Festival.preview(preview) : Festival.celebrating(Festival.today());
    }

    /**
     * Puts up the festival's scene. Its opening plays when the festival changes, or when the home
     * screen comes back after a few hours; otherwise the scene is simply there, still.
     */
    private void showFestival() {
        Festival festival = celebrating();
        Festive.Theme theme = festival == null ? null : festival.theme;
        long now = System.currentTimeMillis();
        boolean replay = theme != festive.theme() || now - festivePlayedAt > FESTIVE_REPLAY_MS;
        if (theme == festive.theme() && !replay) return;
        festive.show(theme, replay);
        if (replay) festivePlayedAt = now;
        showWeather();
    }

    /** Fetches only while the home screen is visible, and only what is out of date. */
    private void maybeRefreshWeather() {
        handler.removeCallbacks(weatherCheck);
        if (!resumed) return;
        handler.postDelayed(weatherCheck, WEATHER_CHECK_MS);
        // Golden hour and the rain line depend on the time as well as the data.
        showWeather();

        long now = System.currentTimeMillis();
        // A cache from before the forecast card existed has no hourly data, so it counts as due.
        boolean wantWeather = now - weather.weatherAt > Weather.WEATHER_TTL || !weather.hasForecast();
        boolean wantAir = now - weather.airAt > Weather.AIR_TTL || weather.airTime.length == 0;
        if (fetching || now < retryAt || (!wantWeather && !wantAir)) return;

        fetching = true;
        network.execute(() -> {
            boolean ok = true;
            try {
                if (wantWeather) weather.fetchWeather(now);
            } catch (Exception e) {
                ok = false;
            }
            try {
                if (wantAir) weather.fetchAir(now);
            } catch (Exception e) {
                ok = false;
            }
            boolean success = ok;
            handler.post(() -> {
                fetching = false;
                if (success) {
                    failures = 0;
                    retryAt = 0;
                } else {
                    // Back off 1, 2, 4... minutes, up to 30.
                    retryAt = System.currentTimeMillis() + Math.min(30, 1L << Math.min(failures, 5)) * 60_000L;
                    failures++;
                }
                weather.save(prefs);
                showWeather();
            });
        });
    }

    // ---- apps -------------------------------------------------------------------------------

    private List<String> readList(String key) {
        String raw = prefs.getString(key, "");
        List<String> out = new ArrayList<>();
        for (String s : raw.split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    private void writeList(String key, List<String> values) {
        prefs.edit().putString(key, TextUtils.join(",", values)).apply();
    }

    private Map<String, App> loadApps() {
        PackageManager pm = getPackageManager();
        Map<String, App> apps = new LinkedHashMap<>();
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String category : categories) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || apps.containsKey(pkg)) continue;
                App app = new App();
                app.pkg = pkg;
                app.label = ri.loadLabel(pm).toString();
                app.intent = new Intent(Intent.ACTION_MAIN)
                        .addCategory(category)
                        .setComponent(new ComponentName(pkg, ri.activityInfo.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                app.banner = ri.activityInfo.loadBanner(pm);
                app.icon = ri.loadIcon(pm);
                apps.put(pkg, app);
            }
        }
        return apps;
    }

    private App settingsApp() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ResolveInfo ri = pm.resolveActivity(intent, 0);
        App app = new App();
        app.pkg = SETTINGS_KEY;
        app.label = "Settings";
        app.intent = intent;
        app.icon = ri != null ? ri.loadIcon(pm) : null;
        return app;
    }

    /** Aangan's own settings: city, Home button, birthdays. Labelled as a call to action until set up. */
    private App aanganApp(boolean setUp) {
        App app = new App();
        app.pkg = AANGAN_KEY;
        app.label = setUp ? "Aangan" : "Set up Aangan";
        app.intent = new Intent(this, SetupActivity.class);
        app.banner = getDrawable(R.drawable.banner);
        return app;
    }

    /** True once pressing Home brings up Aangan, either as the default home screen or via {@link HomeButton}. */
    private boolean isSetUp() {
        if (HomeButton.isOn(this)) return true;
        ResolveInfo home = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return home != null && getPackageName().equals(home.activityInfo.packageName);
    }

    /** Rebuilds the grids only when the set of apps, pins or hidden apps actually changed. */
    private void refresh() {
        Map<String, App> apps = loadApps();
        List<String> pins = readList("pins");
        Set<String> hidden = new LinkedHashSet<>(readList("hidden"));

        boolean setUp = isSetUp();
        String sig = apps.keySet() + "|" + pins + "|" + hidden + "|" + setUp;
        if (sig.equals(signature)) return;
        signature = sig;

        View focused = getCurrentFocus();
        Object focusedKey = focused != null ? focused.getTag() : null;

        pinnedGrid.removeAllViews();
        appGrid.removeAllViews();

        for (String pkg : pins) {
            App app = apps.get(pkg);
            if (app != null) pinnedGrid.addView(makeTile(app, true), cellParams());
        }

        List<App> rest = new ArrayList<>();
        for (App app : apps.values()) {
            if (!pins.contains(app.pkg) && !hidden.contains(app.pkg)) rest.add(app);
        }
        Collections.sort(rest, (a, b) -> a.label.compareToIgnoreCase(b.label));
        // Until Aangan opens with the Home button, its setup tile leads the grid.
        rest.add(setUp ? rest.size() : 0, aanganApp(setUp));
        rest.add(settingsApp());
        for (App app : rest) appGrid.addView(makeTile(app, false), cellParams());

        View target = focusedKey != null ? scroll.findViewWithTag(focusedKey) : null;
        if (target == null) target = firstTile();
        if (target != null) target.requestFocus();
    }

    private GridLayout.LayoutParams cellParams() {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = dp(TILE_W + GAP);
        lp.height = dp(ROW_PITCH);
        return lp;
    }

    private View firstTile() {
        GridLayout grid = pinnedGrid.getChildCount() > 0 ? pinnedGrid : appGrid;
        if (grid.getChildCount() == 0) return null;
        // Each cell's first child is the focusable lift.
        return ((ViewGroup) grid.getChildAt(0)).getChildAt(0);
    }

    private void goToTop() {
        scroll.smoothScrollTo(0, 0);
        View first = firstTile();
        if (first != null) first.requestFocus();
    }

    /** The pinned row shows the whole header; later rows rise just enough to stay on screen. */
    private int scrollToRow(View cell, boolean pinned) {
        int target = pinned ? 0 : Math.max(0, contentRect(cell).top - dp(LOWEST_ROW));
        scroll.smoothScrollTo(0, target);
        return target;
    }

    private Rect contentRect(View view) {
        Rect r = new Rect();
        view.getDrawingRect(r);
        ((ViewGroup) scroll.getChildAt(0)).offsetDescendantRectToMyCoords(view, r);
        return r;
    }

    /** Pools the app's colour under its tile, where the tile will be once scrolling settles. */
    private void lightUp(App app, View lift, int scrollTarget, boolean pinned) {
        Rect r = contentRect(lift);
        int color = brandColor(app);
        float strength = color == NEUTRAL ? GLOW_NEUTRAL : pinned ? GLOW_PINNED : GLOW_GRID;
        // Centred on the tile's lower edge, so the light pools beneath it rather than over the header.
        ambient.glow(r.exactCenterX(), r.bottom - scrollTarget + dp(8), color, strength);
    }

    /**
     * The app's most vivid colour, from its banner (or icon): hues weighted by how saturated and
     * bright they are, so Netflix gives red and Prime gives blue, not the black around the logo.
     * Grey-only artwork gets a neutral cool white.
     */
    private int brandColor(App app) {
        Integer cached = brand.get(app.pkg);
        if (cached != null) return cached;
        Drawable art = app.banner != null ? app.banner : app.icon;
        int result = NEUTRAL;
        if (art != null && art.getConstantState() != null) {
            Drawable d = art.getConstantState().newDrawable().mutate();
            Bitmap bmp = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, 32, 18);
            d.draw(new Canvas(bmp));
            float[] weight = new float[24];
            float[][] sum = new float[24][3];
            float[] hsv = new float[3];
            for (int y = 0; y < 18; y++) {
                for (int x = 0; x < 32; x++) {
                    int p = bmp.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    Color.colorToHSV(p, hsv);
                    if (hsv[1] < 0.3f || hsv[2] < 0.25f) continue;
                    int bucket = Math.min(23, (int) (hsv[0] / 15f));
                    float wgt = hsv[1] * hsv[2] * hsv[2];
                    weight[bucket] += wgt;
                    sum[bucket][0] += Color.red(p) * wgt;
                    sum[bucket][1] += Color.green(p) * wgt;
                    sum[bucket][2] += Color.blue(p) * wgt;
                }
            }
            bmp.recycle();
            int best = 0;
            for (int i = 1; i < 24; i++) if (weight[i] > weight[best]) best = i;
            // At least about 3% of the artwork has to be colourful to count.
            if (weight[best] > 0.03f * 32 * 18 * 0.3f) {
                int rgb = Color.rgb(Math.round(sum[best][0] / weight[best]),
                        Math.round(sum[best][1] / weight[best]), Math.round(sum[best][2] / weight[best]));
                Color.colorToHSV(rgb, hsv);
                hsv[1] = Math.max(0.5f, Math.min(hsv[1], 0.9f));
                hsv[2] = 1f;
                result = Color.HSVToColor(hsv);
            }
        }
        brand.put(app.pkg, result);
        return result;
    }

    // ---- tiles ------------------------------------------------------------------------------

    /**
     * One app: a 16:9 card with rounded corners, a soft shadow and a specular sheen that appear
     * only on focus, and the app's name beneath the focused card.
     */
    private View makeTile(App app, boolean pinned) {
        final int w = dp(TILE_W);
        final int h = dp(TILE_H);
        final int blur = dp(SHADOW_BLUR);

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);

        // The lift scales as one: shadow and card together.
        FrameLayout lift = new FrameLayout(this);
        lift.setTag(app.pkg);
        lift.setFocusable(true);
        lift.setClickable(true);
        lift.setLongClickable(true);
        lift.setClipChildren(false);
        lift.setClipToPadding(false);

        ImageView shadowView = new ImageView(this);
        shadowView.setImageBitmap(shadow);
        shadowView.setAlpha(0f);
        FrameLayout.LayoutParams shadowLp = new FrameLayout.LayoutParams(w + 2 * blur, h + 2 * blur);
        shadowLp.leftMargin = -blur;
        shadowLp.topMargin = -blur + dp(SHADOW_DROP);
        lift.addView(shadowView, shadowLp);

        FrameLayout card = new FrameLayout(this);
        float radius = dp(RADIUS);
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        if (app.banner != null) {
            ImageView banner = new ImageView(this);
            banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            banner.setImageDrawable(app.banner);
            card.addView(banner, new FrameLayout.LayoutParams(w, h));
        } else {
            int base = tileColor(app.icon);
            card.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{base, scaleBrightness(base, 0.7f)}));
            if (app.icon != null) {
                ImageView icon = new ImageView(this);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                icon.setImageDrawable(app.icon);
                card.addView(icon, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
            }
            // A hairline edge so dark tiles don't dissolve into the background.
            GradientDrawable edge = new GradientDrawable();
            edge.setCornerRadius(radius);
            edge.setStroke(Math.max(1, dp(0.75f)), 0x14FFFFFF);
            card.setForeground(edge);
        }
        View sheen = new Sheen(this);
        sheen.setAlpha(0f);
        card.addView(sheen, new FrameLayout.LayoutParams(w, h));
        View veil = new View(this);
        veil.setBackgroundColor(UNFOCUSED_VEIL);
        card.addView(veil, new FrameLayout.LayoutParams(w, h));
        lift.addView(card, new FrameLayout.LayoutParams(w, h));
        cell.addView(lift, new LinearLayout.LayoutParams(w, h));

        TextView label = text(12, 500, 0xE6F5F5F7);
        label.setText(app.label);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setAlpha(0f);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(12);
        cell.addView(label, labelLp);

        DecelerateInterpolator ease = new DecelerateInterpolator(1.5f);
        lift.setOnFocusChangeListener((v, hasFocus) -> {
            long ms = hasFocus ? FOCUS_IN_MS : FOCUS_OUT_MS;
            float scale = hasFocus ? FOCUS_SCALE : 1f;
            // Draw the focused cell last so its shadow falls over its neighbours, not under them.
            cell.setTranslationZ(hasFocus ? 1 : 0);
            v.animate().scaleX(scale).scaleY(scale).setDuration(ms).setInterpolator(ease).withLayer().start();
            veil.animate().alpha(hasFocus ? 0f : 1f).setDuration(ms).setInterpolator(ease).start();
            shadowView.animate().alpha(hasFocus ? 1f : 0f).setDuration(ms).setInterpolator(ease).start();
            sheen.animate().alpha(hasFocus ? 1f : 0f).setDuration(ms).setInterpolator(ease).start();
            label.animate().alpha(hasFocus ? 1f : 0f).setDuration(LABEL_MS).setInterpolator(ease).start();
            if (hasFocus) lightUp(app, v, scrollToRow(cell, pinned), pinned);
        });
        // A quick press-in before launching, so the remote's OK feels physical.
        lift.setOnClickListener(v -> v.animate().scaleX(PRESS_SCALE).scaleY(PRESS_SCALE).setDuration(90)
                .withEndAction(() -> v.animate().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE).setDuration(120)
                        .withEndAction(() -> launch(app, v)).start())
                .start());
        lift.setOnLongClickListener(v -> {
            showMenu(app, pinned);
            return true;
        });
        return cell;
    }

    /** One soft rounded-rectangle shadow, blurred once and shared by every tile. */
    private Bitmap shadowBitmap() {
        int w = dp(TILE_W);
        int h = dp(TILE_H);
        int blur = dp(SHADOW_BLUR);
        Bitmap bmp = Bitmap.createBitmap(w + 2 * blur, h + 2 * blur, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x80000000);
        p.setMaskFilter(new BlurMaskFilter(blur / 2f, BlurMaskFilter.Blur.NORMAL));
        new Canvas(bmp).drawRoundRect(new RectF(blur, blur, blur + w, blur + h), dp(RADIUS), dp(RADIUS), p);
        return bmp;
    }

    /** Light catching the top-left of a focused card, plus a bright hairline along its top edge. */
    private static final class Sheen extends View {
        private final Paint gloss = new Paint();
        private final Paint rim = new Paint();

        Sheen(Context context) {
            super(context);
            rim.setColor(0x2EFFFFFF);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            gloss.setShader(new LinearGradient(0, 0, w, h, new int[]{0x38FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.55f}, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), gloss);
            canvas.drawRect(0, 0, getWidth(), Math.max(1, getResources().getDisplayMetrics().density), rim);
        }
    }

    /** A dark, muted version of the icon's average colour, for apps without a TV banner. */
    private static int tileColor(Drawable icon) {
        int fallback = 0xFF2A2D34;
        if (icon == null || icon.getConstantState() == null) return fallback;
        Drawable d = icon.getConstantState().newDrawable().mutate();
        Bitmap bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, 16, 16);
        d.draw(new Canvas(bmp));
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int p = bmp.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                n++;
            }
        }
        bmp.recycle();
        if (n == 0) return fallback;
        float[] hsv = new float[3];
        Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (b / n), hsv);
        hsv[1] = Math.min(hsv[1], 0.55f);
        hsv[2] = Math.max(0.30f, Math.min(hsv[2], 0.38f));
        return Color.HSVToColor(hsv);
    }

    private static int scaleBrightness(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= factor;
        return Color.HSVToColor(hsv);
    }

    private void launch(App app) {
        launch(app, null);
    }

    /** Opens the app; from a tile, its window grows out of that tile, as on tvOS. */
    private void launch(App app, View from) {
        try {
            Bundle zoom = from == null ? null : ActivityOptions
                    .makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
            startActivity(app.intent, zoom);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Can't open " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- today card -------------------------------------------------------------------------

    /** Dims the home screen and rises the Today card out of it. Back, OK or Home closes it. */
    private void openToday() {
        if (today != null) return;
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xB3000000);
        View card = TodayCard.build(this, weather);
        overlay.addView(card, new FrameLayout.LayoutParams(dp(TodayCard.WIDTH),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        overlay.setFocusable(true);
        overlay.setOnKeyListener((v, keyCode, event) -> {
            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                case KeyEvent.KEYCODE_VOLUME_MUTE:
                    return false;
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (event.getAction() == KeyEvent.ACTION_UP) closeToday();
                    return true;
                default:
                    return true;
            }
        });
        root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        today = overlay;
        overlay.requestFocus();

        DecelerateInterpolator ease = new DecelerateInterpolator(2f);
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(220).setInterpolator(ease).start();
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.setTranslationY(dp(12));
        card.animate().scaleX(1f).scaleY(1f).translationY(0).setDuration(280).setInterpolator(ease).start();
    }

    private void closeToday() {
        View overlay = today;
        if (overlay == null) return;
        today = null;
        overlay.setFocusable(false);
        overlay.animate().alpha(0f).setDuration(160)
                .withEndAction(() -> root.removeView(overlay)).start();
        pill.requestFocus();
    }

    // ---- options menu -----------------------------------------------------------------------

    private void showMenu(App app, boolean pinned) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        List<String> pins = readList("pins");
        List<String> hidden = readList("hidden");

        if (app.pkg.equals(AANGAN_KEY)) return;
        if (app.pkg.equals(SETTINGS_KEY)) {
            if (hidden.isEmpty()) return;
            labels.add("Show " + hidden.size() + " hidden app" + (hidden.size() == 1 ? "" : "s"));
            actions.add(() -> writeList("hidden", new ArrayList<String>()));
        } else if (pinned) {
            int index = pins.indexOf(app.pkg);
            if (index > 0) {
                labels.add("Move left");
                actions.add(() -> {
                    Collections.swap(pins, index, index - 1);
                    writeList("pins", pins);
                });
            }
            if (index < pins.size() - 1) {
                labels.add("Move right");
                actions.add(() -> {
                    Collections.swap(pins, index, index + 1);
                    writeList("pins", pins);
                });
            }
            labels.add("Remove from top row");
            actions.add(() -> {
                pins.remove(app.pkg);
                writeList("pins", pins);
            });
        } else {
            labels.add("Add to top row");
            actions.add(() -> {
                pins.add(app.pkg);
                writeList("pins", pins);
            });
            labels.add("Hide");
            actions.add(() -> {
                hidden.add(app.pkg);
                writeList("hidden", hidden);
            });
        }
        if (!app.pkg.equals(SETTINGS_KEY)) {
            labels.add("App info");
            actions.add(() -> launch(appInfo(app)));
        }

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(app.label)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    actions.get(which).run();
                    refresh();
                })
                .show();
    }

    private App appInfo(App app) {
        App info = new App();
        info.label = "App info";
        info.intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return info;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // The MENU key, where a remote has one, opens options for the focused tile.
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View focused = getCurrentFocus();
            if (focused != null) return focused.performLongClick();
        }
        return super.onKeyDown(keyCode, event);
    }
}
