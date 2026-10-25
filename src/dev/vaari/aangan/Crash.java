package dev.vaari.aangan;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Aangan sends no crash reports. If it crashes, a short note stays on the TV instead: the app and
 * Android versions, the TV model, and where in the code it failed. Settings mentions it, and the
 * phone page shows it in full, with a link that opens a pre-filled GitHub issue the person can
 * read, edit and submit themselves, or a button to delete it.
 *
 * Installed for the whole app (home screen, Settings, the Home-button service and the
 * screensaver) as its {@link Application}. Only the latest report is kept.
 */
public class Crash extends Application implements Thread.UncaughtExceptionHandler {

    private static final String FILE = "crash.txt";
    private static final String ISSUES = "https://github.com/vaari-dev/aangan/issues/new";
    /** Enough for the stack trace that matters, and short enough for a GitHub link. */
    private static final int MAX_REPORT = 4000;

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
                    + "\n\n" + Log.getStackTraceString(error);
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

    /** The last crash's report, or null if there's none. */
    static String report(Context context) {
        File f = new File(context.getFilesDir(), FILE);
        try {
            return f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** "25 Oct", when the last crash happened, from the report's second line. */
    static String when(String report) {
        String[] lines = report.split("\n");
        return lines.length > 1 ? lines[1].replaceFirst(" \\d{4},.*", "") : "";
    }

    static void delete(Context context) {
        //noinspection ResultOfMethodCallIgnored
        new File(context.getFilesDir(), FILE).delete();
    }

    /** A link to a new GitHub issue with the report filled in, for the person to review and submit. */
    static String issueLink(String report) {
        String body = "What were you doing when Aangan closed?\n\n\n"
                + "The note Aangan kept on the TV:\n```\n" + report + "\n```\n";
        return ISSUES + "?title=" + Uri.encode("Crash: " + report.split("\n")[0]) + "&body=" + Uri.encode(body);
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
