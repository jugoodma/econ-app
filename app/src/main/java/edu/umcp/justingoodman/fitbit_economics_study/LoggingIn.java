package edu.umcp.justingoodman.fitbit_economics_study;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.android.volley.Request;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class LoggingIn extends AppCompatActivity {

    private static final String TAG = "LoggingIn";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logging_in);

        if (Globe.DEBUG) Log.d(TAG, "Creating...");

        Intent i = this.getIntent();
        Uri u = i.getData();
        String code = "";
        if (u != null) code = u.getQueryParameter("code");

        if (Globe.DEBUG) Log.d(TAG, code);

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Basic " + Globe.getB64());
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        Map<String, String>  params = new HashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        params.put("client_id", Globe.client_id);
        params.put("redirect_uri", Globe.callback_uri);

        NetworkManager.getInstance(LoggingIn.this).makeRequest(LoggingIn.this, Request.Method.POST, headers, params, Globe.FITBIT_AUTH_URL, new CustomListener<String>() {
            @Override
            public void getResult(String result) {
                if (!result.isEmpty()) {
                    JSONObject data = null;
                    try {
                        data = new JSONObject(result);
                        Globe.access_token = (String) data.get("access_token");
                        Globe.refresh_token = (String) data.get("refresh_token");
                        Globe.token_type = (String) data.get("token_type");
                    } catch (JSONException e) {
                        e.printStackTrace();
                        if (Globe.DEBUG) Log.d(TAG, "failed to write");
                    }
                    Globe.writeData(LoggingIn.this, data);
                }
                LoggingIn.this.startActivity(new Intent(LoggingIn.this, Home.class));
                finish(); // don't come back
            }
        });

        if (Globe.DEBUG) Log.d(TAG, "Created.");
    }
}
