package dev.vaari.aangan;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/**
 * Makes Aangan the home screen without adb. Home is caught before the system sees it: once the
 * system handles Home, it holds back other apps' activities for seconds, so the stock launcher wins.
 */
public class HomeButton extends AccessibilityService {

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) openAangan();
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) openAangan();
    }

    private void openAangan() {
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    @Override
    public void onInterrupt() {
    }

    /** Google Play requires this disclosure and consent before the accessibility settings open. */
    static void askToTurnOn(Activity activity) {
        if (isOn(activity)) {
            openSettings(activity);
            return;
        }
        Ui.dialog(activity, activity.getString(R.string.disclosure_title))
                .setMessage(activity.getString(R.string.disclosure_body))
                .setPositiveButton(activity.getString(R.string.disclosure_agree), (d, w) -> openSettings(activity))
                .setNegativeButton(activity.getString(R.string.not_now), null)
                .show();
    }

    /** Sony's Android 9 TVs don't answer the standard action, but have the page under their own name. */
    private static void openSettings(Activity activity) {
        if (!Ui.openSettings(activity, Settings.ACTION_ACCESSIBILITY_SETTINGS,
                new ComponentName("com.android.tv.settings", "com.sony.dtv.settings.accessibility.AccessibilityActivity"),
                new ComponentName("com.android.tv.settings", "com.android.tv.settings.accessibility.AccessibilityActivity"))) {
            Toast.makeText(activity, activity.getString(R.string.find_accessibility), Toast.LENGTH_LONG).show();
        }
    }

    static boolean isOn(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName me = new ComponentName(context, HomeButton.class);
        return enabled != null && (enabled.contains(me.flattenToString()) || enabled.contains(me.flattenToShortString()));
    }
}
