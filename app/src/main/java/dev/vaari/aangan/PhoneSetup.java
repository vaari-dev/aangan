package dev.vaari.aangan;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
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
    /** A request's body is read up to this many bytes: room for a backup file, and far more than any form. */
    static final int MAX_BODY = 65_536;
    /** A larger upload is read up to this many bytes and thrown away, so the browser hears why it failed. */
    private static final int MAX_DISCARD = 1 << 20;
    /** The request line and headers together; a browser's are well under this. */
    private static final int MAX_HEAD = 8192;
    /** How long one request may take, from connecting to the end of its body. */
    private static final int TIMEOUT_MS = 10_000;
    /** Days ahead within which the page says "in 9 days"; it lists the whole year, so this reaches further than the home screen. */
    private static final int WHEN_DAYS = 60;
    /** The festival list shows this many months in full; the rest of the year is behind a tap. */
    private static final int OPEN_MONTHS = 3;
    /** What the page says after each kind of change. */
    private static final Map<String, Integer> DONE = new HashMap<>();
    /** Everything the page uses comes from the TV itself, and only its own script runs. */
    private static final String POLICY = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; "
            + "font-src 'self'; connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'";

    static {
        DONE.put("added", R.string.done_added);
        DONE.put("saved", R.string.done_saved);
        DONE.put("removed", R.string.done_removed);
        DONE.put("city", R.string.done_city);
        DONE.put("nocity", R.string.done_no_city);
        DONE.put("dock", R.string.done_dock);
        DONE.put("crash", R.string.done_crash);
        DONE.put("moved", R.string.done_moved);
        DONE.put("hidden", R.string.done_hidden);
        DONE.put("shown", R.string.done_shown);
        DONE.put("restored", R.string.done_restored);
        DONE.put("invalid", R.string.done_invalid);
        DONE.put("full", R.string.done_full);
        DONE.put("gone", R.string.done_gone);
        DONE.put("imported", R.string.done_imported);
        DONE.put("notbackup", R.string.done_not_backup);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final Runnable onChange;
    private final Handler main = new Handler(Looper.getMainLooper());
    /** The page's secret address; package-private for the tests. */
    final String secret;
    private ServerSocket server;
    /** "192.168.1.20:8737", the address in the QR code; requests naming any other host are refused. */
    volatile String host;

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
            // Only on the home network's address, not every interface the TV has (a VPN, or public IPv6).
            InetAddress address = InetAddress.getByName(ip);
            server = new ServerSocket();
            server.setReuseAddress(true);
            try {
                server.bind(new InetSocketAddress(address, PORT));
            } catch (IOException taken) {
                // Something else has the usual port: let the system pick one.
                server.close();
                server = new ServerSocket();
                server.bind(new InetSocketAddress(address, 0));
            }
        } catch (IOException e) {
            stop();
            return null;
        }
        host = ip + ":" + server.getLocalPort();
        Thread thread = new Thread(this::serve, "phone-setup");
        thread.setDaemon(true);
        thread.start();
        return "http://" + host + "/" + secret;
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
        Network active = cm.getActiveNetwork();
        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        LinkProperties link = cm.getLinkProperties(active);
        if (link != null && caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
            for (LinkAddress a : link.getLinkAddresses()) {
                if (a.getAddress() instanceof Inet4Address) return a.getAddress().getHostAddress();
            }
            return null;
        }
        // With a VPN on, the active network is the tunnel, which a phone on the Wi-Fi can't reach:
        // use the Wi-Fi or Ethernet address under it instead.
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            if (all == null) return null;
            for (NetworkInterface n : Collections.list(all)) {
                if (!n.isUp() || n.isLoopback() || n.isVirtual() || n.isPointToPoint()) continue;
                if (!n.getName().startsWith("wlan") && !n.getName().startsWith("eth")) continue;
                for (InetAddress a : Collections.list(n.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (IOException e) {
            // No address, so no phone page.
        }
        return null;
    }

    // ---- HTTP -------------------------------------------------------------------------------

    private void serve() {
        ServerSocket s = server;
        while (s != null && !s.isClosed()) {
            try (Socket socket = s.accept()) {
                handle(socket, System.currentTimeMillis() + TIMEOUT_MS);
            } catch (IOException | RuntimeException | StackOverflowError e) {
                // A closed server ends the loop; a broken or malformed request is just dropped.
            }
        }
    }

    /**
     * Answers one request. Everything about it is bounded, since anyone on the network can connect:
     * its size, and its time in total (a slow sender can't hold the one-at-a-time server).
     */
    void handle(Socket socket, long deadline) throws IOException {
        InputStream in = new BufferedInputStream(socket.getInputStream());
        String[] head = new String(read(socket, in, deadline, MAX_HEAD, true), StandardCharsets.UTF_8).split("\r\n");
        String[] parts = head[0].split(" ");
        if (parts.length < 2) return;
        int length = 0;
        String hostHeader = "";
        String type = "";
        for (String line : head) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("content-length:")) length = parseInt(lower.substring(15).trim());
            else if (lower.startsWith("host:")) hostHeader = lower.substring(5).trim();
            else if (lower.startsWith("content-type:")) type = line.substring(13).trim();
        }
        OutputStream out = socket.getOutputStream();
        // A web page elsewhere can point its own domain name at the TV ("DNS rebinding"); its requests
        // then name that domain, not the TV's address, and get nothing.
        if (!hostHeader.equals(host)) {
            send(out, "421 Misdirected Request", "text/plain; charset=utf-8", "no-store", null,
                    context.getString(R.string.page_wrong_address).getBytes(StandardCharsets.UTF_8));
            return;
        }
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
            respond(out, "404 Not Found", expired());
            return;
        }
        String route = path.substring(base.length());
        if (length > MAX_BODY) {
            // Too big for anything the page sends. Read on (to a limit) before answering: closing
            // with the upload unread would reset the connection, and the browser wouldn't say why.
            read(socket, in, deadline, Math.min(length, MAX_DISCARD), false);
            redirect(out, base, route.equals("/import") ? "notbackup" : null, route.equals("/import") ? "backup" : null);
            return;
        }
        // Lengths are bytes, so the body is read as bytes and decoded whole: a name in Hindi has
        // fewer characters than bytes.
        String body = new String(read(socket, in, deadline, Math.max(0, length), false), StandardCharsets.UTF_8);
        boolean multipart = type.toLowerCase(Locale.ROOT).startsWith("multipart/form-data");
        Map<String, String> form = multipart ? new HashMap<>() : parseForm(body);
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
            case "/page.js":
                asset(out, "phone/page.js", "text/javascript; charset=utf-8");
                return;
            case "/city.json":
                send(out, "200 OK", "application/json; charset=utf-8", "no-store", null,
                        cityJson(parseForm(query).getOrDefault("q", "")).getBytes(StandardCharsets.UTF_8));
                return;
            case "/export":
                send(out, "200 OK", "application/json; charset=utf-8", "no-store",
                        "Content-Disposition: attachment; filename=\"aangan-backup-" + Dates.today() + ".json\"",
                        Backup.export(prefs).getBytes(StandardCharsets.UTF_8));
                return;
            default:
                break;
        }
        if (!post) {
            Integer done = DONE.get(parseForm(query).get("s"));
            respond(out, "200 OK", home(base, done == null ? null : context.getString(done, People.MAX)));
            return;
        }
        switch (route) {
            case "/add": {
                People.Day day = day(form);
                String result = day == null ? "invalid" : People.add(prefs, day) ? changed("added") : "full";
                redirect(out, base, result, "family");
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
            {
                Place place = Place.of(form.get("name"), form.get("region"), form.get("lat"), form.get("lon"),
                        form.get("tz"), form.get("cc"));
                // A malformed form leaves the city as it was.
                if (place != null) {
                    place.save(prefs);
                    Weather.clear(prefs);
                }
                redirect(out, base, place == null ? "nocity" : changed("city"), "city");
            }
                break;
            case "/dock":
                redirect(out, base, dock(form), "dock");
                break;
            case "/crash/delete":
                Crash.delete(context);
                redirect(out, base, changed("crash"), null);
                break;
            case "/festival/move":
            case "/festival/hide":
            case "/festival/show":
            case "/festival/restore":
                redirect(out, base, festival(route, form), "festivals");
                break;
            case "/import": {
                String file = multipart ? multipartFile(body, type) : null;
                boolean restored = file != null && Backup.restore(prefs, file);
                if (restored) changed();
                redirect(out, base, restored ? "imported" : "notbackup", restored ? null : "backup");
                break;
            }
            default:
                redirect(out, base, null, null);
        }
    }

    /** Saves the dock from the page's four choices. The page swaps choices rather than repeat one; without its script, an app chosen twice keeps only its first place. */
    private String dock(Map<String, String> form) {
        Map<String, Apps.App> apps = Apps.load(context);
        List<String> dock = new ArrayList<>();
        for (int i = 0; i < Dock.SIZE; i++) {
            String pkg = form.getOrDefault("s" + i, Dock.EMPTY);
            dock.add(apps.containsKey(pkg) && !dock.contains(pkg) ? pkg : Dock.EMPTY);
        }
        Dock.set(prefs, dock);
        return changed("dock");
    }

    /**
     * Reads the request's head, up to its blank line ({@code head}), or exactly {@code limit} bytes of
     * body. Fails if the head runs past {@code limit} or the deadline passes.
     */
    private static byte[] read(Socket socket, InputStream in, long deadline, int limit, boolean head) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int matched = 0;
        while (bytes.size() < limit || head) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) throw new SocketTimeoutException("too slow");
            socket.setSoTimeout((int) left);
            int b = in.read();
            if (b < 0) break;
            if (head && bytes.size() >= limit) throw new IOException("head too long");
            bytes.write(b);
            // The head ends at the first CR LF CR LF.
            matched = b == (matched % 2 == 0 ? '\r' : '\n') ? matched + 1 : b == '\r' ? 1 : 0;
            if (head && matched == 4) return Arrays.copyOf(bytes.toByteArray(), bytes.size() - 4);
        }
        if (head) throw new IOException("no end to the head");
        return bytes.toByteArray();
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
        if (who.isEmpty() || who.length() > People.MAX_NAME || !People.Day.valid(month, day)) return null;
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

    /**
     * The first file in a multipart/form-data body, as text, or null. Enough for the one upload the
     * page has: a single small file, sent by a browser.
     */
    static String multipartFile(String body, String contentType) {
        int b = contentType.indexOf("boundary=");
        if (b < 0) return null;
        String boundary = contentType.substring(b + 9).trim();
        int semicolon = boundary.indexOf(';');
        if (semicolon >= 0) boundary = boundary.substring(0, semicolon);
        if (boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length() > 1) {
            boundary = boundary.substring(1, boundary.length() - 1);
        }
        if (boundary.isEmpty()) return null;
        String delimiter = "--" + boundary;
        for (int at = body.indexOf(delimiter); at >= 0; ) {
            int next = body.indexOf("\r\n" + delimiter, at + delimiter.length());
            if (next < 0) return null;
            String part = body.substring(at + delimiter.length(), next);
            int split = part.indexOf("\r\n\r\n");
            if (split >= 0 && part.substring(0, split).toLowerCase(Locale.ROOT).contains("filename=")) {
                return part.substring(split + 4);
            }
            at = next + 2;
        }
        return null;
    }

    static Map<String, String> parseForm(String body) {
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
        send(out, status, "text/html; charset=utf-8", "no-store", null, html.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(OutputStream out, String status, String type, String cache, String extra, byte[] bytes)
            throws IOException {
        out.write(("HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + bytes.length
                + "\r\nCache-Control: " + cache + "\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff"
                + "\r\nContent-Security-Policy: " + POLICY
                + (extra != null ? "\r\n" + extra : "")
                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
    }

    private void asset(OutputStream out, String name, String type) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = context.getAssets().open(name)) {
            byte[] buffer = new byte[16384];
            for (int n; (n = in.read(buffer)) > 0; ) bytes.write(buffer, 0, n);
        }
        send(out, "200 OK", type, "private, max-age=86400", null, bytes.toByteArray());
    }

    /** Back to the page after a change, scrolled to {@code section}, with a word about what happened. */
    private static void redirect(OutputStream out, String base, String done, String section) throws IOException {
        String to = base + (done != null ? "?s=" + done : "") + (section != null ? "#" + section : "");
        out.write(("HTTP/1.1 303 See Other\r\nLocation: " + to + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    // ---- pages ------------------------------------------------------------------------------

    /** A string in the TV's language, escaped for the page. */
    private String text(int id, Object... args) {
        return esc(context.getString(id, args));
    }

    private String home(String base, String done) {
        LocalDate today = Festival.today();
        String[] months = Dates.months(TextStyle.FULL);
        StringBuilder h = new StringBuilder();
        h.append("<div><h1>").append(text(R.string.page_title)).append("</h1>")
                .append("<p class=lede>").append(text(R.string.page_lede)).append("</p></div>");

        // The city.
        Place place = Place.load(prefs);
        h.append("<section id=city><h2>").append(text(R.string.page_city)).append("</h2><div class='card city'><div>")
                .append("<div class=label>").append(text(R.string.page_city_label)).append("</div>")
                .append("<div class=name>").append(esc(place.name)).append("</div>")
                .append("<div class=region>").append(esc(place.region)).append("</div></div>")
                .append("<form class=search method=post action='").append(base).append("/city'")
                .append(" data-searching='").append(text(R.string.city_searching))
                .append("' data-failed='").append(text(R.string.search_failed))
                .append("' data-none='").append(text(R.string.no_places, "{q}"))
                .append("' data-one='").append(esc(context.getResources().getQuantityString(R.plurals.places_found, 1, 1)))
                .append("' data-many='").append(esc(context.getResources().getQuantityString(R.plurals.places_found, 2, "{n}")))
                .append("'>")
                .append("<input type=search name=q placeholder='").append(text(R.string.page_city_search))
                .append("' aria-label='").append(text(R.string.city)).append("' required ")
                .append("autocomplete=off enterkeyhint=search><button>").append(text(R.string.search))
                .append("</button></form></div></section>");

        // A note from the last crash, if there is one.
        String crash = Crash.report(context);
        if (crash != null) {
            h.append("<section id=crash><h2>").append(text(R.string.crash_title)).append("</h2><div class='card crash'>")
                    .append("<p>").append(text(R.string.page_crash_about, Crash.when(crash))).append("</p>")
                    .append("<details><summary>").append(text(R.string.page_crash_show)).append("</summary><pre>")
                    .append(esc(crash)).append("</pre></details>")
                    .append("<div class=actions><a class=button href='").append(esc(Crash.issueLink(crash)))
                    .append("' target=_blank rel=noopener>").append(text(R.string.page_crash_report)).append("</a>")
                    .append("<form method=post action='").append(base).append("/crash/delete'>")
                    .append("<button class=quiet>").append(text(R.string.delete)).append("</button></form></div></div></section>");
        }

        // The dock.
        Map<String, Apps.App> apps = Apps.load(context);
        List<Apps.App> sorted = Apps.sorted(apps.values());
        List<String> dock = Dock.load(prefs, apps, Prefs.list(prefs, HomeActivity.HIDDEN));
        h.append("<section id=dock><h2>").append(text(R.string.dock)).append("</h2><form class='card dock' method=post action='")
                .append(base).append("/dock'>");
        for (int i = 0; i < Dock.SIZE; i++) {
            h.append("<label class=slot><span class=n aria-hidden=true>").append(i + 1).append("</span>")
                    .append("<select name=s").append(i).append(" aria-label='").append(text(R.string.page_dock_place, i + 1)).append("'>")
                    .append("<option value=''>").append(text(R.string.dock_empty)).append("</option>");
            for (Apps.App app : sorted) {
                h.append("<option value='").append(esc(app.pkg)).append('\'')
                        .append(app.pkg.equals(dock.get(i)) ? " selected" : "").append('>').append(esc(app.label)).append("</option>");
            }
            h.append("</select></label>");
        }
        h.append("<button class=wide>").append(text(R.string.save)).append("</button></form>")
                .append("<p class=note>").append(text(R.string.page_dock_note)).append("</p></section>");

        // The family's days.
        List<People.Day> days = People.load(prefs);
        h.append("<section id=family><h2>").append(text(R.string.welcome_family));
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
                    .append("<span class=sub>").append(text(d.birthday ? R.string.birthday : R.string.anniversary)).append(" · ")
                    .append(d.day).append(' ').append(esc(months[d.month - 1])).append("</span></span>")
                    .append(when(until))
                    .append("<span class=chev aria-hidden=true></span></summary>")
                    .append("<form class=edit method=post action='").append(base).append("/edit'>")
                    .append(hidden("d", d.key()))
                    .append(dayFields("e" + i, d, months))
                    .append("<div class=actions><button>").append(text(R.string.save)).append("</button>")
                    .append("<button class=quiet formaction='").append(base).append("/remove' formnovalidate>")
                    .append(text(R.string.remove)).append("</button>")
                    .append("</div></form></details></li>");
        }
        h.append("<li><details class=add").append(days.isEmpty() ? " open" : "").append(">")
                .append("<summary><span class=badge aria-hidden=true>+</span>").append(text(R.string.add_day)).append("</summary>")
                .append("<form class=edit method=post action='").append(base).append("/add'>")
                .append(dayFields("new", null, months))
                .append("<button class=wide>").append(text(R.string.add)).append("</button></form></details></li></ul>")
                .append("<p class=note>").append(text(R.string.page_family_note)).append("</p>")
                .append("</section>");

        // The festivals, a year ahead, by month.
        h.append("<section id=festivals><h2>").append(text(R.string.festivals)).append(" <small>")
                .append(text(R.string.page_next_12_months)).append("</small></h2><ul class='card list'>");
        String month = "";
        List<Festival> coming = Festival.editable(today);
        boolean folded = false;
        for (int i = 0; i < coming.size(); i++) {
            Festival f = coming.get(i);
            // The next three months in full; the rest of the year behind a tap.
            if (!folded && f.date.isAfter(today.plusMonths(OPEN_MONTHS))) {
                folded = true;
                h.append("<li><details class=more><summary>")
                        .append(esc(context.getResources().getQuantityString(R.plurals.page_show_more, coming.size() - i,
                                coming.size() - i, Dates.month(coming.get(coming.size() - 1).date.getMonth(), TextStyle.FULL))))
                        .append("</summary><ul class=list>");
            }
            String m = Dates.month(f.date.getMonth(), TextStyle.FULL) + " " + f.date.getYear();
            if (!m.equals(month)) {
                h.append("<li class=month>").append(esc(m)).append("</li>");
                month = m;
            }
            h.append("<li").append(f.hidden ? " class=hidden-day" : "").append("><details><summary class=item>")
                    .append("<span class=date><b>").append(f.date.getDayOfMonth()).append("</b><span>")
                    .append(esc(Dates.weekday(f.date.getDayOfWeek(), TextStyle.SHORT))).append("</span></span>")
                    .append("<span class=text><span class=title>").append(esc(f.title(context))).append("</span>");
            if (f.hidden || f.moved() || f.expected) {
                h.append("<span class=sub>");
                if (f.hidden) h.append("<span class=tag>").append(text(R.string.hidden)).append("</span>");
                if (f.moved()) {
                    h.append("<span class='tag moved'>").append(text(R.string.page_moved_from, Dates.dayMonth(f.original))).append("</span>");
                } else if (f.expected) {
                    h.append("<span class=tag>").append(text(R.string.page_expected)).append("</span>");
                }
                h.append("</span>");
            }
            h.append("</span>").append(f.hidden ? "" : when(ChronoUnit.DAYS.between(today, f.date)))
                    .append("<span class=chev aria-hidden=true></span></summary><div class=edit>")
                    .append("<form class=actions method=post action='").append(base).append("/festival/move'>")
                    .append(hidden("k", f.key()))
                    .append("<input type=date name=date aria-label='").append(text(R.string.page_new_date))
                    .append("' required value=").append(f.date)
                    .append(" min=").append(f.clampMove(LocalDate.MIN))
                    .append(" max=").append(f.clampMove(LocalDate.MAX)).append(">")
                    .append("<button>").append(text(R.string.page_move)).append("</button></form><div class=actions>")
                    .append("<form method=post action='").append(base).append(f.hidden ? "/festival/show'>" : "/festival/hide'>")
                    .append(hidden("k", f.key())).append("<button class=ghost>")
                    .append(text(f.hidden ? R.string.page_show_again : R.string.page_hide_year)).append("</button></form>");
            if (f.moved()) {
                h.append("<form method=post action='").append(base).append("/festival/restore'>")
                        .append(hidden("k", f.key())).append("<button class=quiet>")
                        .append(text(R.string.back_to, Dates.dayMonth(f.original))).append("</button></form>");
            }
            h.append("</div>");
            if (f.expected && !f.moved()) {
                h.append("<p class=hint>").append(text(R.string.page_moon_hint)).append("</p>");
            }
            h.append("</div></details></li>");
        }
        if (folded) h.append("</ul></details></li>");
        h.append("</ul><p class=note>").append(text(R.string.page_festival_note)).append("</p></section>");

        // A backup to keep on the phone, and to restore on a new TV.
        h.append("<section id=backup><h2>").append(text(R.string.page_backup)).append("</h2><div class='card backup'>")
                .append("<p class=note>").append(text(R.string.page_backup_about)).append("</p>")
                .append("<a class=button href='").append(base).append("/export' download>")
                .append(text(R.string.page_export)).append("</a>")
                .append("<form class=actions method=post action='").append(base).append("/import' enctype=multipart/form-data>")
                .append("<input type=file name=file accept='.json,application/json' required aria-label='")
                .append(text(R.string.page_backup_file)).append("'><button class=ghost>").append(text(R.string.page_import))
                .append("</button></form><p class=hint>").append(text(R.string.page_import_note)).append("</p></div></section>");
        return page(base, context.getString(R.string.app_name), h.toString(), done);
    }

    /** Name, birthday or anniversary, and the date, for adding or editing a family day. */
    private String dayFields(String id, People.Day d, String[] months) {
        boolean birthday = d == null || d.birthday;
        StringBuilder h = new StringBuilder()
                .append("<input type=text name=who required maxlength=").append(People.MAX_NAME).append(" autocomplete=off autocapitalize=words ")
                .append("aria-label='").append(text(R.string.page_name)).append("' placeholder='").append(text(R.string.name_hint)).append("'")
                .append(d != null ? " value='" + esc(d.who) + "'" : "").append('>')
                .append("<div class=kind role=radiogroup aria-label='").append(text(R.string.page_kind)).append("'>")
                .append("<input type=radio name=kind value=birthday id=b").append(id).append(birthday ? " checked" : "")
                .append("><label for=b").append(id).append('>').append(text(R.string.birthday)).append("</label>")
                .append("<input type=radio name=kind value=anniversary id=a").append(id).append(birthday ? "" : " checked")
                .append("><label for=a").append(id).append('>').append(text(R.string.anniversary)).append("</label></div>")
                .append("<div class=row><select name=day aria-label='").append(text(R.string.page_day)).append("'>");
        for (int i = 1; i <= 31; i++) {
            h.append("<option").append(d != null && d.day == i ? " selected" : "").append('>').append(i).append("</option>");
        }
        h.append("</select><select name=month aria-label='").append(text(R.string.page_month)).append("'>");
        for (int m = 1; m <= 12; m++) {
            h.append("<option value=").append(m).append(d != null && d.month == m ? " selected" : "").append('>')
                    .append(esc(months[m - 1])).append("</option>");
        }
        return h.append("</select></div>").toString();
    }

    private String cityResults(String base, String query) {
        StringBuilder h = new StringBuilder("<div><a class=back href='").append(base).append("#city'>‹ ")
                .append(text(R.string.page_back)).append("</a>")
                .append("<h1>").append(text(R.string.choose_city)).append("</h1></div><section><ul class='card list'>");
        try {
            List<Place> places = Place.search(query, Place.load(prefs).country);
            if (places.isEmpty()) {
                h.append("<li class=item><span class=text>").append(text(R.string.no_places, query.trim()))
                        .append("</span></li>");
            }
            for (Place p : places) {
                h.append("<li><form method=post action='").append(base).append("/city/set'>")
                        .append(hidden("name", p.name)).append(hidden("region", p.region))
                        .append(hidden("lat", String.valueOf(p.lat))).append(hidden("lon", String.valueOf(p.lon)))
                        .append(hidden("tz", p.timezone)).append(hidden("cc", p.country))
                        .append("<button class=place><span><b>").append(esc(p.name)).append("</b><span>")
                        .append(esc(p.describe(context.getResources()))).append("</span></span><span class='chev go'></span>")
                        .append("</button></form></li>");
            }
        } catch (Exception e) {
            h.append("<li class=item><span class=text>").append(text(R.string.search_failed)).append("</span></li>");
        }
        h.append("</ul></section>");
        return page(base, context.getString(R.string.choose_city), h.toString(), null);
    }

    /** Places matching {@code query}, for the page's suggestions as you type; null if the search failed. */
    private String cityJson(String query) {
        if (query.trim().length() < 2) return "[]";
        try {
            JSONArray a = new JSONArray();
            for (Place p : Place.search(query, Place.load(prefs).country)) {
                a.put(new JSONObject().put("name", p.name).put("region", p.region).put("about", p.describe(context.getResources()))
                        .put("lat", p.lat).put("lon", p.lon).put("tz", p.timezone).put("cc", p.country));
            }
            return a.toString();
        } catch (Exception e) {
            return "null";
        }
    }

    /** "Today", "Tomorrow" or "in 9 days", for anything within {@link #WHEN_DAYS}; otherwise nothing. */
    private String when(long days) {
        if (days > WHEN_DAYS) return "";
        return "<span class='when" + (Dates.soon(days) ? " soon" : "") + "'>"
                + esc(Dates.until(context.getResources(), days, false)) + "</span>";
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

    /**
     * Shown for any address without the current secret. It links to nothing and loads nothing, so it
     * gives nothing away.
     */
    private String expired() {
        return "<!doctype html><html lang=" + Locale.getDefault().toLanguageTag() + "><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width,initial-scale=1'><title>" + text(R.string.page_expired_title)
                + "</title><h1>" + text(R.string.page_expired_title) + "</h1><p>" + text(R.string.page_expired_body) + "</p></html>";
    }

    /** A whole page; {@code title} and {@code done} are plain text, escaped here. */
    private String page(String base, String title, String body, String done) {
        String tv = Settings.Global.getString(context.getContentResolver(), "device_name");
        return "<!doctype html><html lang=" + Locale.getDefault().toLanguageTag() + "><head><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width,initial-scale=1,viewport-fit=cover'>"
                + "<meta name=theme-color content='#f5eee2' media='(prefers-color-scheme: light)'>"
                + "<meta name=theme-color content='#14100d' media='(prefers-color-scheme: dark)'>"
                + "<title>" + esc(title) + "</title>"
                + "<link rel=icon href='" + base + "/mark.svg'><link rel=stylesheet href='" + base + "/page.css'>"
                + "<script src='" + base + "/page.js' defer></script>"
                + "</head><body><main><header class=mast><img src='" + base + "/mark.svg' alt=''><div><b>Aangan</b>"
                + "<span>" + (tv == null || tv.isEmpty() ? text(R.string.page_your_tv) : esc(tv)) + "</span></div></header>"
                + body
                + "<footer><img src='" + base + "/mark.svg' alt=''><div>" + text(R.string.page_footer_private) + "</div>"
                + "<div>" + text(R.string.page_footer_open) + "</div></footer></main>"
                + (done != null ? "<div class=toast role=status>" + esc(done) + "</div>" : "")
                + "</body></html>";
    }
}
