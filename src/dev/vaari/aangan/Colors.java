package dev.vaari.aangan;

import android.graphics.Color;

/** Small colour sums shared by the drawn views. */
final class Colors {

    private Colors() {
    }

    /** {@code color} with its opacity scaled by {@code a}, from 0 (clear) to 1 (as it is). */
    static int alpha(int color, float a) {
        return (Math.round(Math.max(0f, Math.min(1f, a)) * Color.alpha(color)) << 24) | (color & 0xFFFFFF);
    }

    /** Part of the way from {@code a} to {@code b}, opaque. */
    static int blend(int a, int b, float f) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }
}
