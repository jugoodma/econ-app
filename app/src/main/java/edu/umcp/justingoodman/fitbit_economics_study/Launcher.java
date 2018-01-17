package edu.umcp.justingoodman.fitbit_economics_study;

import android.content.Intent;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONException;
import org.json.JSONObject;

/* Launcher
 *
 * The starting point for the app
 * This is where the user can log-in to the Google Firebase system
 *
 * **/
public class Launcher extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "Launcher";

    private EditText mEmailField;
    private EditText mPasswordField;
    private ProgressBar p;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        if (Globe.DEBUG) Log.d(TAG, "Creating...");

        // Set the views
        mEmailField = findViewById(R.id.email);
        mPasswordField = findViewById(R.id.password);
        p = findViewById(R.id.progressbar);
        p.setIndeterminate(true);
        p.setVisibility(View.GONE);

        // Set click listeners for the buttons
        findViewById(R.id.signIn).setOnClickListener(Launcher.this);
        findViewById(R.id.newUser).setOnClickListener(Launcher.this);

        // set some globals
        Globe.auth = FirebaseAuth.getInstance();
        Globe.user = Globe.auth.getCurrentUser();
        Globe.db = FirebaseDatabase.getInstance();
        Globe.dbRef = Globe.db.getReference();

        // Read in stored local data
        JSONObject data = Globe.readData(Launcher.this);
        if (data.toString().isEmpty()) {
            // if empty, then write a new file with necessary in.puts
            JSONObject in = new JSONObject();
            try {
                in.put("access_token", "");
                in.put("refresh_token", "");
                in.put("token_type", "");
                in.put("hitForDate", "");
                in.put("hitTime", -1f);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            Globe.writeData(Launcher.this, in);
        } else {
            // else, get the access/refresh tokens and hitButton time
            try {
                Globe.access_token = (String) data.get("access_token");
                Globe.refresh_token = (String) data.get("refresh_token");
                Globe.token_type = (String) data.get("token_type");
            } catch (Exception e) {
                // any json call could've failed
                e.printStackTrace();
            }
        }

        // update the UI
        updateUI();

        if (Globe.DEBUG) Log.d(TAG, "Created.");
    }

    private void signIn(String email, String password) {
        if (Globe.DEBUG) Log.d(TAG, "signIn:" + email);
        if (validateForm()) {
            Globe.auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(Launcher.this, new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if (task.isSuccessful()) {
                        // Sign in success, update UI with the signed-in user's information
                        if (Globe.DEBUG) Log.d(TAG, "email sign-in success");
                        Globe.user = Globe.auth.getCurrentUser();
                        updateUI();
                    } else {
                        // If sign in fails, display a message to the user.
                        if (Globe.DEBUG) Log.d(TAG, "email sign-in failure", task.getException());
                        Toast.makeText(Launcher.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                        updateUI(); // this may not be needed
                    }

                    if (!task.isSuccessful()) {
                        if (Globe.DEBUG) Log.d(TAG, "sorry");
                        p.setVisibility(View.GONE);
                    }
                }
            });
        }
    }

    private boolean validateForm() {
        boolean valid = true;

        String email = mEmailField.getText().toString();
        if (TextUtils.isEmpty(email)) {
            mEmailField.setError("Required.");
            valid = false;
        } else {
            mEmailField.setError(null);
        }

        String password = mPasswordField.getText().toString();
        if (TextUtils.isEmpty(password)) {
            mPasswordField.setError("Required.");
            valid = false;
        } else {
            mPasswordField.setError(null);
        }

        return valid;
    }

    private void updateUI() {
        Globe.user = Globe.auth.getCurrentUser();
        if (Globe.user != null) {
            Launcher.this.startActivity(new Intent(Launcher.this, Home.class));
            finish();
        }
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.newUser) {
            Launcher.this.startActivity(new Intent(Launcher.this, NewUser.class));
        } else if (i == R.id.signIn) {
            p.setVisibility(View.VISIBLE); // signing in, show the user we are doing work
            signIn(mEmailField.getText().toString(), mPasswordField.getText().toString());
        }
    }
}
