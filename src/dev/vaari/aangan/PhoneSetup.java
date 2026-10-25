package dev.vaari.aangan;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A small page on the home network for setting the city and adding birthdays and anniversaries
 * from a phone, which is far easier than typing with a remote. It runs only while Settings is on
 * screen, answers only on the local network at an address with a fresh secret in it, and keeps
 * everything on the TV.
 */
final class PhoneSetup {

    private static final int PORT = 8737;
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December"};

    private final SharedPreferences prefs;
    private final Runnable onChange;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String secret;
    private ServerSocket server;

    PhoneSetup(SharedPreferences prefs, Runnable onChange) {
        this.prefs = prefs;
        this.onChange = onChange;
        SecureRandom random = new SecureRandom();
        String alphabet = "abcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 10; i++) s.append(alphabet.charAt(random.nextInt(alphabet.length())));
        secret = s.toString();
    }

    /** Starts serving and returns the page's address, or null when the TV has no network. */
    String start(Context context) {
        String ip = localAddress(context);
        if (ip == null) return null;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            try {
                server.bind(new InetSocketAddress(PORT));
            } catch (IOException taken) {
                server = new ServerSocket(0);
            }
        } catch (IOException e) {
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
                socket.setSoTimeout(10_000);
                handle(socket);
            } catch (IOException e) {
                // A closed server ends the loop; a broken connection is just dropped.
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
        char[] body = new char[Math.min(length, 8192)];
        for (int read = 0; read < body.length; ) {
            int n = in.read(body, read, body.length - read);
            if (n < 0) break;
            read += n;
        }
        Map<String, String> form = parseForm(new String(body));
        OutputStream out = socket.getOutputStream();
        String base = "/" + secret;
        String path = parts[1];

        if (!path.equals(base) && !path.startsWith(base + "/")) {
            respond(out, "404 Not Found", page("Not found", "<p>This setup link has expired. Open Aangan's Settings on the TV for a new one.</p>"));
        } else if (parts[0].equals("POST") && path.equals(base + "/add")) {
            String who = form.getOrDefault("who", "").trim();
            int month = parseInt(form.get("month"));
            int day = parseInt(form.get("day"));
            if (!who.isEmpty() && month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                People.add(prefs, new People.Day(month, day, !"anniversary".equals(form.get("kind")), who));
                changed();
            }
            redirect(out, base);
        } else if (parts[0].equals("POST") && path.equals(base + "/remove")) {
            People.remove(prefs, parseInt(form.get("i")));
            changed();
            redirect(out, base);
        } else if (parts[0].equals("POST") && path.equals(base + "/city")) {
            respond(out, "200 OK", cityResults(base, form.getOrDefault("q", "")));
        } else if (parts[0].equals("POST") && path.equals(base + "/city/set")) {
            try {
                new Place(form.get("name"), form.getOrDefault("region", ""), Double.parseDouble(form.get("lat")),
                        Double.parseDouble(form.get("lon")), form.get("tz"), form.getOrDefault("cc", "")).save(prefs);
                Weather.clear(prefs);
                changed();
            } catch (RuntimeException ignored) {
                // A malformed form leaves the city as it was.
            }
            redirect(out, base);
        } else {
            respond(out, "200 OK", home(base));
        }
    }

    private void changed() {
        main.post(onChange);
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
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 " + status + "\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + bytes.length
                + "\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
    }

    private static void redirect(OutputStream out, String to) throws IOException {
        out.write(("HTTP/1.1 303 See Other\r\nLocation: " + to + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    // ---- pages ------------------------------------------------------------------------------

    private String home(String base) {
        Place place = Place.load(prefs);
        StringBuilder h = new StringBuilder();
        h.append("<section><h2>City</h2><p class=now>").append(esc(place.name)).append(" <span>")
                .append(esc(place.region)).append("</span></p>")
                .append("<form method=post action='").append(base).append("/city'>")
                .append("<input name=q placeholder='Search for a city' required autocomplete=off>")
                .append("<button>Search</button></form></section>");

        h.append("<section><h2>Birthdays &amp; anniversaries</h2>");
        List<People.Day> days = People.load(prefs);
        if (days.isEmpty()) h.append("<p class=muted>None yet. They appear on the TV on the day, with a countdown before.</p>");
        h.append("<ul>");
        for (int i = 0; i < days.size(); i++) {
            People.Day d = days.get(i);
            h.append("<li><span><b>").append(esc(d.who)).append("</b> ").append(d.birthday ? "Birthday" : "Anniversary")
                    .append(" · ").append(d.day).append(' ').append(MONTHS[d.month - 1]).append("</span>")
                    .append("<form method=post action='").append(base).append("/remove'><input type=hidden name=i value=")
                    .append(i).append("><button class=quiet>Remove</button></form></li>");
        }
        h.append("</ul><form method=post action='").append(base).append("/add' class=add>")
                .append("<input name=who placeholder='Name, e.g. Asha or Asha &amp; Rohan' required maxlength=40>")
                .append("<div class=row><select name=kind><option value=birthday>Birthday</option>")
                .append("<option value=anniversary>Anniversary</option></select>")
                .append("<select name=day>");
        for (int d = 1; d <= 31; d++) h.append("<option>").append(d).append("</option>");
        h.append("</select><select name=month>");
        for (int m = 1; m <= 12; m++) h.append("<option value=").append(m).append('>').append(MONTHS[m - 1]).append("</option>");
        h.append("</select></div><button>Add</button></form></section>")
                .append("<p class=muted>Saved only on your TV. This page works only while Aangan's Settings are open on the TV.</p>");
        return page("Aangan", h.toString());
    }

    private String cityResults(String base, String query) {
        StringBuilder h = new StringBuilder("<section><h2>Choose your city</h2>");
        try {
            List<Place> places = Place.search(query);
            if (places.isEmpty()) h.append("<p class=muted>No places called “").append(esc(query)).append("”.</p>");
            for (Place p : places) {
                h.append("<form method=post action='").append(base).append("/city/set'>")
                        .append(hidden("name", p.name)).append(hidden("region", p.region))
                        .append(hidden("lat", String.valueOf(p.lat))).append(hidden("lon", String.valueOf(p.lon)))
                        .append(hidden("tz", p.timezone)).append(hidden("cc", p.country))
                        .append("<button class=place><b>").append(esc(p.name)).append("</b><span>")
                        .append(esc(p.region)).append("</span></button></form>");
            }
        } catch (Exception e) {
            h.append("<p class=muted>Couldn't search right now. Check the TV's internet connection.</p>");
        }
        h.append("<p><a href='").append(base).append("'>Back</a></p></section>");
        return page("Choose your city", h.toString());
    }

    private static String hidden(String name, String value) {
        return "<input type=hidden name=" + name + " value='" + esc(value) + "'>";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;").replace("\"", "&quot;");
    }

    private static String page(String title, String body) {
        return "<!doctype html><html lang=en><head><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width,initial-scale=1'><title>" + esc(title) + "</title><style>"
                + ":root{color-scheme:light dark;--bg:#f6f4f1;--card:#fff;--ink:#1c1b1a;--muted:#77716b;--line:#e6e1db;--accent:#c4621f}"
                + "@media(prefers-color-scheme:dark){:root{--bg:#121214;--card:#1d1d21;--ink:#f3f1ee;--muted:#9a958f;--line:#2c2c31}}"
                + "body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.45 system-ui,-apple-system,sans-serif}"
                + "main{max-width:480px;margin:0 auto;padding:24px 16px 40px}h1{font-size:28px;margin:0 0 4px}"
                + "h1+p{color:var(--muted);margin:0 0 20px}h2{font-size:15px;text-transform:uppercase;letter-spacing:.06em;color:var(--muted);margin:0 0 12px}"
                + "section{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:16px;margin:0 0 16px}"
                + "input,select,button{font:inherit;border-radius:10px;border:1px solid var(--line);padding:10px 12px;background:var(--bg);color:var(--ink)}"
                + "input{width:100%;box-sizing:border-box;margin:0 0 10px}button{background:var(--accent);color:#fff;border:0;font-weight:600;cursor:pointer}"
                + "form{margin:0}.row{display:flex;gap:8px;margin:0 0 10px}.row select{flex:1}.add button,section>form>button{width:100%}"
                + "ul{list-style:none;padding:0;margin:0 0 14px}li{display:flex;justify-content:space-between;align-items:center;gap:8px;padding:10px 0;border-bottom:1px solid var(--line)}"
                + "button.quiet{background:none;color:var(--accent);padding:6px 0}.now{font-size:20px;font-weight:600;margin:0 0 12px}.now span,.muted{color:var(--muted);font-size:14px;font-weight:400}"
                + "button.place{display:flex;flex-direction:column;align-items:flex-start;width:100%;margin:0 0 8px;background:var(--bg);color:var(--ink);border:1px solid var(--line)}"
                + "button.place span{color:var(--muted);font-weight:400;font-size:14px}a{color:var(--accent)}"
                + "</style></head><body><main><h1>Aangan</h1><p>Set up your TV's home screen</p>" + body + "</main></body></html>";
    }
}
