package edu.umcp.justingoodman.fitbit_economics_study;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONException;
import org.json.JSONObject;

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
        if ("android.intent.action.BOOT_COMPLETED".equals(i.getAction()) || "android.intent.action.QUICKBOOT_POWERON".equals(i.getAction())) {
            // device rebooted, reset the alarms (just start the app after a minute - change this later)
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    ctx.startActivity(new Intent(ctx, Launcher.class));
                }
            }, 60 * 1000);
        } else {
            int type = i.getIntExtra("type", -1);
            if (Globe.DEBUG) Log.d(TAG, "Called. Type is " + type);

            if (type == 0) DataUpdater.collectFitbitData(ctx);
            if (type == 1) DataUpdater.sendNotification(ctx, i);
            // if (type == 2) DataUpdater.airplaneModeOff(ctx);
            if (type == 3) Home.updateButtons();

            // I might move all of the functions to Globe.. we'll see
        }
    }

    private static void collectFitbitData(final Context ctx) {
        // Setup the network
        NetworkManager.getInstance(ctx);

        // Set some globals
        FirebaseApp.initializeApp(ctx); // this must be called here because we are outside the main process (this is called automatically INSIDE the main process)
        Globe.auth = FirebaseAuth.getInstance();
        Globe.user = Globe.auth.getCurrentUser();
        Globe.db = FirebaseDatabase.getInstance();
        Globe.dbRef = Globe.db.getReference();
        try {
            JSONObject data = Globe.readData(ctx);
            Globe.access_token = data.get("access_token").toString();
            Globe.refresh_token = data.get("refresh_token").toString();
            Globe.token_type = data.get("token_type").toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

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

    private static void sendNotification(Context ctx, Intent i) {
        if (Globe.DEBUG) Log.d(TAG, "Notification service started.");
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            PendingIntent pi = PendingIntent.getActivity(ctx, 2, i, 0);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, "Economics");

            builder.setTicker("Bedtime Soon!");
            builder.setContentTitle("Bedtime Soon!");
            builder.setContentText("Looks like it's almost your bedtime! Maybe start getting ready for bed soon?");
            builder.setSmallIcon(R.mipmap.ic_launcher);
            builder.setContentIntent(pi);

            Notification n = builder.build();

            // create the notification
            // n.vibrate = new long[]{150, 300, 150, 400};
            n.flags = Notification.FLAG_AUTO_CANCEL;
            nm.notify(R.mipmap.ic_launcher, n);
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
