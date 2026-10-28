package dev.vaari.aangan;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
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
import android.window.OnBackInvokedDispatcher;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.vaari.aangan.Apps.App;

/**
 * A calm, offline-first home screen in the spirit of the Apple TV 4K: clock, date, weather and
 * air quality across the top, a dock of four large apps the viewer chooses, and every other app
 * in a uniform grid.
 * The focused app casts a soft light in its own colour; press Up to the weather and OK for
 * the day's forecast and air. Hold OK on any tile for options. Laid out on tvOS proportions
 * for a 960x540dp canvas.
 */
public class HomeActivity extends Activity {

    private static final String SETTINGS_KEY = "__settings__";
    private static final String AANGAN_KEY = "__aangan__";
    /** Followed by the slot's number, for an empty place in the dock. */
    private static final String EMPTY_KEY = "__empty__";
    /** Followed by the slot's number, tags a dock tile, so focus stays on a place whatever app is in it. */
    private static final String DOCK_TAG = "dock:";
    // Preference keys: hidden apps (package names), and how many visits have shown the tips.
    static final String HIDDEN = "hidden";
    // "tips2" from 1.3, so people upgrading see the tips again, and learn about the dock.
    private static final String TIPS = "tips2";

    // Layout, in dp. Six 16:9 tiles with 20dp gaps span 868dp, centred on the 960dp canvas; so do
    // the dock's four larger ones.
    private static final int COLUMNS = 6;
    private static final int GAP = 20;
    private static final int ROW_PITCH = 124;
    private static final int DOCK_PITCH = 166;
    private static final int TOP = 30;
    private static final int HEADER_H = 86;
    private static final int FIRST_ROW = 132;
    /** Grid rows are scrolled so the focused one never sits lower than this. */
    private static final int LOWEST_ROW = 380;
    private static final int RADIUS = 8;
    private static final int SHADOW_BLUR = 20;
    private static final int SHADOW_DROP = 10;
    /** Room below the last row, so it can scroll up to {@link #LOWEST_ROW}. */
    private static final int BOTTOM_ROOM = 220;
    private static final int PILL_H = 40;
    private static final int TIP_MARGIN = 22;

    // The ambient light's strength behind the dock, the grid, and apps with no strong colour.
    private static final float GLOW_DOCK = 0.24f;
    private static final float GLOW_GRID = 0.16f;
    private static final float GLOW_NEUTRAL = 0.10f;
    private static final int NEUTRAL = 0xFFB4C3E6;
    // An app's colour is sampled from its banner drawn this small, in this many hue buckets; icons smaller still.
    private static final int BRAND_W = 32;
    private static final int BRAND_H = 18;
    private static final int HUES = 24;
    private static final int ICON_SAMPLE = 16;

    // Motion.
    /** Tiles other than the focused one recede slightly, under a dark veil (they stay opaque). */
    private static final int UNFOCUSED_VEIL = 0x2E000000;
    private static final float PRESS_SCALE = 0.96f;
    private static final long FOCUS_IN_MS = 180;
    private static final long FOCUS_OUT_MS = 220;
    private static final long LABEL_MS = 150;
    private static final long PRESS_MS = 90;
    private static final long RELEASE_MS = 120;
    private static final long TIP_DELAY_MS = 1200;
    private static final long TIP_FADE_MS = 600;
    private static final long TODAY_FADE_MS = 220;
    private static final long TODAY_RISE_MS = 280;
    private static final long TODAY_CLOSE_MS = 160;
    /** The dimming behind the Today card. */
    private static final int SCRIM = 0xB3000000;

    // Colour.
    /** Festivals: a warm marigold, for festivals coming up soon. */
    static final int FESTIVE = Festive.Theme.PLAIN.accent;
    /** The weather pill: frosted glass, brighter when focused. */
    private static final int[] GLASS = {0x17FFFFFF, 0x0DFFFFFF};
    private static final int[] GLASS_FOCUSED = {0x33FFFFFF, 0x1FFFFFFF};
    private static final int GLASS_EDGE_FOCUSED = 0x59FFFFFF;
    /** Tiles without a banner get a colour from their icon, or this if it has none. */
    private static final int TILE_FALLBACK = 0xFF2A2D34;
    /** A festival's opening plays again only after this long away from the home screen. */
    private static final long FESTIVE_REPLAY_MS = 3 * 60 * 60_000L;

    /** How many visits to the home screen show the tips. */
    private static final int TIP_VISITS = 8;

    static final String PREVIEW = "festival";

    /** How often to check, while visible, whether weather or air quality is due a refresh. */
    private static final long WEATHER_CHECK_MS = 5 * 60_000L;

    /** A tile's size: the dock's or the grid's. */
    private static final class Size {
        final int w;
        final int h;
        final int icon;
        final float label;
        /** How much a focused tile grows. */
        final float scale;
        final float glow;
        final int pitch;
        Bitmap shadow;

        Size(int w, int h, int icon, float label, float scale, float glow, int pitch) {
            this.w = w;
            this.h = h;
            this.icon = icon;
            this.label = label;
            this.scale = scale;
            this.glow = glow;
            this.pitch = pitch;
        }
    }

    private static final Size DOCK = new Size(202, 114, 56, 14, 1.1f, GLOW_DOCK, DOCK_PITCH);
    private static final Size GRID = new Size(128, 72, 40, 12, 1.15f, GLOW_GRID, ROW_PITCH);

    private Ui ui;
    private SharedPreferences prefs;
    private FrameLayout root;
    private Ambient ambient;
    private ScrollView scroll;
    private View header;
    private GridLayout dockGrid;
    private GridLayout appGrid;
    private String signature = "";
    /** The tile to focus after the next refresh, when a change to the dock should keep focus there. */
    private Object focusNext;
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
    private TextView tip;
    private TextView previewNote;
    private Festive festive;
    /** A festival to show as though it were today, until the home screen is left; see Festival.preview. */
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
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.canvas(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        prefs = Prefs.of(this);
        Festival.load(this, prefs);
        weather = Weather.load(prefs);
        DOCK.shadow = shadowBitmap(DOCK);
        GRID.shadow = shadowBitmap(GRID);
        // From Android 16, Back reaches apps targeting it only through this callback, not onBackPressed.
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::back);
        }
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
        welcomeOrTip();
        showFestival();
        showPreviewNote();
        maybeRefreshWeather();
    }

    @Override
    protected void onStop() {
        preview = null;
        super.onStop();
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(weatherCheck);
        super.onPause();
    }

    /** The first time on a TV that isn't set up, the welcome; then a few visits with a tip. */
    private void welcomeOrTip() {
        if (preview != null) return;
        if (WelcomeActivity.needed(prefs)) {
            if (!isSetUp()) {
                startActivity(new Intent(this, WelcomeActivity.class));
                return;
            }
            WelcomeActivity.markSeen(prefs);
        }
        int shown = prefs.getInt(TIPS, 0);
        if (shown >= TIP_VISITS) {
            tip.setVisibility(View.GONE);
            return;
        }
        prefs.edit().putInt(TIPS, shown + 1).apply();
        tip.animate().alpha(1f).setStartDelay(TIP_DELAY_MS).setDuration(TIP_FADE_MS).start();
    }

    /** Pressing Home while already home returns to the dock. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // A plain Home press clears any preview; a new one plays its opening.
        preview = intent.getStringExtra(PREVIEW);
        if (preview != null) festivePlayedAt = 0;
        if (today != null) closeToday();
        goToTop();
        // On screen already, onResume may not follow.
        if (resumed) {
            showFestival();
            showPreviewNote();
        }
    }

    /** Back never leaves the home screen: it returns to the dock, or from a preview to Settings. */
    @Override
    public void onBackPressed() {
        back();
    }

    private void back() {
        Festival previewed = Festival.preview(preview);
        if (today != null) {
            closeToday();
        } else if (previewed != null) {
            startActivity(new Intent(this, SetupActivity.class).putExtra(SetupActivity.FOCUS, previewed.key()));
        } else {
            goToTop();
        }
    }

    // ---- units -------------------------------------------------------------------------------

    private int dp(float v) {
        return ui.dp(v);
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

        int side = (getResources().getDisplayMetrics().widthPixels - dp(COLUMNS * GRID.w + (COLUMNS - 1) * GAP)) / 2;
        LinearLayout content = ui.column();
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(side, dp(TOP), side, dp(BOTTOM_ROOM));
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        header = buildHeader();
        content.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(HEADER_H)));

        dockGrid = newGrid(Dock.SIZE);
        content.addView(dockGrid, ui.wrap(FIRST_ROW - TOP - HEADER_H));

        appGrid = newGrid(COLUMNS);
        content.addView(appGrid);

        // The light sits behind the scrolling content, so tiles always draw over it.
        root = new FrameLayout(this);
        ambient = new Ambient(this);
        festive = new Festive(this);
        root.addView(festive, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(ambient, new FrameLayout.LayoutParams(dp(Ambient.WIDTH), dp(Ambient.HEIGHT)));
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // One-time tips along the bottom edge, for the first few visits.
        tip = ui.line(13, 400, Ui.SECONDARY,
                "Press  ▲  then OK for today's weather and air     ·     Hold OK on an app to put it in the dock or hide it");
        tip.setAlpha(0f);
        FrameLayout.LayoutParams tipLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        tipLp.bottomMargin = dp(TIP_MARGIN);
        root.addView(tip, tipLp);
        previewNote = ui.line(13, 400, Ui.SECONDARY, "");
        previewNote.setAlpha(0f);
        root.addView(previewNote, new FrameLayout.LayoutParams(tipLp));
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

        LinearLayout clock = ui.column();

        LinearLayout timeRow = ui.row();
        timeRow.setBaselineAligned(true);
        timeRow.addView(ui.clock(48, 300, Ui.LABEL));
        TextClock meridiem = new TextClock(this);
        meridiem.setFormat12Hour("a");
        meridiem.setFormat24Hour("");
        meridiem.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        meridiem.setTypeface(Ui.font(500));
        meridiem.setTextColor(Ui.SECONDARY);
        meridiem.setAllCaps(true);
        LinearLayout.LayoutParams meridiemLp = Ui.wrap();
        meridiemLp.leftMargin = dp(5);
        timeRow.addView(meridiem, meridiemLp);
        clock.addView(timeRow);

        // The next festival rides on the date line: "Sunday, 25 October  ·  Diwali in 14 days".
        LinearLayout dateRow = ui.row();
        dateRow.addView(ui.date(15, 400, Ui.SECONDARY));
        festivalView = ui.line(15, 500, FESTIVE);
        dateRow.addView(festivalView);
        clock.addView(dateRow, ui.wrap(4));
        frame.addView(clock, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.TOP));

        LinearLayout right = ui.column();
        right.setClipChildren(false);
        right.setGravity(Gravity.END);
        pill = buildPill();
        right.addView(pill, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(PILL_H)));
        // Open-Meteo data is CC BY 4.0; air quality comes from Copernicus CAMS.
        credit = ui.line(9, 400, Ui.TERTIARY, "Open-Meteo · Copernicus CAMS");
        LinearLayout.LayoutParams creditLp = ui.wrap(7);
        creditLp.rightMargin = dp(14);
        right.addView(credit, creditLp);
        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP);
        rightLp.topMargin = dp(12);
        frame.addView(right, rightLp);
        return frame;
    }

    private View buildPill() {
        LinearLayout row = ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        glass = glass(GLASS, Ui.HAIRLINE);
        glassFocused = glass(GLASS_FOCUSED, GLASS_EDGE_FOCUSED);
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

        tempView = ui.line(22, 500, Ui.LABEL);
        tempView.setFontFeatureSettings("tnum");
        row.addView(tempView, leftOf(8));

        LinearLayout lines = ui.column();
        conditionView = ui.line(12, 500, 0xD9F5F5F7);
        feelsView = ui.line(11.5f, 400, Ui.SECONDARY);
        lines.addView(conditionView);
        lines.addView(feelsView, ui.wrap(2));
        row.addView(lines, leftOf(10));

        LinearLayout aqi = ui.row();
        aqi.setGravity(Gravity.CENTER_VERTICAL);
        View divider = new View(this);
        divider.setBackgroundColor(Ui.HAIRLINE);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(ui.hairline(), dp(20));
        dividerLp.leftMargin = dp(14);
        dividerLp.rightMargin = dp(14);
        aqi.addView(divider, dividerLp);
        aqiDot = new View(this);
        aqi.addView(aqiDot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        aqiView = ui.line(15, 500, Ui.LABEL);
        aqiView.setFontFeatureSettings("tnum");
        aqi.addView(aqiView, leftOf(6));
        aqiCategoryView = ui.line(12, 500, Ui.LABEL);
        aqi.addView(aqiCategoryView, leftOf(6));
        aqiGroup = aqi;
        row.addView(aqi);
        return row;
    }

    private GradientDrawable glass(int[] colors, int edge) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
        d.setCornerRadius(dp(20));
        d.setStroke(ui.hairline(), edge);
        return d;
    }

    /** Wrapped, {@code left} dp after the view before it in a row. */
    private LinearLayout.LayoutParams leftOf(int left) {
        LinearLayout.LayoutParams lp = Ui.wrap();
        lp.leftMargin = dp(left);
        return lp;
    }

    private GridLayout newGrid(int columns) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
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
            feelsView.setTextColor(rain != null ? Ui.RAIN : Ui.SECONDARY);
        }

        aqiGroup.setVisibility(weather.hasAir() ? View.VISIBLE : View.GONE);
        if (weather.hasAir()) {
            aqiDot.setBackground(Ui.dot(weather.aqiColor()));
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

    private void showPreviewNote() {
        Festival festival = Festival.preview(preview);
        previewNote.animate().cancel();
        previewNote.setAlpha(0f);
        if (festival == null) return;
        tip.animate().cancel();
        tip.setAlpha(0f);
        previewNote.setText("A preview of " + festival.name + "     ·     Back returns to Settings");
        previewNote.animate().alpha(1f).setStartDelay(TIP_DELAY_MS).setDuration(TIP_FADE_MS).start();
    }

    /**
     * Puts up the festival's scene. Its opening plays when the festival changes, or when the home
     * screen comes back after a few hours; otherwise the scene is simply there, still.
     */
    private void showFestival() {
        Festival festival = celebrating();
        // A PLAIN festival has a greeting but no scene, so it shows as no scene at all.
        Festive.Theme theme = festival == null || festival.theme == Festive.Theme.PLAIN ? null : festival.theme;
        long now = System.currentTimeMillis();
        boolean replay = theme != festive.theme() || now - festivePlayedAt > FESTIVE_REPLAY_MS;
        if (!replay) return;
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
        boolean wantAir = now - weather.airAt > Weather.AIR_TTL || !weather.hasAirSeries();
        if (fetching || now < retryAt || (!wantWeather && !wantAir)) return;

        fetching = true;
        // The fetch fills this object; onResume may swap in a fresh one meanwhile (say, for a new city).
        Weather target = weather;
        network.execute(() -> {
            boolean ok = true;
            try {
                if (wantWeather) target.fetchWeather(now);
            } catch (Exception e) {
                ok = false;
            }
            try {
                if (wantAir) target.fetchAir(now);
            } catch (Exception e) {
                ok = false;
            }
            boolean success = ok;
            handler.post(() -> {
                fetching = false;
                if (target != weather) {
                    // Fetched for a city or cache that has since been replaced: start again for the current one.
                    maybeRefreshWeather();
                    return;
                }
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

    /** Rebuilds the grids only when the apps, the dock or the hidden apps actually changed. */
    private void refresh() {
        Map<String, App> apps = Apps.load(this);
        List<String> hidden = Prefs.list(prefs, HIDDEN);
        List<String> dock = Dock.load(prefs, apps, hidden);

        boolean setUp = isSetUp();
        String sig = apps.keySet() + "|" + dock + "|" + hidden + "|" + setUp;
        View focused = getCurrentFocus();
        Object focusedKey = focusNext != null ? focusNext : focused != null ? focused.getTag() : null;
        focusNext = null;
        if (sig.equals(signature)) return;
        signature = sig;

        dockGrid.removeAllViews();
        appGrid.removeAllViews();

        for (int slot = 0; slot < Dock.SIZE; slot++) {
            App app = apps.get(dock.get(slot));
            dockGrid.addView(makeTile(app != null ? app : emptySlot(slot), DOCK, slot), cellParams(DOCK));
        }

        List<App> rest = new ArrayList<>();
        for (App app : Apps.sorted(apps.values())) {
            if (!dock.contains(app.pkg) && !hidden.contains(app.pkg)) rest.add(app);
        }
        // Until Aangan opens with the Home button, its setup tile leads the grid.
        rest.add(setUp ? rest.size() : 0, aanganApp(setUp));
        rest.add(settingsApp());
        for (App app : rest) appGrid.addView(makeTile(app, GRID, -1), cellParams(GRID));

        View target = focusedKey != null ? scroll.findViewWithTag(focusedKey) : null;
        if (target == null) target = firstTile();
        if (target != null) target.requestFocus();
    }

    /** A dock slot with no app in it: OK chooses one. */
    private App emptySlot(int slot) {
        App app = new App();
        app.pkg = EMPTY_KEY + slot;
        app.label = "Choose an app";
        return app;
    }

    private GridLayout.LayoutParams cellParams(Size size) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = dp(size.w + GAP);
        lp.height = dp(size.pitch);
        return lp;
    }

    private View firstTile() {
        if (dockGrid.getChildCount() == 0) return null;
        // Each cell's first child is the focusable lift.
        return ((ViewGroup) dockGrid.getChildAt(0)).getChildAt(0);
    }

    private void goToTop() {
        scroll.smoothScrollTo(0, 0);
        View first = firstTile();
        if (first != null) first.requestFocus();
    }

    /** The dock shows the whole header; grid rows rise just enough to stay on screen. */
    private int scrollToRow(View cell, Size size) {
        int target = size == DOCK ? 0 : Math.max(0, contentRect(cell).top - dp(LOWEST_ROW));
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
    private void lightUp(App app, View lift, int scrollTarget, Size size) {
        Rect r = contentRect(lift);
        int color = brandColor(app);
        float strength = color == NEUTRAL ? GLOW_NEUTRAL : size.glow;
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
        Bitmap bmp = render(app.banner != null ? app.banner : app.icon, BRAND_W, BRAND_H);
        int result = NEUTRAL;
        if (bmp != null) {
            float[] weight = new float[HUES];
            float[][] sum = new float[HUES][3];
            float[] hsv = new float[3];
            for (int y = 0; y < BRAND_H; y++) {
                for (int x = 0; x < BRAND_W; x++) {
                    int p = bmp.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    Color.colorToHSV(p, hsv);
                    if (hsv[1] < 0.3f || hsv[2] < 0.25f) continue;
                    int bucket = Math.min(HUES - 1, (int) (hsv[0] / (360f / HUES)));
                    float wgt = hsv[1] * hsv[2] * hsv[2];
                    weight[bucket] += wgt;
                    sum[bucket][0] += Color.red(p) * wgt;
                    sum[bucket][1] += Color.green(p) * wgt;
                    sum[bucket][2] += Color.blue(p) * wgt;
                }
            }
            bmp.recycle();
            int best = 0;
            for (int i = 1; i < HUES; i++) if (weight[i] > weight[best]) best = i;
            // At least about 3% of the artwork has to be colourful to count.
            if (weight[best] > 0.03f * BRAND_W * BRAND_H * 0.3f) {
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
     * only on focus, and the app's name beneath the focused card. {@code slot} is its place in the
     * dock, or -1 in the grid.
     */
    private View makeTile(App app, Size size, int slot) {
        final int w = dp(size.w);
        final int h = dp(size.h);
        final int blur = dp(SHADOW_BLUR);

        LinearLayout cell = ui.column();
        cell.setClipChildren(false);
        cell.setClipToPadding(false);

        // The lift scales as one: shadow and card together.
        FrameLayout lift = new FrameLayout(this);
        lift.setTag(slot >= 0 ? DOCK_TAG + slot : app.pkg);
        // The name shows only on focus, so screen readers need it spelt out.
        lift.setContentDescription(app.intent == null ? "Empty place " + (slot + 1) + " in the dock. Choose an app" : app.label);
        lift.setFocusable(true);
        lift.setClickable(true);
        lift.setLongClickable(true);
        lift.setClipChildren(false);
        lift.setClipToPadding(false);

        ImageView shadowView = new ImageView(this);
        shadowView.setImageBitmap(size.shadow);
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
        if (app.intent == null) {
            // An empty dock slot: an outline with a plus.
            GradientDrawable outline = new GradientDrawable();
            outline.setCornerRadius(radius);
            outline.setColor(0x0DFFFFFF);
            outline.setStroke(dp(1.5f), 0x40FFFFFF, dp(6), dp(5));
            card.setBackground(outline);
            card.addView(ui.line(32, 300, Ui.SECONDARY, "+"),
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        } else if (app.banner != null) {
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
                card.addView(icon, new FrameLayout.LayoutParams(dp(size.icon), dp(size.icon), Gravity.CENTER));
            }
            // A hairline edge so dark tiles don't dissolve into the background.
            GradientDrawable edge = new GradientDrawable();
            edge.setCornerRadius(radius);
            edge.setStroke(ui.hairline(), 0x14FFFFFF);
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

        TextView label = ui.line(size.label, 500, 0xE6F5F5F7, app.label);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setAlpha(0f);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(12);
        cell.addView(label, labelLp);

        DecelerateInterpolator ease = new DecelerateInterpolator(1.5f);
        lift.setOnFocusChangeListener((v, hasFocus) -> {
            long ms = hasFocus ? FOCUS_IN_MS : FOCUS_OUT_MS;
            float scale = hasFocus ? size.scale : 1f;
            // Draw the focused cell last so its shadow falls over its neighbours, not under them.
            cell.setTranslationZ(hasFocus ? 1 : 0);
            v.animate().scaleX(scale).scaleY(scale).setDuration(ms).setInterpolator(ease).withLayer().start();
            veil.animate().alpha(hasFocus ? 0f : 1f).setDuration(ms).setInterpolator(ease).start();
            shadowView.animate().alpha(hasFocus ? 1f : 0f).setDuration(ms).setInterpolator(ease).start();
            sheen.animate().alpha(hasFocus ? 1f : 0f).setDuration(ms).setInterpolator(ease).start();
            label.animate().alpha(hasFocus ? 1f : 0f).setDuration(LABEL_MS).setInterpolator(ease).start();
            if (hasFocus) lightUp(app, v, scrollToRow(cell, size), size);
        });
        // A quick press-in before launching, so the remote's OK feels physical.
        lift.setOnClickListener(v -> v.animate().scaleX(PRESS_SCALE).scaleY(PRESS_SCALE).setDuration(PRESS_MS)
                .withEndAction(() -> v.animate().scaleX(size.scale).scaleY(size.scale).setDuration(RELEASE_MS)
                        .withEndAction(() -> {
                            if (app.intent == null) chooseForSlot(slot);
                            else launch(app, v);
                        }).start())
                .start());
        lift.setOnLongClickListener(v -> {
            if (app.intent == null) chooseForSlot(slot);
            else showMenu(app, slot);
            return true;
        });
        return cell;
    }

    /** One soft rounded-rectangle shadow, blurred once and shared by every tile of this size. */
    private Bitmap shadowBitmap(Size size) {
        int w = dp(size.w);
        int h = dp(size.h);
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
        Bitmap bmp = render(icon, ICON_SAMPLE, ICON_SAMPLE);
        if (bmp == null) return TILE_FALLBACK;
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < ICON_SAMPLE; y++) {
            for (int x = 0; x < ICON_SAMPLE; x++) {
                int p = bmp.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                n++;
            }
        }
        bmp.recycle();
        if (n == 0) return TILE_FALLBACK;
        float[] hsv = new float[3];
        Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (b / n), hsv);
        hsv[1] = Math.min(hsv[1], 0.55f);
        hsv[2] = Math.max(0.30f, Math.min(hsv[2], 0.38f));
        return Color.HSVToColor(hsv);
    }

    /** A drawable drawn small into a bitmap, for sampling its colours; null if it can't be copied. */
    private static Bitmap render(Drawable art, int w, int h) {
        if (art == null || art.getConstantState() == null) return null;
        Drawable d = art.getConstantState().newDrawable().mutate();
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, w, h);
        d.draw(new Canvas(bmp));
        return bmp;
    }

    private static int scaleBrightness(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= factor;
        return Color.HSVToColor(hsv);
    }

    /** Opens the app; from a tile ({@code from} not null), its window grows out of that tile, as on tvOS. */
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
        overlay.setBackgroundColor(SCRIM);
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
        overlay.animate().alpha(1f).setDuration(TODAY_FADE_MS).setInterpolator(ease).start();
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.setTranslationY(dp(12));
        card.animate().scaleX(1f).scaleY(1f).translationY(0).setDuration(TODAY_RISE_MS).setInterpolator(ease).start();
    }

    private void closeToday() {
        View overlay = today;
        if (overlay == null) return;
        today = null;
        overlay.setFocusable(false);
        overlay.animate().alpha(0f).setDuration(TODAY_CLOSE_MS)
                .withEndAction(() -> root.removeView(overlay)).start();
        pill.requestFocus();
    }

    // ---- options menu -----------------------------------------------------------------------

    /** Options for a tile: {@code slot} is its place in the dock, or -1 in the grid. */
    private void showMenu(App app, int slot) {
        if (app.pkg.equals(AANGAN_KEY)) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        Map<String, App> apps = Apps.load(this);
        List<String> hidden = Prefs.list(prefs, HIDDEN);
        List<String> dock = Dock.load(prefs, apps, hidden);

        if (app.pkg.equals(SETTINGS_KEY)) {
            if (hidden.isEmpty()) return;
            labels.add("Show " + hidden.size() + " hidden app" + (hidden.size() == 1 ? "" : "s"));
            actions.add(() -> Prefs.putList(prefs, HIDDEN, new ArrayList<String>()));
        } else if (slot >= 0) {
            labels.add("Change app");
            actions.add(() -> chooseForSlot(slot));
            if (slot > 0) {
                labels.add("Move left");
                actions.add(() -> {
                    Dock.swap(prefs, dock, slot, slot - 1);
                    focusNext = DOCK_TAG + (slot - 1);
                });
            }
            if (slot < Dock.SIZE - 1) {
                labels.add("Move right");
                actions.add(() -> {
                    Dock.swap(prefs, dock, slot, slot + 1);
                    focusNext = DOCK_TAG + (slot + 1);
                });
            }
            labels.add("Remove from the dock");
            actions.add(() -> {
                Dock.put(prefs, dock, slot, Dock.EMPTY);
                focusNext = DOCK_TAG + slot;
            });
        } else {
            labels.add("Put in the dock");
            actions.add(() -> chooseSlotFor(app, apps, dock));
            labels.add("Hide");
            actions.add(() -> {
                hidden.add(app.pkg);
                Prefs.putList(prefs, HIDDEN, hidden);
                focusNext = neighbour(app.pkg);
            });
        }
        if (!app.pkg.equals(SETTINGS_KEY)) {
            labels.add("App info");
            actions.add(() -> launch(appInfo(app), null));
        }

        Ui.dialog(this, app.label)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    actions.get(which).run();
                    refresh();
                })
                .show();
    }

    /** The grid tile after this one (or before it, at the end), to keep focus nearby when it goes. */
    private Object neighbour(String pkg) {
        for (int i = 0; i < appGrid.getChildCount(); i++) {
            Object tag = ((ViewGroup) appGrid.getChildAt(i)).getChildAt(0).getTag();
            if (!pkg.equals(tag)) continue;
            int next = i + 1 < appGrid.getChildCount() ? i + 1 : i - 1;
            return next >= 0 ? ((ViewGroup) appGrid.getChildAt(next)).getChildAt(0).getTag() : null;
        }
        return null;
    }

    /** Asks which app goes in this dock slot. */
    private void chooseForSlot(int slot) {
        Map<String, App> apps = Apps.load(this);
        List<String> dock = Dock.load(prefs, apps, Prefs.list(prefs, HIDDEN));
        Dock.choose(this, apps, dock, Prefs.list(prefs, HIDDEN), slot, pkg -> {
            focusNext = DOCK_TAG + slot;
            refresh();
        });
    }

    /** Asks which dock slot {@code app} should take. */
    private void chooseSlotFor(App app, Map<String, App> apps, List<String> dock) {
        String[] names = new String[Dock.SIZE];
        for (int i = 0; i < names.length; i++) {
            App there = apps.get(dock.get(i));
            names[i] = there != null ? "Replace " + there.label : "Empty place " + (i + 1);
        }
        Ui.dialog(this, "Put " + app.label + " in the dock")
                .setItems(names, (d, which) -> {
                    Dock.put(prefs, dock, which, app.pkg);
                    focusNext = DOCK_TAG + which;
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
