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

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/* CoffeeRewards
 *
 * This is where users go to redeem their coffee coupon
 *
 * **/
public class CoffeeRewards extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "CoffeeRewards";

    private TextView dialogue;
    private Button mainRedeem;
    private Button cancel;
    private Button redeem;
    private ProgressBar p;

    private final Calendar c = Calendar.getInstance();
    private final String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    private double button = -1f;
    private double start = -1f;
    private ValueEventListener vel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coffee_rewards);

        if (Globe.DEBUG) Log.d(TAG, "Creating...");

        // Set the views
        dialogue = findViewById(R.id.text_coffee);
        mainRedeem = findViewById(R.id.button_coffee);
        cancel = findViewById(R.id.cancel_coffee);
        redeem = findViewById(R.id.redeem_coffee);
        p = findViewById(R.id.progressbar);
        p.setIndeterminate(true);
        p.setVisibility(View.GONE);

        vel = Globe.dbRef.child(Globe.user.getUid()).child("waketime").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Double waketime = 8.0;
                try {
                    Double d = (Double) dataSnapshot.getValue();
                    if (d != null)
                        waketime = d;
                } catch (Exception e) {
                    try {
                        Long l = (Long) dataSnapshot.getValue();
                        if (l != null)
                            waketime = l + 0.0;
                    } catch (Exception f) {
                        f.printStackTrace();
                    }
                }
                Globe.wakeTime = waketime;
                ((TextView) findViewById(R.id.time_coffee)).setText(String.format(getResources().getString(R.string.redeemTime), Globe.timeToString(Globe.wakeTime)));
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                if (Globe.DEBUG) Log.d(TAG, "Failed to read value.", error.toException());
            }
        });

        mainRedeem.setOnClickListener(CoffeeRewards.this);
        cancel.setOnClickListener(CoffeeRewards.this);
        redeem.setOnClickListener(CoffeeRewards.this);

        if (Globe.DEBUG) Log.d(TAG, "Created.");
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Globe.dbRef.child(Globe.user.getUid()).child("waketime").removeEventListener(vel);
        finish();
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.button_coffee) {
            p.setVisibility(View.VISIBLE); // waiting
            mainRedeem.setClickable(false);
            mainRedeem.setBackgroundColor(getResources().getColor(R.color.grayOut));
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    threader();
                }
            });
            thread.start();
        } else if (i == R.id.redeem_coffee) {
            // signal a redemption in the db and check that the current time is still behind the wake-time (5min leeway)
            // if we got here, then button and start floats are still valid times
            if (c.get(Calendar.HOUR_OF_DAY) + (c.get(Calendar.MINUTE) / 60f) <= Globe.wakeTime + (5 / 60f)) {
                dialogue.setText(getResources().getString(R.string.thanks));
                cancel.setVisibility(View.GONE);
                redeem.setVisibility(View.GONE);
                Globe.dbRef.child(Globe.user.getUid()).child("_coffee").child(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Calendar.getInstance().getTime())).setValue(1);
                // This allows the user to only use the coupon once!
                try {
                    Globe.writeData(CoffeeRewards.this, new JSONObject().put("hitForDate", "-"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                button = -1f; // this doesn't matter too much
            } else {
                cancel.setVisibility(View.GONE);
                redeem.setVisibility(View.GONE);
                dialogue.setText(getResources().getString(R.string.pastCoupon));
                dialogue.setBackgroundColor(Color.RED);
            }
        } else if (i == R.id.cancel_coffee) {
            // stop displaying the coupon stuff
            cancel.setVisibility(View.GONE);
            redeem.setVisibility(View.GONE);
            dialogue.setText("");
            dialogue.setBackgroundColor(Color.TRANSPARENT);
            mainRedeem.setClickable(true);
            mainRedeem.setBackgroundColor(getResources().getColor(R.color.orange));
        }
    }

    private void threader() {
        // do some logic and maybe display a coupon

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                getButton();
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

        t1.start();
        if (Globe.DEBUG) Log.d(TAG, "Thread1 started");
        t2.start();
        if (Globe.DEBUG) Log.d(TAG, "Thread2 started");

        try {
            t1.join(); // wait
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (Globe.DEBUG) Log.d(TAG, "Thread1 joined");
        try {
            t2.join(); // wait
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (Globe.DEBUG) Log.d(TAG, "Thread2 joined");

        // after this point, button & start are set
        t3.start();
        try {
            t3.join(); // wait
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (Globe.DEBUG) Log.d(TAG, "Thread3 joined");

    }

    // returns the time that the user hit the 'in-bed' button
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

    // returns the time the user actually went to sleep
    private void getStart() {
        double result = -1f;
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
            Tasks.await(t, 10, TimeUnit.SECONDS);
            if (Globe.DEBUG) Log.d(TAG, "Task in Thread2 complete");
            String s = (String) t.getResult().child(today).child("startTime").getValue();
            if (s != null) {
                result = 0f;
                result += Integer.parseInt(s.substring(11, 13));
                result += Integer.parseInt(s.substring(14, 16)) / 60f;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            if (Globe.DEBUG) Log.d(TAG, "Failed to read value.", e);
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (Globe.DEBUG) Log.d(TAG, "setting start value");
        start = result;
    }

    // decides if the user gets the coupon
    private void coupon() {
        final double time = c.get(Calendar.HOUR_OF_DAY) + (c.get(Calendar.MINUTE) / 60f); // current time
        Runnable r;

        // reward is given IF:
        // group 0 (control) need not press the 'in-bed' button before their bedtime
        // otherwise, 'in-bed' button must be pressed (group 1 (treatment) must press it before bedtime)
        // participant must be asleep within 30 minutes of pressing 'in-bed' button
        if (time < Globe.wakeTime && button != -1f && start != -1f) {
            // button = time the user hit the 'in-bed' button
            // start = time the user fell asleep
            if (button < 12f)
                button += 24f;
            if (start < 12f)
                start += 24f;
            boolean g1check = true;
            if (Globe.group == 1 && button > Globe.bedTime) {
                g1check = false; // simplify this later
            }
            if (button <= start + 0.5 && g1check) { // 'in-bed' button time must be within 30 minutes of falling asleep
                // display coupon
                // display cancel/redeem buttons for Barista
                r = new Runnable() {
                    @Override
                    public void run() {
                        dialogue.setBackgroundColor(Color.GREEN);
                        dialogue.setText(String.format(getResources().getString(R.string.valid), new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Calendar.getInstance().getTime())));
                        cancel.setVisibility(View.VISIBLE);
                        redeem.setVisibility(View.VISIBLE);
                    }
                };
            } else {
                // no display coupon
                r = new Runnable() {
                    @Override
                    public void run() {
                        dialogue.setBackgroundColor(Color.RED);
                        dialogue.setText(getResources().getString(R.string.invalidCoffee));
                    }
                };
            }
        } else {
            // display a message
            r = new Runnable() {
                @Override
                public void run() {
                    dialogue.setBackgroundColor(Color.GRAY);
                    if (time >= Globe.wakeTime)
                        dialogue.setText(getResources().getString(R.string.pastCoupon));
                    else if (start == -1f)
                        dialogue.setText(getResources().getString(R.string.syncFitbit));
                    else
                        dialogue.setText(getResources().getString(R.string.noBedButton));
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
