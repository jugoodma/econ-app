package edu.umcp.justingoodman.fitbit_economics_study;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.Calendar;

// import android.os.Build;
// import android.provider.Settings;

/* DataUpdater
 *
 * Extends BroadcastReceiver - listens for all remote calls and handles appropriately
 *
 * **/
public class DataUpdater extends BroadcastReceiver {

    private static final String TAG = "DataUpdater";

    @Override
    public void onReceive(final Context ctx, Intent i) {
        if (Globe.DEBUG) Log.d(TAG, "Broadcast received!");
        int type = i.getIntExtra("type", -1);
        if (Globe.DEBUG) Log.d(TAG, "Type is " + type);

        if (type == 0) DataUpdater.collectFitbitData(ctx);
        if (type == 1) DataUpdater.bedtimeNotification(ctx);
        if (type == 2) DataUpdater.waketimeNotification(ctx);

        // I might move all of the functions to Globe.. we'll see
    }

    private static void collectFitbitData(final Context ctx) {
        // Setup the network
        NetworkManager.getInstance(ctx);

        // Set some globals
        Globe.init(ctx);

        // Refresh token & update data
        // refreshToken will block until we succeed or fail
        if (Globe.DEBUG) Log.d(TAG, "Running refreshToken");
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // because Toast
                if (Globe.refreshToken(ctx)) {
                    Globe.collectData(ctx);
                } else {
                    Globe.authFitbit(ctx);
                }
            }
        });
        thread.start();
    }

    private static void bedtimeNotification(Context ctx) {
        if (Globe.DEBUG) Log.d(TAG, "Bedtime notification service started.");
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            PendingIntent pi = PendingIntent.getActivity(ctx, 3, new Intent(ctx, Launcher.class), 0);

            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, "Economics");

            b.setTicker("Bedtime Soon!");
            b.setContentTitle("Bedtime Soon!");
            b.setContentText("Looks like it's almost your bedtime! Start getting ready for bed soon!");
            b.setSmallIcon(R.mipmap.ic_launcher);
            b.setContentIntent(pi);
            // big style
            b.setStyle(new NotificationCompat.BigTextStyle().bigText("Looks like it's almost your bedtime! Start getting ready for bed soon!"));

            Notification n = b.build();

            // create the notification
            // n.vibrate = new long[]{150, 300, 150, 400};
            n.flags = Notification.FLAG_AUTO_CANCEL;
            nm.notify(R.mipmap.ic_launcher, n);
        }
    }

    private static void waketimeNotification(Context ctx) {
        if (Globe.DEBUG) Log.d(TAG, "Waketime notification service started.");
        // we want to actually figure out if they earned the coupon -- realistically,
        // FitBit probably has not figured out the user's sleep yet... wtf
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        // later - set actual redemption time, and do a *quick* check of validity
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        // no point in sending notification on sat/sun
        if (!(day == Calendar.SATURDAY || day == Calendar.SUNDAY)) {
            // now test the user's sleep data, and see if they might earn a coffee
            Globe.init(ctx);
            

            if (nm != null) {
                PendingIntent pi = PendingIntent.getActivity(ctx, 4, new Intent(ctx, Launcher.class), 0);

                NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, "Economics");

                b.setTicker("Coffee Coupon");
                b.setContentTitle("Coffee Coupon");
                b.setContentText("Looks like you might have earned a free coffee! Be sure to redeem your coupon before it expires!");
                b.setSmallIcon(R.mipmap.ic_launcher);
                b.setContentIntent(pi);
                // big style
                b.setStyle(new NotificationCompat.BigTextStyle().bigText("Looks like you might have earned a free coffee! Be sure to redeem your coupon before it expires!"));

                Notification n = b.build();

                // create the notification
                // n.vibrate = new long[]{150, 300, 150, 400};
                n.flags = Notification.FLAG_AUTO_CANCEL;
                nm.notify(R.mipmap.ic_launcher, n);
            }
        }
    }

    /*
    private static void airplaneModeOff(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Settings.System.putInt(ctx.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.putExtra("state", false);
            ctx.sendBroadcast(intent);
        }
    }
    */
}
