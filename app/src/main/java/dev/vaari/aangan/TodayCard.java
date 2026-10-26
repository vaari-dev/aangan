package dev.vaari.aangan;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

final class TodayCard {

    static final int WIDTH = 700;

    private static final int[] SURFACE = {0xFF26272E, 0xFF1A1B20};
    private static final int STRIP_HOURS = 6;
    private static final int RAIN_SHOWN = 20;
    private static final int COMING = 4;

    private final Context context;
    private final Ui ui;

    private TodayCard(Context context) {
        this.context = context;
        this.ui = new Ui(context);
    }

    static View build(Context context, Weather w) {
        return new TodayCard(context).card(w);
    }

    private int dp(float v) {
        return ui.dp(v);
    }

    private TextView text(float sp, int weight, int color, String s) {
        return ui.line(sp, weight, color, s);
    }

    private View hairline() {
        View v = new View(context);
        v.setBackgroundColor(Ui.HAIRLINE);
        return v;
    }

    private TextView caps(String s) {
        TextView t = text(9.5f, 600, Ui.TERTIARY, s.toUpperCase(Locale.getDefault()));
        t.setLetterSpacing(0.06f);
        return t;
    }

    private LinearLayout.LayoutParams leftOf(float left) {
        LinearLayout.LayoutParams lp = Ui.wrap();
        lp.leftMargin = dp(left);
        return lp;
    }

    private View card(Weather w) {
        Resources r = context.getResources();
        LinearLayout card = ui.column();
        card.setPadding(dp(26), dp(22), dp(26), dp(20));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, SURFACE);
        bg.setCornerRadius(dp(22));
        bg.setStroke(ui.hairline(), Ui.HAIRLINE);
        card.setBackground(bg);
        card.setElevation(dp(24));

        LinearLayout title = ui.row();
        title.setGravity(Gravity.BOTTOM);
        title.addView(text(17, 600, Ui.LABEL, context.getString(R.string.today_in, w.place.name)));
        title.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1));
        if (w.hasWeather()) {
            title.addView(text(11, 400, Ui.TERTIARY,
                    context.getString(R.string.updated_at, Weather.clockAt(r, Math.max(w.weatherAt, w.airAt)))));
        }
        card.addView(title, Ui.fill());

        LinearLayout columns = ui.row();
        card.addView(columns, ui.fill(18));

        columns.addView(sky(w), Ui.share(1));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(ui.hairline(), ViewGroup.LayoutParams.MATCH_PARENT);
        dividerLp.leftMargin = dp(24);
        dividerLp.rightMargin = dp(24);
        columns.addView(hairline(), dividerLp);
        columns.addView(air(w), Ui.share(1));

        List<Festival> coming = Festival.upcoming(Festival.today(), COMING);
        if (!coming.isEmpty()) {
            LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.hairline());
            ruleLp.topMargin = dp(20);
            card.addView(hairline(), ruleLp);
            card.addView(festivals(coming), ui.fill(16));
        }

        TextView footer = text(9.5f, 400, Ui.TERTIARY, context.getString(R.string.today_footer));
        LinearLayout.LayoutParams footerLp = ui.wrap(18);
        footerLp.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(footer, footerLp);
        return card;
    }

    private View sky(Weather w) {
        LinearLayout col = ui.column();
        if (!w.hasWeather()) {
            col.addView(text(13, 400, Ui.SECONDARY, context.getString(R.string.no_weather)));
            return col;
        }

        LinearLayout hero = ui.row();
        hero.setGravity(Gravity.CENTER_VERTICAL);
        WeatherGlyph glyph = new WeatherGlyph(context);
        glyph.setGlyph(w.glyph());
        hero.addView(glyph, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView temp = text(44, 300, Ui.LABEL, Weather.degrees(w.temp));
        temp.setFontFeatureSettings("tnum");
        hero.addView(temp, leftOf(12));
        LinearLayout lines = ui.column();
        lines.addView(text(15, 500, Ui.LABEL, w.condition(context.getResources())));
        StringBuilder range = new StringBuilder();
        if (!Double.isNaN(w.high)) {
            range.append(context.getString(R.string.high_low, Weather.degrees(w.high), Weather.degrees(w.low))).append("  ·  ");
        }
        range.append(context.getString(R.string.feels_like, Weather.degrees(w.feels)));
        lines.addView(text(12.5f, 400, Ui.SECONDARY, range.toString()), ui.wrap(4));
        hero.addView(lines, leftOf(14));
        col.addView(hero);

        String rain = w.rainSoon(context.getResources());
        if (rain != null) col.addView(text(12.5f, 500, Ui.RAIN, rain), ui.wrap(10));

        if (w.hasForecast()) col.addView(hours(w), Ui.fill());
        col.addView(facts(w), Ui.fill());
        return col;
    }

    private View hours(Weather w) {
        LinearLayout strip = ui.row();
        strip.setPadding(0, dp(18), 0, 0);
        int now = Weather.indexAt(w.hourTime, Weather.isoNow());
        if (now < 0) return strip;
        for (int i = now; i < Math.min(w.hourTime.length, now + STRIP_HOURS); i++) {
            LinearLayout cell = ui.column();
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            String when = i == now ? context.getString(R.string.now) : Weather.hour(context.getResources(), w.hourTime[i]);
            cell.addView(text(11, i == now ? 600 : 400, i == now ? Ui.LABEL : Ui.SECONDARY, when));
            WeatherGlyph g = new WeatherGlyph(context);
            g.setGlyph(w.hourGlyph(i));
            LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(dp(20), dp(20));
            gLp.topMargin = dp(8);
            cell.addView(g, gLp);
            TextView t = text(14, 500, Ui.LABEL, Double.isNaN(w.hourTemp[i]) ? "–" : Weather.degrees(w.hourTemp[i]));
            t.setFontFeatureSettings("tnum");
            cell.addView(t, ui.wrap(8));
            // Small rain chances are hidden, but the row keeps its height either way.
            TextView pop = text(10.5f, 500, Ui.RAIN, w.hourRain[i] + "%");
            pop.setVisibility(w.hourRain[i] >= RAIN_SHOWN ? View.VISIBLE : View.INVISIBLE);
            cell.addView(pop, ui.wrap(4));
            strip.addView(cell, Ui.share(1));
        }
        return strip;
    }

    private View facts(Weather w) {
        Resources r = context.getResources();
        LinearLayout grid = ui.column();
        grid.setPadding(0, dp(16), 0, 0);
        LinearLayout top = ui.row();
        if (w.sunrise != null) top.addView(fact(r.getString(R.string.sunrise), Weather.clock(r, w.sunrise, true), null), Ui.share(1));
        if (w.sunset != null) top.addView(fact(r.getString(R.string.sunset), Weather.clock(r, w.sunset, true), null), Ui.share(1));
        String daylight = w.daylight(r);
        if (daylight != null) top.addView(fact(r.getString(R.string.daylight_label), daylight, null), Ui.share(1));
        grid.addView(top);

        LinearLayout bottom = ui.row();
        bottom.setPadding(0, dp(12), 0, 0);
        if (!Double.isNaN(w.uvMax)) bottom.addView(fact(r.getString(R.string.uv_peak), Weather.uvLabel(r, w.uvMax), null), Ui.share(1));
        double age = Weather.moonAge(Dates.nowMillis());
        bottom.addView(fact(r.getString(R.string.moon), Weather.moonName(r, age), new MoonGlyph(context, age)), Ui.share(2));
        grid.addView(bottom);
        return grid;
    }

    private View festivals(List<Festival> coming) {
        LinearLayout col = ui.column();
        col.addView(caps(context.getString(R.string.coming_up)));
        LinearLayout row = ui.row();
        LocalDate today = Festival.today();
        for (Festival f : coming) {
            LinearLayout cell = ui.column();
            long days = f.daysFrom(today);
            cell.addView(text(13, 500, Dates.soon(days) ? HomeActivity.FESTIVE : Ui.LABEL, f.title(context)));
            cell.addView(text(11, 400, Ui.SECONDARY, f.shortDate(context.getResources()) + "  ·  "
                    + Dates.until(context.getResources(), days, true)), ui.wrap(5));
            row.addView(cell, Ui.share(1));
        }
        col.addView(row, ui.fill(8));
        return col;
    }

    private View fact(String label, String value, View icon) {
        LinearLayout col = ui.column();
        col.addView(caps(label));
        LinearLayout valueRow = ui.row();
        valueRow.setGravity(Gravity.CENTER_VERTICAL);
        if (icon != null) {
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(13), dp(13));
            iconLp.rightMargin = dp(6);
            valueRow.addView(icon, iconLp);
        }
        valueRow.addView(text(13, 500, Ui.LABEL, value));
        col.addView(valueRow, ui.wrap(5));
        return col;
    }

    private View air(Weather w) {
        Resources r = context.getResources();
        LinearLayout col = ui.column();
        if (!w.hasAir()) {
            col.addView(text(13, 400, Ui.SECONDARY, context.getString(R.string.no_air)));
            return col;
        }

        LinearLayout head = ui.row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(context);
        dot.setBackground(Ui.dot(w.aqiColor()));
        head.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        TextView index = text(26, 400, Ui.LABEL, r.getString(R.string.aqi_value, w.aqi));
        index.setFontFeatureSettings("tnum");
        head.addView(index, leftOf(10));
        head.addView(text(15, 600, w.aqiColor(), w.aqiCategory(r)), leftOf(10));
        col.addView(head);

        col.addView(text(11.5f, 400, Ui.SECONDARY,
                r.getString(R.string.air_average, Math.round(w.pm25), Math.round(w.pm10))), ui.wrap(8));

        if (w.hasAirSeries()) {
            LinearLayout.LayoutParams trendLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92));
            trendLp.topMargin = dp(14);
            col.addView(new AirTrend(context, w), trendLp);
        }

        TextView advice = text(12, 400, Ui.SECONDARY, w.aqiAdvice(r));
        advice.setSingleLine(false);
        advice.setLineSpacing(dp(2), 1f);
        col.addView(advice, ui.wrap(12));

        int clean = w.cleanestHour();
        if (clean >= 0) {
            long pm = Math.round(w.airPm25[clean]);
            String line = pm > Weather.HEAVY_PM25
                    ? r.getQuantityString(R.plurals.no_clean_air, Weather.CLEAN_AIR_HOURS, Weather.CLEAN_AIR_HOURS)
                    : r.getString(R.string.air_out, w.airHour(r, clean), pm);
            col.addView(text(12, 500, Ui.LABEL, line), ui.wrap(10));
        }
        return col;
    }

    private static final class AirTrend extends View {

        private final double[] values;
        private final int now;
        private final int count;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;
        private final Weather weather;
        private final double limit;
        private final String limitLabel;
        private final String agoLabel;
        private final String nowLabel;
        private final String aheadLabel;

        AirTrend(Context context, Weather w) {
            super(context);
            weather = w;
            limit = w.pm25Limit();
            density = context.getResources().getDisplayMetrics().density;
            int nowIndex = Weather.indexAt(w.airTime, Weather.isoNow());
            int from = Math.max(0, nowIndex - 24);
            int to = Math.min(w.airTime.length - 1, nowIndex + 12);
            count = Math.max(0, to - from + 1);
            values = new double[count];
            System.arraycopy(w.airPm25, from, values, 0, count);
            now = nowIndex - from;
            limitLabel = context.getString(R.string.safe_limit, Math.round(limit));
            agoLabel = context.getString(R.string.hours_ago_24);
            nowLabel = context.getString(R.string.now);
            aheadLabel = context.getString(R.string.hours_ahead, count - 1 - now);

            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2 * density);
            line.setStrokeCap(Paint.Cap.ROUND);
            guide.setStyle(Paint.Style.STROKE);
            guide.setStrokeWidth(Math.max(1, 0.75f * density));
            guide.setColor(0x33FFFFFF);
            guide.setPathEffect(new DashPathEffect(new float[]{2 * density, 3 * density}, 0));
            label.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9.5f, context.getResources().getDisplayMetrics()));
            label.setColor(Ui.TERTIARY);
        }

        @Override
        protected void onDraw(Canvas c) {
            if (count < 2 || now < 0) return;
            float labelH = 14 * density;
            float top = 4 * density;
            float bottom = getHeight() - labelH;
            float w = getWidth();
            double max = limit * 1.2;
            for (double v : values) if (!Double.isNaN(v)) max = Math.max(max, v * 1.12);
            float step = w / (count - 1);

            float limitY = y(limit, max, top, bottom);
            c.drawLine(0, limitY, w, limitY, guide);
            label.setTextAlign(Paint.Align.RIGHT);
            c.drawText(limitLabel, w, limitY + 11 * density, label);

            Path area = new Path();
            boolean started = false;
            float lastX = 0;
            for (int i = 0; i <= now; i++) {
                if (Double.isNaN(values[i])) continue;
                lastX = i * step;
                if (!started) area.moveTo(lastX, bottom);
                started = true;
                area.lineTo(lastX, y(values[i], max, top, bottom));
            }
            if (started) {
                area.lineTo(lastX, bottom);
                area.close();
                int tint = weather.pm25Color(Double.isNaN(values[now]) ? limit : values[now]);
                fill.setShader(new LinearGradient(0, top, 0, bottom,
                        Colors.alpha(tint, 0x38 / 255f), tint & 0xFFFFFF, Shader.TileMode.CLAMP));
                c.drawPath(area, fill);
            }

            DashPathEffect dashes = new DashPathEffect(new float[]{3 * density, 4 * density}, 0);
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
                c.drawCircle(nowX, nowY, 5 * density, dot);
                dot.setColor(weather.pm25Color(values[now]));
                c.drawCircle(nowX, nowY, 3.5f * density, dot);
            }

            float baseline = getHeight() - 2 * density;
            label.setTextAlign(Paint.Align.LEFT);
            c.drawText(agoLabel, 0, baseline, label);
            label.setTextAlign(Paint.Align.CENTER);
            c.drawText(nowLabel, nowX, baseline, label);
            if (count - 1 > now + 3) {
                label.setTextAlign(Paint.Align.RIGHT);
                c.drawText(aheadLabel, w, baseline, label);
            }
        }

        private static float y(double v, double max, float top, float bottom) {
            return (float) (bottom - (bottom - top) * Math.min(v, max) / max);
        }
    }

    /** As seen from the northern hemisphere: lit on the right while waxing. */
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
            // The lit limb, then back up the terminator: an ellipse whose width follows the phase.
            p.arcTo(new RectF(cx - r, cy - r, cx + r, cy + r), -90, waxing ? 180 : -180);
            for (int i = 0; i <= 24; i++) {
                double t = Math.PI / 2 - Math.PI * i / 24;
                p.lineTo(cx + side * (float) (r * k * Math.cos(t)), cy + (float) (r * Math.sin(t)));
            }
            p.close();
            c.drawPath(p, lit);
        }
    }
}
