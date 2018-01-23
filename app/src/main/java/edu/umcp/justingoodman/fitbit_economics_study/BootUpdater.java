package edu.umcp.justingoodman.fitbit_economics_study;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import static android.content.Context.ALARM_SERVICE;

/* BootUpdater
 *
 * Extends BroadcastReceiver - listens for device boot to reset alarms
 *
 * **/
public class BootUpdater extends BroadcastReceiver {

    private static final String TAG = "BootUpdater";

    @Override
    public void onReceive(final Context ctx, Intent i) {
        if (Globe.DEBUG) Log.d(TAG, "Booted!");
        if ("android.intent.action.BOOT_COMPLETED".equals(i.getAction()) || "android.intent.action.QUICKBOOT_POWERON".equals(i.getAction())) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    AlarmManager am = (AlarmManager) ctx.getSystemService(ALARM_SERVICE);
                    if (am != null) {
                        Intent iFB = new Intent(ctx, DataUpdater.class);
                        iFB.putExtra("type", 0); // 0 = FitBit updater
                        PendingIntent senderFB = PendingIntent.getBroadcast(ctx, 0, iFB, 0);
                        // start now, 1 hour interval
                        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), AlarmManager.INTERVAL_HOUR, senderFB);
                    }
                }
            }, 60 * 1000);
        }
    }
}
