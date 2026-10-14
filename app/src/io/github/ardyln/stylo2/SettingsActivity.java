package io.github.ardyln.stylo2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Settings: writing feedback (texture, strength) and the thumb button. */
public final class SettingsActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private TextView status;
    private Button chooseApp;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            status.setText(getString(R.string.status_label, PenService.status));
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        status = new TextView(this);
        root.addView(status);

        // Writing feedback
        root.addView(header(R.string.feedback_header));
        Switch feedback = new Switch(this);
        feedback.setText(R.string.feedback_switch);
        feedback.setChecked(prefs.feedbackOn());
        root.addView(feedback);

        RadioGroup textures = new RadioGroup(this);
        int[] names = {R.string.texture_pencil, R.string.texture_eraser, R.string.texture_ballpen, R.string.texture_pen};
        for (int i = 0; i < names.length; i++) {
            RadioButton b = new RadioButton(this);
            b.setId(View.generateViewId());
            b.setText(names[i]);
            b.setTag(i);
            textures.addView(b);
            if (i == prefs.texture()) b.setChecked(true);
        }
        root.addView(textures);

        TextView strengthLabel = new TextView(this);
        root.addView(strengthLabel);
        SeekBar strength = new SeekBar(this);
        strength.setMax(PenProtocol.LEVEL_MAX);
        strength.setProgress(prefs.level());
        strengthLabel.setText(getString(R.string.strength, prefs.level(), PenProtocol.LEVEL_MAX));
        root.addView(strength);

        Runnable enableFeedbackViews = () -> {
            boolean on = prefs.feedbackOn();
            for (int i = 0; i < textures.getChildCount(); i++) textures.getChildAt(i).setEnabled(on);
            strength.setEnabled(on);
        };
        enableFeedbackViews.run();

        feedback.setOnCheckedChangeListener((v, on) -> {
            prefs.setFeedbackOn(on);
            enableFeedbackViews.run();
            apply();
        });
        textures.setOnCheckedChangeListener((g, id) -> {
            prefs.setTexture((Integer) g.findViewById(id).getTag());
            apply();
        });
        strength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                strengthLabel.setText(getString(R.string.strength, p, PenProtocol.LEVEL_MAX));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {
                prefs.setLevel(s.getProgress());
                apply();
            }
        });

        // Thumb button
        root.addView(header(R.string.button_header));
        RadioGroup actions = new RadioGroup(this);
        RadioButton none = new RadioButton(this);
        none.setId(View.generateViewId());
        none.setText(R.string.action_none);
        RadioButton open = new RadioButton(this);
        open.setId(View.generateViewId());
        open.setText(R.string.action_open_app);
        actions.addView(none);
        actions.addView(open);
        (prefs.buttonAction() == Prefs.ACTION_OPEN_APP ? open : none).setChecked(true);
        root.addView(actions);

        chooseApp = new Button(this);
        updateChooseApp();
        chooseApp.setEnabled(prefs.buttonAction() == Prefs.ACTION_OPEN_APP);
        chooseApp.setOnClickListener(v -> pickApp());
        root.addView(chooseApp);

        actions.setOnCheckedChangeListener((g, id) -> {
            boolean openApp = id == open.getId();
            prefs.setButtonAction(openApp ? Prefs.ACTION_OPEN_APP : Prefs.ACTION_NONE);
            chooseApp.setEnabled(openApp);
            if (openApp && prefs.buttonApp() == null) pickApp();
        });

        CheckBox buzz = new CheckBox(this);
        buzz.setText(R.string.button_buzz);
        buzz.setChecked(prefs.buttonBuzz());
        buzz.setOnCheckedChangeListener((v, on) -> prefs.setButtonBuzz(on));
        root.addView(buzz);

        Button test = new Button(this);
        test.setText(R.string.test_buzz);
        test.setOnClickListener(v -> startForegroundService(
                new Intent(this, PenService.class).setAction(PenService.ACTION_BUZZ)));
        root.addView(test);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        startForegroundService(new Intent(this, PenService.class));
        handler.post(refresh);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private void apply() {
        startForegroundService(new Intent(this, PenService.class).setAction(PenService.ACTION_APPLY));
    }

    private void pickApp() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        apps.sort((a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
        List<String> labels = new ArrayList<>();
        for (ResolveInfo r : apps) labels.add(r.loadLabel(pm).toString());
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_app)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    prefs.setButtonApp(apps.get(which).activityInfo.packageName);
                    updateChooseApp();
                })
                .show();
    }

    private void updateChooseApp() {
        String pkg = prefs.buttonApp();
        String label = null;
        if (pkg != null) {
            try {
                PackageManager pm = getPackageManager();
                label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            } catch (PackageManager.NameNotFoundException e) {
                label = pkg;
            }
        }
        chooseApp.setText(label == null ? getString(R.string.choose_app) : getString(R.string.chosen_app, label));
    }

    private TextView header(int text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setPadding(0, dp(24), 0, dp(8));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
