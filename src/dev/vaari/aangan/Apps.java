package dev.vaari.aangan;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The TV's launchable apps, for the home screen, the dock chooser in Settings and the phone page. */
final class Apps {

    static final class App {
        String pkg;
        String label;
        Intent intent;
        Drawable banner;
        Drawable icon;
    }

    private Apps() {
    }

    /** Every app with a TV or phone launcher entry, except Aangan, keyed by package name. */
    static Map<String, App> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<String, App> apps = new LinkedHashMap<>();
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String category : categories) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(context.getPackageName()) || apps.containsKey(pkg)) continue;
                App app = new App();
                app.pkg = pkg;
                app.label = ri.loadLabel(pm).toString();
                app.intent = new Intent(Intent.ACTION_MAIN)
                        .addCategory(category)
                        .setComponent(new ComponentName(pkg, ri.activityInfo.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                app.banner = ri.activityInfo.loadBanner(pm);
                app.icon = ri.loadIcon(pm);
                apps.put(pkg, app);
            }
        }
        return apps;
    }

    /** {@code apps} in alphabetical order of their names. */
    static List<App> sorted(Collection<App> apps) {
        List<App> list = new ArrayList<>(apps);
        Collections.sort(list, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return list;
    }
}
