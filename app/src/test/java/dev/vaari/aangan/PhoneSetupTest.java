package dev.vaari.aangan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/** The phone page over a real socket, as anything on the home network could reach it. */
@RunWith(RobolectricTestRunner.class)
public class PhoneSetupTest {

    private SharedPreferences prefs;
    private PhoneSetup setup;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        prefs = Prefs.of(context);
        setup = new PhoneSetup(context, prefs, () -> { });
    }

    /** Sends a request, with "{host}" in it replaced by the server's address, and returns the whole response. */
    private String exchange(String head, byte[] body) throws IOException {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (ServerSocket server = new ServerSocket(0, 1, loopback);
             Socket client = new Socket(loopback, server.getLocalPort())) {
            setup.host = loopback.getHostAddress() + ":" + server.getLocalPort();
            OutputStream out = client.getOutputStream();
            out.write((head.replace("{host}", setup.host) + "Content-Length: " + body.length + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write(body);
            out.flush();
            try (Socket accepted = server.accept()) {
                setup.handle(accepted, System.currentTimeMillis() + 5000);
            }
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[16384];
            for (int n; (n = in.read(buffer)) > 0; ) response.write(buffer, 0, n);
            return response.toString("UTF-8");
        }
    }

    private String get(String path) throws IOException {
        return exchange("GET /" + setup.secret + path + " HTTP/1.1\r\nHost: {host}\r\n", new byte[0]);
    }

    private String post(String path, String form) throws IOException {
        return exchange("POST /" + setup.secret + path + " HTTP/1.1\r\nHost: {host}\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\n", form.getBytes(StandardCharsets.UTF_8));
    }

    private String upload(String file) throws IOException {
        String body = "--XyZ\r\nContent-Disposition: form-data; name=\"file\"; filename=\"aangan.json\"\r\n"
                + "Content-Type: application/json\r\n\r\n" + file + "\r\n--XyZ--\r\n";
        return exchange("POST /" + setup.secret + "/import HTTP/1.1\r\nHost: {host}\r\n"
                + "Content-Type: multipart/form-data; boundary=XyZ\r\n", body.getBytes(StandardCharsets.UTF_8));
    }

    private static String location(String response) {
        for (String line : response.split("\r\n")) if (line.startsWith("Location: ")) return line.substring(10);
        throw new AssertionError("no redirect in " + response);
    }

    // ---- who gets an answer -----------------------------------------------------------------

    @Test
    public void onlyTheTvsOwnAddressIsAnswered() throws IOException {
        String r = exchange("GET /" + setup.secret + " HTTP/1.1\r\nHost: tv.example.com\r\n", new byte[0]);
        assertTrue(r, r.startsWith("HTTP/1.1 421 "));
    }

    @Test
    public void anOldLinkGivesNothingAway() throws IOException {
        String r = exchange("GET /oldsecret HTTP/1.1\r\nHost: {host}\r\n", new byte[0]);
        assertTrue(r, r.startsWith("HTTP/1.1 404 "));
        assertTrue(r.contains("This link has expired"));
        assertFalse("the expired page leaks the secret", r.contains(setup.secret));
    }

    @Test
    public void thePageLoadsWithItsSecurityPolicy() throws IOException {
        String r = get("");
        assertTrue(r, r.startsWith("HTTP/1.1 200 "));
        assertTrue(r.contains("Content-Security-Policy: default-src 'none'"));
        assertTrue(r.contains("id=backup"));
    }

    @Test
    public void whatThePageSaysIsEscapedOnce() throws IOException {
        String r = get("?s=restored");
        assertTrue(r, r.contains("<div class=toast role=status>Back to the calendar&#39;s date</div>"));
        assertFalse(r.contains("&amp;#39;"));
    }

    // ---- changes ----------------------------------------------------------------------------

    @Test
    public void aNameInHindiIsReadWhole() throws IOException {
        // More bytes than characters: Content-Length counts bytes.
        String r = post("/add", "who=%E0%A4%86%E0%A4%B6%E0%A4%BE&month=1&day=9&kind=birthday");
        assertTrue(location(r).endsWith("?s=added#family"));
        assertEquals("आशा", People.load(prefs).get(0).who);
    }

    @Test
    public void aBackupGoesToThePhoneAndBack() throws IOException {
        People.add(prefs, new People.Day(1, 9, true, "आशा"));
        new Place("Pune", "Maharashtra, India", 18.52, 73.86, "Asia/Kolkata", "IN").save(prefs);
        String exported = get("/export");
        assertTrue(exported.contains("Content-Disposition: attachment; filename=\"aangan-backup-"));
        String file = exported.substring(exported.indexOf("\r\n\r\n") + 4);

        prefs.edit().clear().commit();
        assertTrue(location(upload(file)).endsWith("?s=imported"));
        assertEquals("आशा", People.load(prefs).get(0).who);
        assertEquals("Pune", Place.load(prefs).name);
    }

    @Test
    public void aFileThatIsntABackupChangesNothing() throws IOException {
        People.add(prefs, new People.Day(1, 9, true, "Asha"));
        String before = prefs.getAll().toString();
        assertTrue(location(upload("{\"hello\":1}")).endsWith("?s=notbackup#backup"));
        // Nested deeply enough to exhaust the JSON parser's stack, were it parsed.
        char[] open = new char[20_000];
        Arrays.fill(open, '[');
        assertTrue(location(upload(new String(open))).endsWith("?s=notbackup#backup"));
        assertEquals(before, prefs.getAll().toString());
    }

    @Test
    public void anUploadTooBigIsReadAndRefused() throws IOException {
        char[] big = new char[PhoneSetup.MAX_BODY * 2];
        Arrays.fill(big, ' ');
        assertTrue(location(upload("{\"app\":\"Aangan\",\"format\":1}" + new String(big))).endsWith("?s=notbackup#backup"));
    }

    // ---- parsing ----------------------------------------------------------------------------

    @Test
    public void multipartWithQuotedBoundaryAndOtherFields() {
        String body = "--b1\r\nContent-Disposition: form-data; name=\"note\"\r\n\r\nhello\r\n"
                + "--b1\r\nContent-Disposition: form-data; name=\"file\"; filename=\"x.json\"\r\n\r\n{}\r\n--b1--\r\n";
        assertEquals("{}", PhoneSetup.multipartFile(body, "multipart/form-data; boundary=\"b1\""));
        assertNull(PhoneSetup.multipartFile(body, "multipart/form-data"));
        assertNull(PhoneSetup.multipartFile("--b1\r\nno end", "multipart/form-data; boundary=b1"));
    }

    @Test
    public void badFormEncodingIsSkipped() {
        Map<String, String> form = PhoneSetup.parseForm("a=1&b=%ZZ&=x&c");
        assertEquals("1", form.get("a"));
        assertEquals(1, form.size());
    }
}
