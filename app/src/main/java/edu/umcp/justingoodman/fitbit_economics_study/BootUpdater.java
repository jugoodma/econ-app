package edu.umcp.justingoodman.fitbit_economics_study;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

/* BootUpdater
 *
 * Extends BroadcastReceiver - listens for device boot to reset alarms
 *
 * **/
public class BootUpdater extends BroadcastReceiver {

    private static final String TAG = "BootUpdater";

    @Override
    public void onReceive(final Context ctx, Intent i) {
        if (Globe.DEBUG) Log.d(TAG, "Booted!");
        Globe.init(ctx);
        // I dislike this nested style, but it's easier to code compared to setting up threads/tasks with waits
        if ("android.intent.action.BOOT_COMPLETED".equals(i.getAction()) || "android.intent.action.QUICKBOOT_POWERON".equals(i.getAction())) {
            // start with getting the stage
            Globe.dbRef.child("_stage").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot d) {
                    // need Globe.stage
                    Globe.stage = Globe.parseLong(d.getValue(), 0);

                    // now set the other variables
                    Globe.dbRef.child(Globe.user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot d) {
                            // set globals
                            Globe.bedTime = Globe.parseDouble(d.child("bedtime"), 22.0);
                            Globe.notification = Globe.parseDouble(d.child("notification"), 1.0);
                            Globe.wakeTime = Globe.parseDouble(d.child("waketime"), 10.0);

                            // schedule fitbit
                            Globe.scheduleAlarm(ctx, 0); // fitbit updater

                            // schedule notifications if stage is active
                            if (Globe.stage == 1) {
                                Globe.scheduleAlarm(ctx, 1); // bedtime notification
                                Globe.scheduleAlarm(ctx, 2); // waketime notification
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError e) {
                            // Failed to read value
                            if (Globe.DEBUG) Log.d(TAG, "Failed to read value.", e.toException());
                        }
                    });
                }

                @Override
                public void onCancelled(DatabaseError e) {
                    // Failed to read value
                    if (Globe.DEBUG) Log.d(TAG, "Failed to read value.", e.toException());
                }
            });
        }
    }
}
