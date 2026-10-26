package dev.vaari.aangan;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

/**
 * Aangan sends no crash reports. If it crashes, a short note stays on the TV instead: the app and
 * Android versions, the TV model, and where in the code it failed. Not the error's message, which
 * can quote a family member's name or anything else the TV keeps. Settings mentions it, and the
 * phone page shows it in full, with a link that opens a pre-filled GitHub issue the person can
 * read, edit and submit themselves, or a button to delete it.
 *
 * Installed for the whole app (home screen, Settings, the Home-button service and the
 * screensaver) as its {@link Application}. Only the latest report is kept.
 */
public class Crash extends Application implements Thread.UncaughtExceptionHandler {

    private static final String FILE = "crash.txt";
    private static final String ISSUES = "https://github.com/vaari-dev/aangan/issues/new";
    /** Enough for the stack trace that matters. */
    private static final int MAX_REPORT = 4000;
    /** Browsers and GitHub turn away much longer links. */
    static final int MAX_LINK = 6000;

    private Thread.UncaughtExceptionHandler next;

    @Override
    public void onCreate() {
        super.onCreate();
        next = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable error) {
        try {
            String report = "Aangan " + version(this) + " on Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT
                    + "), " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                    + new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ENGLISH).format(new Date())
                    + "\n\n" + trace(error);
            if (report.length() > MAX_REPORT) report = report.substring(0, MAX_REPORT) + "\n…";
            // Written straight to disk: the process is about to end.
            try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), FILE))) {
                out.write(report.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
            // Keeping a report must never get in the way of the crash being handled.
        }
        if (next != null) next.uncaughtException(thread, error);
    }

    /** The error's kind and where in the code it happened, for it and each of its causes. */
    static String trace(Throwable error) {
        StringBuilder b = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = error; t != null && seen.add(t); t = t.getCause()) {
            if (t != error) b.append("Caused by: ");
            b.append(t.getClass().getName()).append('\n');
            for (StackTraceElement frame : t.getStackTrace()) b.append("    at ").append(frame).append('\n');
        }
        return b.toString();
    }

    /** The last crash's report, or null if there's none. */
    static String report(Context context) {
        File f = new File(context.getFilesDir(), FILE);
        try {
            return f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** "25 Oct", in the TV's language: when the last crash happened, from the report's second line. */
    static String when(String report) {
        String[] lines = report.split("\n");
        try {
            Date day = new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ENGLISH).parse(lines.length > 1 ? lines[1] : "");
            return new SimpleDateFormat("d MMM", Locale.getDefault()).format(day);
        } catch (ParseException e) {
            return "";
        }
    }

    static void delete(Context context) {
        //noinspection ResultOfMethodCallIgnored
        new File(context.getFilesDir(), FILE).delete();
    }

    /** A link to a new GitHub issue with the report filled in, for the person to review and submit. */
    static String issueLink(String report) {
        String title = ISSUES + "?title=" + Uri.encode("Crash: " + report.split("\n")[0]) + "&body=";
        // Encoding can triple the length, so the note is cut until the link fits.
        for (int keep = report.length(); ; keep = keep * 3 / 4) {
            String note = keep < report.length() ? report.substring(0, keep) + "\n…" : report;
            String link = title + Uri.encode("What were you doing when Aangan closed?\n\n\n"
                    + "The note Aangan kept on the TV:\n```\n" + note + "\n```\n");
            if (link.length() <= MAX_LINK || keep == 0) return link;
        }
    }

    private static String version(Context context) {
        try {
            PackageInfo p = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return p.versionName + " (" + p.getLongVersionCode() + ")";
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }
}
