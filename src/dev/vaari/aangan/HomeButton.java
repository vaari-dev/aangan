package dev.vaari.aangan;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Makes Aangan the home screen without adb. Most TVs offer no way to change the default home
 * screen, so once this is switched on (Settings, Accessibility, Aangan) it takes the Home button
 * for Aangan, and opens Aangan if the stock home screen appears anyway (as it does at start-up).
 *
 * The Home button has to be caught before the system sees it: once the system handles Home, it
 * holds back other apps' activities for several seconds, so the stock launcher would win. Every
 * other button is passed through untouched. Window events come from the stock launchers only,
 * as listed in res/xml/home_button.xml, and nothing on screen can be read.
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

    /**
     * Says plainly what the service does, and only on agreement opens Android's accessibility
     * settings to switch it on. Google Play requires this disclosure and consent first.
     */
    static void askToTurnOn(Activity activity) {
        if (isOn(activity)) {
            openSettings(activity);
            return;
        }
        new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Open Aangan with the Home button")
                .setMessage("Aangan uses Android's accessibility service for one thing: when you press Home, "
                        + "or the TV's own home screen appears, it opens Aangan instead.\n\n"
                        + "It reacts to the Home button only; every other button passes straight through. "
                        + "It can't see what's on screen or what you watch, and it stores and sends nothing.\n\n"
                        + "Next, choose Aangan and switch it on. You can switch it off there any time.")
                .setPositiveButton("Agree and continue", (d, w) -> openSettings(activity))
                .setNegativeButton("Not now", null)
                .show();
    }

    private static void openSettings(Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (ActivityNotFoundException | SecurityException e) {
            activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    static boolean isOn(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName me = new ComponentName(context, HomeButton.class);
        return enabled != null && (enabled.contains(me.flattenToString()) || enabled.contains(me.flattenToShortString()));
    }
}
