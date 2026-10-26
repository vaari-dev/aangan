package dev.vaari.aangan;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aangan's settings: the city, the dock, opening with the Home button, the screensaver, the family's
 * birthdays and anniversaries, and the festival dates. Everything can also be set from a phone by
 * scanning the QR code.
 */
public class SetupActivity extends Activity {

    private static final ComponentName SONY_SCREENSAVER =
            new ComponentName("com.android.tv.settings", "com.sony.dtv.settings.device.display.daydream.DaydreamActivity");

    private Ui ui;
    private SharedPreferences prefs;
    private LinearLayout rows;
    private LinearLayout phonePanel;
    private PhoneSetup phone;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.canvas(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        prefs = Prefs.of(this);

        LinearLayout root = ui.row();
        root.setPadding(ui.dp(48), ui.dp(36), ui.dp(48), ui.dp(24));

        LinearLayout left = ui.column();
        left.addView(ui.text(34, 300, Ui.LABEL, getString(R.string.settings)));
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        // Rows fade out under the title rather than butting against it.
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(ui.dp(32));
        rows = ui.column();
        rows.setPadding(0, ui.dp(12), 0, ui.dp(24));
        scroll.addView(rows);
        left.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        phonePanel = ui.column();
        phonePanel.setGravity(Gravity.CENTER_HORIZONTAL);
        phonePanel.setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(24));
        phonePanel.setBackground(ui.panel(20));
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(ui.dp(290), ViewGroup.LayoutParams.WRAP_CONTENT);
        panelLp.leftMargin = ui.dp(40);
        panelLp.topMargin = ui.dp(56);
        root.addView(phonePanel, panelLp);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        phone = new PhoneSetup(this, prefs, this::showRows);
        showPhonePanel(phone.start());
        showRows();
    }

    @Override
    protected void onPause() {
        phone.stop();
        super.onPause();
    }

    // ---- views ------------------------------------------------------------------------------

    private void heading(String s) {
        TextView t = ui.text(12, 600, Ui.TERTIARY, s.toUpperCase(Locale.getDefault()));
        t.setLetterSpacing(0.08f);
        t.setPadding(ui.dp(16), ui.dp(20), 0, ui.dp(6));
        rows.addView(t);
    }

    /** A focusable line with a title on the left and its current value on the right. */
    private void row(String title, String value, View.OnClickListener action) {
        LinearLayout row = ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
        row.setFocusable(true);
        row.setClickable(true);
        GradientDrawable focused = ui.rounded(Ui.FOCUS_FILL, 12);
        row.setOnFocusChangeListener((v, hasFocus) -> v.setBackground(hasFocus ? focused : null));
        row.setOnClickListener(action);
        row.addView(ui.text(16, 500, Ui.LABEL, title), Ui.share(1));
        TextView v = ui.text(14, 400, Ui.SECONDARY, value);
        // One line, leaving the title room: "Netflix, Prime Video, YouTube, YouTube…"
        v.setMaxLines(1);
        v.setMaxWidth(ui.dp(300));
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setPadding(ui.dp(16), 0, 0, 0);
        row.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0));
        rows.addView(row);
    }

    private void showRows() {
        int focus = rows.indexOfChild(getCurrentFocus());
        rows.removeAllViews();
        Festival.load(this, prefs);

        Place place = Place.load(prefs);
        row(getString(R.string.city), place.name, v -> startActivity(new Intent(this, CityActivity.class)));
        row(getString(R.string.dock), dockNames(), v -> askDock());
        row(getString(R.string.open_with_home), getString(HomeButton.isOn(this) ? R.string.on : R.string.off),
                v -> HomeButton.askToTurnOn(this));
        // Only where the TV lets people choose a screensaver: recent Google TVs offer only their own
        // Ambient mode. Sony's Android 9 TVs keep the page under their own name.
        if (Ui.hasSettings(this, Settings.ACTION_DREAM_SETTINGS, SONY_SCREENSAVER)) {
            row(getString(R.string.screensaver), getString(R.string.choose),
                    v -> Ui.openSettings(this, Settings.ACTION_DREAM_SETTINGS, SONY_SCREENSAVER));
        }
        String crash = Crash.report(this);
        if (crash != null) row(getString(R.string.crash_title), Crash.when(crash), v -> showCrash());

        heading(getString(R.string.family_days_heading));
        for (People.Day d : People.load(prefs)) {
            row(d.who, getString(d.birthday ? R.string.birthday : R.string.anniversary) + "  ·  " + Dates.dayMonth(d.month, d.day),
                    v -> dialog(d.who).setItems(new String[]{getString(R.string.edit), getString(R.string.remove)}, (dlg, which) -> {
                        if (which == 0) askDay(d);
                        else confirmRemove(d);
                    }).show());
        }
        row(getString(R.string.add_one), "", v -> askDay(null));

        heading(getString(R.string.festivals));
        for (Festival f : Festival.editable(Festival.today())) {
            String state = f.hidden ? "  ·  " + getString(R.string.hidden) : f.moved() ? "  ·  " + getString(R.string.moved) : "";
            row(f.title(this), f.shortDate(getResources()) + state, v -> askFestival(f));
        }

        heading(getString(R.string.about));
        row(getString(R.string.version), version(), v -> { });

        View target = rows.getChildAt(Math.max(0, Math.min(focus, rows.getChildCount() - 1)));
        if (target == null || !target.isFocusable()) target = rows.getChildAt(0);
        target.requestFocus();
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private void showPhonePanel(String url) {
        phonePanel.removeAllViews();
        phonePanel.addView(ui.text(18, 600, Ui.LABEL, getString(R.string.phone_title)));
        if (url == null) {
            TextView t = ui.text(13, 400, Ui.SECONDARY, getString(R.string.phone_offline));
            t.setPadding(0, ui.dp(12), 0, 0);
            phonePanel.addView(t);
            return;
        }
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(ui.dp(176), ui.dp(176));
        qrLp.topMargin = ui.dp(16);
        qrLp.bottomMargin = ui.dp(14);
        phonePanel.addView(new QrView(this, QrCode.encode(url)), qrLp);
        TextView how = ui.text(13, 400, Ui.SECONDARY, getString(R.string.phone_how));
        how.setGravity(Gravity.CENTER_HORIZONTAL);
        phonePanel.addView(how);
        TextView link = ui.text(12, 400, Ui.TERTIARY, url.replace("http://", ""));
        link.setGravity(Gravity.CENTER_HORIZONTAL);
        link.setPadding(0, ui.dp(10), 0, 0);
        phonePanel.addView(link);
        // Browsers warn about any page without HTTPS, and a TV on a home network can't have it.
        TextView warning = ui.text(12, 400, Ui.TERTIARY, getString(R.string.phone_not_secure));
        warning.setGravity(Gravity.CENTER_HORIZONTAL);
        warning.setPadding(0, ui.dp(8), 0, 0);
        phonePanel.addView(warning);
    }

    // ---- actions ----------------------------------------------------------------------------

    private AlertDialog.Builder dialog(String title) {
        return Ui.dialog(this, title);
    }

    /** The note kept from the last crash: reported from the phone page, where it can be read in full, or deleted. */
    private void showCrash() {
        dialog(getString(R.string.crash_title))
                .setMessage(getString(R.string.crash_message))
                .setPositiveButton(getString(R.string.delete), (d, w) -> {
                    Crash.delete(this);
                    showRows();
                })
                .setNegativeButton(getString(R.string.keep), null)
                .show();
    }

    private List<String> dock(Map<String, Apps.App> apps) {
        return Dock.load(prefs, apps, Prefs.list(prefs, HomeActivity.HIDDEN));
    }

    /** "Netflix, YouTube, Prime Video, Empty" */
    private String dockNames() {
        Map<String, Apps.App> apps = Apps.load(this);
        List<String> names = new ArrayList<>();
        for (String pkg : dock(apps)) names.add(apps.containsKey(pkg) ? apps.get(pkg).label : getString(R.string.dock_empty));
        return TextUtils.join(", ", names);
    }

    /** The dock's four places, to change any of them, or to reset it to popular apps. */
    private void askDock() {
        Map<String, Apps.App> apps = Apps.load(this);
        List<String> dock = dock(apps);
        String[] items = new String[Dock.SIZE + 1];
        for (int i = 0; i < Dock.SIZE; i++) {
            Apps.App app = apps.get(dock.get(i));
            items[i] = (i + 1) + "   " + (app != null ? app.label : getString(R.string.dock_empty));
        }
        items[Dock.SIZE] = getString(R.string.dock_reset);
        dialog(getString(R.string.dock_title)).setItems(items, (d, which) -> {
            if (which < Dock.SIZE) {
                // Back to the four places after each choice, to change another.
                Dock.choose(this, apps, dock, Prefs.list(prefs, HomeActivity.HIDDEN), which, pkg -> {
                    showRows();
                    askDock();
                });
            } else {
                dialog(getString(R.string.dock_reset_title))
                        .setMessage(getString(R.string.dock_reset_message))
                        .setPositiveButton(getString(R.string.reset), (d2, w) -> {
                            Dock.reset(prefs);
                            showRows();
                        })
                        .setNegativeButton(getString(R.string.keep_mine), null)
                        .show();
            }
        }).show();
    }

    /** Adds a day when {@code existing} is null, or edits it. */
    private void askDay(People.Day existing) {
        LinearLayout form = ui.column();
        EditText who = new EditText(this);
        who.setHint(getString(R.string.name_hint));
        // A longer name kept from before the limit stays whole.
        int longest = Math.max(People.MAX_NAME, existing != null ? existing.who.length() : 0);
        who.setFilters(new InputFilter[]{new InputFilter.LengthFilter(longest)});
        who.setSingleLine(true);
        who.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        if (existing != null) who.setText(existing.who);
        form.addView(who);

        RadioGroup kind = new RadioGroup(this);
        kind.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton birthday = new RadioButton(this);
        birthday.setText(getString(R.string.birthday));
        birthday.setId(View.generateViewId());
        RadioButton anniversary = new RadioButton(this);
        anniversary.setText(getString(R.string.anniversary));
        anniversary.setId(View.generateViewId());
        kind.addView(birthday);
        kind.addView(anniversary);
        kind.check(existing == null || existing.birthday ? birthday.getId() : anniversary.getId());
        form.addView(kind);

        LinearLayout date = new LinearLayout(this);
        date.setGravity(Gravity.CENTER);
        NumberPicker[] dayMonth = dayMonthPickers(date);
        NumberPicker day = dayMonth[0];
        NumberPicker month = dayMonth[1];
        if (existing != null) {
            day.setValue(existing.day);
            month.setValue(existing.month);
        }
        form.addView(date);

        dialog(existing == null ? getString(R.string.add_day) : getString(R.string.edit_day, existing.who))
                .setView(padded(form))
                .setPositiveButton(getString(existing == null ? R.string.add : R.string.save), (d, w) -> {
                    String name = who.getText().toString().trim();
                    if (name.isEmpty()) return;
                    // The day wheel always runs to 31; 31 February becomes the 29th, the month's last.
                    int m = month.getValue();
                    People.Day edited = new People.Day(m, Math.min(day.getValue(), Month.of(m).maxLength()),
                            kind.getCheckedRadioButtonId() == birthday.getId(), name);
                    if (existing == null) People.add(prefs, edited);
                    else People.update(prefs, existing.key(), edited);
                    showRows();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void confirmRemove(People.Day d) {
        dialog(getString(d.birthday ? R.string.remove_birthday : R.string.remove_anniversary, d.who))
                .setPositiveButton(getString(R.string.remove), (dlg, w) -> {
                    People.remove(prefs, d.key());
                    showRows();
                })
                .setNegativeButton(getString(R.string.keep), null)
                .show();
    }

    private void askFestival(Festival f) {
        String change = getString(R.string.change_date);
        String hide = getString(f.hidden ? R.string.show_it : R.string.hide_it);
        String[] actions = f.moved()
                ? new String[]{change, hide, getString(R.string.back_to, Dates.dayMonth(f.original))}
                : new String[]{change, hide};
        dialog(f.title(this)).setItems(actions, (d, which) -> {
            if (which == 0) askFestivalDate(f);
            else if (which == 1) Festival.hide(prefs, f, !f.hidden);
            else Festival.move(prefs, f, f.original);
            if (which != 0) showRows();
        }).show();
    }

    private void askFestivalDate(Festival f) {
        LinearLayout date = new LinearLayout(this);
        date.setGravity(Gravity.CENTER);
        NumberPicker[] dayMonth = dayMonthPickers(date);
        NumberPicker day = dayMonth[0];
        NumberPicker month = dayMonth[1];
        day.setValue(f.date.getDayOfMonth());
        month.setValue(f.date.getMonthValue());
        // Only the years a move can reach; Festival.move keeps the date itself within range.
        NumberPicker year = new NumberPicker(this);
        year.setMinValue(f.clampMove(LocalDate.MIN).getYear());
        year.setMaxValue(f.clampMove(LocalDate.MAX).getYear());
        year.setValue(f.date.getYear());
        year.setWrapSelectorWheel(false);
        date.addView(year);
        dialog(getString(R.string.when_is, f.title(this)))
                .setView(padded(date))
                .setPositiveButton(getString(R.string.save), (d, w) -> {
                    YearMonth ym = YearMonth.of(year.getValue(), month.getValue());
                    Festival.move(prefs, f, ym.atDay(Math.min(day.getValue(), ym.lengthOfMonth())));
                    showRows();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    /** A day wheel (1 to 31) and a month wheel ("Jan" to "Dec"), added to {@code into} in that order. */
    private NumberPicker[] dayMonthPickers(LinearLayout into) {
        NumberPicker day = new NumberPicker(this);
        day.setMinValue(1);
        day.setMaxValue(31);
        NumberPicker month = new NumberPicker(this);
        month.setMinValue(1);
        month.setMaxValue(12);
        month.setDisplayedValues(Dates.months(TextStyle.SHORT));
        into.addView(day);
        into.addView(month);
        return new NumberPicker[]{day, month};
    }

    private View padded(View content) {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0);
        box.addView(content, Ui.fill());
        return box;
    }

    /** A QR code, black on white with the standard four-module quiet zone. */
    private static final class QrView extends View {
        /** The quiet zone around the code, in modules, as the QR standard requires. */
        private static final int QUIET = 4;
        private final boolean[][] modules;
        private final Paint paint = new Paint();

        QrView(Context context, boolean[][] modules) {
            super(context);
            this.modules = modules;
        }

        @Override
        protected void onDraw(Canvas c) {
            int n = modules.length + 2 * QUIET;
            float cell = Math.min(getWidth(), getHeight()) / (float) n;
            paint.setColor(0xFFFFFFFF);
            c.drawRoundRect(0, 0, cell * n, cell * n, cell * 2, cell * 2, paint);
            paint.setColor(0xFF000000);
            for (int y = 0; y < modules.length; y++) {
                for (int x = 0; x < modules.length; x++) {
                    if (modules[y][x]) {
                        c.drawRect((x + QUIET) * cell, (y + QUIET) * cell, (x + QUIET + 1) * cell, (y + QUIET + 1) * cell, paint);
                    }
                }
            }
        }
    }
}
