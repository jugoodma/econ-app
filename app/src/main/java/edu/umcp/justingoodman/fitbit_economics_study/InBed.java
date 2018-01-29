package edu.umcp.justingoodman.fitbit_economics_study;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

// import org.json.JSONException;
// import org.json.JSONObject;

/* InBed
 *
 * The screen where users signal to the researchers they are in bed
 *
 * **/
public class InBed extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "InBed";

    private ValueEventListener vel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_in_bed);

        if (Globe.DEBUG) Log.d(TAG, "Creating...");

        vel = Globe.dbRef.child(Globe.user.getUid()).child("bedtime").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Globe.bedTime = Globe.parseDouble(dataSnapshot.getValue(), 22.0);
                ((TextView) findViewById(R.id.time_inbed)).setText(String.format(getResources().getString(R.string.bedTime), Globe.timeToString(Globe.bedTime)));
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                if (Globe.DEBUG) Log.d(TAG, "Failed to read value.", error.toException());
            }
        });

        findViewById(R.id.button_inbed).setOnClickListener(InBed.this);

        if (Globe.DEBUG) Log.d(TAG, "Created.");
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Globe.dbRef.child(Globe.user.getUid()).child("bedtime").removeEventListener(vel);
        finish();
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.button_inbed) {
            Calendar c = Calendar.getInstance();
            // Set hit-button time (NO NEED)
            // JSONObject hit = new JSONObject();
            double time = c.get(Calendar.HOUR_OF_DAY) + (c.get(Calendar.MINUTE) / 60f);
            // time += 0.01f; // guarantee it's stored as a double (36s leeway)
            /*
            try {
                // this is important
                if (time >= Globe.wakeTime)
                    c.add(Calendar.DATE, 1);
                hit.put("hitForDate", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime()));
                hit.put("hitTime", time);
            } catch (JSONException e) {
                if (Globe.DEBUG) Log.d(TAG, "?");
            }
            if (Globe.DEBUG) Log.d(TAG, "Writing data: " + hit);
            Globe.writeData(InBed.this, hit);
            */

            // Set dialogue based on bedtime
            TextView dialogue = findViewById(R.id.dialogue_inbed); // should push to UI-only function?
            // This makes the comparison work for all 4 cases of times
            if (time < 12 && Globe.bedTime >= 12) // did not press in time
                time += 24f;
            else if (time >= 12 && Globe.bedTime < 12) // pressed it in time
                time -= 24f;

            if (Globe.DEBUG) Log.d(TAG, "time " + time + ", bedtime " + Globe.bedTime);
            String pressed;
            if (time <= + Globe.bedTime) {
                dialogue.setBackgroundColor(Color.GREEN);
                dialogue.setText(String.format(getResources().getString(R.string.validBed), Globe.timeToString(Globe.wakeTime)));
                pressed = new SimpleDateFormat("HH:mm:ss", Locale.US).format(c.getTime());
            } else {
                dialogue.setBackgroundColor(Color.RED);
                dialogue.setText(getResources().getString(R.string.invalidBed));
                pressed = "nil";
            }
            Globe.dbRef.child(Globe.user.getUid()).child("_inbed").child(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime())).setValue(pressed);

            // Turn on Airplane mode
            /*
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                Settings.System.putInt(InBed.this.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 1);
                Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
                intent.putExtra("state", true);
                sendBroadcast(intent);

                AlarmManager am = (AlarmManager) InBed.this.getSystemService(ALARM_SERVICE);
                if (am != null) {
                    Intent iAP = new Intent(InBed.this, DataUpdater.class);
                    iAP.putExtra("type", 2); // 2 = Airplane mode updater
                    // set for 4 hours later
                    am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + (4 * 60 * 60 * 1000), PendingIntent.getBroadcast(InBed.this,2, iAP, 0));
                }
            }
            */

            // Close App 10s later
            /*
            if (Globe.DEBUG) Log.d(TAG, "Finishing 10s later");
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    finish();
                }
            }, 10 * 1000);
            */
        }
    }
}
