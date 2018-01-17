package edu.umcp.justingoodman.fitbit_economics_study;

import android.content.Context;
import android.net.Uri;
import android.support.customtabs.CustomTabsIntent;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
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

/* Globe
 *
 * The global environment
 * Class holds global variables and functions needed through all parts of the app
 * Think of this as the app library
 *
 * **/
class Globe {

    /** variables */
    private static final String TAG = "GLOBAL CALL";

    private static final String FILENAME = "scr_data";
    static final String client_id = "228Q8G";
    // *** client secret is below (only ever used here)
    static final String callback_uri = "scr://callback";
    static final String FITBIT_AUTH_URL = "https://api.fitbit.com/oauth2/token";

    static long stage = 0; // 0 = passive stage (0-2 weeks), 1 = active stage (treatment/control groups, 3-6 weeks), 2 = ? (7-8 weeks)
    static long group = 0; // 0 = control group, 1 = treatment group
    static double bedTime = -1f; // goal bedtime
    static double notification = 1.0; // time before bedTime that we send a notification
    static double wakeTime = 8.0; // time the need to wake-up before to get coffee
    static String access_token = "";
    static String refresh_token = "";
    static String token_type = "";
    static FirebaseAuth auth;
    static FirebaseUser user;
    static FirebaseDatabase db;
    static DatabaseReference dbRef;

    public static final boolean DEBUG = false; // set to false in production

    /** functions */
    static String getB64() {
        String result = "";
        try {
            String client_secret = "ce339199861149c3dccf9a52f4e27623"; // *** client secret
            result = Base64.encodeToString((client_id + ":" + client_secret).getBytes("UTF-8"), Base64.DEFAULT);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return result;
    }

    static String timeToString(double time) {
        int mins = (int) ((time % 1) * 60);
        return ((int) time + ":" + ((mins < 10)?("0"):("")) + mins);
    }

    static void authFitbit(Context ctx) {
        String url =
            "https://www.fitbit.com/oauth2/authorize?" +
            "prompt=none" +
            "&response_type=code" +
            "&client_id=" + Globe.client_id +
            "&redirect_uri=" + Globe.callback_uri +
            "&scope=activity%20sleep";
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setToolbarColor(0xff182B49);
        CustomTabsIntent cti = builder.build();
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
        c.add(Calendar.DATE, 1);
        String FITBIT_SLEEP_URL = "https://api.fitbit.com/1.2/user/-/sleep/list.json?beforeDate=" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime()) + "&sort=desc&offset=0&limit=30";
        c.add(Calendar.DATE, -1);
        String FITBIT_ACTIVITY_URL;

        // Set up Authorization
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", Globe.token_type + " " + Globe.access_token);

        // Get prev. 30 sleep records
        NetworkManager.getInstance().makeRequest(ctx, Request.Method.GET, headers, null, FITBIT_SLEEP_URL, new CustomListener<String>() {
            @Override
            public void getResult(String result) {
                // This is what we do when we receive the response
                if (Globe.DEBUG) Log.d(TAG, "Attempting to extract FitBit SLEEP data...");
                if (!result.isEmpty()) {
                    if (Globe.DEBUG) Log.d(TAG, "Here's the result - " + result);
                    try {
                        JSONArray arr = (new JSONObject(result)).getJSONArray("sleep"); // we only need the sleep array
                        JSONObject j;
                        Map<String, Object> data = new HashMap<>();
                        Iterator<String> idx;
                        String s;
                        int len = arr.length();

                        for (int i = 0; i < len; i++) {
                            try {
                                j = (JSONObject) arr.get(i);
                            } catch (Exception e) {
                                j = new JSONObject();
                            }
                            if (Globe.DEBUG) Log.d(TAG, j.toString());
                            try {
                                idx = j.keys();
                                while (idx.hasNext()) {
                                    s = idx.next();
                                    if (!s.equals("levels")) // skip the levels object
                                        data.put(s, j.get(s));
                                }
                                Globe.dbRef.child(Globe.user.getUid()).child("_sleep").child(j.get("dateOfSleep").toString()).updateChildren(data);
                            } catch (Exception e) {
                                if (Globe.DEBUG) Log.d(TAG, "One date segment had an error!");
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

        try {
            Tasks.await(t1);
            Tasks.await(t2);

            if (t1.getResult() && t2.getResult()) {
                Toast.makeText(ctx, "Successfully captured FitBit data.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ctx, "Something went wrong in capturing FitBit data.", Toast.LENGTH_SHORT).show();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        // set updated time to now
        c.add(Calendar.DATE, 10);
        Globe.dbRef.child(Globe.user.getUid()).child("updated").setValue(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(c.getTime()));
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
