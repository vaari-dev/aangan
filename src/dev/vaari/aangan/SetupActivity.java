package dev.vaari.aangan;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
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
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Aangan's settings: the city, opening with the Home button, the screensaver, and the family's
 * birthdays and anniversaries. Everything can also be set from a phone by scanning the QR code.
 */
public class SetupActivity extends Activity {

    private static final int LABEL = 0xFFF5F5F7;
    private static final int SECONDARY = 0x99EBEBF5;
    private static final int TERTIARY = 0x4DEBEBF5;
    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private SharedPreferences prefs;
    private LinearLayout rows;
    private LinearLayout phonePanel;
    private PhoneSetup phone;
    private final ExecutorService network = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("aangan", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(48), dp(36), dp(48), dp(24));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text(34, 300, LABEL, "Settings"));
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(12), 0, dp(24));
        scroll.addView(rows);
        left.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        phonePanel = new LinearLayout(this);
        phonePanel.setOrientation(LinearLayout.VERTICAL);
        phonePanel.setGravity(Gravity.CENTER_HORIZONTAL);
        phonePanel.setPadding(dp(24), dp(24), dp(24), dp(24));
        GradientDrawable card = new GradientDrawable();
        card.setColor(0xFF1F2026);
        card.setCornerRadius(dp(20));
        card.setStroke(Math.max(1, dp(0.75f)), 0x1FFFFFFF);
        phonePanel.setBackground(card);
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(dp(290), ViewGroup.LayoutParams.WRAP_CONTENT);
        panelLp.leftMargin = dp(40);
        panelLp.topMargin = dp(56);
        root.addView(phonePanel, panelLp);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        phone = new PhoneSetup(prefs, this::showRows);
        showPhonePanel(phone.start(this));
        showRows();
    }

    @Override
    protected void onPause() {
        phone.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        network.shutdownNow();
        super.onDestroy();
    }

    // ---- views ------------------------------------------------------------------------------

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView text(float sp, int weight, int color, String s) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, weight, false));
        t.setTextColor(color);
        t.setText(s);
        return t;
    }

    private void heading(String s) {
        TextView t = text(11, 600, TERTIARY, s.toUpperCase());
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(16), dp(20), 0, dp(6));
        rows.addView(t);
    }

    /** A focusable line with a title on the left and its current value on the right. */
    private void row(String title, String value, View.OnClickListener action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setFocusable(true);
        row.setClickable(true);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0x26FFFFFF);
        focused.setCornerRadius(dp(12));
        row.setOnFocusChangeListener((v, has) -> v.setBackground(has ? focused : null));
        row.setOnClickListener(action);
        row.addView(text(16, 500, LABEL, title), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text(14, 400, SECONDARY, value));
        rows.addView(row);
    }

    private void showRows() {
        int focus = rows.indexOfChild(getCurrentFocus());
        rows.removeAllViews();

        Place place = Place.load(prefs);
        row("City", place.name, v -> askCity());
        row("Open with the Home button", HomeButton.isOn(this) ? "On" : "Off", v -> open(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        row("Screensaver", "Choose", v -> open(Settings.ACTION_DREAM_SETTINGS));

        heading("Birthdays & anniversaries");
        List<People.Day> days = People.load(prefs);
        for (int i = 0; i < days.size(); i++) {
            People.Day d = days.get(i);
            int index = i;
            row(d.who, (d.birthday ? "Birthday" : "Anniversary") + "  ·  " + d.day + " " + MONTHS[d.month - 1],
                    v -> confirmRemove(index, d));
        }
        row("Add one", "", v -> askDay());

        View target = rows.getChildAt(Math.max(0, Math.min(focus, rows.getChildCount() - 1)));
        if (target == null || !target.isFocusable()) target = rows.getChildAt(0);
        target.requestFocus();
    }

    private void showPhonePanel(String url) {
        phonePanel.removeAllViews();
        phonePanel.addView(text(18, 600, LABEL, "Set up from your phone"));
        if (url == null) {
            TextView t = text(13, 400, SECONDARY, "Connect the TV to Wi-Fi to set things up from a phone.");
            t.setPadding(0, dp(12), 0, 0);
            phonePanel.addView(t);
            return;
        }
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(dp(176), dp(176));
        qrLp.topMargin = dp(16);
        qrLp.bottomMargin = dp(14);
        phonePanel.addView(new QrView(this, QrCode.encode(url)), qrLp);
        TextView how = text(13, 400, SECONDARY, "Scan with your phone's camera to set the city and add birthdays. "
                + "Your phone needs to be on the same Wi-Fi.");
        how.setGravity(Gravity.CENTER_HORIZONTAL);
        phonePanel.addView(how);
        TextView link = text(11, 400, TERTIARY, url.replace("http://", ""));
        link.setPadding(0, dp(10), 0, 0);
        phonePanel.addView(link);
    }

    // ---- actions ----------------------------------------------------------------------------

    private void open(String action) {
        try {
            startActivity(new Intent(action));
        } catch (ActivityNotFoundException | SecurityException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private AlertDialog.Builder dialog(String title) {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(title);
    }

    private void askCity() {
        EditText query = new EditText(this);
        query.setHint("City name");
        query.setSingleLine(true);
        query.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        dialog("Choose your city")
                .setView(padded(query))
                .setPositiveButton("Search", (d, w) -> searchCity(query.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void searchCity(String query) {
        if (query.trim().isEmpty()) return;
        network.execute(() -> {
            List<Place> found;
            try {
                found = Place.search(query);
            } catch (Exception e) {
                found = null;
            }
            List<Place> places = found;
            runOnUiThread(() -> {
                if (places == null || places.isEmpty()) {
                    Toast.makeText(this, places == null ? "Couldn't search. Is the TV online?" : "No places called " + query,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                String[] names = new String[places.size()];
                for (int i = 0; i < names.length; i++) names[i] = places.get(i).name + "  ·  " + places.get(i).region;
                dialog("Choose your city").setItems(names, (d, which) -> {
                    places.get(which).save(prefs);
                    Weather.clear(prefs);
                    showRows();
                }).show();
            });
        });
    }

    private void askDay() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText who = new EditText(this);
        who.setHint("Name, e.g. Asha or Asha & Rohan");
        who.setSingleLine(true);
        who.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        form.addView(who);

        RadioGroup kind = new RadioGroup(this);
        kind.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton birthday = new RadioButton(this);
        birthday.setText("Birthday");
        birthday.setId(View.generateViewId());
        RadioButton anniversary = new RadioButton(this);
        anniversary.setText("Anniversary");
        anniversary.setId(View.generateViewId());
        kind.addView(birthday);
        kind.addView(anniversary);
        kind.check(birthday.getId());
        form.addView(kind);

        LinearLayout date = new LinearLayout(this);
        date.setGravity(Gravity.CENTER);
        NumberPicker day = new NumberPicker(this);
        day.setMinValue(1);
        day.setMaxValue(31);
        NumberPicker month = new NumberPicker(this);
        month.setMinValue(1);
        month.setMaxValue(12);
        month.setDisplayedValues(MONTHS);
        date.addView(day);
        date.addView(month);
        form.addView(date);

        dialog("Add a birthday or anniversary")
                .setView(padded(form))
                .setPositiveButton("Add", (d, w) -> {
                    String name = who.getText().toString().trim();
                    if (name.isEmpty()) return;
                    People.add(prefs, new People.Day(month.getValue(), day.getValue(),
                            kind.getCheckedRadioButtonId() == birthday.getId(), name));
                    showRows();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRemove(int index, People.Day d) {
        dialog("Remove " + d.who + "'s " + (d.birthday ? "birthday" : "anniversary") + "?")
                .setPositiveButton("Remove", (dlg, w) -> {
                    People.remove(prefs, index);
                    showRows();
                })
                .setNegativeButton("Keep", null)
                .show();
    }

    private View padded(View content) {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /** A QR code, black on white with the standard four-module quiet zone. */
    private static final class QrView extends View {
        private final boolean[][] modules;
        private final Paint paint = new Paint();

        QrView(Context context, boolean[][] modules) {
            super(context);
            this.modules = modules;
        }

        @Override
        protected void onDraw(Canvas c) {
            int n = modules.length + 8;
            float cell = Math.min(getWidth(), getHeight()) / (float) n;
            paint.setColor(0xFFFFFFFF);
            c.drawRoundRect(0, 0, cell * n, cell * n, cell * 2, cell * 2, paint);
            paint.setColor(0xFF000000);
            for (int y = 0; y < modules.length; y++) {
                for (int x = 0; x < modules.length; x++) {
                    if (modules[y][x]) c.drawRect((x + 4) * cell, (y + 4) * cell, (x + 5) * cell, (y + 5) * cell, paint);
                }
            }
        }
    }
}
