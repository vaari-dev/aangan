package dev.vaari.aangan;

import android.content.SharedPreferences;
import android.content.res.Resources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Weather, a short forecast and air quality for the chosen city, from Open-Meteo.
 * No key, no account, no device identifiers: each request carries only the city's coordinates.
 * The raw responses are cached, so the forecast card works offline from the last fetch.
 */
final class Weather {

    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
            + "&current=temperature_2m,apparent_temperature,weather_code,is_day"
            + "&hourly=temperature_2m,weather_code,precipitation_probability,is_day"
            + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max"
            + "&forecast_days=2&timezone=%s";
    private static final String AIR_URL = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.2f&longitude=%.2f"
            + "&current=pm2_5,pm10&hourly=pm2_5,pm10&past_days=1&forecast_days=2&timezone=%s";

    static final long WEATHER_TTL = 30 * 60_000L;
    static final long AIR_TTL = 60 * 60_000L;
    private static final long STALE_AFTER = 3 * 60 * 60_000L;

    /** Minutes either side of sunrise and sunset that count as golden hour. */
    private static final int GOLDEN_MIN = 45;
    /** PM2.5 (µg/m³) above which the air is heavy: clear skies read as haze, and no hour is worth airing out. */
    static final int HEAVY_PM25 = 90;
    /** How far ahead, in hours, to look for the cleanest hour to open the windows. */
    static final int CLEAN_AIR_HOURS = 18;
    /** How far ahead, in hours after the current one, a likely shower is worth mentioning. */
    private static final int RAIN_HOURS = 6;
    /** A chance of rain, in percent, from which it is called likely. */
    private static final int RAIN_LIKELY = 50;

    /**
     * An air-quality index: the index range for each category, the 24-hour PM2.5 and PM10
     * concentrations (µg/m³) at the same breakpoints, and how each category is described.
     */
    private static final class Scale {
        final double[] aqi;
        final double[] pm25;
        final double[] pm10;
        /** String resources for each category's name and advice. */
        final int[] names;
        final int[] colors;
        final int[] advice;
        /** The 24-hour PM2.5 standard, drawn on the trend chart. */
        final double limit;

        Scale(double[] aqi, double[] pm25, double[] pm10, int[] names, int[] colors, int[] advice, double limit) {
            this.aqi = aqi;
            this.pm25 = pm25;
            this.pm10 = pm10;
            this.names = names;
            this.colors = colors;
            this.advice = advice;
            this.limit = limit;
        }
    }

    // Category colours, softened for a dark background.
    private static final int[] COLORS = {0xFF30D158, 0xFFA4D65E, 0xFFFFD60A, 0xFFFF9F0A, 0xFFFF453A, 0xFFC0394B};

    /** India's National AQI (CPCB), with its health statements shortened. */
    private static final Scale CPCB = new Scale(
            new double[]{0, 50, 100, 200, 300, 400, 500},
            new double[]{0, 30, 60, 90, 120, 250, 380},
            new double[]{0, 50, 100, 250, 350, 430, 510},
            new int[]{R.string.cpcb_good, R.string.cpcb_satisfactory, R.string.cpcb_moderate, R.string.cpcb_poor,
                    R.string.cpcb_very_poor, R.string.cpcb_severe}, COLORS,
            new int[]{R.string.cpcb_good_advice, R.string.cpcb_satisfactory_advice, R.string.cpcb_moderate_advice,
                    R.string.cpcb_poor_advice, R.string.cpcb_very_poor_advice, R.string.cpcb_severe_advice}, 60);

    /** The US EPA AQI (2024 PM2.5 breakpoints), used everywhere else. */
    private static final Scale EPA = new Scale(
            new double[]{0, 50, 100, 150, 200, 300, 500},
            new double[]{0, 9, 35.4, 55.4, 125.4, 225.4, 325.4},
            new double[]{0, 54, 154, 254, 354, 424, 604},
            new int[]{R.string.epa_good, R.string.epa_moderate, R.string.epa_sensitive, R.string.epa_unhealthy,
                    R.string.epa_very_unhealthy, R.string.epa_hazardous}, COLORS,
            new int[]{R.string.epa_good_advice, R.string.epa_moderate_advice, R.string.epa_sensitive_advice,
                    R.string.epa_unhealthy_advice, R.string.epa_very_unhealthy_advice, R.string.epa_hazardous_advice}, 35);

    /** The chosen city's time zone, which every forecast time is in. */
    private static TimeZone zone = TimeZone.getDefault();

    Place place = Place.DEFAULT;
    private Scale scale = CPCB;

    enum Glyph { SUN, MOON, PARTLY_DAY, PARTLY_NIGHT, CLOUD, FOG, DRIZZLE, RAIN, SNOW, THUNDER }

    double temp;
    double feels;
    int code = -1;
    boolean day = true;
    long weatherAt;
    private String weatherJson;

    // Hourly forecast from midnight today, and today's extremes.
    String[] hourTime = new String[0];
    double[] hourTemp = new double[0];
    int[] hourCode = new int[0];
    int[] hourRain = new int[0];
    boolean[] hourDay = new boolean[0];
    double high = Double.NaN;
    double low = Double.NaN;
    double uvMax = Double.NaN;
    /** Today's sunrise and sunset as "HH:mm", or null. */
    String sunrise;
    String sunset;

    double pm25;
    double pm10;
    int aqi = -1;
    long airAt;
    private String airJson;

    // Hourly PM2.5 from midnight yesterday to midnight tomorrow; NaN where missing.
    String[] airTime = new String[0];
    double[] airPm25 = new double[0];

    // ---- cache ------------------------------------------------------------------------------

    static Weather load(SharedPreferences prefs) {
        Weather w = new Weather();
        w.place = Place.load(prefs);
        w.scale = "IN".equals(w.place.country) ? CPCB : EPA;
        zone = TimeZone.getTimeZone(w.place.timezone);
        w.temp = Double.longBitsToDouble(prefs.getLong("w_temp", 0));
        w.feels = Double.longBitsToDouble(prefs.getLong("w_feels", 0));
        w.code = prefs.getInt("w_code", -1);
        w.day = prefs.getBoolean("w_day", true);
        w.weatherAt = prefs.getLong("w_at", 0);
        w.pm25 = Double.longBitsToDouble(prefs.getLong("a_pm25", 0));
        w.pm10 = Double.longBitsToDouble(prefs.getLong("a_pm10", 0));
        w.aqi = prefs.getInt("a_aqi", -1);
        w.airAt = prefs.getLong("a_at", 0);
        try {
            String json = prefs.getString("w_json", null);
            if (json != null) w.parseForecast(json);
            json = prefs.getString("a_json", null);
            if (json != null) w.parseAirSeries(json);
        } catch (JSONException ignored) {
            // An unreadable cache just means no forecast until the next fetch.
        }
        return w;
    }

    void save(SharedPreferences prefs) {
        prefs.edit()
                .putLong("w_temp", Double.doubleToLongBits(temp))
                .putLong("w_feels", Double.doubleToLongBits(feels))
                .putInt("w_code", code)
                .putBoolean("w_day", day)
                .putLong("w_at", weatherAt)
                .putString("w_json", weatherJson)
                .putLong("a_pm25", Double.doubleToLongBits(pm25))
                .putLong("a_pm10", Double.doubleToLongBits(pm10))
                .putInt("a_aqi", aqi)
                .putLong("a_at", airAt)
                .putString("a_json", airJson)
                .apply();
    }

    /** Forgets everything fetched for the old city, so the next check fetches for the new one. */
    static void clear(SharedPreferences prefs) {
        prefs.edit().remove("w_at").remove("w_json").remove("a_at").remove("a_json").remove("a_aqi").apply();
    }

    boolean hasWeather() {
        return weatherAt > 0;
    }

    boolean hasAir() {
        return airAt > 0 && aqi >= 0;
    }

    boolean hasForecast() {
        return hourTime.length > 0;
    }

    boolean hasAirSeries() {
        return airTime.length > 0;
    }

    boolean isStale(long now) {
        return now - weatherAt > STALE_AFTER;
    }

    // ---- network ----------------------------------------------------------------------------

    // Downloads block, so they run off the main thread. The main thread reads this object while it
    // draws, so it takes the answers in itself, with readWeather and readAir.

    String downloadWeather() throws IOException {
        return get(url(FORECAST_URL));
    }

    /** Takes in a forecast response, fetched at {@code now}. */
    void readWeather(String body, long now) throws JSONException {
        JSONObject current = new JSONObject(body).getJSONObject("current");
        temp = current.getDouble("temperature_2m");
        feels = current.getDouble("apparent_temperature");
        code = current.getInt("weather_code");
        day = current.getInt("is_day") == 1;
        parseForecast(body);
        weatherJson = body;
        weatherAt = now;
    }

    String downloadAir() throws IOException {
        return get(url(AIR_URL));
    }

    /**
     * Takes in an air-quality response, fetched at {@code now}. Uses the mean of the last 24 hourly
     * readings, as both indexes are defined on 24-hour averages.
     */
    void readAir(String body, long now) throws JSONException {
        JSONObject json = new JSONObject(body);
        String currentTime = json.getJSONObject("current").getString("time");
        JSONObject hourly = json.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        String[] hours = new String[times.length()];
        for (int i = 0; i < hours.length; i++) hours[i] = times.getString(i);

        int last = indexAt(hours, currentTime);
        if (last < 0) throw new JSONException("no hourly data before " + currentTime);
        pm25 = mean(hourly.getJSONArray("pm2_5"), last);
        pm10 = mean(hourly.getJSONArray("pm10"), last);
        aqi = Math.max(subIndex(pm25, scale.pm25), subIndex(pm10, scale.pm10));
        parseAirSeries(body);
        airJson = body;
        airAt = now;
    }

    private void parseForecast(String body) throws JSONException {
        JSONObject json = new JSONObject(body);
        JSONObject hourly = json.optJSONObject("hourly");
        if (hourly != null) {
            JSONArray t = hourly.getJSONArray("time");
            int n = t.length();
            String[] time = new String[n];
            double[] temps = new double[n];
            int[] codes = new int[n];
            int[] rain = new int[n];
            boolean[] days = new boolean[n];
            JSONArray temp = hourly.getJSONArray("temperature_2m");
            JSONArray code = hourly.getJSONArray("weather_code");
            JSONArray pop = hourly.getJSONArray("precipitation_probability");
            JSONArray isDay = hourly.getJSONArray("is_day");
            for (int i = 0; i < n; i++) {
                time[i] = t.getString(i);
                temps[i] = temp.optDouble(i, Double.NaN);
                codes[i] = code.optInt(i, -1);
                rain[i] = pop.optInt(i, 0);
                days[i] = isDay.optInt(i, 1) == 1;
            }
            hourTime = time;
            hourTemp = temps;
            hourCode = codes;
            hourRain = rain;
            hourDay = days;
        }
        JSONObject daily = json.optJSONObject("daily");
        if (daily != null) {
            JSONArray dates = daily.getJSONArray("time");
            String today = isoNow().substring(0, 10);
            for (int i = 0; i < dates.length(); i++) {
                if (!dates.getString(i).equals(today)) continue;
                high = daily.getJSONArray("temperature_2m_max").optDouble(i, Double.NaN);
                low = daily.getJSONArray("temperature_2m_min").optDouble(i, Double.NaN);
                uvMax = daily.getJSONArray("uv_index_max").optDouble(i, Double.NaN);
                sunrise = clockPart(daily.getJSONArray("sunrise").optString(i, null));
                sunset = clockPart(daily.getJSONArray("sunset").optString(i, null));
            }
        }
    }

    private void parseAirSeries(String body) throws JSONException {
        JSONObject hourly = new JSONObject(body).getJSONObject("hourly");
        JSONArray t = hourly.getJSONArray("time");
        JSONArray pm = hourly.getJSONArray("pm2_5");
        String[] time = new String[t.length()];
        double[] values = new double[t.length()];
        for (int i = 0; i < time.length; i++) {
            time[i] = t.getString(i);
            values[i] = pm.isNull(i) ? Double.NaN : pm.getDouble(i);
        }
        airTime = time;
        airPm25 = values;
    }

    private static double mean(JSONArray values, int last) throws JSONException {
        double sum = 0;
        int n = 0;
        for (int i = Math.max(0, last - 23); i <= last; i++) {
            if (values.isNull(i)) continue;
            sum += values.getDouble(i);
            n++;
        }
        if (n == 0) throw new JSONException("no readings");
        return sum / n;
    }

    private String url(String template) throws IOException {
        return String.format(Locale.US, template, place.lat, place.lon, URLEncoder.encode(place.timezone, "UTF-8"));
    }

    private int subIndex(double c, double[] bp) {
        if (c <= 0) return 0;
        double[] index = scale.aqi;
        for (int i = 1; i < bp.length; i++) {
            if (c <= bp[i]) {
                return (int) Math.round(index[i - 1] + (index[i] - index[i - 1]) * (c - bp[i - 1]) / (bp[i] - bp[i - 1]));
            }
        }
        return 500;
    }

    /** Open-Meteo's answers are a few tens of kilobytes; anything far bigger is refused. */
    private static final int MAX_RESPONSE = 1 << 20;

    static String get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects(false);
        // The default user agent names the TV model and build; send nothing identifying instead.
        conn.setRequestProperty("User-Agent", "Aangan");
        try {
            if (conn.getResponseCode() != 200) throw new IOException("HTTP " + conn.getResponseCode());
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_RESPONSE) throw new IOException("response too large");
                }
                return out.toString("UTF-8");
            }
        } finally {
            conn.disconnect();
        }
    }

    // ---- time -------------------------------------------------------------------------------

    /** The city's local time as Open-Meteo writes it, e.g. "2026-10-25T17:42". */
    static String isoNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        f.setTimeZone(zone);
        return f.format(new Date(Dates.nowMillis()));
    }

    /** The index of the latest entry in {@code times} at or before {@code iso}, or -1. */
    static int indexAt(String[] times, String iso) {
        int found = -1;
        for (int i = 0; i < times.length; i++) {
            if (times[i].compareTo(iso) <= 0) found = i;
        }
        return found;
    }

    /** A moment as "5:26 PM", the city's time. */
    static String clockAt(Resources r, long millis) {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        f.setTimeZone(zone);
        return clock(r, f.format(new Date(millis)), true);
    }

    /** An hour of the forecast, "2026-10-25T19:00", as "7 PM". */
    static String hour(Resources r, String iso) {
        return clock(r, clockPart(iso), false);
    }

    private static String clockPart(String iso) {
        return iso != null && iso.length() >= 16 ? iso.substring(11, 16) : null;
    }

    private static int minutes(String hhmm) {
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
    }

    /** "17:00" → "5 PM"; "06:31" → "6:31 AM". */
    static String clock(Resources r, String hhmm, boolean withMinutes) {
        int h = Integer.parseInt(hhmm.substring(0, 2));
        String m = hhmm.substring(3, 5);
        int h12 = h % 12 == 0 ? 12 : h % 12;
        String time = withMinutes ? h12 + ":" + m : String.valueOf(h12);
        return r.getString(h < 12 ? R.string.clock_am : R.string.clock_pm, time);
    }

    // ---- things worth knowing today ---------------------------------------------------------

    /** "Rain likely around 7 PM" when this hour or one of the next six is likely to rain, else null. */
    String rainSoon(Resources r) {
        int now = indexAt(hourTime, isoNow());
        if (now < 0) return null;
        for (int i = now; i < Math.min(hourTime.length, now + RAIN_HOURS + 1); i++) {
            if (hourRain[i] >= RAIN_LIKELY) {
                return i == now ? r.getString(R.string.rain_now) : r.getString(R.string.rain_around, hour(r, hourTime[i]));
            }
        }
        return null;
    }

    /**
     * The hour with the least PM2.5 between now and the end of tomorrow morning, as an index into
     * {@link #airPm25}, or -1. In a North Indian winter this is when to open the windows.
     */
    int cleanestHour() {
        int from = indexAt(airTime, isoNow());
        if (from < 0) return -1;
        int best = -1;
        for (int i = from; i < Math.min(airTime.length, from + CLEAN_AIR_HOURS); i++) {
            if (!Double.isNaN(airPm25[i]) && (best < 0 || airPm25[i] < airPm25[best])) best = i;
        }
        return best;
    }

    /** Hour {@code i} of the air series: "now", "7 AM", or "7 AM tomorrow". */
    String airHour(Resources r, int i) {
        String nowIso = isoNow();
        if (i == indexAt(airTime, nowIso)) return r.getString(R.string.air_now);
        String when = hour(r, airTime[i]);
        return airTime[i].startsWith(nowIso.substring(0, 10)) ? when : r.getString(R.string.air_tomorrow, when);
    }

    /** Today's daylight, e.g. "11h 04m", or null. */
    String daylight(Resources r) {
        if (sunrise == null || sunset == null) return null;
        int m = minutes(sunset) - minutes(sunrise);
        return r.getString(R.string.daylight, m / 60, m % 60);
    }

    static String uvLabel(Resources r, double uv) {
        long u = Math.round(uv);
        int level = u <= 2 ? R.string.uv_low : u <= 5 ? R.string.uv_moderate : u <= 7 ? R.string.uv_high
                : u <= 10 ? R.string.uv_very_high : R.string.uv_extreme;
        return r.getString(R.string.uv_label, u, r.getString(level));
    }

    /** Fraction of the lunar cycle, 0 = new moon, 0.5 = full; computed locally from a known new moon. */
    static double moonAge(long now) {
        double synodic = 29.530588853;
        // Days since a known new moon: 6 January 2000, 18:14 UTC.
        double days = (now - 947182440000L) / 86_400_000.0;
        double age = (days % synodic) / synodic;
        return age < 0 ? age + 1 : age;
    }

    static String moonName(Resources r, double age) {
        return r.getStringArray(R.array.moon_phases)[(int) Math.floor(age * 8 + 0.5) % 8];
    }

    // ---- presentation -----------------------------------------------------------------------

    /** Smog never shows up as a weather code, so clear skies with heavy PM2.5 read as haze. */
    private static boolean hazy(int code, double pm25) {
        return code >= 0 && code <= 2 && pm25 > HEAVY_PM25;
    }

    private boolean hazy() {
        return hasAir() && hazy(code, pm25);
    }

    String condition(Resources r) {
        int id = conditionName();
        return id == 0 ? "" : r.getString(id);
    }

    /** The string resource naming the sky, or 0 for a code Open-Meteo doesn't document. */
    private int conditionName() {
        if (hazy()) return R.string.sky_hazy;
        switch (code) {
            case 0: return R.string.sky_clear;
            case 1: return R.string.sky_mostly_clear;
            case 2: return R.string.sky_partly_cloudy;
            case 3: return R.string.sky_cloudy;
            case 45: case 48: return R.string.sky_fog;
            case 51: case 53: case 55: return R.string.sky_drizzle;
            case 56: case 57: return R.string.sky_freezing_drizzle;
            case 61: return R.string.sky_light_rain;
            case 63: return R.string.sky_rain;
            case 65: return R.string.sky_heavy_rain;
            case 66: case 67: return R.string.sky_freezing_rain;
            case 71: return R.string.sky_light_snow;
            case 73: return R.string.sky_snow;
            case 75: return R.string.sky_heavy_snow;
            case 77: return R.string.sky_snow_grains;
            case 80: case 81: return R.string.sky_showers;
            case 82: return R.string.sky_heavy_showers;
            case 85: case 86: return R.string.sky_snow_showers;
            case 95: return R.string.sky_thunderstorm;
            case 96: case 99: return R.string.sky_thunderstorm_hail;
            default: return 0;
        }
    }

    Glyph glyph() {
        return glyph(code, day, hazy());
    }

    /** The icon for hour {@code i} of the forecast, hazy when that hour's PM2.5 is heavy. */
    Glyph hourGlyph(int i) {
        int a = indexAt(airTime, hourTime[i]);
        boolean haze = a >= 0 && airTime[a].equals(hourTime[i]) && hazy(hourCode[i], airPm25[a]);
        return glyph(hourCode[i], hourDay[i], haze);
    }

    private static Glyph glyph(int code, boolean day, boolean hazy) {
        if (hazy || code == 45 || code == 48) return Glyph.FOG;
        if (code <= 1) return day ? Glyph.SUN : Glyph.MOON;
        if (code == 2) return day ? Glyph.PARTLY_DAY : Glyph.PARTLY_NIGHT;
        if (code == 3) return Glyph.CLOUD;
        if (code >= 51 && code <= 57) return Glyph.DRIZZLE;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Glyph.SNOW;
        if (code >= 95) return Glyph.THUNDER;
        return Glyph.RAIN;
    }

    /** Which category an index value falls in, 0 (best) to 5. */
    private int category(int value) {
        for (int i = 1; i < scale.aqi.length - 1; i++) if (value <= scale.aqi[i]) return i - 1;
        return scale.names.length - 1;
    }

    String aqiCategory(Resources r) {
        return r.getString(scale.names[category(aqi)]);
    }

    int aqiColor() {
        return scale.colors[category(aqi)];
    }

    String aqiAdvice(Resources r) {
        return r.getString(scale.advice[category(aqi)]);
    }

    /** The category colour for one hour's PM2.5 reading, for the trend chart. */
    int pm25Color(double pm25) {
        return scale.colors[category(subIndex(pm25, scale.pm25))];
    }

    double pm25Limit() {
        return scale.limit;
    }

    /**
     * The colour of the soft glow in the top-left of the background, matched to the sky:
     * rose around sunrise and sunset, blue by day, indigo by night, brown in smog.
     */
    int glowColor() {
        if (!hasWeather()) return 0xFF3A4A7A;
        if (goldenHour()) return 0xFF7A4E66;
        if (hazy()) return 0xFF5A4A3A;
        return day ? 0xFF4A5A86 : 0xFF2A3360;
    }

    private boolean goldenHour() {
        if (sunrise == null || sunset == null) return false;
        int now = minutes(isoNow().substring(11, 16));
        return Math.abs(now - minutes(sunrise)) <= GOLDEN_MIN || Math.abs(now - minutes(sunset)) <= GOLDEN_MIN;
    }

    static String degrees(double celsius) {
        return Math.round(celsius) + "°";
    }

    /** One line for the screensaver, e.g. "24° Clear · AQI 214 Poor". */
    String summary(Resources r) {
        StringBuilder s = new StringBuilder();
        if (hasWeather()) s.append(degrees(temp)).append(' ').append(condition(r));
        if (hasAir()) {
            if (s.length() > 0) s.append("  ·  ");
            s.append(r.getString(R.string.aqi_value, aqi)).append(' ').append(aqiCategory(r));
        }
        return s.toString();
    }
}
