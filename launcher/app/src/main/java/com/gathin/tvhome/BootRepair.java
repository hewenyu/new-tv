package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/**
 * Runs {@link ServiceRepair} at boot, not only when Home starts. On the MiTV-ASTP0 the enabled accessibility list
 * came back as the firmware default (the karaoke helper alone, pkg:android) after the set was switched off and on,
 * so HomeKeyService was gone; the stock launcher comes up at boot and Home never started to put it back, and the
 * set stayed on the stock home screen until this launcher was opened by hand. Same switch as the Home-start repair:
 * restore_key_services, and WRITE_SECURE_SETTINGS granted by the installer.
 */
public final class BootRepair extends BroadcastReceiver {
    /** ServiceRepair writes add, remove, add with 1 s pauses on its own thread; keep the process up past that. */
    private static final long HOLD_MS = 3_500L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Context app = context.getApplicationContext();
        if (!Config.load(app).restoreKeyServices) return;
        PendingResult pending = goAsync();
        ServiceRepair.restore(app);
        new Handler(Looper.getMainLooper()).postDelayed(pending::finish, HOLD_MS);
    }
}
