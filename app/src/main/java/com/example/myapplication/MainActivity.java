package com.example.myapplication;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView displayTextView = (TextView) findViewById(R.id.textView);

        try {
            new MyGatewayHandler().handleGateway(this, displayTextView);
        } catch (Exception e) {
            Log.e(TAG, "Error occurred", e);
        }
    }
}