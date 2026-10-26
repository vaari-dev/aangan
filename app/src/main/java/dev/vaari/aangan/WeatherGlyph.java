package dev.vaari.aangan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Drawn in a 22-unit box, since emoji and font symbols look inconsistent on Android TV. */
final class WeatherGlyph extends View {

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Weather.Glyph glyph = Weather.Glyph.SUN;

    WeatherGlyph(Context context) {
        super(context);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(Ui.LABEL);
        fill.setColor(Ui.LABEL);
    }

    void setGlyph(Weather.Glyph glyph) {
        if (this.glyph == glyph) return;
        this.glyph = glyph;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float unit = getWidth() / 22f;
        canvas.save();
        canvas.scale(unit, unit);
        stroke.setStrokeWidth(1.6f);
        switch (glyph) {
            case SUN: sun(canvas, 11, 11, 4.5f, 7f, 9.5f); break;
            case MOON: moon(canvas, 11, 11, 6.5f); break;
            case PARTLY_DAY: behindCloud(canvas, true); break;
            case PARTLY_NIGHT: behindCloud(canvas, false); break;
            case CLOUD: canvas.drawPath(cloud(0), stroke); break;
            case FOG:
                canvas.drawLine(3, 7, 17, 7, stroke);
                canvas.drawLine(5, 11, 19, 11, stroke);
                canvas.drawLine(3, 15, 17, 15, stroke);
                break;
            case DRIZZLE:
                canvas.drawPath(cloud(-3), stroke);
                for (float x : new float[]{7.5f, 11, 14.5f}) canvas.drawCircle(x, 18.5f, 0.9f, fill);
                break;
            case RAIN:
                canvas.drawPath(cloud(-3), stroke);
                for (float x : new float[]{8, 11.5f, 15}) canvas.drawLine(x, 16.5f, x - 1.4f, 20.5f, stroke);
                break;
            case SNOW:
                canvas.drawPath(cloud(-3), stroke);
                stroke.setStrokeWidth(1.2f);
                for (float x : new float[]{7.5f, 11.5f, 15.5f}) asterisk(canvas, x, 18.5f, 1.6f);
                break;
            case THUNDER:
                canvas.drawPath(cloud(-3), stroke);
                Path bolt = new Path();
                bolt.moveTo(12, 14.5f);
                bolt.lineTo(9.5f, 18.5f);
                bolt.lineTo(12, 18.5f);
                bolt.lineTo(10.5f, 21.5f);
                canvas.drawPath(bolt, stroke);
                break;
        }
        canvas.restore();
    }

    private void sun(Canvas c, float cx, float cy, float r, float rayIn, float rayOut) {
        c.drawCircle(cx, cy, r, stroke);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            c.drawLine(cx + cos * rayIn, cy + sin * rayIn, cx + cos * rayOut, cy + sin * rayOut, stroke);
        }
    }

    private void moon(Canvas c, float cx, float cy, float r) {
        Path disc = new Path();
        disc.addCircle(cx, cy, r, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(cx + r * 0.55f, cy - r * 0.45f, r * 0.85f, Path.Direction.CW);
        disc.op(bite, Path.Op.DIFFERENCE);
        c.drawPath(disc, stroke);
    }

    private void behindCloud(Canvas c, boolean day) {
        Path cloud = cloud(2);
        c.save();
        c.clipOutPath(cloud);
        if (day) sun(c, 8, 8, 3f, 4.8f, 6.5f);
        else moon(c, 8, 8, 4.5f);
        c.restore();
        c.drawPath(cloud, stroke);
    }

    private static Path cloud(float dy) {
        Path p = new Path();
        p.addCircle(7.5f, 13.5f + dy, 3.5f, Path.Direction.CW);
        Path mid = new Path();
        mid.addCircle(12, 11 + dy, 4.8f, Path.Direction.CW);
        Path right = new Path();
        right.addCircle(16, 13.8f + dy, 3.2f, Path.Direction.CW);
        Path base = new Path();
        base.addRect(7.5f, 13.5f + dy, 16, 17 + dy, Path.Direction.CW);
        p.op(mid, Path.Op.UNION);
        p.op(right, Path.Op.UNION);
        p.op(base, Path.Op.UNION);
        return p;
    }

    private void asterisk(Canvas c, float cx, float cy, float r) {
        for (int i = 0; i < 3; i++) {
            double a = Math.PI / 3 * i + Math.PI / 2;
            float dx = (float) Math.cos(a) * r;
            float dy = (float) Math.sin(a) * r;
            c.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, stroke);
        }
    }
}
