package edu.umcp.justingoodman.fitbit_economics_study;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
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
import java.util.concurrent.ExecutionException;

/* Home
 *
 * The home-screen for the app
 * This is where Group 1 users can access 'Coffee Rewards', etc...
 * This is where Group 0 users can do very little...
 *
 * **/
public class Home extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "Home";

    private final Calendar c = Calendar.getInstance();

    private ValueEventListener vStage;
    private ValueEventListener vUser;
    private boolean calcBed = false;
    private boolean updater = true;
    private ProgressBar p;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        if (Globe.DEBUG) Log.d(TAG, "Creating....");
        if (Globe.DEBUG) Log.d(TAG, Globe.refresh_token);

        p = findViewById(R.id.progressbar);
        p.setIndeterminate(true);
        p.setVisibility(View.VISIBLE);
        p.bringToFront();

        Globe.am = (AlarmManager) getSystemService(ALARM_SERVICE);

        final TaskCompletionSource<Boolean> tcs = new TaskCompletionSource<>();
        final Task<Boolean> t = tcs.getTask();

        ((TextView) findViewById(R.id.welcome_home)).setText(String.format(getResources().getString(R.string.welcome), "--"));
        ((TextView) findViewById(R.id.goaltime_home)).setText(String.format(getResources().getString(R.string.yourGoal), "--:--"));
        ((TextView) findViewById(R.id.waketime_home)).setText(String.format(getResources().getString(R.string.yourWake), "--:--"));

        // Setup database listener for stage change
        vStage = Globe.dbRef.child("_stage").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // variable from database
                Globe.stage = Globe.parseLong(dataSnapshot.getValue()); // default 0
                if (Globe.stage == 0) // only for first passive stage
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
                ((TextView) findViewById(R.id.welcome_home)).setText(String.format(getResources().getString(R.string.welcome), dataSnapshot.child("id").getValue()));
                if (Globe.DEBUG) Log.d(TAG, "Current user is: " + dataSnapshot.getKey());

                if (updater) {
                    updater = false; // only do this once!
                    tcs.setResult(true); // now we want to call the fitbit updater
                    p.setVisibility(View.GONE);
                }

                // variables from database
                Globe.bedTime = Globe.parseDouble(dataSnapshot.child("bedtime").getValue(), 22.0);
                Globe.notification = Globe.parseDouble(dataSnapshot.child("notification").getValue(), 1.0);
                Globe.wakeTime = Globe.parseDouble(dataSnapshot.child("waketime").getValue(), 10.0);
                Globe.group = Globe.parseLong(dataSnapshot.child("group").getValue()); // default 0

                calcBed = "x".equals(dataSnapshot.child("bedtime").getValue());

                // update
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
        findViewById(R.id.rewards_home).setOnClickListener(Home.this);

        // setup thread to wait for db to be loaded
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (Globe.DEBUG) Log.d(TAG, "About to wait for task...");
                    Tasks.await(t); // don't block on main thread!
                    if (Globe.DEBUG) Log.d(TAG, "Task complete - Scheduling alarm for DataUpdater");
                    // Schedule alarm to update FitBit data
                    Globe.scheduleAlarm(Home.this, 0);
                    // Update FitBit data
                    Intent iFB = new Intent(Home.this, DataUpdater.class);
                    iFB.putExtra("type", 0); // 0 = FitBit updater
                    sendBroadcast(iFB);
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
        c.setTimeInMillis(System.currentTimeMillis());
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
    public void onPause() { super.onPause(); }

    @Override
    public void onBackPressed() { finish(); }

    @Override
    public void onStop() { super.onStop(); }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.out_home) {
            signOut();
        } else if (i == R.id.fitbit_home) {
            Globe.authFitbit(Home.this);
        } else if (i == R.id.rewards_home) {
            // TODO: schedule a handler for this
            int day = c.get(Calendar.DAY_OF_WEEK); // cannot redeem on saturday or sunday, or before 7:30am
            if (day == Calendar.FRIDAY || day == Calendar.SATURDAY)
                Toast.makeText(Home.this, "No coupons on Saturday or Sunday", Toast.LENGTH_SHORT).show();
            else if (c.get(Calendar.HOUR_OF_DAY) + (c.get(Calendar.MINUTE) / 60f) < 7.5)
                Toast.makeText(Home.this, "It's before 7:30AM!", Toast.LENGTH_SHORT).show();
            else
                Home.this.startActivity(new Intent(Home.this, CoffeeRewards.class));
        }
    }

    private void update() {
        // Set some stuff depending on stage
        if (Globe.stage == 0 || Globe.stage == 2 || Globe.group == 0) {
            // Make these gone if stage is passive
            // second passive stage should be same as first, but we don't want to delete the old bedtime
            findViewById(R.id.rewards_home).setVisibility(View.GONE);
            findViewById(R.id.goaltime_home).setVisibility(View.GONE);
            findViewById(R.id.waketime_home).setVisibility(View.GONE);
            // we don't want a bedtime notification if stage is passive
            if (Globe.am != null && Globe.senderNS != null) { Globe.am.cancel(Globe.senderNS); }
            if (Globe.am != null && Globe.senderRD != null) { Globe.am.cancel(Globe.senderRD); }
        } else { // stage is 1 and group is 1
            // Make these seen if stage is active AND user is in group 1 (experimental)
            if (Globe.DEBUG) Log.d(TAG, "update is else... calcBed is " + calcBed);
            findViewById(R.id.rewards_home).setVisibility(View.VISIBLE);
            findViewById(R.id.goaltime_home).setVisibility(View.VISIBLE);
            findViewById(R.id.waketime_home).setVisibility(View.VISIBLE);
            if (calcBed) {
                calcBed = false;
                // call Globe bedtime calculator (will set the bedtime correctly on the db)
                Globe.calculateBedtime();
            }
            // Display bedtime
            ((TextView) findViewById(R.id.goaltime_home)).setText(String.format(getResources().getString(R.string.yourGoal), Globe.timeToString(Globe.bedTime)));

            // Display waketime
            ((TextView) findViewById(R.id.waketime_home)).setText(String.format(getResources().getString(R.string.yourWake), Globe.timeToString(Globe.wakeTime)));

            // Schedule alarms for bedtime/waketime notifications
            Globe.scheduleAlarm(Home.this, 1);
            Globe.scheduleAlarm(Home.this, 2);
        }
    }

    private void signOut() {
        AlertDialog.Builder b = new AlertDialog.Builder(Home.this, R.style.Dialog);
        b.setPositiveButton("Sign Out", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked OK button
                Toast.makeText(Home.this, "Signing out.", Toast.LENGTH_SHORT).show();
                Globe.auth.signOut();
                Globe.dbRef.child(Globe.user.getUid()).removeEventListener(vUser);
                Globe.dbRef.child("_stage").removeEventListener(vStage);
                if (Globe.am != null) { // this crashes if user is not loaded
                    if (Globe.senderFB != null)
                        Globe.am.cancel(Globe.senderFB);
                    if (Globe.senderNS != null)
                        Globe.am.cancel(Globe.senderNS);
                }
                // delete current FitBit login data (+ other cached data)
                // the average user won't logout of their device and log into someone else's
                // Globe.clearData(Home.this);
                Home.this.startActivity(new Intent(Home.this, Launcher.class));
                finish();
            }
        });
        b.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog
                dialog.cancel();
            }
        });
        b.setMessage("Are you sure you want to sign out?");

        b.create().show();
    }
}
