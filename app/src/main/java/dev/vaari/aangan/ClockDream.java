package dev.vaari.aangan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.Random;

/**
 * Aangan's screensaver: a dim clock, date and the last known weather on black. It moves to a new
 * spot once a minute so nothing burns in, and otherwise does no work at all. No network.
 */
public class ClockDream extends DreamService {

    // Dim greys on black, so a screen left on for hours has nothing bright to wear in.
    private static final int TIME = 0x99FFFFFF;
    private static final int DATE = 0x66FFFFFF;
    private static final int LINE = 0x4DFFFFFF;
    /** The block wanders within this share of the screen's width and height. */
    private static final float ROOM = 0.7f;

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
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.canvas(base));
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        Ui ui = new Ui(this);
        block = ui.column();
        block.setGravity(Gravity.CENTER_HORIZONTAL);
        block.addView(ui.clock(72, 200, TIME));
        block.addView(ui.date(16, 400, DATE));
        weatherLine = ui.text(14, 400, LINE, null);
        block.addView(weatherLine, ui.wrap(8));

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
        SharedPreferences prefs = Prefs.of(this);
        Weather w = Weather.load(prefs);
        String line = w.isStale(System.currentTimeMillis()) ? "" : w.summary(getResources());
        // On a festival day the screensaver says so too: "Happy Diwali  ·  24° Clear".
        Festival.load(this, prefs);
        LocalDate today = Festival.today();
        Festival festival = Festival.next(today);
        if (festival != null && festival.isToday(today)) {
            String greeting = festival.headline(this, today);
            line = line.isEmpty() ? greeting : greeting + "  ·  " + line;
        }
        weatherLine.setText(line);
        weatherLine.setVisibility(line.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Moves the block somewhere else within the middle of the screen. */
    private void drift() {
        View root = (View) block.getParent();
        float roomX = Math.max(0, (root.getWidth() * ROOM - block.getWidth()) / 2f);
        float roomY = Math.max(0, (root.getHeight() * ROOM - block.getHeight()) / 2f);
        block.setTranslationX((random.nextFloat() * 2 - 1) * roomX);
        block.setTranslationY((random.nextFloat() * 2 - 1) * roomY);
    }
}
