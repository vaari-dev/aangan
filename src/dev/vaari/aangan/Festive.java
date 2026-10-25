package dev.vaari.aangan;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * The festival look: a hand-drawn scene behind the home screen on festival days. Diyas for
 * Diwali, gulal for Holi, kites for Sankranti, a moon through the chalni for Karwa Chauth and
 * so on. Each scene plays a short opening (flames catching, colour bursting, lanterns swinging)
 * and then keeps gently moving: flames flicker, snow falls, lanterns sway. The loop runs at
 * 20 fps, only on festival days and only while the home screen has focus; an app, the
 * screensaver or standby stops it (measured: about 3-7% of the TV's CPU while it runs, 0 otherwise).
 *
 * Scenes keep to the margins (the strip above the clock, the gap between the clock and the
 * weather, and the bottom edge) so the tiles always read cleanly.
 */
final class Festive extends View {

    enum Theme {
        DIWALI(0xFF7A5A2E, 0xFFFFC56B),
        HOLI(0xFF6A2A5E, 0xFFFF7AC0),
        BONFIRE(0xFF7A3A1A, 0xFFFFA04D),
        KITES(0xFF2E5A7A, 0xFF8AD8FF),
        BASANT(0xFF6E6420, 0xFFFFE066),
        KARWA(0xFF4A3466, 0xFFF2C46B),
        MOON(0xFF3A3F66, 0xFFF2E6C2),
        CRESCENT(0xFF26305E, 0xFFBFCBFF),
        PEACOCK(0xFF1D4A5E, 0xFF63D8C8),
        RAKHI(0xFF6A2A3A, 0xFFFF94A8),
        TORAN(0xFF7A4A1E, 0xFFFFB347),
        SUNSET(0xFF7A3A22, 0xFFFFA86B),
        DEEPMALA(0xFF6A5A36, 0xFFFFE2A6),
        CHRISTMAS(0xFF2A4A36, 0xFFFF7B72),
        FIREWORKS(0xFF3E2A66, 0xFFDDBDFF),
        EID(0xFF1E4A40, 0xFF86E3BF),
        TRICOLOUR(0xFF5A4A36, 0xFFFFB066),
        BIRTHDAY(0xFF5A2E66, 0xFFFFA3CF),
        ANNIVERSARY(0xFF6A2A40, 0xFFFFB3C1),
        /**
         * No scene, just the greeting. It borrows Diwali's warm glow and marigold accent, which are
         * also the home screen's festive colour: the general "something to celebrate" look. (Written
         * out rather than referring to DIWALI, since an enum constant can't read another's fields
         * in its constructor call; tools/site.py also reads these values from this file.)
         */
        PLAIN(0xFF7A5A2E, 0xFFFFC56B);

        /** The background's glow on the day. */
        final int glow;
        /** The countdown and greeting colour. */
        final int accent;

        Theme(int glow, int accent) {
            this.glow = glow;
            this.accent = accent;
        }
    }

    private static final float INTRO_S = 2.6f;
    private static final long FRAME_MS = 50;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    /** For gradient fills only; its own alpha stays opaque, so the gradient's colours alone set the look. */
    private final Paint gradient = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Paint glowPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final RectF glowRect = new RectF();
    private final Bitmap falloff = falloff(128);
    private final float u;
    private Theme theme;
    private float progress = 1f;
    /** Seconds of animation so far; it only advances while the loop is running. */
    private float time;
    private float introStart = -100f;
    private long lastFrame;
    private boolean running;
    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            time += Math.min(0.1f, (now - lastFrame) / 1000f);
            lastFrame = now;
            invalidate();
            if (running) postDelayed(this, FRAME_MS);
        }
    };

    Festive(Context context) {
        super(context);
        u = context.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        setWillNotDraw(false);
    }

    Theme theme() {
        return theme;
    }

    /** Shows {@code theme} (null for none), playing its opening when {@code animate} is set. */
    void show(Theme theme, boolean animate) {
        this.theme = theme == Theme.PLAIN ? null : theme;
        introStart = animate ? time : time - 100f;
        loop(hasWindowFocus());
        invalidate();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        loop(hasFocus);
    }

    @Override
    protected void onDetachedFromWindow() {
        loop(false);
        super.onDetachedFromWindow();
    }

    private void loop(boolean on) {
        on &= theme != null;
        if (on == running) return;
        running = on;
        removeCallbacks(frame);
        if (on) {
            lastFrame = SystemClock.uptimeMillis();
            post(frame);
        }
    }

    // ---- timing helpers ---------------------------------------------------------------------

    private static float clamp(float x) {
        return Math.max(0f, Math.min(1f, x));
    }

    /** Progress through one element's part of the opening: starts at {@code at}, lasts {@code len}. */
    private float part(float at, float len) {
        return clamp((progress - at) / len);
    }

    private static float easeOut(float x) {
        float i = 1f - x;
        return 1f - i * i * i;
    }

    /** Ease-out with a small overshoot, for things that drop or pop into place. */
    private static float easeBack(float x) {
        float s = 1.6f;
        float t = x - 1f;
        return t * t * ((s + 1) * t + s) + 1f;
    }

    /** A gentle, endless sway: a sine of the animation clock. */
    private float wave(float speed, float seed) {
        return (float) Math.sin(time * speed + seed);
    }

    /** An irregular flame flicker, from two out-of-step waves. */
    private float flicker(float seed, float amount) {
        return amount * (0.6f * wave(9f, seed * 2.3f) + 0.4f * wave(23.7f, seed * 5.1f));
    }

    /**
     * A soft pool of light. Drawn from one cached falloff image tinted to the colour, rather than a
     * new gradient each frame, which keeps the looping scenes cheap.
     */
    private void glow(Canvas c, float x, float y, float r, int color, float a) {
        if (a <= 0 || r <= 0) return;
        glowPaint.setColorFilter(new PorterDuffColorFilter(color | 0xFF000000, PorterDuff.Mode.SRC_IN));
        glowPaint.setAlpha(Math.round(clamp(a) * 255));
        glowRect.set(x - r, y - r, x + r, y + r);
        c.drawBitmap(falloff, null, glowRect, glowPaint);
    }

    /** A radial falloff matching a linear gradient from full to none, with a little dither noise. */
    private static Bitmap falloff(int size) {
        int[] px = new int[size * size];
        Random random = new Random(2);
        float half = size / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float d = (float) Math.hypot(x + 0.5f - half, y + 0.5f - half) / half;
                float a = Math.max(0f, 1f - d) * 255f + random.nextFloat() - 0.5f;
                px[y * size + x] = (Math.max(0, Math.min(255, Math.round(a))) << 24) | 0xFFFFFF;
            }
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
    }

    // ---- drawing ----------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        if (theme == null) return;
        progress = clamp((time - introStart) / INTRO_S);
        float w = getWidth();
        float h = getHeight();
        switch (theme) {
            case DIWALI:
                rangoli(c, 34 * u, h + 26 * u, 0f);
                rangoli(c, w - 34 * u, h + 26 * u, 0.12f);
                diyas(c, w, h, 7, 0xFFFFB347);
                fireworks(c, new float[][]{{0.46f, 0.11f, 34}, {0.56f, 0.06f, 24}, {0.62f, 0.16f, 20}},
                        new int[]{0xFFFFD27A, 0xFFFF7AB6, 0xFF8FE3FF}, 0.35f);
                break;
            case HOLI:
                gulal(c, w, h);
                break;
            case BONFIRE:
                bonfire(c, w, h);
                break;
            case KITES:
                kites(c, w, h);
                break;
            case BASANT:
                gradient.setShader(new LinearGradient(0, h - 150 * u, 0, h, 0x00FFD60A,
                        Colors.alpha(0xFFFFD60A, 0.24f * part(0, 0.5f)), Shader.TileMode.CLAMP));
                c.drawRect(0, h - 150 * u, w, h, gradient);
                kites(c, w, h);
                break;
            case KARWA:
                stars(c, w, h, 60);
                moon(c, w * 0.52f, h * 0.14f, 24 * u, true);
                break;
            case MOON:
                stars(c, w, h, 50);
                moon(c, w * 0.52f, h * 0.13f, 24 * u, false);
                break;
            case CRESCENT:
                stars(c, w, h, 70);
                crescent(c, w * 0.52f, h * 0.12f, 22 * u, 0xFFF4EEDA, false);
                break;
            case PEACOCK:
                stars(c, w, h, 40);
                feather(c, w, h);
                flute(c, w, h);
                break;
            case RAKHI:
                rakhi(c, w, h);
                break;
            case TORAN:
                toran(c, w);
                break;
            case SUNSET:
                sunset(c, w, h);
                break;
            case DEEPMALA:
                lights(c, w, new int[]{0xFFFFF1D0}, 0.7f);
                diyas(c, w, h, 9, 0xFFFFE2A6);
                break;
            case CHRISTMAS:
                snow(c, w, h);
                lights(c, w, new int[]{0xFFFF453A, 0xFF30D158, 0xFFFFD60A, 0xFF0A84FF}, 0.55f);
                break;
            case FIREWORKS:
                fireworks(c, new float[][]{{0.45f, 0.12f, 46}, {0.56f, 0.06f, 30}, {0.63f, 0.17f, 26},
                                {0.84f, 0.88f, 54}, {0.16f, 0.9f, 48}, {0.37f, 0.05f, 22}},
                        new int[]{0xFFFFD27A, 0xFFFF6FB0, 0xFF8FE3FF, 0xFFD9B8FF, 0xFFFFFFFF, 0xFF9CF59C}, 0.45f);
                break;
            case EID:
                stars(c, w, h, 40);
                crescent(c, w * 0.49f, h * 0.10f, 14 * u, 0xFFFFE7A8, true);
                lantern(c, w * 0.40f, 40 * u, 0.85f, 0f);
                lantern(c, w * 0.57f, 64 * u, 1.15f, 1.3f);
                lantern(c, w * 0.645f, 34 * u, 0.8f, 2.4f);
                break;
            case TRICOLOUR:
                tricolour(c, w, h);
                break;
            case BIRTHDAY:
                confetti(c, w, h);
                balloons(c, w, h);
                break;
            case ANNIVERSARY:
                petals(c, w, h);
                hearts(c, w, h);
                rings(c, w * 0.52f, h * 0.12f);
                break;
            default:
                break;
        }
    }

    // ---- Diwali -----------------------------------------------------------------------------

    /** Clay diyas along the bottom edge; their flames catch one after another. */
    private void diyas(Canvas c, float w, float h, int n, int light) {
        float y = h - 30 * u;
        float k = 1.45f;
        for (int i = 0; i < n; i++) {
            float x = w * (i + 0.5f) / n;
            float t = part(0.05f + i * 0.07f, 0.35f);
            glow(c, x, y - 16 * u, 90 * u, light, 0.42f * t);

            path.reset();
            path.moveTo(x - 15 * k * u, y - 4 * k * u);
            path.quadTo(x, y + 13 * k * u, x + 15 * k * u, y - 4 * k * u);
            path.quadTo(x, y - 1 * k * u, x - 15 * k * u, y - 4 * k * u);
            path.close();
            gradient.setShader(new LinearGradient(0, y - 4 * k * u, 0, y + 9 * k * u, 0xFFD06A2C, 0xFF5E240E, Shader.TileMode.CLAMP));
            c.drawPath(path, gradient);
            // Painted dots around the bowl, and the lit rim.
            paint.setColor(0xCCFFD27A);
            for (int d = -2; d <= 2; d++) c.drawCircle(x + d * 5 * u, y + 2.2f * u + Math.abs(d) * -1.2f * u, 1.1f * u, paint);
            stroke.setStrokeWidth(1.4f * u);
            stroke.setColor(Colors.alpha(0xFFFFC27A, 0.5f + 0.5f * t));
            c.drawLine(x - 13 * k * u, y - 3.6f * k * u, x + 13 * k * u, y - 3.6f * k * u, stroke);

            float fh = 22 * u * easeOut(t) * (1 + flicker(i, 0.18f));
            if (fh <= 0) continue;
            float base = y - 6 * k * u;
            glow(c, x, base - fh * 0.4f, fh * 1.1f, 0xFFFFE6A0, 0.55f * t);
            path.reset();
            path.moveTo(x, base - fh);
            path.cubicTo(x + 7 * u, base - fh * 0.45f, x + 5.5f * u, base, x, base + u);
            path.cubicTo(x - 5.5f * u, base, x - 7 * u, base - fh * 0.45f, x, base - fh);
            path.close();
            gradient.setShader(new RadialGradient(x, base - fh * 0.22f, fh * 0.85f,
                    new int[]{0xFFFFFFF4, 0xFFFFDB6E, 0xFFFF8A1F}, new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
            c.drawPath(path, gradient);
        }
    }

    /** A quarter of a rangoli mandala peeking in from a corner: rings of petals and dots. */
    private void rangoli(Canvas c, float cx, float cy, float delay) {
        float t = easeOut(part(delay, 0.7f));
        if (t <= 0) return;
        float r = 170 * u;
        int[] colors = {0xFFFF9F1C, 0xFFFF3D7F, 0xFF2EC4B6, 0xFFFFD23F, 0xFF9B5DE5};
        c.save();
        c.translate(cx, cy);
        c.rotate(-25 * (1 - t) + time * 1.5f);
        c.scale(0.85f + 0.15f * t, 0.85f + 0.15f * t);
        RectF oval = new RectF();
        for (int k = 0; k < 5; k++) {
            float ring = r * (0.2f + 0.19f * k);
            int petals = 8 + 4 * k;
            float len = r * 0.15f;
            float wid = r * (0.055f - 0.004f * k);
            paint.setColor(Colors.alpha(colors[k], 0.42f * t));
            for (int i = 0; i < petals; i++) {
                c.save();
                c.rotate(360f * i / petals + (k % 2) * 180f / petals);
                oval.set(ring - len / 2, -wid / 2, ring + len / 2, wid / 2);
                c.drawOval(oval, paint);
                c.restore();
            }
            paint.setColor(Colors.alpha(0xFFFFFFFF, 0.30f * t));
            for (int i = 0; i < petals; i++) {
                double a = 2 * Math.PI * (i + 0.5) / petals + (k % 2) * Math.PI / petals;
                c.drawCircle((float) (Math.cos(a) * ring), (float) (Math.sin(a) * ring), 1.6f * u, paint);
            }
        }
        paint.setColor(Colors.alpha(0xFFFF9F1C, 0.5f * t));
        c.drawCircle(0, 0, r * 0.1f, paint);
        c.restore();
    }

    /** Bursts of light: rays of dots that fly out from each centre. */
    private void fireworks(Canvas c, float[][] bursts, int[] colors, float finalAlpha) {
        Random random = new Random(5);
        for (int b = 0; b < bursts.length; b++) {
            float cx = getWidth() * bursts[b][0];
            float cy = getHeight() * bursts[b][1];
            float r = bursts[b][2] * u;
            // Each burst repeats every few seconds: it flies out, glows, then fades away.
            float age = time - introStart - 0.2f - b * 0.45f;
            if (age < 0) continue;
            float phase = (age % 3.6f) / 3.6f;
            float t = clamp(phase * 3f);
            float spread = easeOut(t);
            float a = (finalAlpha + (1f - finalAlpha) * (1f - t)) * (1f - clamp((phase - 0.6f) / 0.4f));
            if (a <= 0) continue;
            int color = colors[b % colors.length];
            glow(c, cx, cy, r * 0.9f, color, 0.18f * spread);
            int rays = 26;
            for (int j = 0; j < rays; j++) {
                double angle = 2 * Math.PI * j / rays + random.nextFloat() * 0.12;
                float len = r * spread * (0.85f + 0.15f * random.nextFloat());
                for (int d = 0; d < 4; d++) {
                    float f = 0.4f + 0.2f * d;
                    paint.setColor(Colors.alpha(color, a * (0.3f + 0.23f * d)));
                    c.drawCircle(cx + (float) Math.cos(angle) * len * f, cy + (float) Math.sin(angle) * len * f,
                            (0.6f + 0.3f * d) * u, paint);
                }
            }
        }
    }

    // ---- Holi -------------------------------------------------------------------------------

    /** Clouds of gulal in the corners and along the edges, thrown outward with a spray of grains. */
    private void gulal(Canvas c, float w, float h) {
        float[][] clouds = {
                {0.04f, 0.96f, 230}, {0.96f, 0.95f, 250}, {0.50f, 1.06f, 190},
                {0.99f, 0.40f, 150}, {0.01f, 0.42f, 150}, {0.52f, 0.0f, 130},
        };
        int[] colors = {0xFFFF2D7A, 0xFF2ECC71, 0xFFFFD60A, 0xFF2D9CFF, 0xFFA64DFF, 0xFFFF7A00};
        for (int k = 0; k < clouds.length; k++) {
            float t = easeOut(part(k * 0.07f, 0.5f));
            float breathe = 1 + 0.06f * wave(0.6f, k);
            glow(c, w * clouds[k][0], h * clouds[k][1], clouds[k][2] * u * (0.35f + 0.65f * t) * breathe, colors[k], 0.62f * t);
            glow(c, w * clouds[k][0], h * clouds[k][1], clouds[k][2] * u * 0.55f * (0.35f + 0.65f * t) * breathe, colors[k], 0.45f * t);
        }
        Random random = new Random(3);
        for (int i = 0; i < 700; i++) {
            int k = random.nextInt(clouds.length);
            double angle = random.nextFloat() * 2 * Math.PI + time * 0.05f * (k % 2 == 0 ? 1 : -1);
            float dist = clouds[k][2] * u * (0.1f + 0.9f * (float) Math.pow(random.nextFloat(), 1.4));
            float size = (0.6f + 1.5f * random.nextFloat()) * u;
            float a = 0.25f + 0.55f * random.nextFloat();
            float t = easeOut(part(k * 0.07f + 0.05f, 0.6f));
            if (t <= 0) continue;
            paint.setColor(Colors.alpha(colors[k], a * t));
            c.drawCircle(w * clouds[k][0] + (float) Math.cos(angle) * dist * t,
                    h * clouds[k][1] + (float) Math.sin(angle) * dist * t, size, paint);
        }
    }

    // ---- Lohri, Holika Dahan ----------------------------------------------------------------

    private void bonfire(Canvas c, float w, float h) {
        float t = easeOut(part(0, 0.6f));
        float x = w / 2;
        glow(c, x, h + 40 * u, 400 * u, 0xFFFF6A00, 0.45f * t);

        float[][] tongues = {{0, 120, 40}, {-34, 86, 30}, {34, 92, 30}, {-62, 60, 24}, {62, 66, 24}, {-14, 100, 26}, {18, 78, 26}};
        for (int i = 0; i < tongues.length; i++) {
            float ft = easeOut(part(0.05f + i * 0.04f, 0.5f));
            float fx = x + tongues[i][0] * u;
            float fh = tongues[i][1] * u * ft * (1 + flicker(i, 0.15f));
            float fw = tongues[i][2] * u / 2;
            float base = h - 14 * u;
            if (fh <= 0) continue;
            path.reset();
            path.moveTo(fx, base - fh);
            path.cubicTo(fx + fw * 0.9f, base - fh * 0.5f, fx + fw, base, fx, base);
            path.cubicTo(fx - fw, base, fx - fw * 0.9f, base - fh * 0.5f, fx, base - fh);
            path.close();
            gradient.setShader(new LinearGradient(0, base, 0, base - fh, 0xCCFF5A00, 0x00FFD36B, Shader.TileMode.CLAMP));
            c.drawPath(path, gradient);
        }
        // Two logs, crossed.
        paint.setColor(0xFF2E1C10);
        for (int tilt : new int[]{-10, 10}) {
            c.save();
            c.rotate(tilt, x, h - 8 * u);
            c.drawRoundRect(x - 70 * u, h - 14 * u, x + 70 * u, h - 2 * u, 6 * u, 6 * u, paint);
            c.restore();
        }

        Random random = new Random(9);
        for (int i = 0; i < 70; i++) {
            float sx = x + (float) random.nextGaussian() * 110 * u;
            float sy = h - 60 * u - random.nextFloat() * 280 * u;
            float st = easeOut(part(0.1f + random.nextFloat() * 0.4f, 0.5f));
            if (st <= 0) continue;
            // Sparks keep rising from the fire and fade as they climb.
            float climb = ((h - 30 * u - sy) + (time - introStart) * 38 * u * (0.6f + (i % 5) * 0.15f)) % (300 * u);
            float y = h - 30 * u - climb;
            st *= 1 - climb / (300 * u);
            paint.setColor(Colors.alpha(random.nextBoolean() ? 0xFFFFC46B : 0xFFFF8A3D, (0.35f + 0.6f * random.nextFloat()) * st));
            c.drawCircle(sx, y, (0.8f + 1.4f * random.nextFloat()) * u, paint);
        }
    }

    // ---- Makar Sankranti, Basant Panchami ---------------------------------------------------

    /** Patangs between the clock and the weather, their strings trailing to rooftops off-screen. */
    private void kites(Canvas c, float w, float h) {
        float[][] kites = {
                {0.47f, 0.10f, 30, -12}, {0.58f, 0.17f, 22, 14}, {0.71f, 0.025f, 13, -22},
                {0.64f, 0.05f, 15, 8}, {0.53f, 0.03f, 11, -5},
        };
        int[][] colors = {
                {0xFFFF3B30, 0xFFFFD60A}, {0xFF0A84FF, 0xFFF5F5F7}, {0xFF34C759, 0xFFFF9F0A},
                {0xFFBF5AF2, 0xFFFF375F}, {0xFFFFD60A, 0xFFFF2D55},
        };
        for (int i = 0; i < kites.length; i++) {
            float t = easeOut(part(i * 0.08f, 0.7f));
            if (t <= 0) continue;
            float s = kites[i][2] * u;
            float kx = w * kites[i][0];
            float ky = h * kites[i][1] + 60 * u * (1 - t) + 3 * u * wave(0.8f, i * 2f);
            float sway = kites[i][3] + 12 * (float) Math.sin(progress * 9 + i) * (1 - progress) + 5 * wave(1.1f, i);

            // The string sags away towards the rooftops and fades out before it reaches the tiles.
            stroke.setStrokeWidth(0.7f * u);
            stroke.setAlpha(255);
            stroke.setShader(new LinearGradient(kx, ky, kx - 90 * u, ky + 120 * u,
                    Colors.alpha(0xFFFFFFFF, 0.4f * t), 0x00FFFFFF, Shader.TileMode.CLAMP));
            path.reset();
            path.moveTo(kx, ky + 0.2f * s);
            path.quadTo(kx - 30 * u, ky + 90 * u, kx - 110 * u, ky + 140 * u);
            c.drawPath(path, stroke);
            stroke.setShader(null);

            c.save();
            c.translate(kx, ky);
            c.rotate(sway);
            path.reset();
            path.moveTo(0, -s);
            path.lineTo(0.85f * s, 0);
            path.lineTo(0, s);
            path.lineTo(-0.85f * s, 0);
            path.close();
            paint.setColor(Colors.alpha(colors[i][0], t));
            c.drawPath(path, paint);
            path.reset();
            path.moveTo(0, -s);
            path.lineTo(0.85f * s, 0);
            path.lineTo(0, s);
            path.close();
            paint.setColor(Colors.alpha(colors[i][1], 0.9f * t));
            c.drawPath(path, paint);
            path.reset();
            path.moveTo(0, s);
            path.lineTo(-0.28f * s, 1.38f * s);
            path.lineTo(0.28f * s, 1.38f * s);
            path.close();
            paint.setColor(Colors.alpha(colors[i][1], t));
            c.drawPath(path, paint);
            stroke.setStrokeWidth(0.9f * u);
            stroke.setColor(Colors.alpha(0xFF000000, 0.35f * t));
            c.drawLine(0, -s, 0, s, stroke);
            path.reset();
            path.moveTo(-0.85f * s, 0);
            path.quadTo(0, -0.55f * s, 0.85f * s, 0);
            c.drawPath(path, stroke);
            c.restore();
        }
    }

    // ---- moon nights ------------------------------------------------------------------------

    private void stars(Canvas c, float w, float h, int n) {
        Random random = new Random(21);
        for (int i = 0; i < n; i++) {
            float x = random.nextFloat() * w;
            float y = random.nextFloat() * h * 0.62f;
            float t = part(random.nextFloat() * 0.6f, 0.3f) * (0.65f + 0.35f * wave(1 + (i % 3), i));
            paint.setColor(Colors.alpha(0xFFFFFFFF, (0.25f + 0.5f * random.nextFloat()) * t));
            c.drawCircle(x, y, (0.5f + 1.0f * random.nextFloat()) * u, paint);
        }
    }

    /** A full moon rising; for Karwa Chauth it is seen through a decorated chalni. */
    private void moon(Canvas c, float cx, float finalY, float r, boolean chalni) {
        float t = easeOut(part(0, 0.75f));
        float cy = finalY + 70 * u * (1 - t);
        glow(c, cx, cy, r * 5f, 0xFFFFF2D0, 0.38f * t * (0.9f + 0.1f * wave(0.8f, 0)));
        gradient.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.3f, Colors.alpha(0xFFFFFFF6, t),
                Colors.alpha(0xFFF3E6C4, t), Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, gradient);
        paint.setColor(Colors.alpha(0xFFB9A57A, 0.10f * t));
        float[][] craters = {{-0.35f, -0.2f, 0.18f}, {0.25f, 0.1f, 0.24f}, {-0.1f, 0.42f, 0.12f}, {0.38f, -0.38f, 0.1f}};
        for (float[] cr : craters) c.drawCircle(cx + cr[0] * r, cy + cr[1] * r, cr[2] * r, paint);
        if (!chalni) return;

        float ct = easeOut(part(0.45f, 0.45f));
        if (ct <= 0) return;
        float ring = r * 1.6f;
        // The sieve's mesh, laid over the moon.
        c.save();
        path.reset();
        path.addCircle(cx, finalY, ring * 0.92f, Path.Direction.CW);
        c.clipPath(path);
        stroke.setStrokeWidth(0.5f * u);
        stroke.setColor(Colors.alpha(0xFFE3B04B, 0.16f * ct));
        for (float d = -ring; d <= ring; d += 4.5f * u) {
            c.drawLine(cx + d, finalY - ring, cx + d, finalY + ring, stroke);
            c.drawLine(cx - ring, finalY + d, cx + ring, finalY + d, stroke);
        }
        c.restore();
        stroke.setStrokeWidth(3.2f * u);
        stroke.setColor(Colors.alpha(0xFFE3B04B, ct));
        c.drawCircle(cx, finalY, ring, stroke);
        stroke.setStrokeWidth(1f * u);
        stroke.setColor(Colors.alpha(0xFFE3B04B, 0.6f * ct));
        c.drawCircle(cx, finalY, ring * 0.92f, stroke);
        for (int i = 0; i < 16; i++) {
            double a = 2 * Math.PI * i / 16;
            paint.setColor(Colors.alpha(i % 2 == 0 ? 0xFFFF5A5F : 0xFFFFE08A, ct));
            c.drawCircle(cx + (float) Math.cos(a) * ring, finalY + (float) Math.sin(a) * ring, 2.3f * u, paint);
        }
    }

    /** A thin crescent (with a star beside it for Eid). */
    private void crescent(Canvas c, float cx, float cy, float r, int color, boolean star) {
        float t = easeOut(part(0, 0.6f));
        glow(c, cx, cy, r * 4f, color, 0.24f * t);
        Path outer = new Path();
        outer.addCircle(cx, cy, r, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(cx + r * 0.38f, cy - r * 0.18f, r * 0.86f, Path.Direction.CW);
        outer.op(bite, Path.Op.DIFFERENCE);
        paint.setColor(Colors.alpha(color, t));
        c.drawPath(outer, paint);
        if (!star) return;
        float sx = cx + r * 1.05f;
        float sy = cy - r * 0.2f;
        float sr = r * 0.32f;
        path.reset();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + Math.PI * i / 5;
            float rr = i % 2 == 0 ? sr : sr * 0.42f;
            float px = sx + (float) Math.cos(a) * rr;
            float py = sy + (float) Math.sin(a) * rr;
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        path.close();
        c.drawPath(path, paint);
    }

    // ---- Janmashtami ------------------------------------------------------------------------

    /** A peacock feather sweeping up from the bottom-right corner. */
    private void feather(Canvas c, float w, float h) {
        float t = easeOut(part(0, 0.8f));
        if (t <= 0) return;
        float bx = w - 30 * u;
        float by = h + 10 * u;
        c.save();
        c.rotate(14 * (1 - t) + 1.5f * wave(0.6f, 0), bx, by);
        // The eye lands in the open space right of the last row, not behind a tile.
        float tipX = w - 300 * u;
        float tipY = h - 110 * u;
        float ctrlX = w - 90 * u;
        float ctrlY = h - 150 * u;

        stroke.setStrokeWidth(0.7f * u);
        for (float s = 0.12f; s < 0.86f; s += 0.011f) {
            float px = bez(bx, ctrlX, tipX, s);
            float py = bez(by, ctrlY, tipY, s);
            float tx = bezD(bx, ctrlX, tipX, s);
            float ty = bezD(by, ctrlY, tipY, s);
            float len = (float) Math.hypot(tx, ty);
            tx /= len;
            ty /= len;
            float barb = 36 * u * (float) Math.pow(Math.sin(Math.PI * s), 0.6) * (0.5f + 0.5f * s);
            stroke.setColor(Colors.alpha(s > 0.6f ? 0xFF6FBF3A : 0xFF3F8F5A, 0.55f * t));
            for (int side = -1; side <= 1; side += 2) {
                float nx = -ty * side;
                float ny = tx * side;
                c.drawLine(px, py, px + (nx * 0.8f + tx * 0.6f) * barb, py + (ny * 0.8f + ty * 0.6f) * barb, stroke);
            }
        }
        stroke.setStrokeWidth(1.4f * u);
        stroke.setColor(Colors.alpha(0xFFE8D9A8, 0.7f * t));
        path.reset();
        path.moveTo(bx, by);
        path.quadTo(ctrlX, ctrlY, tipX, tipY);
        c.drawPath(path, stroke);

        // The eye, aligned with the shaft at its tip.
        float angle = (float) Math.toDegrees(Math.atan2(bezD(by, ctrlY, tipY, 1), bezD(bx, ctrlX, tipX, 1)));
        c.save();
        c.translate(tipX, tipY);
        c.rotate(angle);
        stroke.setStrokeWidth(0.7f * u);
        stroke.setColor(Colors.alpha(0xFF7FC24A, 0.5f * t));
        for (int i = 0; i < 28; i++) {
            double a = 2 * Math.PI * i / 28;
            c.drawLine((float) Math.cos(a) * 24 * u, (float) Math.sin(a) * 19 * u,
                    (float) Math.cos(a) * 40 * u, (float) Math.sin(a) * 32 * u, stroke);
        }
        float[][] rings = {{30, 24}, {25, 20}, {19, 15}, {12, 10.5f}, {6.5f, 6.5f}};
        int[] colors = {0xFFB8862B, 0xFF4CAF50, 0xFF1FB5A8, 0xFF1A3FA8, 0xFF0B1A4A};
        RectF oval = new RectF();
        for (int i = 0; i < rings.length; i++) {
            paint.setColor(Colors.alpha(colors[i], 0.9f * t));
            float rw = rings[i][0] * u;
            float rh = rings[i][1] * u;
            float shift = -i * 1.6f * u;
            oval.set(-rw + shift, -rh, rw + shift, rh);
            c.drawOval(oval, paint);
        }
        c.restore();
        c.restore();
    }

    /** Krishna's bansuri along the bottom-left, with a little tassel. */
    private void flute(Canvas c, float w, float h) {
        float t = easeOut(part(0.25f, 0.6f));
        if (t <= 0) return;
        float len = 230 * u;
        float x = 40 * u - (1 - t) * 120 * u;
        float y = h - 34 * u;
        c.save();
        c.rotate(-7, x, y);
        gradient.setShader(new LinearGradient(0, y - 4 * u, 0, y + 4 * u, Colors.alpha(0xFFD9B26A, t), Colors.alpha(0xFF8A6430, t), Shader.TileMode.CLAMP));
        c.drawRoundRect(x, y - 4 * u, x + len, y + 4 * u, 4 * u, 4 * u, gradient);
        paint.setColor(Colors.alpha(0xFF3A2410, t));
        for (int i = 0; i < 7; i++) c.drawCircle(x + len * (0.42f + i * 0.07f), y, 1.4f * u, paint);
        paint.setColor(Colors.alpha(0xFFD7263D, t));
        for (float f : new float[]{0.08f, 0.3f, 0.95f}) {
            c.drawRect(x + len * f, y - 4.2f * u, x + len * f + 3 * u, y + 4.2f * u, paint);
        }
        stroke.setStrokeWidth(1.2f * u);
        stroke.setColor(Colors.alpha(0xFFFFC233, t));
        c.drawLine(x + len * 0.3f + 1.5f * u, y + 4 * u, x + len * 0.3f - 4 * u, y + 22 * u, stroke);
        c.drawLine(x + len * 0.3f + 1.5f * u, y + 4 * u, x + len * 0.3f + 4 * u, y + 21 * u, stroke);
        c.restore();
    }

    private static float bez(float a, float b, float c, float t) {
        float i = 1 - t;
        return i * i * a + 2 * i * t * b + t * t * c;
    }

    private static float bezD(float a, float b, float c, float t) {
        return 2 * (1 - t) * (b - a) + 2 * t * (c - b);
    }

    // ---- Raksha Bandhan ---------------------------------------------------------------------

    /** A twisted red-and-gold thread across the bottom, unspooling from a flower medallion. */
    private void rakhi(Canvas c, float w, float h) {
        float reveal = easeOut(part(0, 0.6f));
        float y0 = h - 70 * u;
        float yc = h - 20 * u;
        int steps = 240;
        for (int strand = 0; strand < 2; strand++) {
            stroke.setStrokeWidth(1.6f * u);
            stroke.setColor(strand == 0 ? 0xFFD7263D : 0xFFF2C14E);
            float lastX = 0, lastY = 0;
            for (int i = 0; i <= steps; i++) {
                float s = (float) i / steps;
                if (Math.abs(s - 0.5f) > reveal * 0.5f + 0.001f) {
                    lastX = Float.NaN;
                    continue;
                }
                float x = bez(-20 * u, w / 2, w + 20 * u, s);
                float y = bez(y0, yc, y0, s) + (float) Math.sin(s * 150 + strand * Math.PI) * 1.3f * u;
                if (i > 0 && !Float.isNaN(lastX)) c.drawLine(lastX, lastY, x, y, stroke);
                lastX = x;
                lastY = y;
            }
        }
        float pop = part(0.45f, 0.45f);
        if (pop <= 0) return;
        float s = Math.max(0, easeBack(pop));
        float cx = w / 2;
        float cy = bez(y0, yc, y0, 0.5f);
        c.save();
        c.translate(cx, cy);
        c.scale(s, s);
        c.rotate(time * 8);
        glow(c, 0, 0, 60 * u, 0xFFFFC56B, 0.35f);
        RectF oval = new RectF();
        int[][] layers = {{16, 0xFFF2C14E, 24, 13}, {12, 0xFFD7263D, 17, 10}};
        for (int[] layer : layers) {
            paint.setColor(layer[1]);
            for (int i = 0; i < layer[0]; i++) {
                c.save();
                c.rotate(360f * i / layer[0]);
                oval.set(layer[2] * u - layer[3] * u, -3.4f * u, layer[2] * u + 2 * u, 3.4f * u);
                c.drawOval(oval, paint);
                c.restore();
            }
        }
        paint.setColor(0xFFFFFFFF);
        for (int i = 0; i < 10; i++) {
            double a = 2 * Math.PI * i / 10;
            c.drawCircle((float) Math.cos(a) * 10 * u, (float) Math.sin(a) * 10 * u, 1.5f * u, paint);
        }
        gradient.setShader(new RadialGradient(-2 * u, -2 * u, 9 * u, 0xFFFFFFFF, 0xFFF2C14E, Shader.TileMode.CLAMP));
        c.drawCircle(0, 0, 6.5f * u, gradient);
        c.restore();
    }

    // ---- Navratri, Dussehra, Ganesh Chaturthi -----------------------------------------------

    /** Marigold torans with mango leaves, dropping in from the top edge. */
    private void toran(Canvas c, float w) {
        float t = part(0, 0.65f);
        if (t <= 0) return;
        float drop = (1 - easeBack(t)) * -70 * u;
        c.save();
        c.translate(0, drop);
        int swags = 5;
        float dip = 20 * u;
        for (int s = 0; s < swags; s++) {
            float x0 = w * s / swags;
            float x1 = w * (s + 1) / swags;
            float mid = (x0 + x1) / 2;
            int n = 11;
            // Mango leaves hang from the garland between every other pair of flowers.
            for (int i = 1; i < n; i += 2) {
                float f = (i + 0.5f) / n;
                leaf(c, bez(x0, mid, x1, f), bez(-3 * u, dip * 2, -3 * u, f) + 2 * u, (i == n / 2 ? 30 : 22) * u, (f - 0.5f) * -30f);
            }
            for (int i = 0; i <= n; i++) {
                float f = (float) i / n;
                marigold(c, bez(x0, mid, x1, f), bez(-3 * u, dip * 2, -3 * u, f), 7.5f * u, (i + s) % 2 == 0);
            }
        }
        // Short hanging strands where the swags meet, only in the open space mid-screen.
        for (int s = 2; s <= 3; s++) {
            float x = w * s / swags;
            float sway = (float) Math.sin(progress * 10 + s) * 6 * (1 - progress) + 3 * wave(1.2f, s);
            c.save();
            c.rotate(sway, x, 0);
            for (int i = 0; i < 4; i++) marigold(c, x, (6 + i * 12) * u, 6.5f * u, i % 2 == 1);
            leaf(c, x, 52 * u, 20 * u, 0);
            c.restore();
        }
        c.restore();
    }

    /** A ruffled marigold: a scalloped ring of petals around a brighter, denser centre. */
    private void marigold(Canvas c, float x, float y, float r, boolean yellow) {
        int outer = yellow ? 0xFFF0A800 : 0xFFE86A00;
        int inner = yellow ? 0xFFFFD84A : 0xFFFFA21C;
        paint.setColor(outer);
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * i / 5;
            c.drawCircle(x + (float) Math.cos(a) * r * 0.62f, y + (float) Math.sin(a) * r * 0.62f, r * 0.42f, paint);
        }
        gradient.setShader(new RadialGradient(x - r * 0.15f, y - r * 0.15f, r * 0.8f, inner, outer, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r * 0.66f, gradient);
        paint.setColor(0x26000000);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * i / 4 + 0.3;
            c.drawCircle(x + (float) Math.cos(a) * r * 0.38f, y + (float) Math.sin(a) * r * 0.38f, r * 0.1f, paint);
        }
    }

    private void leaf(Canvas c, float x, float y, float len, float rotation) {
        c.save();
        c.rotate(rotation, x, y);
        float wid = len * 0.32f;
        path.reset();
        path.moveTo(x, y);
        path.quadTo(x + wid, y + len * 0.45f, x, y + len);
        path.quadTo(x - wid, y + len * 0.45f, x, y);
        path.close();
        gradient.setShader(new LinearGradient(x, y, x, y + len, 0xFF4CAF50, 0xFF1E6B2A, Shader.TileMode.CLAMP));
        c.drawPath(path, gradient);
        stroke.setStrokeWidth(0.6f * u);
        stroke.setColor(0x669BD39F);
        c.drawLine(x, y + len * 0.1f, x, y + len * 0.9f, stroke);
        c.restore();
    }

    // ---- Chhath -----------------------------------------------------------------------------

    /** The setting sun at the water's edge, with its reflection breaking into ripples. */
    private void sunset(Canvas c, float w, float h) {
        float t = easeOut(part(0, 0.8f));
        float horizon = h - 66 * u;
        glow(c, w / 2, horizon, 440 * u, 0xFFFF7A3D, 0.38f * t);
        float r = 46 * u;
        float cy = horizon + 6 * u - 34 * u * (1 - t);
        c.save();
        c.clipRect(0, 0, w, horizon);
        gradient.setShader(new LinearGradient(0, cy - r, 0, cy + r, Colors.alpha(0xFFFFD27A, t), Colors.alpha(0xFFFF5E3A, t), Shader.TileMode.CLAMP));
        c.drawCircle(w / 2, cy, r, gradient);
        c.restore();
        stroke.setStrokeWidth(0.8f * u);
        stroke.setColor(Colors.alpha(0xFFFFB066, 0.3f * t));
        c.drawLine(0, horizon, w, horizon, stroke);
        Random random = new Random(4);
        stroke.setStrokeWidth(1.7f * u);
        for (int k = 0; k < 14; k++) {
            float y = horizon + 5 * u + k * 4.4f * u;
            float half = 70 * u * (1 - k / 16f) * (0.55f + 0.45f * random.nextFloat()) * t * (1 + 0.15f * wave(2f, k));
            float jitter = (random.nextFloat() - 0.5f) * 14 * u + 5 * u * wave(1.3f, k * 0.7f);
            stroke.setColor(Colors.alpha(0xFFFFB066, 0.75f * (1 - k / 14f) * t));
            c.drawLine(w / 2 - half + jitter, y, w / 2 + half + jitter, y, stroke);
        }
    }

    // ---- lights -----------------------------------------------------------------------------

    /** Strings of bulbs swagged along the top; they light up in sequence. */
    private void lights(Canvas c, float w, int[] colors, float glowAlpha) {
        int swags = 4;
        float dip = 24 * u;
        stroke.setStrokeWidth(0.8f * u);
        stroke.setColor(0x55FFFFFF);
        int index = 0;
        int total = swags * 12;
        for (int s = 0; s < swags; s++) {
            float x0 = w * s / swags;
            float x1 = w * (s + 1) / swags;
            float mid = (x0 + x1) / 2;
            path.reset();
            path.moveTo(x0, -2 * u);
            path.quadTo(mid, dip * 2, x1, -2 * u);
            c.drawPath(path, stroke);
            for (int i = 1; i <= 12; i++, index++) {
                float f = i / 13f;
                float x = bez(x0, mid, x1, f);
                float y = bez(-2 * u, dip * 2, -2 * u, f) + 5 * u;
                boolean on = progress >= (float) index / total * 0.8f;
                int color = colors[index % colors.length];
                if (on) glow(c, x, y, 14 * u, color, glowAlpha * (0.55f + 0.45f * wave(3f, -index * 0.6f)));
                paint.setColor(on ? color : 0x40FFFFFF);
                c.drawOval(x - 2.6f * u, y - 3.8f * u, x + 2.6f * u, y + 3.8f * u, paint);
            }
        }
    }

    /** Snow across the whole screen, settling as the opening ends, with a few crystal flakes. */
    private void snow(Canvas c, float w, float h) {
        Random random = new Random(12);
        float fall = (1 - easeOut(part(0, 1f))) * 80 * u;
        for (int i = 0; i < 130; i++) {
            float x = random.nextFloat() * w;
            float speed = 0.5f + random.nextFloat();
            float y = (random.nextFloat() * h + time * 16 * u * speed) % h - fall * speed;
            paint.setColor(Colors.alpha(0xFFFFFFFF, 0.2f + 0.35f * random.nextFloat()));
            c.drawCircle(x, y, (0.6f + 1.6f * random.nextFloat()) * u, paint);
        }
        stroke.setStrokeWidth(0.9f * u);
        float[][] flakes = {{0.42f, 0.12f, 9}, {0.6f, 0.2f, 7}, {0.08f, 0.9f, 10}, {0.92f, 0.88f, 8}, {0.5f, 0.92f, 6}};
        for (float[] f : flakes) {
            float x = w * f[0];
            float y = h * f[1] - fall;
            float r = f[2] * u;
            stroke.setColor(0x55FFFFFF);
            for (int a = 0; a < 6; a++) {
                double ang = Math.PI * a / 3;
                float ex = x + (float) Math.cos(ang) * r;
                float ey = y + (float) Math.sin(ang) * r;
                c.drawLine(x, y, ex, ey, stroke);
                float bx = x + (float) Math.cos(ang) * r * 0.6f;
                float by = y + (float) Math.sin(ang) * r * 0.6f;
                c.drawLine(bx, by, bx + (float) Math.cos(ang + 0.6) * r * 0.3f, by + (float) Math.sin(ang + 0.6) * r * 0.3f, stroke);
                c.drawLine(bx, by, bx + (float) Math.cos(ang - 0.6) * r * 0.3f, by + (float) Math.sin(ang - 0.6) * r * 0.3f, stroke);
            }
        }
    }

    // ---- Eid --------------------------------------------------------------------------------

    /** A fanoos hanging from the top edge; it swings to rest during the opening. */
    private void lantern(Canvas c, float x, float length, float scale, float phase) {
        float t = easeOut(part(phase * 0.05f, 0.4f));
        if (t <= 0) return;
        float s = scale * u;
        float swing = 16f * (float) Math.exp(-2.5 * progress) * (float) Math.sin(progress * 16 + phase) * (1 - progress)
                + 4 * wave(0.9f, phase);
        c.save();
        c.rotate(swing, x, 0);
        stroke.setStrokeWidth(0.8f * u);
        stroke.setColor(Colors.alpha(0xFFD9A441, 0.7f * t));
        c.drawLine(x, 0, x, length, stroke);
        float top = length;
        glow(c, x, top + 20 * s, 50 * s, 0xFFFFC46B, 0.35f * t);

        path.reset();
        path.moveTo(x - 6 * s, top + 6 * s);
        path.quadTo(x, top - 3 * s, x + 6 * s, top + 6 * s);
        path.close();
        paint.setColor(Colors.alpha(0xFFD9A441, t));
        c.drawPath(path, paint);

        path.reset();
        path.moveTo(x - 7 * s, top + 6 * s);
        path.lineTo(x - 10 * s, top + 18 * s);
        path.lineTo(x - 6 * s, top + 32 * s);
        path.lineTo(x + 6 * s, top + 32 * s);
        path.lineTo(x + 10 * s, top + 18 * s);
        path.lineTo(x + 7 * s, top + 6 * s);
        path.close();
        gradient.setShader(new RadialGradient(x, top + 19 * s, 14 * s, Colors.alpha(0xFFFFF0C4, t), Colors.alpha(0xFFE08A1E, t), Shader.TileMode.CLAMP));
        c.drawPath(path, gradient);
        stroke.setStrokeWidth(1.1f * u);
        stroke.setColor(Colors.alpha(0xFFD9A441, t));
        c.drawPath(path, stroke);
        c.drawLine(x, top + 6 * s, x, top + 32 * s, stroke);
        c.drawLine(x - 10 * s, top + 18 * s, x + 10 * s, top + 18 * s, stroke);

        path.reset();
        path.moveTo(x - 4 * s, top + 32 * s);
        path.lineTo(x + 4 * s, top + 32 * s);
        path.lineTo(x, top + 39 * s);
        path.close();
        c.drawPath(path, paint);
        c.drawLine(x, top + 39 * s, x, top + 46 * s, stroke);
        c.restore();
    }

    // ---- family days ------------------------------------------------------------------------

    /** Balloons rising into place: a bunch between the clock and the weather, another at bottom right. */
    private void balloons(Canvas c, float w, float h) {
        float[][] balloons = {
                {0.465f, 0.06f, 12}, {0.505f, 0.11f, 16}, {0.55f, 0.065f, 19}, {0.59f, 0.12f, 15}, {0.625f, 0.07f, 13},
                {0.80f, 0.84f, 16}, {0.86f, 0.78f, 22}, {0.92f, 0.73f, 18},
        };
        int[] colors = {0xFFBF5AF2, 0xFFFF375F, 0xFFFFD60A, 0xFF0A84FF, 0xFF30D158, 0xFF64D2FF, 0xFFFF9F0A, 0xFFFF375F};
        for (int i = 0; i < balloons.length; i++) {
            float t = easeOut(part(i * 0.06f, 0.75f));
            if (t <= 0) continue;
            float r = balloons[i][2] * u;
            float x = w * balloons[i][0] + (float) Math.sin(progress * 7 + i) * 8 * u * (1 - progress) + 4 * u * wave(0.7f, i);
            float y = h * balloons[i][1] + h * 0.7f * (1 - t) + 5 * u * wave(0.9f, i * 1.7f);
            // A wavy string down from the knot.
            stroke.setStrokeWidth(0.8f * u);
            stroke.setColor(0x80FFFFFF);
            path.reset();
            path.moveTo(x, y + r * 1.15f);
            for (int k = 1; k <= 8; k++) {
                path.lineTo(x + (float) Math.sin(k * 1.3 + i) * 2.5f * u, y + r * 1.15f + k * 6 * u);
            }
            c.drawPath(path, stroke);
            int color = colors[i];
            gradient.setShader(new RadialGradient(x - r * 0.35f, y - r * 0.4f, r * 1.4f,
                    Colors.blend(color, 0xFFFFFFFF, 0.45f), color, Shader.TileMode.CLAMP));
            c.drawOval(x - r * 0.86f, y - r, x + r * 0.86f, y + r, gradient);
            paint.setColor(color);
            path.reset();
            path.moveTo(x, y + r * 0.95f);
            path.lineTo(x - 2.5f * u, y + r * 1.15f);
            path.lineTo(x + 2.5f * u, y + r * 1.15f);
            path.close();
            c.drawPath(path, paint);
            paint.setColor(0x66FFFFFF);
            c.drawOval(x - r * 0.5f, y - r * 0.72f, x - r * 0.22f, y - r * 0.28f, paint);
        }
    }

    /** Confetti drifting down into place over the whole screen. */
    private void confetti(Canvas c, float w, float h) {
        Random random = new Random(8);
        int[] colors = {0xFFFF375F, 0xFFFFD60A, 0xFF0A84FF, 0xFF30D158, 0xFFBF5AF2, 0xFFFF9F0A};
        float fall = (1 - easeOut(part(0.1f, 0.9f))) * 120 * u;
        for (int i = 0; i < 170; i++) {
            float x = random.nextFloat() * w;
            float speed = 0.5f + random.nextFloat();
            float y = (random.nextFloat() * h + time * 22 * u * speed) % (h + 20 * u) - 10 * u - fall * speed;
            float a = random.nextFloat() * 180;
            float pw = (2 + 3 * random.nextFloat()) * u;
            // Keep the clock, date and weather clear.
            if (y < h * 0.23f && (x < w * 0.45f || x > w * 0.64f)) continue;
            paint.setColor(Colors.alpha(colors[random.nextInt(colors.length)], 0.45f + 0.4f * random.nextFloat()));
            c.save();
            c.rotate(a + time * 70 * speed, x, y);
            c.drawRect(x - pw, y - pw * 0.4f, x + pw, y + pw * 0.4f, paint);
            c.restore();
        }
    }

    /** Two gold rings drawing together until they interlock, with a glint on one. */
    private void rings(Canvas c, float cx, float cy) {
        float t = easeOut(part(0.15f, 0.7f));
        if (t <= 0) return;
        float r = 17 * u;
        float apart = 6 * u + 40 * u * (1 - t);
        glow(c, cx, cy, 90 * u, 0xFFFFD27A, 0.3f * t);
        stroke.setStrokeWidth(3.6f * u);
        stroke.setAlpha(255);
        stroke.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFFFFE7A3, 0xFFC8902E, Shader.TileMode.MIRROR));
        stroke.setAlpha(Math.round(255 * t));
        c.drawCircle(cx - apart, cy, r, stroke);
        c.drawCircle(cx + apart, cy, r, stroke);
        stroke.setShader(null);
        stroke.setAlpha(255);
        // The glint: a four-pointed sparkle on the top of the left ring.
        float gx = cx - apart - r * 0.5f;
        float gy = cy - r * 0.87f;
        float g = 6 * u * part(0.75f, 0.25f) * (0.7f + 0.3f * wave(2.2f, 0));
        if (g <= 0) return;
        paint.setColor(0xFFFFFFFF);
        path.reset();
        path.moveTo(gx, gy - g);
        path.quadTo(gx, gy, gx + g, gy);
        path.quadTo(gx, gy, gx, gy + g);
        path.quadTo(gx, gy, gx - g, gy);
        path.quadTo(gx, gy, gx, gy - g);
        path.close();
        c.drawPath(path, paint);
    }

    /** Hearts floating up into the open spaces. */
    private void hearts(Canvas c, float w, float h) {
        float[][] hearts = {
                {0.44f, 0.07f, 7}, {0.62f, 0.06f, 9}, {0.60f, 0.19f, 6},
                {0.84f, 0.80f, 13}, {0.91f, 0.72f, 9}, {0.76f, 0.88f, 8}, {0.30f, 0.90f, 10}, {0.55f, 0.86f, 7},
        };
        for (int i = 0; i < hearts.length; i++) {
            float t = easeOut(part(0.1f + i * 0.05f, 0.6f));
            if (t <= 0) continue;
            // A heartbeat every couple of seconds, each heart out of step with the next.
            float s = hearts[i][2] * u * (1 + 0.14f * (float) Math.pow(Math.max(0, wave(3f, i)), 8));
            float x = w * hearts[i][0];
            float y = h * hearts[i][1] + 60 * u * (1 - t) + 4 * u * wave(0.8f, i);
            paint.setColor(Colors.alpha(i % 2 == 0 ? 0xFFFF6B8B : 0xFFFFB3C1, 0.8f * t));
            path.reset();
            path.moveTo(x, y + s);
            path.cubicTo(x - 1.25f * s, y + 0.15f * s, x - 0.65f * s, y - 0.95f * s, x, y - 0.35f * s);
            path.cubicTo(x + 0.65f * s, y - 0.95f * s, x + 1.25f * s, y + 0.15f * s, x, y + s);
            path.close();
            c.drawPath(path, paint);
        }
    }

    /** Rose petals scattered along the bottom edge. */
    private void petals(Canvas c, float w, float h) {
        Random random = new Random(14);
        float t = easeOut(part(0.05f, 0.8f));
        for (int i = 0; i < 46; i++) {
            float x = random.nextFloat() * w;
            float y = h - random.nextFloat() * 70 * u - 60 * u * (1 - t) * random.nextFloat();
            float a = random.nextFloat() * 360;
            float pl = (5 + 4 * random.nextFloat()) * u;
            paint.setColor(Colors.alpha(random.nextBoolean() ? 0xFFE63950 : 0xFFFF8FA3, (0.55f + 0.35f * random.nextFloat()) * t));
            c.save();
            c.rotate(a, x, y);
            c.drawOval(x - pl * 0.6f, y - pl, x + pl * 0.6f, y + pl, paint);
            c.restore();
        }
    }

    // ---- national days ----------------------------------------------------------------------

    /** Saffron, white and green ribbons along the bottom, with the chakra turning into place. */
    private void tricolour(Canvas c, float w, float h) {
        float reveal = easeOut(part(0, 0.7f));
        int[] colors = {0xFFFF9933, 0xFFF5F5F7, 0xFF138808};
        float[] bases = {h - 84 * u, h - 62 * u, h - 40 * u};
        c.save();
        c.clipRect(0, 0, w * reveal, h);
        stroke.setStrokeWidth(15 * u);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        for (int k = 0; k < 3; k++) {
            stroke.setColor(Colors.alpha(colors[k], k == 1 ? 0.18f : 0.32f));
            path.reset();
            for (int i = 0; i <= 60; i++) {
                float x = w * i / 60f;
                float y = bases[k] + (float) Math.sin(i / 60f * Math.PI * 3.2 + 0.6 + time * 0.8f) * 10 * u;
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            c.drawPath(path, stroke);
        }
        stroke.setStrokeCap(Paint.Cap.ROUND);
        c.restore();

        float ct = easeOut(part(0.35f, 0.6f));
        if (ct <= 0) return;
        float cx = w / 2;
        float cy = bases[1] + (float) Math.sin(0.5 * Math.PI * 3.2 + 0.6 + time * 0.8f) * 10 * u;
        float r = 13 * u;
        c.save();
        c.rotate(-360 * (1 - ct) + time * 20, cx, cy);
        stroke.setStrokeWidth(1.4f * u);
        stroke.setColor(Colors.alpha(0xFF6F8BE8, ct));
        c.drawCircle(cx, cy, r, stroke);
        stroke.setStrokeWidth(0.7f * u);
        for (int i = 0; i < 24; i++) {
            double a = 2 * Math.PI * i / 24;
            c.drawLine(cx, cy, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, stroke);
        }
        c.restore();
    }
}
