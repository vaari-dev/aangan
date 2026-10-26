package dev.vaari.aangan;

import android.graphics.Color;

final class Colors {

    private Colors() {
    }

    static int alpha(int color, float a) {
        return (Math.round(Math.max(0f, Math.min(1f, a)) * Color.alpha(color)) << 24) | (color & 0xFFFFFF);
    }

    static int blend(int a, int b, float f) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }
}
