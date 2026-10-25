package dev.vaari.aangan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Random;

/**
 * Aangan's screensaver: a dim clock, date and the last known weather on black. It moves to a new
 * spot once a minute so nothing burns in, and otherwise does no work at all. No network.
 */
public class ClockDream extends DreamService {

    private final Random random = new Random();
    private LinearLayout block;
    private TextView weatherLine;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showWeather();
            drift();
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);

        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("H:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 72);
        time.setTypeface(Typeface.create(Typeface.DEFAULT, 200, false));
        time.setTextColor(0x99FFFFFF);
        time.setLetterSpacing(-0.02f);
        time.setFontFeatureSettings("tnum");
        time.setIncludeFontPadding(false);
        block.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        date.setTypeface(Typeface.create(Typeface.DEFAULT, 400, false));
        date.setTextColor(0x66FFFFFF);
        block.addView(date);

        weatherLine = new TextView(this);
        weatherLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        weatherLine.setTypeface(Typeface.create(Typeface.DEFAULT, 400, false));
        weatherLine.setTextColor(0x4DFFFFFF);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lineLp.topMargin = Math.round(8 * getResources().getDisplayMetrics().density);
        block.addView(weatherLine, lineLp);

        root.addView(block, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setContentView(root);
        showWeather();
        registerReceiver(tick, new IntentFilter(Intent.ACTION_TIME_TICK));
        root.post(this::drift);
    }

    @Override
    public void onDetachedFromWindow() {
        unregisterReceiver(tick);
        super.onDetachedFromWindow();
    }

    private void showWeather() {
        Weather w = Weather.load(getSharedPreferences("aangan", MODE_PRIVATE));
        String line = w.isStale(System.currentTimeMillis()) ? "" : w.summary();
        // On a festival day the screensaver says so too: "Happy Diwali  ·  24° Clear".
        Festival.load(this, getSharedPreferences("aangan", MODE_PRIVATE));
        Festival festival = Festival.next(Festival.today());
        if (festival != null && festival.isToday(Festival.today())) {
            String greeting = festival.headline(Festival.today());
            line = line.isEmpty() ? greeting : greeting + "  ·  " + line;
        }
        weatherLine.setText(line);
        weatherLine.setVisibility(line.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Moves the block somewhere else within the middle 70% of the screen. */
    private void drift() {
        View root = (View) block.getParent();
        float roomX = Math.max(0, (root.getWidth() * 0.7f - block.getWidth()) / 2f);
        float roomY = Math.max(0, (root.getHeight() * 0.7f - block.getHeight()) / 2f);
        block.setTranslationX((random.nextFloat() * 2 - 1) * roomX);
        block.setTranslationY((random.nextFloat() * 2 - 1) * roomY);
    }
}
