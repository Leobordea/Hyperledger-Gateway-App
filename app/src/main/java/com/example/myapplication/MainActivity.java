package com.example.myapplication;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView displayTextView = (TextView) findViewById(R.id.textView);
        Button startButton = (Button) findViewById(R.id.start_button);

        startButton.setOnClickListener(v -> {
            Log.d("BUTTONS", "User tapped the Supabutton");
            try {
                new MyGatewayHandler().handleGateway(MainActivity.this, displayTextView);
            } catch (Exception e) {
                Log.e(TAG, "Error occurred", e);
            }
        });
    }
}