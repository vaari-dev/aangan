package dev.vaari.aangan;

import android.annotation.SuppressLint;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.vaari.aangan.Apps.App;

public class HomeActivity extends Activity {

    private static final String SETTINGS_KEY = "__settings__";
    private static final String AANGAN_KEY = "__aangan__";
    private static final String EMPTY_KEY = "__empty__";
    // Dock tiles are tagged by slot, not app, so focus stays on a place whatever app is in it.
    private static final String DOCK_TAG = "dock:";
    static final String HIDDEN = "hidden";
    // "tips2" from 1.3, so upgraders see the tips again.
    private static final String TIPS = "tips2";

    // Layout, in dp. Six 16:9 tiles with 20dp gaps span 868dp, centred on the 960dp canvas; so do
    private static final int COLUMNS = 6;
    private static final int GAP = 20;
    private static final int ROW_PITCH = 124;
    private static final int DOCK_PITCH = 166;
    private static final int TOP = 30;
    private static final int HEADER_H = 86;
    private static final int FIRST_ROW = 132;
    private static final int LOWEST_ROW = 380;
    private static final int RADIUS = 8;
    private static final int SHADOW_BLUR = 20;
    private static final int SHADOW_DROP = 10;
    private static final int BOTTOM_ROOM = 220;
    private static final int PILL_H = 40;
    private static final int TIP_MARGIN = 22;

    private static final float GLOW_DOCK = 0.24f;
    private static final float GLOW_GRID = 0.16f;
    private static final float GLOW_NEUTRAL = 0.10f;
    private static final int NEUTRAL = 0xFFB4C3E6;
    private static final int BRAND_W = 32;
    private static final int BRAND_H = 18;
    private static final int HUES = 24;
    private static final int ICON_SAMPLE = 16;

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
    private static final int SCRIM = 0xB3000000;

    static final int FESTIVE = Festive.Theme.PLAIN.accent;
    private static final int[] GLASS = {0x17FFFFFF, 0x0DFFFFFF};
    private static final int[] GLASS_FOCUSED = {0x33FFFFFF, 0x1FFFFFFF};
    private static final int GLASS_EDGE_FOCUSED = 0x59FFFFFF;
    private static final int TILE_FALLBACK = 0xFF2A2D34;
    private static final long FESTIVE_REPLAY_MS = 3 * 60 * 60_000L;
    private static final int TIP_VISITS = 8;
    private static final long WEATHER_CHECK_MS = 5 * 60_000L;

    private static final class Size {
        final int w;
        final int h;
        final int icon;
        final float label;
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
    private Festive festive;
    // Preview a festival: am start -n dev.vaari.aangan/.HomeActivity --es festival Holi
    private String preview;
    private long festivePlayedAt;
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
        weather = Weather.load(prefs);
        Festival.load(this, prefs);
        refresh();
        welcomeOrTip();
        showFestival();
        maybeRefreshWeather();
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(weatherCheck);
        super.onPause();
    }

    private void welcomeOrTip() {
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        preview = intent.getStringExtra("festival");
        if (today != null) closeToday();
        goToTop();
    }

    // Before Android 13. A home screen has nowhere to go back to, so it doesn't opt into predictive back.
    @Override
    @SuppressWarnings("deprecation")
    @SuppressLint("GestureBackNavigation")
    public void onBackPressed() {
        back();
    }

    private void back() {
        if (today != null) closeToday();
        else goToTop();
    }

    private int dp(float v) {
        return ui.dp(v);
    }

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

        root = new FrameLayout(this);
        ambient = new Ambient(this);
        festive = new Festive(this);
        root.addView(festive, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(ambient, new FrameLayout.LayoutParams(dp(Ambient.WIDTH), dp(Ambient.HEIGHT)));
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tip = ui.line(13, 400, Ui.SECONDARY, getString(R.string.home_tip));
        tip.setAlpha(0f);
        FrameLayout.LayoutParams tipLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        tipLp.bottomMargin = dp(TIP_MARGIN);
        root.addView(tip, tipLp);
        setContentView(root);
        paintBackdrop(glowColor());
    }

    private void paintBackdrop(int color) {
        glow = color;
        DisplayMetrics m = getResources().getDisplayMetrics();
        getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(),
                Backdrop.paint(m.widthPixels, m.heightPixels, m.density, color)));
    }

    private View buildHeader() {
        FrameLayout frame = new FrameLayout(this);
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
        timeRow.addView(meridiem, ui.leftOf(5));
        clock.addView(timeRow);

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
        credit = ui.line(9, 400, Ui.TERTIARY, getString(R.string.weather_credit));
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
        GradientDrawable glass = glass(GLASS, Ui.HAIRLINE);
        GradientDrawable glassFocused = glass(GLASS_FOCUSED, GLASS_EDGE_FOCUSED);
        row.setBackground(glass);

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
        row.addView(tempView, ui.leftOf(8));

        LinearLayout lines = ui.column();
        conditionView = ui.line(12, 500, 0xD9F5F5F7);
        feelsView = ui.line(11.5f, 400, Ui.SECONDARY);
        lines.addView(conditionView);
        lines.addView(feelsView, ui.wrap(2));
        row.addView(lines, ui.leftOf(10));

        LinearLayout aqi = ui.row();
        aqi.setGravity(Gravity.CENTER_VERTICAL);
        View divider = ui.divider();
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(ui.hairline(), dp(20));
        dividerLp.leftMargin = dp(14);
        dividerLp.rightMargin = dp(14);
        aqi.addView(divider, dividerLp);
        aqiDot = new View(this);
        aqi.addView(aqiDot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        aqiView = ui.line(15, 500, Ui.LABEL);
        aqiView.setFontFeatureSettings("tnum");
        aqi.addView(aqiView, ui.leftOf(6));
        aqiCategoryView = ui.line(12, 500, Ui.LABEL);
        aqi.addView(aqiCategoryView, ui.leftOf(6));
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

    private GridLayout newGrid(int columns) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
        grid.setUseDefaultMargins(false);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        return grid;
    }

    private void showWeather() {
        Festival festival = preview != null ? Festival.preview(preview) : Festival.next(Festival.today());
        festivalView.setText(festival == null ? "" : "  ·  " + festival.headline(this, Festival.today()));
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
            conditionView.setText(weather.condition(getResources()));
            String rain = weather.rainSoon(getResources());
            feelsView.setText(rain != null ? rain : getString(R.string.feels_like, Weather.degrees(weather.feels)));
            feelsView.setTextColor(rain != null ? Ui.RAIN : Ui.SECONDARY);
        }

        aqiGroup.setVisibility(weather.hasAir() ? View.VISIBLE : View.GONE);
        if (weather.hasAir()) {
            aqiDot.setBackground(Ui.dot(weather.aqiColor()));
            aqiView.setText(getString(R.string.aqi_value, weather.aqi));
            aqiCategoryView.setText(weather.aqiCategory(getResources()));
            aqiCategoryView.setTextColor(weather.aqiColor());
        }
        pill.setAlpha(weather.isStale(System.currentTimeMillis()) ? 0.5f : 1f);
    }

    private int glowColor() {
        Festival festival = celebrating();
        return festival != null ? festival.theme.glow : weather.glowColor();
    }

    private Festival celebrating() {
        return preview != null ? Festival.preview(preview) : Festival.celebrating(Festival.today());
    }

    private void showFestival() {
        Festival festival = celebrating();
        Festive.Theme theme = festival == null || festival.theme == Festive.Theme.PLAIN ? null : festival.theme;
        long now = System.currentTimeMillis();
        if (theme == festive.theme() && now - festivePlayedAt <= FESTIVE_REPLAY_MS) return;
        festive.show(theme);
        festivePlayedAt = now;
        showWeather();
    }

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
        // onResume may swap in a fresh object meanwhile (say, for a new city).
        Weather target = weather;
        network.execute(() -> {
            String forecastBody = wantWeather ? orNull(target::downloadWeather) : null;
            String airBody = wantAir ? orNull(target::downloadAir) : null;
            handler.post(() -> {
                fetching = false;
                if (target != weather || !resumed) {
                    maybeRefreshWeather();
                    return;
                }
                // Read on the main thread, which is also where the forecast is drawn from.
                boolean success = (!wantWeather || forecastBody != null) && (!wantAir || airBody != null);
                try {
                    if (forecastBody != null) weather.readWeather(forecastBody, now);
                } catch (Exception e) {
                    success = false;
                }
                try {
                    if (airBody != null) weather.readAir(airBody, now);
                } catch (Exception e) {
                    success = false;
                }
                if (success) {
                    failures = 0;
                    retryAt = 0;
                } else {
                    retryAt = System.currentTimeMillis() + Math.min(30, 1L << Math.min(failures, 5)) * 60_000L;
                    failures++;
                }
                weather.save(prefs);
                showWeather();
            });
        });
    }

    private static String orNull(Callable<String> download) {
        try {
            return download.call();
        } catch (Exception e) {
            return null;
        }
    }

    private App settingsApp() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ResolveInfo ri = pm.resolveActivity(intent, 0);
        App app = new App();
        app.pkg = SETTINGS_KEY;
        app.label = getString(R.string.settings);
        app.intent = intent;
        app.icon = ri != null ? ri.loadIcon(pm) : null;
        return app;
    }

    private App aanganApp(boolean setUp) {
        App app = new App();
        app.pkg = AANGAN_KEY;
        app.label = getString(setUp ? R.string.app_name : R.string.set_up_aangan);
        app.intent = new Intent(this, SetupActivity.class);
        app.banner = getDrawable(R.drawable.banner);
        return app;
    }

    private boolean isSetUp() {
        if (HomeButton.isOn(this)) return true;
        ResolveInfo home = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return home != null && getPackageName().equals(home.activityInfo.packageName);
    }

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
        rest.add(setUp ? rest.size() : 0, aanganApp(setUp));
        rest.add(settingsApp());
        for (App app : rest) appGrid.addView(makeTile(app, GRID, -1), cellParams(GRID));

        View target = focusedKey != null ? scroll.findViewWithTag(focusedKey) : null;
        if (target == null) target = firstTile();
        if (target != null) target.requestFocus();
    }

    private App emptySlot(int slot) {
        App app = new App();
        app.pkg = EMPTY_KEY + slot;
        app.label = getString(R.string.choose_an_app);
        return app;
    }

    private GridLayout.LayoutParams cellParams(Size size) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = dp(size.w + GAP);
        lp.height = dp(size.pitch);
        return lp;
    }

    private static View lift(GridLayout grid, int i) {
        return ((ViewGroup) grid.getChildAt(i)).getChildAt(0);
    }

    private View firstTile() {
        return dockGrid.getChildCount() == 0 ? null : lift(dockGrid, 0);
    }

    private void goToTop() {
        scroll.smoothScrollTo(0, 0);
        View first = firstTile();
        if (first != null) first.requestFocus();
    }

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

    private void lightUp(App app, View lift, int scrollTarget, Size size) {
        Rect r = contentRect(lift);
        int color = brandColor(app);
        float strength = color == NEUTRAL ? GLOW_NEUTRAL : size.glow;
        ambient.glow(r.exactCenterX(), r.bottom - scrollTarget + dp(8), color, strength);
    }

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

    private View makeTile(App app, Size size, int slot) {
        int w = dp(size.w);
        int h = dp(size.h);
        int blur = dp(SHADOW_BLUR);

        LinearLayout cell = ui.column();
        cell.setClipChildren(false);
        cell.setClipToPadding(false);

        FrameLayout lift = new FrameLayout(this);
        lift.setTag(slot >= 0 ? DOCK_TAG + slot : app.pkg);
        lift.setContentDescription(app.intent == null ? getString(R.string.dock_empty_place_hint, slot + 1) : app.label);
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

    private void launch(App app, View from) {
        try {
            Bundle zoom = from == null ? null : ActivityOptions
                    .makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
            startActivity(app.intent, zoom);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, getString(R.string.cant_open, app.label), Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("GestureBackNavigation") // KEYCODE_BACK for older TVs; from Android 13, the callback in onCreate.
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

    private void showMenu(App app, int slot) {
        if (app.pkg.equals(AANGAN_KEY)) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        Map<String, App> apps = Apps.load(this);
        List<String> hidden = Prefs.list(prefs, HIDDEN);
        List<String> dock = Dock.load(prefs, apps, hidden);

        if (app.pkg.equals(SETTINGS_KEY)) {
            if (hidden.isEmpty()) return;
            labels.add(getResources().getQuantityString(R.plurals.show_hidden_apps, hidden.size(), hidden.size()));
            actions.add(() -> Prefs.putList(prefs, HIDDEN, new ArrayList<String>()));
        } else if (slot >= 0) {
            labels.add(getString(R.string.change_app));
            actions.add(() -> chooseForSlot(slot));
            if (slot > 0) {
                labels.add(getString(R.string.move_left));
                actions.add(() -> move(dock, slot, slot - 1));
            }
            if (slot < Dock.SIZE - 1) {
                labels.add(getString(R.string.move_right));
                actions.add(() -> move(dock, slot, slot + 1));
            }
            labels.add(getString(R.string.remove_from_dock));
            actions.add(() -> {
                Dock.put(prefs, dock, slot, Dock.EMPTY);
                focusNext = DOCK_TAG + slot;
            });
        } else {
            labels.add(getString(R.string.put_in_dock));
            actions.add(() -> chooseSlotFor(app, apps, dock));
            labels.add(getString(R.string.hide));
            actions.add(() -> {
                hidden.add(app.pkg);
                Prefs.putList(prefs, HIDDEN, hidden);
                focusNext = neighbour(app.pkg);
            });
        }
        if (!app.pkg.equals(SETTINGS_KEY)) {
            labels.add(getString(R.string.app_info));
            actions.add(() -> launch(appInfo(app), null));
        }

        Ui.dialog(this, app.label)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    actions.get(which).run();
                    refresh();
                })
                .show();
    }

    private void move(List<String> dock, int from, int to) {
        Dock.swap(prefs, dock, from, to);
        focusNext = DOCK_TAG + to;
    }

    private Object neighbour(String pkg) {
        for (int i = 0; i < appGrid.getChildCount(); i++) {
            if (!pkg.equals(lift(appGrid, i).getTag())) continue;
            int next = i + 1 < appGrid.getChildCount() ? i + 1 : i - 1;
            return next >= 0 ? lift(appGrid, next).getTag() : null;
        }
        return null;
    }

    private void chooseForSlot(int slot) {
        Map<String, App> apps = Apps.load(this);
        List<String> hidden = Prefs.list(prefs, HIDDEN);
        Dock.choose(this, apps, Dock.load(prefs, apps, hidden), hidden, slot, pkg -> {
            focusNext = DOCK_TAG + slot;
            refresh();
        });
    }

    private void chooseSlotFor(App app, Map<String, App> apps, List<String> dock) {
        String[] names = new String[Dock.SIZE];
        for (int i = 0; i < names.length; i++) {
            App there = apps.get(dock.get(i));
            names[i] = there != null ? getString(R.string.dock_replace, there.label) : getString(R.string.dock_empty_place, i + 1);
        }
        Ui.dialog(this, getString(R.string.dock_put_title, app.label))
                .setItems(names, (d, which) -> {
                    Dock.put(prefs, dock, which, app.pkg);
                    focusNext = DOCK_TAG + which;
                    refresh();
                })
                .show();
    }

    private App appInfo(App app) {
        App info = new App();
        info.label = getString(R.string.app_info);
        info.intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return info;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View focused = getCurrentFocus();
            if (focused != null) return focused.performLongClick();
        }
        return super.onKeyDown(keyCode, event);
    }
}
