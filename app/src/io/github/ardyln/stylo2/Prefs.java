package io.github.ardyln.stylo2;

import android.content.Context;
import android.content.SharedPreferences;

/** User settings. */
final class Prefs {
    static final int ACTION_NONE = 0;
    static final int ACTION_OPEN_APP = 1;

    private final SharedPreferences sp;

    Prefs(Context context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    boolean feedbackOn() { return sp.getBoolean("feedback_on", true); }
    int texture() { return sp.getInt("texture", PenProtocol.TEXTURE_PENCIL); }
    int level() { return sp.getInt("level", 2); }
    int buttonAction() { return sp.getInt("button_action", ACTION_NONE); }
    String buttonApp() { return sp.getString("button_app", null); }
    boolean buttonBuzz() { return sp.getBoolean("button_buzz", false); }

    void setFeedbackOn(boolean v) { sp.edit().putBoolean("feedback_on", v).apply(); }
    void setTexture(int v) { sp.edit().putInt("texture", v).apply(); }
    void setLevel(int v) { sp.edit().putInt("level", v).apply(); }
    void setButtonAction(int v) { sp.edit().putInt("button_action", v).apply(); }
    void setButtonApp(String pkg) { sp.edit().putString("button_app", pkg).apply(); }
    void setButtonBuzz(boolean v) { sp.edit().putBoolean("button_buzz", v).apply(); }
}
