package io.github.ardyln.stylo2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the pen service after boot and after the app is updated. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        context.startForegroundService(new Intent(context, PenService.class));
    }
}
