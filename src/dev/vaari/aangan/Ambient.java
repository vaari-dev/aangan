package dev.vaari.aangan;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import java.util.Random;

/**
 * A soft pool of light behind the focused tile, in that app's own colour, as if the card were
 * lighting the room. It glides and re-tints only when focus moves; at rest it is a static image.
 */
final class Ambient extends ImageView {

    static final int WIDTH = 560;
    static final int HEIGHT = 280;
    private static final long MOVE_MS = 420;

    private final DecelerateInterpolator ease = new DecelerateInterpolator(1.8f);
    private ValueAnimator tint;
    private int color = 0xFF808080;

    Ambient(Context context) {
        super(context);
        setScaleType(ScaleType.FIT_XY);
        setImageBitmap(mask(384));
        setColorFilter(color, PorterDuff.Mode.SRC_IN);
        setAlpha(0f);
    }

    /** Moves the light so it centres on (cx, cy) in the parent, and fades it to that colour. */
    void glow(float cx, float cy, int target, float strength) {
        boolean wasHidden = getAlpha() == 0f;
        float x = cx - getWidth() / 2f;
        float y = cy - getHeight() / 2f;
        if (wasHidden) {
            // Appear in place rather than sliding in from wherever it was last.
            setTranslationX(x);
            setTranslationY(y);
            setTint(target);
        }
        animate().translationX(x).translationY(y).alpha(strength)
                .setDuration(MOVE_MS).setInterpolator(ease).start();
        if (!wasHidden && target != color) {
            if (tint != null) tint.cancel();
            tint = ValueAnimator.ofObject(new ArgbEvaluator(), color, target);
            tint.setDuration(MOVE_MS);
            tint.setInterpolator(ease);
            tint.addUpdateListener(a -> setTint((Integer) a.getAnimatedValue()));
            tint.start();
        }
    }

    void hide() {
        animate().alpha(0f).setDuration(200).start();
    }

    private void setTint(int c) {
        color = c;
        setColorFilter(c, PorterDuff.Mode.SRC_IN);
    }

    /**
     * A circular falloff, (1 - r²)², with a little noise so the scaled-up gradient doesn't band.
     * Stretched to an ellipse by the view's size.
     */
    private static Bitmap mask(int size) {
        int[] px = new int[size * size];
        Random random = new Random(11);
        float half = size / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f - half) / half;
                float dy = (y + 0.5f - half) / half;
                float r2 = Math.min(1f, dx * dx + dy * dy);
                float a = (1f - r2) * (1f - r2) * 255f + random.nextFloat() - 0.5f;
                int alpha = Math.max(0, Math.min(255, Math.round(a)));
                px[y * size + x] = (alpha << 24) | 0xFFFFFF;
            }
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
    }
}
