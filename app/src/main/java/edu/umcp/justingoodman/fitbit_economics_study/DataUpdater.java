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

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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
            // now test the user's sleep data, and see if they MIGHT earn a coffee
            Globe.init(ctx);
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(System.currentTimeMillis());
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
            // get the sleep start time
            double time = -1.0;
            boolean flag = false;
            String message = "Sync your FitBit to find out if you earned a free coffee!";
            final TaskCompletionSource<DataSnapshot> tcs = new TaskCompletionSource<>();
            final Task<DataSnapshot> t = tcs.getTask();
            Globe.dbRef.child(Globe.user.getUid()).child("_sleep").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) { tcs.setResult(dataSnapshot); }
                @Override
                public void onCancelled(DatabaseError error) { tcs.setException(error.toException()); }
            });
            try {
                Tasks.await(t, 15, TimeUnit.SECONDS);
                String s = (String) t.getResult().child(today).child("startTime").getValue();
                if (Globe.DEBUG) Log.d(TAG, "startTime " + s);
                if (s != null) {
                    time += Integer.parseInt(s.substring(11, 13));
                    time += Integer.parseInt(s.substring(14, 16)) / 60f;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                if (Globe.DEBUG) Log.d(TAG, "Failed to read value.");
                flag = true;
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (!flag) { // java simplified this for me, hopefully it works lol
                flag = !(time < 12 && Globe.bedTime >= 12) && (time >= 12 && Globe.bedTime < 12 || time <= Globe.wakeTime + (5 / 60f));
            }
            if (nm != null && flag) {
                PendingIntent pi = PendingIntent.getActivity(ctx, 4, new Intent(ctx, Launcher.class), 0);

                NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, "Economics");

                b.setTicker("Coffee Coupon");
                b.setContentTitle("Coffee Coupon");
                b.setContentText(message);
                b.setSmallIcon(R.mipmap.ic_launcher);
                b.setContentIntent(pi);
                // big style
                b.setStyle(new NotificationCompat.BigTextStyle().bigText(message));

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
