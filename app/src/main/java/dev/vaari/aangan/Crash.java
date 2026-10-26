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

/** A note of the last crash, kept on the TV. Never the error's message, which can quote what the TV keeps. */
public class Crash extends Application implements Thread.UncaughtExceptionHandler {

    private static final String FILE = "crash.txt";
    private static final String STAMP = "d MMM yyyy, HH:mm";
    private static final String ISSUES = "https://github.com/vaari-dev/aangan/issues/new";
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
                    + new SimpleDateFormat(STAMP, Locale.ENGLISH).format(new Date())
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

    static String report(Context context) {
        File f = new File(context.getFilesDir(), FILE);
        try {
            return f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    static String when(String report) {
        String[] lines = report.split("\n");
        try {
            Date day = new SimpleDateFormat(STAMP, Locale.ENGLISH).parse(lines.length > 1 ? lines[1] : "");
            return new SimpleDateFormat("d MMM", Locale.getDefault()).format(day);
        } catch (ParseException e) {
            return "";
        }
    }

    static void delete(Context context) {
        //noinspection ResultOfMethodCallIgnored
        new File(context.getFilesDir(), FILE).delete();
    }

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
