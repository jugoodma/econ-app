package edu.umcp.justingoodman.fitbit_economics_study;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.ExecutionException;

/* Home
 *
 * The home-screen for the app
 * This is where users can access 'Coffee Rewards', 'In-bed Button', etc...
 *
 * **/
public class Home extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "Home";

    private Handler h = new Handler();
    private Runnable r = new Runnable() {
        @Override
        public void run() {
            if (Globe.DEBUG) Log.d(TAG, "Handler called/ran");
            updateButtons();
        }
    };

    private ValueEventListener vUser;
    private ValueEventListener vStage;
    // private ValueEventListener vWake;
    private PendingIntent senderFB; // for FitBit service
    private PendingIntent senderNS; // for Notification service
    private AlarmManager am;
    private boolean calcBed = false;
    private boolean updater = true;
    private Button bedButton;
    private ProgressBar p;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        if (Globe.DEBUG) Log.d(TAG, "Creating....");
        if (Globe.DEBUG) Log.d(TAG, Home.this.getApplicationContext().toString());
        if (Globe.DEBUG) Log.d(TAG, Globe.refresh_token);

        bedButton = findViewById(R.id.inbed_home);
        bedButton.setClickable(false);
        p = findViewById(R.id.progressbar);
        p.setIndeterminate(true);
        p.setVisibility(View.VISIBLE);
        p.bringToFront();

        am = (AlarmManager) Home.this.getSystemService(ALARM_SERVICE);
        final TaskCompletionSource<Boolean> tcs = new TaskCompletionSource<>();
        final Task<Boolean> t = tcs.getTask();

        ((TextView) findViewById(R.id.welcome_home)).setText(String.format(getResources().getString(R.string.welcome), "--"));
        ((TextView) findViewById(R.id.goaltime_home)).setText(String.format(getResources().getString(R.string.yourGoal), "--:--"));

        // Setup database listener for stage change
        vStage = Globe.dbRef.child("_stage").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // variable from database
                long stage = 0L;
                // stage (0 = passive, 1 = active, 2 = ?)
                try {
                    Long l = (Long) dataSnapshot.getValue(); // 0 or 1 ?or 2?
                    if (l != null)
                        stage = l;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Globe.stage = stage;
                if (Globe.stage == 0)
                    Globe.dbRef.child(Globe.user.getUid()).child("bedtime").setValue("x"); // set bedtime back to 'x'
                update();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                if (Globe.DEBUG) Log.d(TAG, "Failed to read value.", error.toException());
            }
        });

        // Setup database listener for the specific user
        vUser = Globe.dbRef.child(Globe.user.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                // Display user's random id
                ((TextView) findViewById(R.id.welcome_home)).setText(String.format(getResources().getString(R.string.welcome), dataSnapshot.getKey()));
                if (Globe.DEBUG) Log.d(TAG, "Current user is: " + dataSnapshot.getKey());

                if (updater) {
                    updater = false; // only do this once!
                    tcs.setResult(true); // now we want to call the fitbit updater
                    p.setVisibility(View.GONE);
                }

                // variables from database
                double bedtime = 22.0;
                double notification = 1.0;
                double waketime = 8.0;
                long group = 0L;
                // bedtime (will be 'x' if we are in stage 0)
                if (!"x".equals(dataSnapshot.child("bedtime").getValue())) {
                    try {
                        Double d = (Double) dataSnapshot.child("bedtime").getValue();
                        if (d != null)
                            bedtime = d;
                    } catch (Exception e) {
                        try {
                            Long l = (Long) dataSnapshot.child("bedtime").getValue();
                            if (l != null)
                                bedtime = l + 0.0;
                        } catch (Exception f) {
                            f.printStackTrace();
                        }
                    }
                } else {
                    calcBed = true;
                }
                // notification
                try {
                    Double d = (Double) dataSnapshot.child("notification").getValue();
                    if (d != null)
                        notification = d;
                } catch (Exception e) {
                    try {
                        Long l = (Long) dataSnapshot.child("notification").getValue();
                        if (l != null)
                            notification = l + 0.0;
                    } catch (Exception f) {
                        f.printStackTrace();
                    }
                }
                // waketime
                try {
                    Double d = (Double) dataSnapshot.child("waketime").getValue();
                    if (d != null)
                        waketime = d;
                } catch (Exception e) {
                    try {
                        Long l = (Long) dataSnapshot.child("waketime").getValue();
                        if (l != null)
                            waketime = l + 0.0;
                    } catch (Exception f) {
                        f.printStackTrace();
                    }
                }
                // group
                try {
                    Long l = (Long) dataSnapshot.child("group").getValue(); // 0 or 1
                    if (l != null)
                        group = l;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Globe.bedTime = bedtime;
                Globe.notification = notification;
                Globe.wakeTime = waketime;
                Globe.group = group;

                // Setup delay for 'in-bed' button (simplify the math later)
                h.removeCallbacks(r);
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(System.currentTimeMillis());
                double time = c.get(Calendar.HOUR_OF_DAY) + (c.get(Calendar.MINUTE) / 60f);
                double disabled = Globe.bedTime + 5; // right bound
                if (disabled > 24)
                    disabled -= 24f;
                double enabled = disabled - 8; // left bound
                if (enabled < 0) {
                    enabled += 24f;
                    disabled += 24f;
                }
                if (Globe.DEBUG) Log.d(TAG, "Handler times: " + enabled + " - " + time + " - " + disabled);
                long delay = 2000;
                if (time <= enabled || time > disabled)
                    delay += (
                             (((((int) enabled) * 60) + (((int) ((enabled % 1) * 60))))) -
                             (((((int) time) * 60) + (((int) ((time % 1) * 60)))))
                             ) * 60 * 1000;
                else
                    delay += (
                             (((((int) disabled) * 60) + (((int) ((disabled % 1) * 60))))) -
                             (((((int) time) * 60) + (((int) ((time % 1) * 60)))))
                             ) * 60 * 1000;

                h.postDelayed(r, delay);
                if (Globe.DEBUG) Log.d(TAG, "Handler postdelay time is at " + new Date(c.getTimeInMillis() + delay).toString() + ", delay is " + delay);

                // update
                updateButtons();
                update();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                if (Globe.DEBUG) Log.d(TAG, "Failed to read value.", error.toException());
            }
        });

        // Setup database listener for wake-time (deals with blocking and unblocking coffee-rewards button)
        // (later)

        // Set view click listeners
        findViewById(R.id.out_home).setOnClickListener(Home.this);
        findViewById(R.id.fitbit_home).setOnClickListener(Home.this);
        bedButton.setOnClickListener(Home.this);
        findViewById(R.id.rewards_home).setOnClickListener(Home.this);

        // setup thread to wait for db to be loaded
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (Globe.DEBUG) Log.d(TAG, "About to wait for task...");
                    Tasks.await(t); // don't block on main thread!
                    if (Globe.DEBUG) Log.d(TAG, "Task complete - Scheduling alarm for DataUpdater");
                    // Sechedule alarm to update FitBit data
                    if (am != null) {
                        Intent iFB = new Intent(Home.this, DataUpdater.class);
                        iFB.putExtra("type", 0); // 0 = FitBit updater
                        senderFB = PendingIntent.getBroadcast(Home.this,0, iFB, 0);
                        // start now, 1 hour interval
                        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), AlarmManager.INTERVAL_HOUR, senderFB);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();

        if (Globe.DEBUG) Log.d(TAG, "Created.");
    }

    @Override
    public void onResume() {
        super.onResume();
        /*
        if (Globe.user == null) signOut(); // can't do anything with a null user!
        try {
            Globe.user.reload();
        } catch (Exception e) {
            signOut();
        }
        */
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // super.onBackPressed();
        finish();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.out_home) {
            signOut();
        } else if (i == R.id.fitbit_home) {
            Globe.authFitbit(Home.this);
        } else if (i == R.id.inbed_home) {
            Home.this.startActivity(new Intent(Home.this, InBed.class));
        } else if (i == R.id.rewards_home) {
            Home.this.startActivity(new Intent(Home.this, CoffeeRewards.class));
        }
    }

    private void update() {
        // Set some stuff depending on stage
        if (Globe.stage == 0) {
            // Make these gone if stage is passive
            findViewById(R.id.rewards_home).setVisibility(View.GONE);
            findViewById(R.id.inbed_home).setVisibility(View.GONE);
            findViewById(R.id.goaltime_home).setVisibility(View.GONE);
        } else if (Globe.stage == 1) {
            // Make these seen if stage is active
            findViewById(R.id.rewards_home).setVisibility(View.VISIBLE);
            findViewById(R.id.inbed_home).setVisibility(View.VISIBLE);
            findViewById(R.id.goaltime_home).setVisibility(View.VISIBLE);
            if (calcBed) {
                calcBed = false;
                // call Globe bedtime calculator (will set the bedtime correctly on the db)
                Globe.calculateBedtime();
            }
            // Display bedtime
            ((TextView) findViewById(R.id.goaltime_home)).setText(String.format(getResources().getString(R.string.yourGoal), Globe.timeToString(Globe.bedTime)));

            // Display waketime


            // Schedule alarm for bedtime notification
            if (am != null) {
                Intent iNS = new Intent(Home.this, DataUpdater.class);
                iNS.putExtra("type", 1); // 1 = notification
                senderNS = PendingIntent.getBroadcast(Home.this, 1, iNS, 0);
                // calculate bedtime notification
                double bedtime = Globe.bedTime;
                bedtime -= Globe.notification; // subtract notification hours
                if (bedtime < 0)
                    bedtime += 24f;
                // setup time for alarm to go off
                Calendar c = Calendar.getInstance(); // current time
                c.setTimeInMillis(System.currentTimeMillis());
                if (c.get(Calendar.HOUR_OF_DAY) + (c.get(Calendar.MINUTE) / 60f) >= bedtime) // make sure we haven't passed the current bedtime
                    c.add(Calendar.DATE, 1); // add one day, because we passed the notification time
                c.set(Calendar.HOUR_OF_DAY, (int) bedtime);
                c.set(Calendar.MINUTE, (int) ((bedtime % 1) * 60));
                // start at bedtime notification time, 1 day interval (does not need to be exact)
                am.setInexactRepeating(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), AlarmManager.INTERVAL_DAY, senderNS);
            }
        } // else ?
    }

    private void signOut() {
        // maybe ask if they really want to sign out?
        Toast.makeText(Home.this, "Signing out.", Toast.LENGTH_SHORT).show();
        Globe.auth.signOut();
        Globe.dbRef.child(Globe.user.getUid()).removeEventListener(vUser);
        Globe.dbRef.child("_stage").removeEventListener(vStage);
        if (am != null) { // this crashes if user is not loaded
            if (senderFB != null)
                am.cancel(senderFB);
            if (senderNS != null)
                am.cancel(senderNS);
        }
        // delete current FitBit login data (+ other cached data)
        // the average user won't logout of their device and log into someone else's
        // Globe.clearData(Home.this);
        Home.this.startActivity(new Intent(Home.this, Launcher.class));
        finish();
    }

    void updateButtons() {
        // reset handler too? *****
        // 'in-bed' button becomes available 3hrs before bedtime
        // button is disabled 5hrs after bedtime
        Calendar c = Calendar.getInstance();
        double time = c.get(Calendar.HOUR_OF_DAY) + (c.get(Calendar.MINUTE) / 60f);
        double disabled = Globe.bedTime + 5; // right bound
        if (disabled > 24)
            disabled -= 24f;
        double enabled = disabled - 8; // left bound
        if (enabled < 0) {
            time -= 24f;
        }
        if (Globe.DEBUG) Log.d(TAG, "times: " + enabled + " - " + time + " - " + disabled);
        final boolean flag = (time >= enabled && time <= disabled);

        this.runOnUiThread(new Runnable() { // just to be sure
            @Override
            public void run() {
                if (flag) {
                    // we are within the 'enabled' time
                    bedButton.setClickable(true);
                    bedButton.setBackgroundColor(getResources().getColor(R.color.orange));
                } else {
                    // we are within the 'disabled' time
                    bedButton.setClickable(false);
                    bedButton.setBackgroundColor(getResources().getColor(R.color.grayOut));
                }
            }
        });
    }
}
