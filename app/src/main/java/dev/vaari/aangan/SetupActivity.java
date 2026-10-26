package dev.vaari.aangan;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public class SetupActivity extends Activity {

    private static final ComponentName SONY_SCREENSAVER =
            new ComponentName("com.android.tv.settings", "com.sony.dtv.settings.device.display.daydream.DaydreamActivity");

    /** Using the phone isn't using the TV: without this the screensaver would start and stop the phone page. */
    private static final long PHONE_AWAKE_MS = 20 * 60_000L;

    private final Runnable letScreenSleep = () -> getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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
        rows = ui.column();
        rows.setPadding(0, ui.dp(12), 0, ui.dp(24));
        left.addView(ui.scroll(rows), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
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
        phone = new PhoneSetup(this, prefs, () -> {
            keepScreenOn();
            showRows();
        });
        String url = phone.start();
        showPhonePanel(url);
        if (url != null) keepScreenOn();
        showRows();
    }

    @Override
    protected void onPause() {
        phone.stop();
        phonePanel.removeCallbacks(letScreenSleep);
        letScreenSleep.run();
        super.onPause();
    }

    private void keepScreenOn() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        phonePanel.removeCallbacks(letScreenSleep);
        phonePanel.postDelayed(letScreenSleep, PHONE_AWAKE_MS);
    }

    private void heading(int title) {
        TextView t = ui.heading(getString(title));
        t.setPadding(ui.dp(16), ui.dp(20), 0, ui.dp(6));
        rows.addView(t);
    }

    private void row(String title, String value, View.OnClickListener action) {
        LinearLayout row = ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
        ui.selectable(row, action);
        row.addView(ui.text(16, 500, Ui.LABEL, title), Ui.share(1));
        TextView v = ui.text(14, 400, Ui.SECONDARY, value);
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

        row(getString(R.string.city), Place.load(prefs).name, v -> startActivity(new Intent(this, CityActivity.class)));
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
        if (crash != null) {
            row(getString(R.string.crash_title), Crash.when(crash), v -> confirm(getString(R.string.crash_title),
                    getString(R.string.crash_message), R.string.delete, R.string.keep, () -> Crash.delete(this)));
        }

        heading(R.string.family_days_heading);
        for (People.Day d : People.load(prefs)) {
            row(d.who, getString(d.birthday ? R.string.birthday : R.string.anniversary) + "  ·  " + Dates.dayMonth(d.month, d.day),
                    v -> dialog(d.who).setItems(new String[]{getString(R.string.edit), getString(R.string.remove)}, (dlg, which) -> {
                        if (which == 0) askDay(d);
                        else confirm(getString(d.birthday ? R.string.remove_birthday : R.string.remove_anniversary, d.who),
                                null, R.string.remove, R.string.keep, () -> People.remove(prefs, d.key()));
                    }).show());
        }
        row(getString(R.string.add_one), "", v -> askDay(null));

        heading(R.string.festivals);
        for (Festival f : Festival.editable(Festival.today())) {
            String state = f.hidden ? "  ·  " + getString(R.string.hidden) : f.moved() ? "  ·  " + getString(R.string.moved) : "";
            row(f.title(this), f.shortDate(getResources()) + state, v -> askFestival(f));
        }

        heading(R.string.about);
        row(getString(R.string.version), BuildConfig.VERSION_NAME, v -> { });

        View target = rows.getChildAt(Math.max(0, Math.min(focus, rows.getChildCount() - 1)));
        if (target == null || !target.isFocusable()) target = rows.getChildAt(0);
        target.requestFocus();
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
        centered(13, Ui.SECONDARY, getString(R.string.phone_how), 0);
        centered(12, Ui.TERTIARY, url.replace("http://", ""), 10);
        // Browsers warn about any page without HTTPS, and a TV on a home network can't have it.
        centered(12, Ui.TERTIARY, getString(R.string.phone_not_secure), 8);
    }

    private void centered(float sp, int color, String s, int top) {
        TextView t = ui.text(sp, 400, color, s);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(0, ui.dp(top), 0, 0);
        phonePanel.addView(t);
    }

    private AlertDialog.Builder dialog(String title) {
        return Ui.dialog(this, title);
    }

    private void confirm(String title, String message, int yes, int no, Runnable action) {
        dialog(title)
                .setMessage(message)
                .setPositiveButton(getString(yes), (d, w) -> {
                    action.run();
                    showRows();
                })
                .setNegativeButton(getString(no), null)
                .show();
    }

    private void ask(String title, View content, int ok, BooleanSupplier save) {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0);
        box.addView(content, Ui.fill());
        dialog(title)
                .setView(box)
                .setPositiveButton(getString(ok), (d, w) -> {
                    if (save.getAsBoolean()) showRows();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private List<String> hidden() {
        return Prefs.list(prefs, HomeActivity.HIDDEN);
    }

    private String dockNames() {
        Map<String, Apps.App> apps = Apps.load(this);
        List<String> names = new ArrayList<>();
        for (String pkg : Dock.load(prefs, apps, hidden())) {
            names.add(apps.containsKey(pkg) ? apps.get(pkg).label : getString(R.string.dock_empty));
        }
        return TextUtils.join(", ", names);
    }

    private void askDock() {
        Map<String, Apps.App> apps = Apps.load(this);
        List<String> dock = Dock.load(prefs, apps, hidden());
        String[] items = new String[Dock.SIZE + 1];
        for (int i = 0; i < Dock.SIZE; i++) {
            Apps.App app = apps.get(dock.get(i));
            items[i] = (i + 1) + "   " + (app != null ? app.label : getString(R.string.dock_empty));
        }
        items[Dock.SIZE] = getString(R.string.dock_reset);
        dialog(getString(R.string.dock_title)).setItems(items, (d, which) -> {
            if (which < Dock.SIZE) {
                Dock.choose(this, apps, dock, hidden(), which, pkg -> {
                    showRows();
                    askDock();
                });
            } else {
                confirm(getString(R.string.dock_reset_title), getString(R.string.dock_reset_message),
                        R.string.reset, R.string.keep_mine, () -> Dock.reset(prefs));
            }
        }).show();
    }

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
        RadioButton birthday = radio(kind, R.string.birthday);
        RadioButton anniversary = radio(kind, R.string.anniversary);
        kind.check(existing == null || existing.birthday ? birthday.getId() : anniversary.getId());
        form.addView(kind);

        LinearLayout date = pickers();
        NumberPicker day = picker(date, 1, 31, existing != null ? existing.day : 1);
        NumberPicker month = monthPicker(date, existing != null ? existing.month : 1);
        form.addView(date);

        ask(existing == null ? getString(R.string.add_day) : getString(R.string.edit_day, existing.who), form,
                existing == null ? R.string.add : R.string.save, () -> {
                    String name = who.getText().toString().trim();
                    if (name.isEmpty()) return false;
                    int m = month.getValue();
                    People.Day edited = new People.Day(m, Math.min(day.getValue(), Month.of(m).maxLength()),
                            kind.getCheckedRadioButtonId() == birthday.getId(), name);
                    if (existing == null) People.add(prefs, edited);
                    else People.update(prefs, existing.key(), edited);
                    return true;
                });
    }

    private RadioButton radio(RadioGroup group, int text) {
        RadioButton b = new RadioButton(this);
        b.setText(getString(text));
        b.setId(View.generateViewId());
        group.addView(b);
        return b;
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
        LinearLayout date = pickers();
        NumberPicker day = picker(date, 1, 31, f.date.getDayOfMonth());
        NumberPicker month = monthPicker(date, f.date.getMonthValue());
        NumberPicker year = picker(date, f.clampMove(LocalDate.MIN).getYear(), f.clampMove(LocalDate.MAX).getYear(),
                f.date.getYear());
        year.setWrapSelectorWheel(false);
        ask(getString(R.string.when_is, f.title(this)), date, R.string.save, () -> {
            YearMonth ym = YearMonth.of(year.getValue(), month.getValue());
            Festival.move(prefs, f, ym.atDay(Math.min(day.getValue(), ym.lengthOfMonth())));
            return true;
        });
    }

    private LinearLayout pickers() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private NumberPicker picker(LinearLayout into, int min, int max, int value) {
        NumberPicker p = new NumberPicker(this);
        p.setMinValue(min);
        p.setMaxValue(max);
        p.setValue(value);
        into.addView(p);
        return p;
    }

    private NumberPicker monthPicker(LinearLayout into, int value) {
        NumberPicker p = picker(into, 1, 12, value);
        p.setDisplayedValues(Dates.months(TextStyle.SHORT));
        return p;
    }

    private static final class QrView extends View {
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
