package dev.vaari.aangan;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * The home screen background, painted once into a bitmap: a deep vertical gradient, two soft
 * colour glows, a vignette and a trace of grain to stop banding on 8-bit panels. Nothing animates,
 * so it costs nothing while idle.
 */
final class Backdrop {

    private Backdrop() {
    }

    static Bitmap paint(int w, int h, float density, int glow) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setDither(true);

        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF101219, 0xFF050608, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        // A cool glow from above the top-left corner, tinted by the sky outside.
        p.setShader(new RadialGradient(w * 0.15f, h * -0.10f, 620 * density,
                withAlpha(glow, 0.35f), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        // A warm counter-glow from below the bottom-right corner.
        p.setShader(new RadialGradient(w * 0.95f, h * 1.05f, 520 * density,
                withAlpha(0xFF3B2A4F, 0.28f), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        p.setShader(new RadialGradient(w / 2f, h / 2f, (float) Math.hypot(w, h) / 2f,
                new int[]{Color.TRANSPARENT, Color.TRANSPARENT, 0x59000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        p.setShader(new BitmapShader(grain(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        c.drawRect(0, 0, w, h, p);
        return bmp;
    }

    private static int withAlpha(int color, float alpha) {
        return (Math.round(alpha * 255) << 24) | (color & 0x00FFFFFF);
    }

    /** A 96px tile of faint monochrome noise, about ±1.5% brightness. */
    private static Bitmap grain() {
        int size = 96;
        int[] px = new int[size * size];
        Random random = new Random(7);
        for (int i = 0; i < px.length; i++) {
            boolean light = random.nextBoolean();
            int alpha = random.nextInt(5);
            px[i] = (alpha << 24) | (light ? 0xFFFFFF : 0x000000);
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
    }
}
