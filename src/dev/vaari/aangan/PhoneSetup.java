package dev.vaari.aangan;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A small page on the home network for setting the city, the family's birthdays and anniversaries
 * and the festival dates from a phone, which is far easier than typing with a remote. It runs only
 * while Settings is on screen, answers only at an address with a fresh secret in it, loads nothing
 * from the internet (its stylesheet, font and logo come from the TV), and keeps everything on the TV.
 */
final class PhoneSetup {

    private static final int PORT = 8737;
    /** The secret in the page's address: ten characters, avoiding ones easily misread (l, o, 0, 1). */
    private static final String SECRET_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final int SECRET_LENGTH = 10;
    /** A request's form data is read up to this many characters; the page's forms are far smaller. */
    private static final int MAX_BODY = 8192;
    private static final int TIMEOUT_MS = 10_000;
    /** The family's longest name, as the form allows. */
    private static final int MAX_NAME = 40;
    /** Days ahead within which the page says "in 9 days"; it lists the whole year, so this reaches further than the home screen. */
    private static final int WHEN_DAYS = 60;
    /** The festival list shows this many months in full; the rest of the year is behind a tap. */
    private static final int OPEN_MONTHS = 3;
    private static final String[] MONTHS = Dates.months(TextStyle.FULL);
    /** What the page says after each kind of change. */
    private static final Map<String, String> DONE = new HashMap<>();

    static {
        DONE.put("added", "Added");
        DONE.put("saved", "Saved");
        DONE.put("removed", "Removed");
        DONE.put("city", "City changed");
        DONE.put("moved", "Date changed");
        DONE.put("hidden", "Hidden this year");
        DONE.put("shown", "Showing again");
        DONE.put("restored", "Back to the calendar's date");
        DONE.put("invalid", "Not saved: check the name and date");
        DONE.put("gone", "Not saved: that day was changed on the TV");
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final Runnable onChange;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String secret;
    private ServerSocket server;

    PhoneSetup(Context context, SharedPreferences prefs, Runnable onChange) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
        this.onChange = onChange;
        SecureRandom random = new SecureRandom();
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < SECRET_LENGTH; i++) s.append(SECRET_ALPHABET.charAt(random.nextInt(SECRET_ALPHABET.length())));
        secret = s.toString();
    }

    /** Starts serving and returns the page's address, or null when the TV has no network. */
    String start() {
        String ip = localAddress(context);
        if (ip == null) return null;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            try {
                server.bind(new InetSocketAddress(PORT));
            } catch (IOException taken) {
                // Something else has the usual port: let the system pick one.
                server.close();
                server = new ServerSocket(0);
            }
        } catch (IOException e) {
            stop();
            return null;
        }
        Thread thread = new Thread(this::serve, "phone-setup");
        thread.setDaemon(true);
        thread.start();
        return "http://" + ip + ":" + server.getLocalPort() + "/" + secret;
    }

    void stop() {
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
            // Closing is best effort.
        }
        server = null;
    }

    private static String localAddress(Context context) {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        LinkProperties link = cm.getLinkProperties(cm.getActiveNetwork());
        if (link == null) return null;
        for (LinkAddress a : link.getLinkAddresses()) {
            if (a.getAddress() instanceof Inet4Address) return a.getAddress().getHostAddress();
        }
        return null;
    }

    // ---- HTTP -------------------------------------------------------------------------------

    private void serve() {
        ServerSocket s = server;
        while (s != null && !s.isClosed()) {
            try (Socket socket = s.accept()) {
                socket.setSoTimeout(TIMEOUT_MS);
                handle(socket);
            } catch (IOException | RuntimeException e) {
                // A closed server ends the loop; a broken or malformed request is just dropped.
            }
        }
    }

    private void handle(Socket socket) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String request = in.readLine();
        if (request == null) return;
        String[] parts = request.split(" ");
        if (parts.length < 2) return;
        int length = 0;
        for (String line; (line = in.readLine()) != null && !line.isEmpty(); ) {
            if (line.toLowerCase().startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
        }
        char[] body = new char[Math.max(0, Math.min(length, MAX_BODY))];
        for (int read = 0; read < body.length; ) {
            int n = in.read(body, read, body.length - read);
            if (n < 0) break;
            read += n;
        }
        Map<String, String> form = parseForm(new String(body));
        OutputStream out = socket.getOutputStream();
        String base = "/" + secret;
        String path = parts[1];
        String query = "";
        int q = path.indexOf('?');
        if (q >= 0) {
            query = path.substring(q + 1);
            path = path.substring(0, q);
        }
        boolean post = parts[0].equals("POST");

        if (!path.equals(base) && !path.startsWith(base + "/")) {
            respond(out, "404 Not Found", page(base, "Link expired", "<section><h1>This link has expired</h1>"
                    + "<p class=lede>Open Aangan's Settings on the TV and scan the new code.</p></section>", null));
            return;
        }
        String route = path.substring(base.length());
        Festival.load(context, prefs);
        switch (route) {
            case "/page.css":
                asset(out, "phone/page.css", "text/css; charset=utf-8");
                return;
            case "/fraunces.woff2":
                asset(out, "phone/fraunces.woff2", "font/woff2");
                return;
            case "/mark.svg":
                asset(out, "phone/mark.svg", "image/svg+xml");
                return;
            default:
                break;
        }
        if (!post) {
            respond(out, "200 OK", home(base, DONE.get(parseForm(query).get("s"))));
            return;
        }
        switch (route) {
            case "/add": {
                People.Day day = day(form);
                if (day != null) People.add(prefs, day);
                redirect(out, base, day == null ? "invalid" : changed("added"), "family");
                break;
            }
            case "/edit": {
                People.Day day = day(form);
                String result = day == null ? "invalid"
                        : People.update(prefs, form.getOrDefault("d", ""), day) ? changed("saved") : "gone";
                redirect(out, base, result, "family");
                break;
            }
            case "/remove":
                redirect(out, base, People.remove(prefs, form.getOrDefault("d", "")) ? changed("removed") : "gone", "family");
                break;
            case "/city":
                respond(out, "200 OK", cityResults(base, form.getOrDefault("q", "")));
                break;
            case "/city/set":
                try {
                    new Place(form.get("name"), form.getOrDefault("region", ""), Double.parseDouble(form.get("lat")),
                            Double.parseDouble(form.get("lon")), form.get("tz"), form.getOrDefault("cc", "")).save(prefs);
                    Weather.clear(prefs);
                    changed();
                } catch (RuntimeException ignored) {
                    // A malformed form leaves the city as it was.
                }
                redirect(out, base, "city", "city");
                break;
            case "/festival/move":
            case "/festival/hide":
            case "/festival/show":
            case "/festival/restore":
                redirect(out, base, festival(route, form), "festivals");
                break;
            default:
                redirect(out, base, null, null);
        }
    }

    /** Applies a change to a festival day and says which, or returns null if nothing changed. */
    private String festival(String route, Map<String, String> form) {
        Festival f = Festival.byKey(form.getOrDefault("k", ""));
        if (f == null) return null;
        switch (route) {
            case "/festival/move":
                try {
                    LocalDate date = LocalDate.parse(form.getOrDefault("date", ""));
                    if (!f.canMoveTo(date)) return null;
                    Festival.move(prefs, f, date);
                } catch (DateTimeParseException e) {
                    return null;
                }
                changed();
                return "moved";
            case "/festival/hide":
            case "/festival/show":
                Festival.hide(prefs, f, route.endsWith("hide"));
                changed();
                return route.endsWith("hide") ? "hidden" : "shown";
            default:
                Festival.move(prefs, f, f.original);
                changed();
                return "restored";
        }
    }

    private static People.Day day(Map<String, String> form) {
        String who = form.getOrDefault("who", "").trim();
        int month = parseInt(form.get("month"));
        int day = parseInt(form.get("day"));
        if (who.isEmpty() || who.length() > MAX_NAME || !People.Day.valid(month, day)) return null;
        return new People.Day(month, day, !"anniversary".equals(form.get("kind")), who);
    }

    private void changed() {
        main.post(onChange);
    }

    /** Tells the TV something changed, and passes on what the page should say about it. */
    private String changed(String done) {
        changed();
        return done;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> form = new HashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try {
                form.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"), URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            } catch (IOException | IllegalArgumentException ignored) {
                // Skip anything that isn't valid form encoding.
            }
        }
        return form;
    }

    private static void respond(OutputStream out, String status, String html) throws IOException {
        send(out, status, "text/html; charset=utf-8", "no-store", html.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(OutputStream out, String status, String type, String cache, byte[] bytes) throws IOException {
        out.write(("HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + bytes.length
                + "\r\nCache-Control: " + cache + "\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff"
                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
    }

    private void asset(OutputStream out, String name, String type) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = context.getAssets().open(name)) {
            byte[] buffer = new byte[16384];
            for (int n; (n = in.read(buffer)) > 0; ) bytes.write(buffer, 0, n);
        }
        send(out, "200 OK", type, "private, max-age=86400", bytes.toByteArray());
    }

    /** Back to the page after a change, scrolled to {@code section}, with a word about what happened. */
    private static void redirect(OutputStream out, String base, String done, String section) throws IOException {
        String to = base + (done != null ? "?s=" + done : "") + (section != null ? "#" + section : "");
        out.write(("HTTP/1.1 303 See Other\r\nLocation: " + to + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    // ---- pages ------------------------------------------------------------------------------

    private String home(String base, String done) {
        LocalDate today = Festival.today();
        StringBuilder h = new StringBuilder();
        h.append("<div><h1>Set up your home screen</h1>")
                .append("<p class=lede>Changes appear on the TV as soon as you make them.</p></div>");

        // The city.
        Place place = Place.load(prefs);
        h.append("<section id=city><h2>Your city</h2><div class='card city'><div>")
                .append("<div class=label>Weather and air quality for</div>")
                .append("<div class=name>").append(esc(place.name)).append("</div>")
                .append("<div class=region>").append(esc(place.region)).append("</div></div>")
                .append("<form class=search method=post action='").append(base).append("/city'>")
                .append("<input type=search name=q placeholder='Search for another city' aria-label='City' required ")
                .append("autocomplete=off enterkeyhint=search><button>Search</button></form></div></section>");

        // The family's days.
        List<People.Day> days = People.load(prefs);
        h.append("<section id=family><h2>Family days");
        if (!days.isEmpty()) h.append(" <small>").append(days.size()).append("</small>");
        h.append("</h2><ul class='card list'>");
        for (int i = 0; i < days.size(); i++) {
            People.Day d = days.get(i);
            long until = ChronoUnit.DAYS.between(today, d.next(today));
            String tone = d.birthday ? new String[]{"t1", "", "t3"}[i % 3] : "ring";
            h.append("<li><details><summary class=item>")
                    .append("<span class='badge ").append(tone).append("' aria-hidden=true>")
                    .append(esc(initial(d.who))).append("</span>")
                    .append("<span class=text><span class=title>").append(esc(d.who)).append("</span>")
                    .append("<span class=sub>").append(d.birthday ? "Birthday" : "Anniversary").append(" · ")
                    .append(d.day).append(' ').append(MONTHS[d.month - 1]).append("</span></span>")
                    .append(when(until))
                    .append("<span class=chev aria-hidden=true></span></summary>")
                    .append("<form class=edit method=post action='").append(base).append("/edit'>")
                    .append(hidden("d", d.key()))
                    .append(dayFields("e" + i, d))
                    .append("<div class=actions><button>Save</button>")
                    .append("<button class=quiet formaction='").append(base).append("/remove' formnovalidate>Remove</button>")
                    .append("</div></form></details></li>");
        }
        h.append("<li><details class=add").append(days.isEmpty() ? " open" : "").append(">")
                .append("<summary><span class=badge aria-hidden=true>+</span>Add a birthday or anniversary</summary>")
                .append("<form class=edit method=post action='").append(base).append("/add'>")
                .append(dayFields("new", null))
                .append("<button class=wide>Add</button></form></details></li></ul>")
                .append("<p class=note>On the day, Aangan greets them by name, with balloons for a birthday and petals for an anniversary.</p>")
                .append("</section>");

        // The festivals, a year ahead, by month.
        h.append("<section id=festivals><h2>Festivals <small>the next 12 months</small></h2><ul class='card list'>");
        String month = "";
        List<Festival> coming = Festival.editable(today);
        boolean folded = false;
        for (int i = 0; i < coming.size(); i++) {
            Festival f = coming.get(i);
            // The next three months in full; the rest of the year behind a tap.
            if (!folded && f.date.isAfter(today.plusMonths(OPEN_MONTHS))) {
                folded = true;
                h.append("<li><details class=more><summary>Show ").append(coming.size() - i)
                        .append(" more, to ").append(Dates.month(coming.get(coming.size() - 1).date.getMonth(), TextStyle.FULL))
                        .append("</summary><ul class=list>");
            }
            String m = Dates.month(f.date.getMonth(), TextStyle.FULL) + " " + f.date.getYear();
            if (!m.equals(month)) {
                h.append("<li class=month>").append(m).append("</li>");
                month = m;
            }
            h.append("<li").append(f.hidden ? " class=hidden-day" : "").append("><details><summary class=item>")
                    .append("<span class=date><b>").append(f.date.getDayOfMonth()).append("</b><span>")
                    .append(Dates.weekday(f.date.getDayOfWeek(), TextStyle.SHORT)).append("</span></span>")
                    .append("<span class=text><span class=title>").append(esc(f.name)).append("</span>");
            if (f.hidden || f.moved() || f.expected) {
                h.append("<span class=sub>");
                if (f.hidden) h.append("<span class=tag>Hidden</span>");
                if (f.moved()) h.append("<span class='tag moved'>Moved from ").append(Dates.dayMonth(f.original)).append("</span>");
                else if (f.expected) h.append("<span class=tag>Expected</span>");
                h.append("</span>");
            }
            h.append("</span>").append(f.hidden ? "" : when(ChronoUnit.DAYS.between(today, f.date)))
                    .append("<span class=chev aria-hidden=true></span></summary><div class=edit>")
                    .append("<form class=actions method=post action='").append(base).append("/festival/move'>")
                    .append(hidden("k", f.key()))
                    .append("<input type=date name=date aria-label='New date' required value=").append(f.date)
                    .append(" min=").append(f.clampMove(LocalDate.MIN))
                    .append(" max=").append(f.clampMove(LocalDate.MAX)).append(">")
                    .append("<button>Move</button></form><div class=actions>")
                    .append("<form method=post action='").append(base).append(f.hidden ? "/festival/show'>" : "/festival/hide'>")
                    .append(hidden("k", f.key())).append("<button class=ghost>")
                    .append(f.hidden ? "Show it again" : "Hide this year").append("</button></form>");
            if (f.moved()) {
                h.append("<form method=post action='").append(base).append("/festival/restore'>")
                        .append(hidden("k", f.key())).append("<button class=quiet>Back to ")
                        .append(Dates.dayMonth(f.original)).append("</button></form>");
            }
            h.append("</div>");
            if (f.expected && !f.moved()) {
                h.append("<p class=hint>This date depends on the moon being sighted, and may move by a day.</p>");
            }
            h.append("</div></details></li>");
        }
        if (folded) h.append("</ul></details></li>");
        h.append("</ul><p class=note>Dates follow Drik Panchang for New Delhi. If your family keeps a festival on "
                + "another day, move it here.</p></section>");
        return page(base, "Aangan", h.toString(), done);
    }

    /** Name, birthday or anniversary, and the date, for adding or editing a family day. */
    private static String dayFields(String id, People.Day d) {
        boolean birthday = d == null || d.birthday;
        StringBuilder h = new StringBuilder()
                .append("<input type=text name=who required maxlength=").append(MAX_NAME).append(" autocomplete=off autocapitalize=words ")
                .append("aria-label=Name placeholder='Name, e.g. Asha or Asha &amp; Rohan'")
                .append(d != null ? " value='" + esc(d.who) + "'" : "").append('>')
                .append("<div class=kind role=radiogroup aria-label='Kind of day'>")
                .append("<input type=radio name=kind value=birthday id=b").append(id).append(birthday ? " checked" : "")
                .append("><label for=b").append(id).append(">Birthday</label>")
                .append("<input type=radio name=kind value=anniversary id=a").append(id).append(birthday ? "" : " checked")
                .append("><label for=a").append(id).append(">Anniversary</label></div>")
                .append("<div class=row><select name=day aria-label=Day>");
        for (int i = 1; i <= 31; i++) {
            h.append("<option").append(d != null && d.day == i ? " selected" : "").append('>').append(i).append("</option>");
        }
        h.append("</select><select name=month aria-label=Month>");
        for (int m = 1; m <= 12; m++) {
            h.append("<option value=").append(m).append(d != null && d.month == m ? " selected" : "").append('>')
                    .append(MONTHS[m - 1]).append("</option>");
        }
        return h.append("</select></div>").toString();
    }

    private String cityResults(String base, String query) {
        StringBuilder h = new StringBuilder("<div><a class=back href='").append(base).append("#city'>‹ Back</a>")
                .append("<h1>Choose your city</h1></div><section><ul class='card list'>");
        try {
            List<Place> places = Place.search(query);
            if (places.isEmpty()) {
                h.append("<li class=item><span class=text>No places called “").append(esc(query)).append("”. ")
                        .append("Try the nearest large town.</span></li>");
            }
            for (Place p : places) {
                h.append("<li><form method=post action='").append(base).append("/city/set'>")
                        .append(hidden("name", p.name)).append(hidden("region", p.region))
                        .append(hidden("lat", String.valueOf(p.lat))).append(hidden("lon", String.valueOf(p.lon)))
                        .append(hidden("tz", p.timezone)).append(hidden("cc", p.country))
                        .append("<button class=place><span><b>").append(esc(p.name)).append("</b><span>")
                        .append(esc(p.region)).append("</span></span><span class=chev style='transform:rotate(-45deg)'></span>")
                        .append("</button></form></li>");
            }
        } catch (Exception e) {
            h.append("<li class=item><span class=text>Couldn't search right now. Check the TV's internet connection.</span></li>");
        }
        h.append("</ul></section>");
        return page(base, "Choose your city", h.toString(), null);
    }

    /** "Today", "Tomorrow" or "in 9 days", for anything within {@link #WHEN_DAYS}; otherwise nothing. */
    private static String when(long days) {
        if (days > WHEN_DAYS) return "";
        return "<span class='when" + (Dates.soon(days) ? " soon" : "") + "'>" + Dates.until(days, false) + "</span>";
    }

    private static String initial(String who) {
        String s = who.trim();
        return s.isEmpty() ? "?" : s.substring(0, s.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
    }

    private static String hidden(String name, String value) {
        return "<input type=hidden name=" + name + " value='" + esc(value) + "'>";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;").replace("\"", "&quot;");
    }

    private String page(String base, String title, String body, String done) {
        String tv = Settings.Global.getString(context.getContentResolver(), "device_name");
        return "<!doctype html><html lang=en><head><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width,initial-scale=1,viewport-fit=cover'>"
                + "<meta name=theme-color content='#f5eee2' media='(prefers-color-scheme: light)'>"
                + "<meta name=theme-color content='#14100d' media='(prefers-color-scheme: dark)'>"
                + "<title>" + esc(title) + "</title>"
                + "<link rel=icon href='" + base + "/mark.svg'><link rel=stylesheet href='" + base + "/page.css'>"
                + "</head><body><main><header class=mast><img src='" + base + "/mark.svg' alt=''><div><b>Aangan</b>"
                + "<span>" + esc(tv == null || tv.isEmpty() ? "Your TV" : tv) + "</span></div></header>"
                + body
                + "<footer><img src='" + base + "/mark.svg' alt=''><div>Everything you set here stays on your TV.</div>"
                + "<div>This page works while Aangan's Settings are open on the TV.</div></footer></main>"
                + (done != null ? "<div class=toast role=status>" + esc(done) + "</div>" : "")
                + "</body></html>";
    }
}
