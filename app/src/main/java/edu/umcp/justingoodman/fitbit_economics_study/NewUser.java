package edu.umcp.justingoodman.fitbit_economics_study;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;

import java.util.HashMap;
import java.util.Map;

/* NewUser
 *
 * This is where the participant establishes themselves in Google Firebase
 *
 * **/
public class NewUser extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "NewUser";

    private EditText email;
    private EditText password;
    private EditText reenter;
    private ProgressBar p;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_user);

        if (Globe.DEBUG) Log.d(TAG, "Creating...");

        email = findViewById(R.id.email_new);
        password = findViewById(R.id.password_new);
        reenter = findViewById(R.id.reenter_new);
        p = findViewById(R.id.progressbar);
        p.setIndeterminate(true);
        p.setVisibility(View.GONE);

        findViewById(R.id.submit).setOnClickListener(this);

        if (Globe.DEBUG) Log.d(TAG, "Created.");
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.submit) {
            p.setVisibility(View.VISIBLE);
            createAccount(email.getText().toString(), password.getText().toString());
        }
    }

    private void createAccount(String email, String password) {
        if (BuildConfig.DEBUG) Log.d(TAG, "createAccount:" + email);
        if (validateForm()) {
            Globe.auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(NewUser.this, new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if (task.isSuccessful()) {
                        Globe.user = Globe.auth.getCurrentUser();
                        if (Globe.DEBUG) Log.d(TAG, "createUserWithEmail - success");
                        // Globe.user.sendEmailVerification(); we don't want to do this because it will give away the 'coffee' aspect of the app
                        updateDB(); // create new database entry
                        updateUI();
                    } else {
                        if (Globe.DEBUG) Log.d(TAG, "createUserWithEmail - failure", task.getException());
                        Toast.makeText(NewUser.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                p.setVisibility(View.GONE);
                            }
                        });
                    }
                }
            });
        }
    }

    private boolean validateForm() {
        boolean valid = true;
        String req = "Required.";

        if (TextUtils.isEmpty(email.getText().toString())) {
            email.setError(req);
            valid = false;
        } else {
            email.setError(null);
        }

        String pw = password.getText().toString();
        if (TextUtils.isEmpty(pw)) {
            password.setError(req);
            valid = false;
        } else {
            password.setError(null);
        }

        String re = reenter.getText().toString();
        if (TextUtils.isEmpty(re)) {
            reenter.setError(req);
            valid = false;
        } else if (!TextUtils.equals(pw, re)) {
            reenter.setError("Password MUST match.");
            valid = false;
        } else {
            reenter.setError(null);
        }

        return valid;
    }

    private void updateDB() {
        // sets up the database
        Map<String, Object> nUser = new HashMap<>();
        // _activity
        // _coffee
        // _sleep
        nUser.put("bedtime", "x"); // 'x' = first stage
        nUser.put("group", 0); // 0 = control group
        nUser.put("notification", 1); // hours before bedtime
        nUser.put("waketime", 9.0001);
        nUser.put("updated", "---");
        Globe.dbRef.child(Globe.user.getUid()).updateChildren(nUser);
    }

    private void updateUI() {
        p.setVisibility(View.GONE);
        Globe.authFitbit(NewUser.this); // the website will send a callback to load 'LoggingIn'
        finish(); // don't come back
    }
}
