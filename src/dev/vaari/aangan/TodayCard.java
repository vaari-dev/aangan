package dev.vaari.aangan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.List;

/**
 * The "Today" sheet that opens from the weather pill: the sky on the left (now, the next five
 * hours, sun, UV and moon) and the air on the right (index, a 36-hour PM2.5 trend, CPCB advice
 * and the cleanest hour to open the windows). Everything comes from the cached weather; opening
 * it makes no network request.
 */
final class TodayCard {

    static final int WIDTH = 700;

    private static final int LABEL = 0xFFF5F5F7;
    private static final int SECONDARY = 0x99EBEBF5;
    private static final int TERTIARY = 0x4DEBEBF5;
    private static final int HAIRLINE = 0x1FFFFFFF;
    private static final int RAIN = 0xFF64D2FF;

    private final Context context;
    private final float density;

    private TodayCard(Context context) {
        this.context = context;
        this.density = context.getResources().getDisplayMetrics().density;
    }

    static View build(Context context, Weather w) {
        return new TodayCard(context).card(w);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private TextView text(float sp, int weight, int color) {
        TextView t = new TextView(context);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, weight, false));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    private TextView text(float sp, int weight, int color, String s) {
        TextView t = text(sp, weight, color);
        t.setText(s);
        return t;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrap(int top) {
        LinearLayout.LayoutParams lp = wrap();
        lp.topMargin = dp(top);
        return lp;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout column() {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        return col;
    }

    private View hairline() {
        View v = new View(context);
        v.setBackgroundColor(HAIRLINE);
        return v;
    }

    // ---- layout -----------------------------------------------------------------------------

    private View card(Weather w) {
        LinearLayout card = column();
        card.setPadding(dp(26), dp(22), dp(26), dp(20));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF26272E, 0xFF1A1B20});
        bg.setCornerRadius(dp(22));
        bg.setStroke(Math.max(1, dp(0.75f)), HAIRLINE);
        card.setBackground(bg);
        card.setElevation(dp(24));

        LinearLayout title = row();
        title.setGravity(Gravity.BOTTOM);
        title.addView(text(17, 600, LABEL, "Today in " + w.place.name));
        View spacer = new View(context);
        title.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        if (w.hasWeather()) {
            title.addView(text(11, 400, TERTIARY, "Updated " + Weather.clockAt(Math.max(w.weatherAt, w.airAt))));
        }
        card.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout columns = row();
        LinearLayout.LayoutParams columnsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        columnsLp.topMargin = dp(18);
        card.addView(columns, columnsLp);

        columns.addView(sky(w), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(Math.max(1, dp(0.75f)), ViewGroup.LayoutParams.MATCH_PARENT);
        dividerLp.leftMargin = dp(24);
        dividerLp.rightMargin = dp(24);
        columns.addView(hairline(), dividerLp);
        columns.addView(air(w), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        List<Festival> coming = Festival.upcoming(Festival.today(), 4);
        if (!coming.isEmpty()) {
            LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.75f)));
            ruleLp.topMargin = dp(20);
            card.addView(hairline(), ruleLp);
            LinearLayout.LayoutParams festLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            festLp.topMargin = dp(16);
            card.addView(festivals(coming), festLp);
        }

        TextView footer = text(9.5f, 400, TERTIARY, "Open-Meteo · Copernicus CAMS · Back to close");
        LinearLayout.LayoutParams footerLp = wrap(18);
        footerLp.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(footer, footerLp);
        return card;
    }

    /** Now, the next five hours, then sunrise, sunset, daylight, UV and the moon. */
    private View sky(Weather w) {
        LinearLayout col = column();
        if (!w.hasWeather()) {
            col.addView(text(13, 400, SECONDARY, "No weather yet"));
            return col;
        }

        LinearLayout hero = row();
        hero.setGravity(Gravity.CENTER_VERTICAL);
        WeatherGlyph glyph = new WeatherGlyph(context);
        glyph.setGlyph(w.glyph());
        hero.addView(glyph, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView temp = text(44, 300, LABEL, Weather.degrees(w.temp));
        temp.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams tempLp = wrap();
        tempLp.leftMargin = dp(12);
        hero.addView(temp, tempLp);
        LinearLayout lines = column();
        lines.addView(text(15, 500, LABEL, w.condition()));
        StringBuilder range = new StringBuilder();
        if (!Double.isNaN(w.high)) range.append("H ").append(Weather.degrees(w.high)).append("  L ").append(Weather.degrees(w.low)).append("  ·  ");
        range.append("Feels ").append(Weather.degrees(w.feels));
        lines.addView(text(12.5f, 400, SECONDARY, range.toString()), wrap(4));
        LinearLayout.LayoutParams linesLp = wrap();
        linesLp.leftMargin = dp(14);
        hero.addView(lines, linesLp);
        col.addView(hero);

        String rain = w.rainSoon();
        if (rain != null) col.addView(text(12.5f, 500, RAIN, rain), wrap(10));

        if (w.hasForecast()) col.addView(hours(w), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(facts(w), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return col;
    }

    private View hours(Weather w) {
        LinearLayout strip = row();
        strip.setPadding(0, dp(18), 0, 0);
        int now = Weather.indexAt(w.hourTime, Weather.isoNow());
        if (now < 0) return strip;
        for (int i = now; i < Math.min(w.hourTime.length, now + 6); i++) {
            LinearLayout cell = column();
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            String when = i == now ? "Now" : Weather.clock(w.hourTime[i].substring(11, 16), false);
            cell.addView(text(11, i == now ? 600 : 400, i == now ? LABEL : SECONDARY, when));
            WeatherGlyph g = new WeatherGlyph(context);
            g.setGlyph(w.hourGlyph(i));
            LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(dp(20), dp(20));
            gLp.topMargin = dp(8);
            cell.addView(g, gLp);
            TextView t = text(14, 500, LABEL, Double.isNaN(w.hourTemp[i]) ? "–" : Weather.degrees(w.hourTemp[i]));
            t.setFontFeatureSettings("tnum");
            cell.addView(t, wrap(8));
            // Rain chances below 20% are noise; the row keeps its height either way.
            TextView pop = text(10.5f, 500, RAIN, w.hourRain[i] + "%");
            pop.setVisibility(w.hourRain[i] >= 20 ? View.VISIBLE : View.INVISIBLE);
            cell.addView(pop, wrap(4));
            strip.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        return strip;
    }

    private View facts(Weather w) {
        LinearLayout grid = column();
        grid.setPadding(0, dp(16), 0, 0);
        LinearLayout top = row();
        if (w.sunrise != null) top.addView(fact("Sunrise", Weather.clock(w.sunrise, true), null), cellLp());
        if (w.sunset != null) top.addView(fact("Sunset", Weather.clock(w.sunset, true), null), cellLp());
        String daylight = w.daylight();
        if (daylight != null) top.addView(fact("Daylight", daylight, null), cellLp());
        grid.addView(top);

        LinearLayout bottom = row();
        bottom.setPadding(0, dp(12), 0, 0);
        if (!Double.isNaN(w.uvMax)) bottom.addView(fact("UV peak", Weather.uvLabel(w.uvMax), null), cellLp());
        double age = Weather.moonAge(System.currentTimeMillis());
        MoonGlyph moon = new MoonGlyph(context, age);
        LinearLayout.LayoutParams moonCellLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2);
        bottom.addView(fact("Moon", Weather.moonName(age), moon), moonCellLp);
        grid.addView(bottom);
        return grid;
    }

    /** The next four festivals; anything within a week is picked out in marigold. */
    private View festivals(List<Festival> coming) {
        LinearLayout col = column();
        TextView heading = text(9.5f, 600, TERTIARY, "COMING UP");
        heading.setLetterSpacing(0.06f);
        col.addView(heading);
        LinearLayout row = row();
        LocalDate today = Festival.today();
        for (Festival f : coming) {
            LinearLayout cell = column();
            boolean soon = f.daysFrom(today) < 7;
            cell.addView(text(13, 500, soon ? HomeActivity.FESTIVE : LABEL, f.name));
            long days = f.daysFrom(today);
            String when = days == 0 ? "Today" : days == 1 ? "Tomorrow" : "In " + days + " days";
            cell.addView(text(11, 400, SECONDARY, f.shortDate() + "  ·  " + when), wrap(5));
            row.addView(cell, cellLp());
        }
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(8);
        col.addView(row, rowLp);
        return col;
    }

    private static LinearLayout.LayoutParams cellLp() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private View fact(String label, String value, View icon) {
        LinearLayout col = column();
        TextView l = text(9.5f, 600, TERTIARY, label.toUpperCase());
        l.setLetterSpacing(0.06f);
        col.addView(l);
        LinearLayout valueRow = row();
        valueRow.setGravity(Gravity.CENTER_VERTICAL);
        if (icon != null) {
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(13), dp(13));
            iconLp.rightMargin = dp(6);
            valueRow.addView(icon, iconLp);
        }
        valueRow.addView(text(13, 500, LABEL, value));
        col.addView(valueRow, wrap(5));
        return col;
    }

    /** Index and category, the PM2.5 trend, CPCB's advice and the cleanest hour ahead. */
    private View air(Weather w) {
        LinearLayout col = column();
        if (!w.hasAir()) {
            col.addView(text(13, 400, SECONDARY, "No air quality yet"));
            return col;
        }

        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(context);
        GradientDrawable dotShape = new GradientDrawable();
        dotShape.setShape(GradientDrawable.OVAL);
        dotShape.setColor(w.aqiColor());
        dot.setBackground(dotShape);
        head.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        TextView index = text(26, 400, LABEL, "AQI " + w.aqi);
        index.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams indexLp = wrap();
        indexLp.leftMargin = dp(10);
        head.addView(index, indexLp);
        LinearLayout.LayoutParams catLp = wrap();
        catLp.leftMargin = dp(10);
        head.addView(text(15, 600, w.aqiColor(), w.aqiCategory()), catLp);
        col.addView(head);

        col.addView(text(11.5f, 400, SECONDARY,
                "24-hour average  ·  PM2.5 " + Math.round(w.pm25) + "  ·  PM10 " + Math.round(w.pm10) + " µg/m³"), wrap(8));

        if (w.airTime.length > 0) {
            LinearLayout.LayoutParams trendLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92));
            trendLp.topMargin = dp(14);
            col.addView(new AirTrend(context, w), trendLp);
        }

        TextView advice = text(12, 400, SECONDARY, w.aqiAdvice());
        advice.setSingleLine(false);
        advice.setLineSpacing(dp(2), 1f);
        col.addView(advice, wrap(12));

        String[] clean = w.cleanestHour();
        if (clean != null) {
            // Above 90 µg/m³ even the best hour is unhealthy, so there is nothing to recommend.
            String line = Integer.parseInt(clean[1]) > 90
                    ? "No clean-air window in the next 18 hours"
                    : "Best time to air out: " + (clean[0].equals("Now") ? "now" : clean[0]) + "  ·  PM2.5 " + clean[1];
            col.addView(text(12, 500, LABEL, line), wrap(10));
        }
        return col;
    }

    // ---- drawings ---------------------------------------------------------------------------

    /**
     * Hourly PM2.5 from 24 hours ago to 12 hours ahead. Each stretch is coloured by its CPCB
     * category, the forecast is dashed, and a dotted line marks the 24-hour safe limit (60 in India, 35 elsewhere).
     */
    private static final class AirTrend extends View {

        private final double[] values;
        private final int now;
        private final int count;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private final Weather weather;
        private final double limit;

        AirTrend(Context context, Weather w) {
            super(context);
            weather = w;
            limit = w.pm25Limit();
            d = context.getResources().getDisplayMetrics().density;
            int nowIndex = Weather.indexAt(w.airTime, Weather.isoNow());
            int from = Math.max(0, nowIndex - 24);
            int to = Math.min(w.airTime.length - 1, nowIndex + 12);
            count = Math.max(0, to - from + 1);
            values = new double[count];
            System.arraycopy(w.airPm25, from, values, 0, count);
            now = nowIndex - from;

            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2 * d);
            line.setStrokeCap(Paint.Cap.ROUND);
            guide.setStyle(Paint.Style.STROKE);
            guide.setStrokeWidth(Math.max(1, 0.75f * d));
            guide.setColor(0x33FFFFFF);
            guide.setPathEffect(new DashPathEffect(new float[]{2 * d, 3 * d}, 0));
            label.setTextSize(9.5f * context.getResources().getDisplayMetrics().scaledDensity);
            label.setColor(TERTIARY);
        }

        @Override
        protected void onDraw(Canvas c) {
            if (count < 2 || now < 0) return;
            float labelH = 14 * d;
            float top = 4 * d;
            float bottom = getHeight() - labelH;
            float w = getWidth();
            double max = limit * 1.2;
            for (double v : values) if (!Double.isNaN(v)) max = Math.max(max, v * 1.12);
            float step = w / (count - 1);

            float limitY = y(limit, max, top, bottom);
            c.drawLine(0, limitY, w, limitY, guide);
            label.setTextAlign(Paint.Align.RIGHT);
            c.drawText("Safe limit " + Math.round(limit), w, limitY + 11 * d, label);

            // A soft wash under the past readings, in today's colour.
            Path area = new Path();
            boolean started = false;
            float lastX = 0;
            for (int i = 0; i <= now; i++) {
                if (Double.isNaN(values[i])) continue;
                float x = i * step;
                float yv = y(values[i], max, top, bottom);
                if (!started) {
                    area.moveTo(x, bottom);
                    area.lineTo(x, yv);
                    started = true;
                } else {
                    area.lineTo(x, yv);
                }
                lastX = x;
            }
            if (started) {
                area.lineTo(lastX, bottom);
                area.close();
                int tint = weather.pm25Color(Double.isNaN(values[now]) ? limit : values[now]);
                fill.setShader(new LinearGradient(0, top, 0, bottom,
                        (0x38 << 24) | (tint & 0xFFFFFF), tint & 0xFFFFFF, Shader.TileMode.CLAMP));
                c.drawPath(area, fill);
            }

            DashPathEffect dashes = new DashPathEffect(new float[]{3 * d, 4 * d}, 0);
            for (int i = 0; i + 1 < count; i++) {
                if (Double.isNaN(values[i]) || Double.isNaN(values[i + 1])) continue;
                boolean future = i >= now;
                line.setColor(weather.pm25Color((values[i] + values[i + 1]) / 2));
                line.setAlpha(future ? 150 : 255);
                line.setPathEffect(future ? dashes : null);
                c.drawLine(i * step, y(values[i], max, top, bottom),
                        (i + 1) * step, y(values[i + 1], max, top, bottom), line);
            }

            float nowX = now * step;
            c.drawLine(nowX, top, nowX, bottom, guide);
            if (!Double.isNaN(values[now])) {
                float nowY = y(values[now], max, top, bottom);
                dot.setColor(0xFF1C1D22);
                c.drawCircle(nowX, nowY, 5 * d, dot);
                dot.setColor(weather.pm25Color(values[now]));
                c.drawCircle(nowX, nowY, 3.5f * d, dot);
            }

            float baseline = getHeight() - 2 * d;
            label.setTextAlign(Paint.Align.LEFT);
            c.drawText("24h ago", 0, baseline, label);
            label.setTextAlign(Paint.Align.CENTER);
            c.drawText("Now", nowX, baseline, label);
            if (count - 1 > now + 3) {
                label.setTextAlign(Paint.Align.RIGHT);
                c.drawText("+" + (count - 1 - now) + "h", w, baseline, label);
            }
        }

        private static float y(double v, double max, float top, float bottom) {
            return (float) (bottom - (bottom - top) * Math.min(v, max) / max);
        }
    }

    /** The moon as it looks tonight from the northern hemisphere: lit on the right while waxing. */
    private static final class MoonGlyph extends View {
        private final double age;
        private final Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);

        MoonGlyph(Context context, double age) {
            super(context);
            this.age = age;
            dark.setColor(0x33FFFFFF);
            lit.setColor(0xFFF2EBD9);
        }

        @Override
        protected void onDraw(Canvas c) {
            float r = Math.min(getWidth(), getHeight()) / 2f;
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            c.drawCircle(cx, cy, r, dark);

            boolean waxing = age < 0.5;
            double k = Math.cos(2 * Math.PI * age);
            float side = waxing ? 1 : -1;
            Path p = new Path();
            p.moveTo(cx, cy - r);
            // The lit limb, top to bottom...
            p.arcTo(new RectF(cx - r, cy - r, cx + r, cy + r), -90, waxing ? 180 : -180);
            // ...then back up along the terminator, an ellipse whose width follows the phase.
            for (int i = 0; i <= 24; i++) {
                double t = Math.PI / 2 - Math.PI * i / 24;
                p.lineTo(cx + side * (float) (r * k * Math.cos(t)), cy + (float) (r * Math.sin(t)));
            }
            p.close();
            c.drawPath(p, lit);
        }
    }
}
