package edu.umcp.justingoodman.fitbit_economics_study;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.support.customtabs.CustomTabsIntent;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutionException;

import static android.content.Context.ALARM_SERVICE;

/* Globe
 *
 * The global environment
 * Class holds global variables and functions needed through all parts of the app
 * Think of this as the app library
 *
 * PENDING-INTENT REQUEST CODES
 * 0 - fitbit updater, broadcast
 * 1 - bedtime notification, broadcast
 * 2 - redeem notification, broadcast
 * 3 - morning sleep check, broadcast
 * 4 - bedtime notification, activity
 * 5 - waketime notification, activity
 * 6 - morning sleep check, activity
 * **/
class Globe {

    /** variables */
    private static final String TAG = "Globe";

    private static final String FILENAME = "scr_data";
    private static final String client_secret = "ce339199861149c3dccf9a52f4e27623";
    static final String client_id = "228Q8G";
    static final String callback_uri = "scr://callback";
    static final String FITBIT_AUTH_URL = "https://api.fitbit.com/oauth2/token";
    static final String DOMAIN = "@sleep-coffee-research.firebaseapp.com";

    static long minSleep = 390; // the minimum sleep duration to earn a coupon (7 hours with 30min leniency)
    static long stage = 0; // 0 = passive stage (0-2 weeks), 1 = active stage (treatment/control groups, 3-6 weeks), 2 = ? (7-8 weeks)
    static long group = 0; // 0 = control group, 1 = treatment group
    static double bedTime = -1f; // goal bedtime
    static double notification = 1.0; // time before bedTime that we send a notification
    static double wakeTime = 10.0; // time the need to wake-up before to get coffee
    static String access_token = "";
    static String refresh_token = "";
    static String token_type = "";
    static FirebaseAuth auth;
    static FirebaseUser user;
    static FirebaseDatabase db;
    static DatabaseReference dbRef;
    static AlarmManager am;
    static PendingIntent senderFB; // for FitBit service
    static PendingIntent senderNS; // for bedtime notification service
    static PendingIntent senderRD; // for wakeup redeem notification
    static PendingIntent senderMN; // for morning sleep checking

    public static final boolean DEBUG = false; // set to false in production

    /** functions */
    static void init(Context ctx) {
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
        Globe.am = (AlarmManager) ctx.getSystemService(ALARM_SERVICE);
    }

    static double parseDouble(Object in, double def) {
        // in = object that may be a double
        // d = default double in case 'in' does not work
        double result = def;
        try {
            Double d = (Double) in;
            if (d != null)
                result = d;
        } catch (Exception e) {
            try {
                Long l = (Long) in;
                if (l != null)
                    result = l + 0.0;
            } catch (Exception f) {
                f.printStackTrace();
            }
        }
        return result;
    }

    static long parseLong(Object in, long def) { // took out the default since it's usually 0
        // in = object that may be a long
        // l = default long in case 'in' does not work
        long result = def;
        try {
            Long l = (Long) in;
            if (l != null)
                result = l;
        } catch (Exception e) {
            e.printStackTrace();
        }
        // maybe put something that checks if it was a double?
        return result;
    }

    static String getB64() {
        String result = "";
        try {
            result = Base64.encodeToString((client_id + ":" + client_secret).getBytes("UTF-8"), Base64.DEFAULT);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return result;
    }

    static String timeToString(double time) {
        int hour = (int) time;
        String m = "AM";
        if (hour == 0) {
            hour = 12;
        } else if (hour >= 12) {
            if (hour > 12)
                hour -= 12;
            m = "PM";
        }
        int mins = (int) ((time % 1) * 60);
        return (hour + ":" + ((mins < 10)?("0"):("")) + mins + " " + m);
    }

    static void scheduleAlarm(Context ctx, int type) {
        // type 0 = FitBit updater
        // type 1 = bedtime notification
        // type 2 = waketime notification
        if (Globe.am != null) {
            Calendar c = Calendar.getInstance(); // current time
            c.setTimeInMillis(System.currentTimeMillis());
            if (type == 0) {
                Intent iFB = new Intent(ctx, DataUpdater.class);
                iFB.putExtra("type", 0); // 0 = FitBit updater
                Globe.senderFB = PendingIntent.getBroadcast(ctx, 0, iFB, 0);
                c.set(Calendar.MINUTE, 55);
                // start at the 55min mark, 1 hour interval
                Globe.am.setInexactRepeating(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), AlarmManager.INTERVAL_HOUR, Globe.senderFB);
            } else if (type == 1) {
                Intent iNS = new Intent(ctx, DataUpdater.class);
                iNS.putExtra("type", 1); // 1 = bedtime notification
                Globe.senderNS = PendingIntent.getBroadcast(ctx, 1, iNS, 0);
                // calculate bedtime notification
                double bedtime = Globe.bedTime;
                bedtime -= Globe.notification; // subtract notification hours
                if (bedtime < 0)
                    bedtime += 24f;
                // setup time for alarm to go off
                if (c.get(Calendar.HOUR_OF_DAY) + (c.get(Calendar.MINUTE) / 60f) >= bedtime) // make sure we haven't passed the current bedtime
                    c.add(Calendar.DATE, 1); // add one day, because we passed the notification time
                c.set(Calendar.HOUR_OF_DAY, (int) bedtime);
                c.set(Calendar.MINUTE, (int) ((bedtime % 1) * 60));
                // start at bedtime notification time, 1 day interval (does not need to be exact)
                Globe.am.setInexactRepeating(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), AlarmManager.INTERVAL_DAY, Globe.senderNS);
            } else if (type == 2) {
                Intent iNS = new Intent(ctx, DataUpdater.class);
                iNS.putExtra("type", 2); // 2 = redeem notification
                Globe.senderRD = PendingIntent.getBroadcast(ctx, 2, iNS, 0);
                if (c.get(Calendar.HOUR_OF_DAY) + (c.get(Calendar.MINUTE) / 60f) >= 7.5) // make sure we haven't passed 7:30am
                    c.add(Calendar.DATE, 1); // add one day, because we passed 7:30am
                c.set(Calendar.HOUR_OF_DAY, 7);
                c.set(Calendar.MINUTE, 30);
                // start at 7:30am, 1 day interval (does not need to be exact)
                Globe.am.setInexactRepeating(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), AlarmManager.INTERVAL_DAY, Globe.senderRD);
            } else if (type == 3) {
                Intent iMN = new Intent(ctx, DataUpdater.class);
                iMN.putExtra("type", 3); // 3 - waketime sleep check
                Globe.senderMN = PendingIntent.getBroadcast(ctx, 3, iMN, 0);
                // no need to edit the calendar, I think...
                // can't set inexact repeating because this is every 5 minutes :(
                // start now, 5 minute interval
                Globe.am.setRepeating(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), 5 * 60 * 1000, Globe.senderMN);
            }
        }
    }

    static void authFitbit(Context ctx) {
        String url =
            "https://www.fitbit.com/oauth2/authorize?" +
            "prompt=none" +
            "&response_type=code" +
            "&client_id=" + Globe.client_id +
            "&redirect_uri=" + Globe.callback_uri +
            "&scope=activity%20sleep%20settings%20heartrate";
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setToolbarColor(0xff182B49);
        CustomTabsIntent cti = builder.build();
        cti.intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY); // hopefully it'll close now
        cti.launchUrl(ctx, Uri.parse(url));
    }

    static void calculateBedtime() {
        if (Globe.DEBUG) Log.d(TAG, "Running bedtime calculator");
        // Calculate average bedtime based on all sleep data in the database
        Globe.dbRef.child(Globe.user.getUid()).child("_sleep").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // int sleepTime = 0; // used for calculating sleep average
                double bed = 0f; // bedtime
                int nights = 0;
                // Integer i = null; // temp vars
                String s;
                int hour = 0;
                int min = 0;
                for (DataSnapshot d : dataSnapshot.getChildren()) {
                    // go thru each child node of '_sleep'
                    try {
                        // i = (Integer) d.child("minutesAsleep").getValue();
                        // if (i != null)
                            // sleepTime += i;
                        s = (String) d.child("startTime").getValue();
                        if (s != null) {
                            hour = Integer.parseInt(s.substring(11, 13)); // hopefully the time format does not change
                            min = Integer.parseInt(s.substring(14, 16));
                        }
                        if (hour < 12)
                            hour += 24;
                        bed += (hour + (min / 60f));
                        nights += 1;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (nights == 0) {
                    // sleepTime = 22;
                    bed = 22.0001f;
                    nights = 1;
                }
                bed = bed / nights;
                bed -= 1; // SET THE GOAL BEDTIME TO BE 1HR BEHIND THEIR AVERAGE (if Zack wants this different, he can change it himself in the database)
                if (bed >= 24f) // wrap around
                    bed -= 24f;

                // Finally, set the bedtime in the database
                if (Globe.DEBUG) Log.d(TAG, "Setting bedtime: " + bed);
                Globe.dbRef.child(Globe.user.getUid()).child("bedtime").setValue(bed + 0.0001f);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                if (Globe.DEBUG) Log.d(TAG, "Failed to read value.", error.toException());
            }
        });
    }

    static boolean refreshToken(final Context ctx) {
        if (Globe.DEBUG) Log.d(TAG, "attempting to refresh the token, given: " + refresh_token);
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Basic " + getB64());
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        Map<String, String>  params = new HashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", refresh_token);

        final TaskCompletionSource<Boolean> tcs = new TaskCompletionSource<>();
        Task<Boolean> t = tcs.getTask();

        NetworkManager.getInstance().makeRequest(ctx, Request.Method.POST, headers, params, FITBIT_AUTH_URL, new CustomListener<String>() {
            @Override
            public void getResult(String result) {
                if (!result.isEmpty()) {
                    try {
                        JSONObject response = new JSONObject(result);
                        Globe.access_token = (String) response.get("access_token");
                        Globe.token_type = (String) response.get("token_type");
                        Globe.refresh_token = (String) response.get("refresh_token");
                        Globe.writeData(ctx, response);

                        tcs.setResult(true); // we successfully refreshed the token, say so!
                    } catch (JSONException e) {
                        if (Globe.DEBUG) Log.d(TAG, "looks like SOMEONE hasn't authenticated Fitbit...");

                        tcs.setResult(false); // :(
                    }
                }
            }
        });

        try {
            Tasks.await(t);

            return t.getResult();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return false;
    }

    static void collectData(final Context ctx) {
        final TaskCompletionSource<Boolean> tcs1 = new TaskCompletionSource<>();
        final TaskCompletionSource<Boolean> tcs2 = new TaskCompletionSource<>();
        Task<Boolean> t1 = tcs1.getTask();
        Task<Boolean> t2 = tcs2.getTask();

        // Dates and such
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DATE, -30);
        String before = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
        c.add(Calendar.DATE, 31);
        String after = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
        c.add(Calendar.DATE, -1);
        String FITBIT_SLEEP_URL = "https://api.fitbit.com/1.2/user/-/sleep/date/" + before + "/" + after + ".json";
        String FITBIT_ACTIVITY_URL; // changes
        String FITBIT_DEVICE_URL = "https://api.fitbit.com/1/user/-/devices.json";
        String FITBIT_HEART_URL = "https://api.fitbit.com/1/user/-/activities/heart/date/" + before + "/" + after + ".json";

        // Set up Authorization
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", Globe.token_type + " " + Globe.access_token);

        // Get device battery level (no need for task, since this data is not vital to the research) (maybe add task later)
        NetworkManager.getInstance().makeRequest(ctx, Request.Method.GET, headers, null, FITBIT_DEVICE_URL, new CustomListener<String>() {
            @Override
            public void getResult(String result) {
                // when we receive response do this
                if (Globe.DEBUG) Log.d(TAG, "Attempting to extract FitBit DEVICE data...");
                if (!result.isEmpty()) {
                    if (Globe.DEBUG) Log.d(TAG, "Here's the result - " + result);
                    try {
                        JSONArray a = new JSONArray(result);
                        int len = a.length();
                        JSONObject test;
                        for (int i = 0; i < len; i++) {
                            test = a.getJSONObject(i);
                            if ("TRACKER".equals(test.get("type"))) {
                                String battery = (String) test.get("battery");
                                // set db and send notification if battery is 'Empty' or 'Low'
                                if ("Low".equals(battery) || "Empty".equals(battery)) {
                                    Globe.dbRef.child(Globe.user.getUid()).child("_battery").child((String) test.get("deviceVersion")).child(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Calendar.getInstance().getTime())).setValue(battery);

                                    NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                                    if (nm != null) {
                                        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, "Economics");

                                        b.setTicker("FitBit Battery");
                                        b.setContentTitle("FitBit Battery");
                                        b.setContentText("Your Fitbit battery is " + battery + ". Please charge your device.");
                                        b.setSmallIcon(R.mipmap.ic_launcher);
                                        // big style
                                        b.setStyle(new NotificationCompat.BigTextStyle().bigText("Your Fitbit battery is " + battery + ". Please charge your device."));

                                        Notification n = b.build();

                                        // create the notification
                                        // n.vibrate = new long[]{150, 300, 150, 400};
                                        n.flags = Notification.FLAG_AUTO_CANCEL;
                                        nm.notify(R.mipmap.ic_launcher, n);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    if (Globe.DEBUG) Log.d(TAG, "Response is empty...?");
                }
            }
        });

        // Get heartrate
        NetworkManager.getInstance().makeRequest(ctx, Request.Method.GET, headers, null, FITBIT_HEART_URL, new CustomListener<String>() {
            @Override
            public void getResult(String result) {
                if (Globe.DEBUG) Log.d(TAG, "Attempting to extract FitBit HEART data...");
                if (!result.isEmpty()) {
                    try {
                        JSONArray arr = (new JSONObject(result)).getJSONArray("activities-heart");
                        JSONObject j;
                        Map<String, Object> data = new HashMap<>();
                        int len = arr.length();

                        for (int i = 0; i < len; i++) {
                            try {
                                j = (JSONObject) arr.get(i);
                            } catch (Exception e) {
                                j = new JSONObject();
                            }
                            if (Globe.DEBUG) Log.d(TAG, j.toString());
                            try {
                                JSONObject value = (JSONObject) j.get("value");
                                JSONArray zones = value.getJSONArray("heartRateZones");
                                int zLen = zones.length();
                                for (int k = 0; k < zLen; k++) {
                                    JSONObject input = (JSONObject) zones.get(k);
                                    String name = (String) input.remove("name");
                                    Map<String, Object> sub = new HashMap<>();
                                    Iterator<String> idx = input.keys();
                                    while (idx.hasNext()) {
                                        String s = idx.next();
                                        sub.put(s, input.get(s));
                                    }
                                    data.put(name, sub);
                                }
                                try {
                                    data.put("restingHeartRate", value.get("restingHeartRate"));
                                } catch (Exception e) {
                                    if (Globe.DEBUG) Log.d(TAG, "No resting heart rate available!");
                                }
                                Globe.dbRef.child(Globe.user.getUid()).child("_heart").child(j.get("dateTime").toString()).updateChildren(data);
                            } catch (Exception e) {
                                if (Globe.DEBUG) Log.d(TAG, "One date segment had an error");
                                e.printStackTrace();
                            }
                            data.clear();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    if (Globe.DEBUG) Log.d(TAG, "Response is empty...?");
                }
            }
        });

        // Get prev. 30 sleep records
        NetworkManager.getInstance().makeRequest(ctx, Request.Method.GET, headers, null, FITBIT_SLEEP_URL, new CustomListener<String>() {
            @Override
            public void getResult(String result) {
                // This is what we do when we receive the response
                if (Globe.DEBUG) Log.d(TAG, "Attempting to extract FitBit SLEEP data...");
                if (!result.isEmpty()) {
                    if (Globe.DEBUG) Log.d(TAG, "Here's the result - " + result);
                    try {
                        // variables
                        JSONArray arr = (new JSONObject(result)).getJSONArray("sleep"); // we only need the sleep array
                        JSONArray test;
                        JSONObject j;
                        Map<String, Object> data = new HashMap<>();
                        Iterator<String> idx;
                        String s;
                        int len = arr.length();
                        boolean flag;

                        for (int i = 0; i < len; i++) {
                            flag = true;
                            try {
                                j = (JSONObject) arr.get(i);
                            } catch (Exception e) {
                                j = new JSONObject();
                            }
                            if (Globe.DEBUG) Log.d(TAG, j.toString());
                            // we need to test this to be sure no one is cheating
                            // the data array will generally have one element if the user entered their own sleep data
                            try {
                                test = ((JSONObject) j.get("levels")).getJSONArray("data");
                                if (test.length() <= 1)
                                    flag = false;
                            } catch (Exception e) {
                                if (Globe.DEBUG) Log.d(TAG, "Something went wrong while reading levels-data array");
                                flag = false;
                            }
                            if (flag) {
                                try {
                                    idx = j.keys();
                                    while (idx.hasNext()) {
                                        s = idx.next();
                                        if (s.equals("levels")) { // fuck
                                            try {
                                                JSONObject summary = (JSONObject) ((JSONObject) j.get("levels")).get("summary");
                                                String type = (String) j.get("type");
                                                // i'm sorry
                                                if (type.equals("classic")) {
                                                    JSONObject asleep = (JSONObject) summary.get("asleep");
                                                    JSONObject awake = (JSONObject) summary.get("awake");
                                                    JSONObject restless = (JSONObject) summary.get("restless");
                                                    data.put("classicAsleepCount", asleep.get("count"));
                                                    data.put("classicAsleepDuration", asleep.get("minutes"));
                                                    data.put("classicAwakeCount", awake.get("count"));
                                                    data.put("classicAwakeDuration", awake.get("minutes"));
                                                    data.put("classicRestlessCount", restless.get("count"));
                                                    data.put("classicRestlessDuration", restless.get("minutes"));
                                                    data.put("stagesDeepCount", "-");
                                                    data.put("stagesDeepDuration", "-");
                                                    data.put("stagesDeepThirtyDayAvg", "-");
                                                    data.put("stagesLightCount", "-");
                                                    data.put("stagesLightDuration", "-");
                                                    data.put("stagesLightThirtyDayAvg", "-");
                                                    data.put("stagesREMCount", "-");
                                                    data.put("stagesREMDuration", "-");
                                                    data.put("stagesREMThirtyDayAvg", "-");
                                                    data.put("stagesWakeCount", "-");
                                                    data.put("stagesWakeDuration", "-");
                                                    data.put("stagesWakeThirtyDayAvg", "-");
                                                } else if (type.equals("stages")) {
                                                    JSONObject deep = (JSONObject) summary.get("deep");
                                                    JSONObject light = (JSONObject) summary.get("light");
                                                    JSONObject rem = (JSONObject) summary.get("rem");
                                                    JSONObject wake = (JSONObject) summary.get("wake");
                                                    data.put("classicAsleepCount", "-");
                                                    data.put("classicAsleepDuration", "-");
                                                    data.put("classicAwakeCount", "-");
                                                    data.put("classicAwakeDuration", "-");
                                                    data.put("classicRestlessCount", "-");
                                                    data.put("classicRestlessDuration", "-");
                                                    data.put("stagesDeepCount", deep.get("count"));
                                                    data.put("stagesDeepDuration", deep.get("minutes"));
                                                    data.put("stagesDeepThirtyDayAvg", deep.get("thirtyDayAvgMinutes"));
                                                    data.put("stagesLightCount", light.get("count"));
                                                    data.put("stagesLightDuration", light.get("minutes"));
                                                    data.put("stagesLightThirtyDayAvg", light.get("thirtyDayAvgMinutes"));
                                                    data.put("stagesREMCount", rem.get("count"));
                                                    data.put("stagesREMDuration", rem.get("minutes"));
                                                    data.put("stagesREMThirtyDayAvg", rem.get("thirtyDayAvgMinutes"));
                                                    data.put("stagesWakeCount", wake.get("count"));
                                                    data.put("stagesWakeDuration", wake.get("minutes"));
                                                    data.put("stagesWakeThirtyDayAvg", wake.get("thirtyDayAvgMinutes"));
                                                }
                                            } catch (Exception e) {
                                                // i don't want this 'small' part to crash the whole data collection, so it gets its own try-catch
                                                if (Globe.DEBUG) Log.d(TAG, "Something went wrong getting the levels-summary data");
                                            }
                                        } else // thank god
                                            data.put(s, j.get(s));
                                    }
                                    Globe.dbRef.child(Globe.user.getUid()).child("_sleep").child(j.get("dateOfSleep").toString()).updateChildren(data);
                                } catch (Exception e) {
                                    if (Globe.DEBUG) Log.d(TAG, "One date segment had an error!");
                                }
                            }
                            data.clear();
                        }

                        if (Globe.DEBUG) Log.d(TAG, "Successfully uploaded sleep data.");
                        tcs1.setResult(true);
                    } catch (Exception e) {
                        // the JSONObject creation may have failed
                        // the JSONArray creation may have failed
                        // network may have not returned a 200 success
                        e.printStackTrace();
                        tcs1.setResult(false);
                    }
                } else {
                    if (Globe.DEBUG) Log.d(TAG, "Response is empty...?");
                    tcs1.setResult(false);
                }
            }
        });

        class Wrap {
            private int count;
            private Wrap() { count = 0; }
            synchronized int getCount() { return count; }
            synchronized void incCount() { count++; }
        }
        final Wrap w = new Wrap();

        // Get prev. 10 activity records (instead of 30 - doing this to save some api calls)
        for (int i = 0; i < 10; i++) {
            final String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
            FITBIT_ACTIVITY_URL = "https://api.fitbit.com/1/user/-/activities/date/" + date + ".json";
            NetworkManager.getInstance().makeRequest(ctx, Request.Method.GET, headers, null, FITBIT_ACTIVITY_URL, new CustomListener<String>() {
                @Override
                public void getResult(String result) {
                    // This is what we do when we receive the response
                    if (Globe.DEBUG) Log.d(TAG, "Attempting to extract FitBit ACTIVITY data...");
                    if (!result.isEmpty()) {
                        if (Globe.DEBUG) Log.d(TAG, "Here's the result - " + result);

                        try {
                            JSONObject res = new JSONObject(result).getJSONObject("summary");
                            JSONArray dist = res.getJSONArray("distances");

                            Map<String, Object> data = new HashMap<>();
                            data.put("totalSteps", res.get("steps"));
                            int length = dist.length();
                            for (int i = 0; i < length; i++)
                                data.put(dist.getJSONObject(i).get("activity") + "Distance", dist.getJSONObject(i).get("distance"));

                            data.put("veryActiveMinutes", res.get("veryActiveMinutes"));
                            data.put("fairlyActiveMinutes", res.get("fairlyActiveMinutes"));
                            data.put("lightlyActiveMinutes", res.get("lightlyActiveMinutes"));
                            data.put("sedentaryMinutes", res.get("sedentaryMinutes"));
                            data.put("caloriesOut", res.get("caloriesOut"));

                            Globe.dbRef.child(Globe.user.getUid()).child("_activity").child(date).updateChildren(data);
                            w.incCount();
                            if (w.getCount() >= 10)
                                tcs2.setResult(true);
                        } catch (Exception e) {
                            // res JSON may have failed
                            // dist array could have failed
                            e.printStackTrace();
                            if (Globe.DEBUG) Log.d(TAG, "Something went wrong.");
                            tcs2.setResult(false);
                        }
                    } else {
                        if (Globe.DEBUG) Log.d(TAG, "Response is empty...?");
                        tcs2.setResult(false);
                    }
                }
            });
            c.add(Calendar.DATE, -1);
        }

        if (Globe.DEBUG) {
            // turn off the Toast "updated fitbit" in production
            try {
                Tasks.await(t1);
                Tasks.await(t2);

                Handler h = new Handler(ctx.getMainLooper());
                if (t1.getResult() && t2.getResult()) {
                    if (Globe.DEBUG) Log.d(TAG, "Success!!");
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ctx, "Successfully captured FitBit data.", Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    if (Globe.DEBUG) Log.d(TAG, "Fail!!");
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ctx, "Something went wrong in capturing FitBit data.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }

        // set updated time to now
        c.add(Calendar.DATE, 10);
        Globe.dbRef.child(Globe.user.getUid()).child("updated").setValue(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(c.getTime()));
    }

    static JSONObject readData(Context ctx) {
        JSONObject result = new JSONObject();
        try {
            FileInputStream fis = ctx.openFileInput(FILENAME);
            StringBuilder sb = new StringBuilder();
            int c;
            while((c = fis.read()) != -1) {
                sb.append((char) c);
            }
            result = new JSONObject(sb.toString());
            fis.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (Globe.DEBUG) Log.d(TAG, "read these data: " + result.toString());
        }
        return result;
    }

    static void writeData(Context ctx, JSONObject newData) {
        try {
            JSONObject oldData = Globe.readData(ctx);

            // overwrite old data with new data
            Queue<String> written = new LinkedList<>();
            Iterator<String> oidx = oldData.keys();
            while(oidx.hasNext()) {
                String k = oidx.next();
                Iterator<String> nidx = newData.keys();
                while(nidx.hasNext()) {
                    String d = nidx.next();
                    if (k.equals(d)) {
                        oldData.put(k, newData.get(d));
                        written.add(d);
                    }
                }
            }

            // append new data that wasn't overwritten
            Iterator<String> nidx = newData.keys();
            while(nidx.hasNext()) {
                String d = nidx.next();
                if (!written.contains(d)) {
                    oldData.put(d, newData.get(d));
                }
            }

            FileOutputStream fos = ctx.openFileOutput(FILENAME, Context.MODE_PRIVATE);
            fos.write(oldData.toString().getBytes());
            fos.close();

            if (Globe.DEBUG) Log.d(TAG, "successfully wrote these data: " + oldData.toString());
        } catch (Exception e) {
            if (Globe.DEBUG) Log.d(TAG, "file does not exist, creating file");
            try {
                FileOutputStream fos = ctx.openFileOutput(FILENAME, Context.MODE_PRIVATE);
                fos.write(newData.toString().getBytes());
                fos.close();
            } catch (Exception f) {
                f.printStackTrace();
            }
        }
    }

    /*static void clearData(Context ctx) {
        try {
            // clear old data
            FileOutputStream fos = ctx.openFileOutput(FILENAME, Context.MODE_PRIVATE);
            JSONObject data = new JSONObject();
            fos.write(data.toString().getBytes());
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }*/
}
