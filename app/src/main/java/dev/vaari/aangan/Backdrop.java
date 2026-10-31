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

final class Backdrop {

    private Backdrop() {
    }

    static Bitmap paint(int w, int h, float density, int glow) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.DITHER_FLAG);
        Shader[] layers = {
                new LinearGradient(0, 0, 0, h, 0xFF101219, 0xFF050608, Shader.TileMode.CLAMP),
                new RadialGradient(w * 0.15f, h * -0.10f, 620 * density,
                        Colors.alpha(glow, 0.35f), Color.TRANSPARENT, Shader.TileMode.CLAMP),
                new RadialGradient(w * 0.95f, h * 1.05f, 520 * density,
                        Colors.alpha(0xFF3B2A4F, 0.28f), Color.TRANSPARENT, Shader.TileMode.CLAMP),
                new RadialGradient(w / 2f, h / 2f, (float) Math.hypot(w, h) / 2f,
                        new int[]{Color.TRANSPARENT, Color.TRANSPARENT, 0x59000000},
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP),
                new BitmapShader(grain(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT),
        };
        for (Shader layer : layers) {
            p.setShader(layer);
            c.drawRect(0, 0, w, h, p);
        }
        return bmp;
    }

    /** About ±1.5% brightness, to stop banding on 8-bit panels. */
    private static Bitmap grain() {
        int size = 96;
        int[] px = new int[size * size];
        Random random = new Random(7);
        for (int i = 0; i < px.length; i++) {
            int rgb = random.nextBoolean() ? 0xFFFFFF : 0x000000;
            px[i] = (random.nextInt(5) << 24) | rgb;
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
    }
}
