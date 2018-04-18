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

import com.android.volley.Request;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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

        switch (type) {
            case 0:
                DataUpdater.collectFitbitData(ctx);
                break;
            case 1:
                DataUpdater.bedtimeNotification(ctx);
                break;
            case 2:
                DataUpdater.waketimeNotification(ctx);
                break;
            case 3:
                DataUpdater.morningSleepCheck(ctx);
                break;
            case 4:
                DataUpdater.redeemChecker(ctx);
                break;
            case 5:
                int select = i.getIntExtra("select", -1);
                DataUpdater.redeemCheckerHandler(ctx, select);
                break;
        }

        // I might move all of the functions to Globe.. we'll see
    }

    private static void collectFitbitData(final Context ctx) {
        // Setup the network
        NetworkManager.getInstance(ctx);

        // Set some globals
        Globe.init(ctx);

        // check for morning notifications
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        Intent iMN = new Intent(ctx, DataUpdater.class);
        iMN.putExtra("type", 3);
        Globe.senderMN = PendingIntent.getBroadcast(ctx, 3, iMN, 0);
        if (hour == 5 && day != Calendar.SATURDAY && day != Calendar.SUNDAY) {
            // should happen at 5:55am
            Globe.scheduleAlarm(ctx, 3);
        } else if ((hour <= 4 || hour >= 9) && Globe.am != null && Globe.senderMN != null) {
            // should happen at 9:55am
            Globe.am.cancel(Globe.senderMN);
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

    private static void bedtimeNotification(final Context ctx) {
        if (Globe.DEBUG) Log.d(TAG, "Bedtime notification service started.");
        // check if it's sunday-thursday evenings (DEPENDENT ON CURRENT TIME)
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if ((hour < 12 && day != Calendar.SATURDAY && day != Calendar.SUNDAY) || (hour >= 12 && day != Calendar.FRIDAY && day != Calendar.SATURDAY)) {
            final NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                Globe.init(ctx);
                // gotta make sure the bedtime is correct!
                // if this is called, then it's likely that the user is part of stage 1
                Globe.dbRef.child(Globe.user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot d) {
                        String time = Globe.timeToString(Globe.parseDouble(d.child("bedtime").getValue(), 23.0));
                        PendingIntent pi = PendingIntent.getActivity(ctx, 7, new Intent(ctx, Launcher.class), 0);

                        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, "Economics");

                        b.setTicker("Bedtime Soon!");
                        b.setContentTitle("Bedtime Soon!");
                        b.setContentText("Your bedtime is at " + time + "! Start getting ready for bed soon!");
                        if (Globe.DEBUG) Log.d(TAG, "Your bedtime is at " + time + "! Start getting ready for bed soon!");
                        b.setSmallIcon(R.mipmap.ic_launcher);
                        b.setContentIntent(pi);
                        // big style
                        b.setStyle(new NotificationCompat.BigTextStyle().bigText("Your bedtime is at " + time + "! Start getting ready for bed soon!"));

                        Notification n = b.build();

                        // create the notification
                        // n.vibrate = new long[]{150, 300, 150, 400};
                        n.flags = Notification.FLAG_AUTO_CANCEL;
                        nm.notify(R.mipmap.ic_launcher, n);
                    }

                    @Override
                    public void onCancelled(DatabaseError e) {
                        // fuck
                        if (Globe.DEBUG) Log.d(TAG, "Failed to read value.", e.toException());
                    }
                });
            }
        }
    }

    private static void waketimeNotification(final Context ctx) {
        // somehow eventually a picture should display in the notification if they earned a coupon fuck my life
        if (Globe.DEBUG) Log.d(TAG, "Waketime notification service started.");
        // we want to actually figure out if they earned the coupon -- realistically,
        // FitBit probably has not figured out the user's sleep yet... wtf
        // later - set actual redemption time, and do a *quick* check of validity
        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        // no point in sending notification on sat/sun
        if (!(day == Calendar.SATURDAY || day == Calendar.SUNDAY)) {
            // now test the user's sleep data, and see if they MIGHT earn a coffee
            Globe.init(ctx);
            Globe.dbRef.child(Globe.user.getUid()).child("_sleep").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                    String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
                    double time = -1.0; // get the sleep start time
                    boolean flag = false;
                    String message = "Sync your FitBit to find out if you earned a free coffee.";
                    String s = (String) dataSnapshot.child(today).child("startTime").getValue();
                    if (Globe.DEBUG) Log.d(TAG, "startTime " + s);
                    if (s != null) {
                        time += Integer.parseInt(s.substring(11, 13));
                        time += Integer.parseInt(s.substring(14, 16)) / 60f;
                    }
                    if (time > -1) { // java simplified this for me, hopefully it works lol
                        flag = !(time < 12 && Globe.bedTime >= 12) && (time >= 12 && Globe.bedTime < 12 || time <= Globe.wakeTime + (5 / 60f));
                        if (flag)
                            message = "You may have earned a free coffee, nice!";
                    }
                    if (nm != null && flag) {
                        PendingIntent pi = PendingIntent.getActivity(ctx, 8, new Intent(ctx, Launcher.class), 0);

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

                @Override
                public void onCancelled(DatabaseError error) { error.toException().printStackTrace(); }
            });
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

    private static void morningSleepCheck(final Context ctx) {
        if (Globe.DEBUG) Log.d(TAG, "Morning check for sleep");
        // this is quick and dirty... basically,
        // - setup Globe and shit
        // - call sleep api
        // - check for sleep
        // - if there's sleep, send a notification and cancel the alarm
        // - else, do nothing and wait for the next 5-min cycle

        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        // one last check just to be sure...
        if (day != Calendar.SATURDAY && day != Calendar.SUNDAY && hour >= 5 && hour < 10) {
            NetworkManager.getInstance(ctx); // no illegal-state
            Globe.init(ctx);

            Globe.dbRef.child(Globe.user.getUid()).child("group").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    // check group
                    if (Globe.parseLong(dataSnapshot.getValue(), 0) == 1) {
                        // Set up Authorization
                        Map<String, String> headers = new HashMap<>();
                        headers.put("Authorization", Globe.token_type + " " + Globe.access_token);

                        // the "dateOfSleep" returned from this will be the day the user woke up (I HOPE), which is today
                        String FITBIT_SLEEP_URL = "https://api.fitbit.com/1.2/user/-/sleep/date/" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime()) + ".json";

                        // do the rest of the work in here
                        NetworkManager.getInstance().makeRequest(ctx, Request.Method.GET, headers, null, FITBIT_SLEEP_URL, new CustomListener<String>() {
                            @Override
                            public void getResult(String result) {
                                if (Globe.DEBUG) Log.d(TAG, "checking for sleep...");
                                if (!result.isEmpty()) {
                                    if (Globe.DEBUG) Log.d(TAG, "Here's the result - " + result);
                                    String message = "Sync your Fitbit to see if you’ve earned free coffee.";
                                    // now for a big try/catch
                                    try {
                                        JSONObject j = new JSONObject(result);
                                        JSONArray test = ((JSONObject) (((JSONObject) j.getJSONArray("sleep").get(0)).get("levels"))).getJSONArray("data");
                                        // test cheating
                                        if (test.length() > 1) {
                                            // probably not cheating
                                            if (Globe.parseLong(((JSONObject) j.get("summary")).get("totalMinutesAsleep"), 300) >= Globe.minSleep) {
                                                // they slept long enough
                                                // get the user's bedtime
                                                double bedtime = 23.9;
                                                double starttime = 0.0;
                                                double waketime = 10.0;
                                                final TaskCompletionSource<DataSnapshot> tcs = new TaskCompletionSource<>();
                                                final Task<DataSnapshot> t = tcs.getTask();
                                                Globe.dbRef.child(Globe.user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                                                    @Override
                                                    public void onDataChange(DataSnapshot dataSnapshot) {
                                                        tcs.setResult(dataSnapshot);
                                                    }

                                                    @Override
                                                    public void onCancelled(DatabaseError error) {
                                                        tcs.setException(error.toException());
                                                    }
                                                });
                                                try {
                                                    if (Globe.DEBUG)
                                                        Log.d(TAG, "Waiting for bedtime");
                                                    Tasks.await(t, 15, TimeUnit.SECONDS);
                                                    if (Globe.DEBUG) Log.d(TAG, "Bedtime found");
                                                    bedtime = Globe.parseDouble(t.getResult().child("bedtime").getValue(), 23.9);
                                                    waketime = Globe.parseDouble(t.getResult().child("waketime").getValue(), 10.0);
                                                } catch (InterruptedException e) {
                                                    e.printStackTrace();
                                                } catch (ExecutionException e) {
                                                    if (Globe.DEBUG)
                                                        Log.d(TAG, "Failed to read value.");
                                                    e.printStackTrace();
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }

                                                // get the sleep start time
                                                String s = (String) ((JSONObject) j.getJSONArray("sleep").get(0)).get("startTime");
                                                starttime += Integer.parseInt(s.substring(11, 13));
                                                starttime += Integer.parseInt(s.substring(14, 16)) / 60f;

                                                // check bedtime
                                                if (starttime < 12 && bedtime >= 12) // did not press it in time
                                                    starttime += 24f;
                                                else if (starttime >= 12 && bedtime < 12) // did press in time
                                                    starttime -= 24f;
                                                if (starttime <= bedtime + (5 / 60f)) {
                                                    // they earned a coupon
                                                    message = "You’ve earned free coffee! Redeem by " + Globe.timeToString(waketime) + ".";
                                                } else {
                                                    // did not get to bed in time
                                                    message = "You missed your goal bedtime. Get to bed on time tonight to earn your reward.";
                                                }
                                            } else {
                                                // coupon not earned because they did not sleep 7 hrs
                                                message = "You did not sleep long enough. Sleep at least seven hours tonight to earn your reward.";
                                            }
                                        } else {
                                            // probably cheating
                                            message = "You manually input sleep data. Please let the device determine your bedtime and sleep duration to earn your reward.";
                                        }

                                        // we made it here, hence there was sleep, so cancel the alarm
                                        if (Globe.am != null && Globe.senderMN != null)
                                            Globe.am.cancel(Globe.senderMN);
                                    } catch (Exception e) {
                                        // no sleep, better luck next time
                                        e.printStackTrace();
                                    }

                                    // send notification
                                    NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                                    if (nm != null) {
                                        PendingIntent pi = PendingIntent.getActivity(ctx, 9, new Intent(ctx, Launcher.class), 0);

                                        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, "Economics");

                                        b.setTicker("Good Morning!");
                                        b.setContentTitle("Good Morning!");
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

                                    // done
                                }
                            }
                        });
                    } else {
                        // cancel
                        if (Globe.am != null && Globe.senderMN != null)
                            Globe.am.cancel(Globe.senderMN);
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    databaseError.toException().printStackTrace();
                }
            });
        }
    }

    private static void redeemChecker(final Context ctx) {
        if (Globe.DEBUG) Log.d(TAG, "10am check to see if they redeemed a coffee coupon");
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        if (day != Calendar.SATURDAY && day != Calendar.SUNDAY) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                PendingIntent pi = PendingIntent.getActivity(ctx, 10, new Intent(ctx, Launcher.class), 0);

                NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, "Economics");

                b.setTicker("Coffee Redemption");
                b.setContentTitle("Coffee Redemption");
                b.setContentText("Did you redeem a coffee coupon this morning?");
                b.setSmallIcon(R.mipmap.ic_launcher);
                b.setContentIntent(pi);

                Intent i = new Intent(ctx, DataUpdater.class);
                i.putExtra("type", 5);

                b.addAction(R.drawable.ic_yes, "YES", PendingIntent.getBroadcast(ctx, 5, i.putExtra("select", 0), 0));
                b.addAction(R.drawable.ic_no, "NO", PendingIntent.getBroadcast(ctx, 6, i.putExtra("select", 1), 0));

                b.setStyle(new NotificationCompat.BigTextStyle().bigText("Did you redeem a coffee coupon this morning?"));

                Notification n = b.build();

                n.flags = Notification.FLAG_AUTO_CANCEL;
                nm.notify(R.mipmap.ic_launcher, n);

            }
        } // else, no coffee on weekends so no need to ask!
    }

    private static void redeemCheckerHandler(final Context ctx, int select) {
        if (Globe.DEBUG) Log.d(TAG, "sending to the db the user's selection = " + select);
        NetworkManager.getInstance(ctx); // no illegal-state
        Globe.init(ctx);
        Calendar c = Calendar.getInstance();
        Globe.dbRef.child(Globe.user.getUid()).child("_coffee").child(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime())).child("coffeeRedeemResponse").setValue(select == 0 ? "yes" : "no");
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(R.mipmap.ic_launcher);
    }
}
