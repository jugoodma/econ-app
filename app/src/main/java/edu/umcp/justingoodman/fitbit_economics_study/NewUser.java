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
import java.util.regex.Pattern;

/* NewUser
 *
 * This is where the participant establishes themselves in Google Firebase
 *
 * **/
public class NewUser extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "NewUser";

    private static final String CODE = "pittecon";

    private EditText email;
    private EditText password;
    private EditText reenter;
    private EditText code;
    private ProgressBar p;

    private Runnable r = new Runnable() {
        @Override
        public void run() {
            p.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_user);

        if (Globe.DEBUG) Log.d(TAG, "Creating...");

        email = findViewById(R.id.email_new);
        password = findViewById(R.id.password_new);
        reenter = findViewById(R.id.reenter_new);
        code = findViewById(R.id.code_new);
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
        if (validateForm()) { // NOTE below, we are putting our domain on the email form, and saving the IDs for later
            Globe.auth.createUserWithEmailAndPassword(email + Globe.DOMAIN, password).addOnCompleteListener(NewUser.this, new OnCompleteListener<AuthResult>() {
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
                        runOnUiThread(r);
                    }
                }
            });
        } else {
            runOnUiThread(r);
        }
    }

    private boolean validateForm() {
        boolean valid = true;
        String req = "Required.";

        String em = email.getText().toString(); // This is the participant ID, but we treat it like an email
        if (TextUtils.isEmpty(em)) {
            email.setError(req);
            valid = false;
        } else if (!Pattern.matches("[0-9a-zA-Z]+", em)) {
            email.setError("No special characters.");
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

        String cd = code.getText().toString();
        if (TextUtils.isEmpty(cd)) {
            code.setError(req);
            valid = false;
        } else if (!TextUtils.equals(CODE, cd)) {
            code.setError("Invalid participant code.");
            valid = false;
        } else {
            code.setError(null);
        }

        return valid;
    }

    private void updateDB() {
        // sets up the database
        Map<String, Object> nUser = new HashMap<>();
        // _activity
        // _coffee
        // _sleep
        // _battery
        // _heart
        nUser.put("bedtime", "x"); // 'x' = first stage
        nUser.put("group", 0); // 0 = control group
        nUser.put("notification", 0.5); // hours before bedtime
        nUser.put("waketime", 10.0001);
        nUser.put("updated", "---");
        nUser.put("id", email.getText().toString()); // should be an email W/O the @sleep-coffee-research.firebaseapp.com part
        Globe.dbRef.child(Globe.user.getUid()).updateChildren(nUser);
    }

    private void updateUI() {
        p.setVisibility(View.GONE);
        Globe.authFitbit(NewUser.this); // the website will send a callback to load 'LoggingIn'
        finish(); // don't come back
    }
}
