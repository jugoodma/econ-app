package edu.umcp.justingoodman.fitbit_economics_study;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

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

// import org.json.JSONException;
// import org.json.JSONObject;

/* CoffeeRewards
 *
 * This is where users go to redeem their coffee coupon
 * Group 0 (control) should never have access to this, so we don't need to test it
 *
 * **/
public class CoffeeRewards extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "CoffeeRewards";

    private TextView dialogue;
    private TextView expire;
    // private Button cancel;
    private Button redeem;
    private ProgressBar p;

    private final Calendar c = Calendar.getInstance();
    private final String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    // private double button = -1f;
    private double start = -1f;
    private long sleeplen = 300;
    private boolean noCheat = false;
    private ValueEventListener vel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coffee_rewards);

        if (Globe.DEBUG) Log.d(TAG, "Creating...");

        // Set the views
        dialogue = findViewById(R.id.text_coffee);
        expire = findViewById(R.id.time_coffee);
        //cancel = findViewById(R.id.cancel_coffee);
        redeem = findViewById(R.id.redeem_coffee);
        p = findViewById(R.id.progressbar);
        p.setIndeterminate(true);
        redeem.setClickable(false); // smart
        redeem.setBackgroundColor(getResources().getColor(R.color.grayOut));
        expire.setText(String.format(getResources().getString(R.string.redeemTime), "--", "--:--"));

        c.setTimeInMillis(System.currentTimeMillis());

        vel = Globe.dbRef.child(Globe.user.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Globe.wakeTime = Globe.parseDouble(dataSnapshot.child("waketime").getValue(), 10.0);
                Globe.bedTime = Globe.parseDouble(dataSnapshot.child("bedtime").getValue(), -1.0); // are you shitting me, I missed the 'getValue(). FUCK
                expire.setText(String.format(getResources().getString(R.string.redeemTime), today, Globe.timeToString(Globe.wakeTime)));
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                if (Globe.DEBUG) Log.d(TAG, "Failed to read value.", error.toException());
            }
        });

        findViewById(R.id.cancel_coffee).setOnClickListener(CoffeeRewards.this);
        redeem.setOnClickListener(CoffeeRewards.this);

        p.setVisibility(View.VISIBLE); // waiting
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                threader();
            }
        });
        thread.start();

        if (Globe.DEBUG) Log.d(TAG, "Created.");
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Globe.dbRef.child(Globe.user.getUid()).removeEventListener(vel);
        finish();
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.redeem_coffee) {
            // signal a redemption in the db and check that the current time is still behind the wake-time (5min leeway)
            // if we got here, then button and start floats are still valid times
            String value;
            if (c.get(Calendar.HOUR_OF_DAY) + (c.get(Calendar.MINUTE) / 60f) <= Globe.wakeTime + (5 / 60f)) {
                dialogue.setText(getResources().getString(R.string.thanks));
                value = new SimpleDateFormat("HH:mm:ss", Locale.US).format(c.getTime());
            } else {
                dialogue.setText(getResources().getString(R.string.pastCoupon));
                dialogue.setBackgroundColor(getResources().getColor(R.color.red));
                value = "nil";
            }
            // We want to track the redemption time
            Globe.dbRef.child(Globe.user.getUid()).child("_coffee").child(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime())).setValue(value);
            redeem.setClickable(false);
            redeem.setBackgroundColor(getResources().getColor(R.color.grayOut));
            expire.setText(getResources().getString(R.string.expired));
            // This allows the user to only use the coupon once!
            /*
            try {
                Globe.writeData(CoffeeRewards.this, new JSONObject().put("hitForDate", "-"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            */
            // button = -1f; // this doesn't matter too much
        } else if (i == R.id.cancel_coffee) {
            onBackPressed();
        }
    }

    private void threader() {
        // do some logic and maybe display a coupon

        /*
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                getButton();
            }
        });
        */
        Thread t4 = new Thread(new Runnable() {
            @Override
            public void run() {
                testCheat();
            }
        });
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                getStart();
            }
        });
        Thread t3 = new Thread(new Runnable() {
            @Override
            public void run() {
                coupon();
            }
        });

        // t1.start();
        // if (Globe.DEBUG) Log.d(TAG, "Thread1 started");
        t4.start();
        if (Globe.DEBUG) Log.d(TAG, "Thread4 started");
        t2.start();
        if (Globe.DEBUG) Log.d(TAG, "Thread2 started");

        try {
            t4.join(); // wait
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (Globe.DEBUG) Log.d(TAG, "Thread4 joined");
        /*
        try {
            t1.join(); // wait
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (Globe.DEBUG) Log.d(TAG, "Thread1 joined");
        */
        try {
            t2.join(); // wait
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (Globe.DEBUG) Log.d(TAG, "Thread2 joined");

        // after this point, cheat & button & start & sleeplen are set
        t3.start();
        try {
            t3.join(); // wait
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (Globe.DEBUG) Log.d(TAG, "Thread3 joined");

    }

    // returns the time that the user hit the 'in-bed' button
    /*
    private void getButton() {
        double result = -1f;
        JSONObject data = Globe.readData(CoffeeRewards.this);
        try {
            if (today.equals(data.get("hitForDate")))
                result = (Double) data.get("hitTime");
        } catch (Exception e) {
            e.printStackTrace();
            // handle ClassCastException later
        }
        if (Globe.DEBUG) Log.d(TAG, "Hit in-bed button time: " + result);
        button = result;
    }
    */

    // test if the user is cheating their coupon
    private void testCheat() {
        boolean result = false;
        final TaskCompletionSource<DataSnapshot> tcs = new TaskCompletionSource<>();
        final Task<DataSnapshot> t = tcs.getTask();
        Globe.dbRef.child(Globe.user.getUid()).child("_coffee").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) { tcs.setResult(dataSnapshot); }
            @Override
            public void onCancelled(DatabaseError error) { tcs.setException(error.toException()); }
        });
        try {
            if (Globe.DEBUG) Log.d(TAG, "Waiting for task in Thread4...");
            Tasks.await(t, 15, TimeUnit.SECONDS);
            if (Globe.DEBUG) Log.d(TAG, "Task in Thread4 complete");
            result = !t.getResult().hasChild(today); // if has child, then we cheating and set false
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            if (Globe.DEBUG) Log.d(TAG, "Failed to read value.");
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (Globe.DEBUG) Log.d(TAG, "setting cheat value");
        noCheat = result;
    }

    // returns the time the user actually went to sleep
    // also gets how LONG the user slept
    private void getStart() {
        double result = -1f;
        long length = 400;
        final TaskCompletionSource<DataSnapshot> tcs = new TaskCompletionSource<>();
        final Task<DataSnapshot> t = tcs.getTask();
        Globe.dbRef.child(Globe.user.getUid()).child("_sleep").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) { tcs.setResult(dataSnapshot); }
            @Override
            public void onCancelled(DatabaseError error) { tcs.setException(error.toException()); }
        });
        try {
            if (Globe.DEBUG) Log.d(TAG, "Waiting for task in Thread2...");
            Tasks.await(t, 15, TimeUnit.SECONDS);
            if (Globe.DEBUG) Log.d(TAG, "Task in Thread2 complete");
            // sleep start time
            try {
                String s = (String) t.getResult().child(today).child("startTime").getValue();
                if (Globe.DEBUG) Log.d(TAG, "startTime " + s);
                if (s != null) {
                    result = 0f;
                    result += Integer.parseInt(s.substring(11, 13));
                    result += Integer.parseInt(s.substring(14, 16)) / 60f;
                }
            } catch (Exception e) {
                // if this fails I still want ot run the second part
                e.printStackTrace();
            }
            // sleep length
            try {
                length = Globe.parseLong(t.getResult().child(today).child("minutesAsleep").getValue(), 300); // default is something greater than the min requirement
                if (Globe.DEBUG) Log.d(TAG, "minutesAsleep " + length);

            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            if (Globe.DEBUG) Log.d(TAG, "Failed to read value.");
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (Globe.DEBUG) Log.d(TAG, "setting start value & sleep length " + result + ", " + length);
        start = result;
        sleeplen = length;
    }

    // decides if the user gets the coupon
    private void coupon() {
        final double time = c.get(Calendar.HOUR_OF_DAY) + (c.get(Calendar.MINUTE) / 60f); // current time
        Runnable r;

        // reward is given IF:
        // group 0 (control) need not press the 'in-bed' button before their bedtime
        // otherwise, 'in-bed' button must be pressed (group 1 (treatment) must press it before bedtime)
        // participant must be asleep within 30 minutes of pressing 'in-bed' button

        // UPDATE: 'in-bed' button does not need to be pressed for any group
        // time should be before waketime (redeem time) (5min leeway) & they need a sleep record

        // UPDATE: user must also have slept 7 hours (420mins) with a 30min leniency (390mins)
        // Globe.minSleep includes the leniency
        if (noCheat && time <= Globe.wakeTime + (5 / 60f) /* && button != -1f */ && start != -1f && sleeplen >= Globe.minSleep) {
            // button = time the user hit the 'in-bed' button
            // start = time the user fell asleep
            /*
            if (button < 12f)
                button += 24f;
            */
            // hopefully this will fix the math!
            if (start < 12 && Globe.bedTime >= 12) // did not press it in time
                start += 24f;
            else if (start >= 12 && Globe.bedTime < 12) // did press in time
                start -= 24f;
            /*
            boolean g1check = true;
            if (Globe.group == 1 && button > Globe.bedTime) {
                g1check = false; // simplify this later
            }
            */
            // 'in-bed' button time must be within 30 minutes of falling asleep (NOT ANYMORE) (button <= start + 0.5 && g1check)
            // UPDATE: user must have fallen asleep before bedtime (i'm adding 5min leeway here)
            if (Globe.DEBUG) Log.d(TAG, "start " + start + " bedtime " + Globe.bedTime);
            if (start <= Globe.bedTime + (5 / 60f)) {
                // display coupon
                // display cancel/redeem buttons for Barista
                r = new Runnable() {
                    @Override
                    public void run() {
                        redeem.setClickable(true);
                        redeem.setBackgroundColor(getResources().getColor(R.color.orange));
                        dialogue.setBackgroundColor(getResources().getColor(R.color.green));
                        dialogue.setText(String.format(getResources().getString(R.string.valid), new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Calendar.getInstance().getTime())));
                    }
                };
            } else {
                // no display coupon
                r = new Runnable() {
                    @Override
                    public void run() {
                        redeem.setClickable(false);
                        redeem.setBackgroundColor(getResources().getColor(R.color.grayOut));
                        dialogue.setBackgroundColor(getResources().getColor(R.color.red));
                        dialogue.setText(getResources().getString(R.string.invalidCoffee));
                        expire.setText(getResources().getString(R.string.expired));
                    }
                };
            }
        } else {
            // display a message
            r = new Runnable() {
                @Override
                public void run() {
                    redeem.setClickable(false);
                    redeem.setBackgroundColor(getResources().getColor(R.color.grayOut));
                    dialogue.setBackgroundColor(Color.GRAY);
                    if (time >= Globe.wakeTime)
                        dialogue.setText(getResources().getString(R.string.pastCoupon));
                    else if (start == -1f)
                        dialogue.setText(getResources().getString(R.string.syncFitbit));
                    else
                        dialogue.setText(getResources().getString(R.string.cheat));
                    expire.setText(getResources().getString(R.string.expired));
                }
            };
        }

        runOnUiThread(r);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                p.setVisibility(View.GONE);
            }
        });
    }
}
