package dev.vaari.aangan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Resources;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.time.LocalDateTime;

@RunWith(RobolectricTestRunner.class)
public class WeatherTest {

    private static final String NOW = "2026-10-25T14:30";

    private Resources r;
    private Weather w;

    @Before
    public void setUp() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        r = context.getResources();
        Testing.now(NOW);
        w = Weather.load(Prefs.of(context));
    }

    @After
    public void tearDown() {
        Testing.resetClock();
    }

    /** Open-Meteo's forecast for today and tomorrow: clear, 24°, with rain likely at 5 PM. */
    private static String forecast(int code) throws Exception {
        JSONArray time = new JSONArray();
        JSONArray temp = new JSONArray();
        JSONArray codes = new JSONArray();
        JSONArray rain = new JSONArray();
        JSONArray isDay = new JSONArray();
        LocalDateTime t = LocalDateTime.parse("2026-10-25T00:00");
        for (int i = 0; i < 48; i++, t = t.plusHours(1)) {
            time.put(t.toString());
            temp.put(20 + i % 10);
            codes.put(code);
            rain.put(i == 17 ? 70 : 10);
            isDay.put(t.getHour() >= 6 && t.getHour() < 18 ? 1 : 0);
        }
        return new JSONObject()
                .put("current", new JSONObject().put("temperature_2m", 24.4).put("apparent_temperature", 26.6)
                        .put("weather_code", code).put("is_day", 1))
                .put("hourly", new JSONObject().put("time", time).put("temperature_2m", temp).put("weather_code", codes)
                        .put("precipitation_probability", rain).put("is_day", isDay))
                .put("daily", new JSONObject().put("time", new JSONArray().put("2026-10-25").put("2026-10-26"))
                        .put("temperature_2m_max", new JSONArray().put(31.2).put(30))
                        .put("temperature_2m_min", new JSONArray().put(18.6).put(18))
                        .put("uv_index_max", new JSONArray().put(6.4).put(6))
                        .put("sunrise", new JSONArray().put("2026-10-25T06:31").put("2026-10-26T06:32"))
                        .put("sunset", new JSONArray().put("2026-10-25T17:35").put("2026-10-26T17:34")))
                .toString();
    }

    /** Air quality from midnight yesterday: steady PM2.5 and PM10, with a cleaner hour at 4 AM tomorrow. */
    private static String air(double pm25, double pm10) throws Exception {
        JSONArray time = new JSONArray();
        JSONArray a = new JSONArray();
        JSONArray b = new JSONArray();
        LocalDateTime t = LocalDateTime.parse("2026-10-24T00:00");
        for (int i = 0; i < 72; i++, t = t.plusHours(1)) {
            time.put(t.toString());
            a.put(t.toString().equals("2026-10-26T04:00") ? pm25 / 2 : pm25);
            b.put(pm10);
        }
        return new JSONObject()
                .put("current", new JSONObject().put("time", "2026-10-25T14:00"))
                .put("hourly", new JSONObject().put("time", time).put("pm2_5", a).put("pm10", b))
                .toString();
    }

    @Test
    public void readsTheForecast() throws Exception {
        w.readWeather(forecast(0), Dates.nowMillis());
        assertEquals("24°", Weather.degrees(w.temp));
        assertEquals("Clear", w.condition(r));
        assertEquals(Weather.Glyph.SUN, w.glyph());
        assertEquals(31.2, w.high, 0.001);
        assertEquals("6:31 AM", Weather.clock(r, w.sunrise, true));
        assertEquals("11h 04m", w.daylight(r));
        assertEquals("6 High", Weather.uvLabel(r, w.uvMax));
        assertEquals("Rain likely around 5 PM", w.rainSoon(r));
    }

    @Test
    public void indexesIndianAirOnTheCpcbScale() throws Exception {
        w.readAir(air(100, 150), Dates.nowMillis());
        // PM2.5 100 lies a third of the way from 90 (index 200) to 120 (300); PM10 150 gives less.
        assertEquals(233, w.aqi);
        assertEquals("Poor", w.aqiCategory(r));
        assertEquals(60, w.pm25Limit(), 0);
        assertEquals("4 AM tomorrow", w.airHour(r, w.cleanestHour()));
    }

    @Test
    public void clearSkiesInSmogReadAsHaze() throws Exception {
        w.readWeather(forecast(0), Dates.nowMillis());
        w.readAir(air(150, 200), Dates.nowMillis());
        assertEquals("Hazy", w.condition(r));
        assertEquals(Weather.Glyph.FOG, w.glyph());
        assertTrue(w.summary(r).startsWith("24° Hazy  ·  AQI "));
    }

    @Test
    public void noRainMeansNoMention() throws Exception {
        Testing.now("2026-10-25T07:00");
        w.readWeather(forecast(0), Dates.nowMillis());
        assertNull(w.rainSoon(r));
    }

    @Test
    public void clockTimes() {
        assertEquals("12 AM", Weather.clock(r, "00:00", false));
        assertEquals("12:05 PM", Weather.clock(r, "12:05", true));
        assertEquals("5 PM", Weather.clock(r, "17:00", false));
    }

    @Test
    @Config(qualifiers = "hi")
    public void inHindi() throws Exception {
        w.readWeather(forecast(61), Dates.nowMillis());
        assertEquals("हल्की बारिश", w.condition(r));
        assertEquals("11 घं 04 मि", w.daylight(r));
        w.readAir(air(100, 150), Dates.nowMillis());
        assertEquals("ख़राब", w.aqiCategory(r));
    }
}
