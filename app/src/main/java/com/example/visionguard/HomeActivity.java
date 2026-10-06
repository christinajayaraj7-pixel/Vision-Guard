package com.example.visionguard;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class HomeActivity extends AppCompatActivity {

    Button startDetectionButton;
    Button historyButton;
    Button logoutButton;

    FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_screen);

        startDetectionButton =
                findViewById(R.id.startDetectionButton);

        historyButton =
                findViewById(R.id.historyButton);

        logoutButton =
                findViewById(R.id.logoutButton);

        mAuth = FirebaseAuth.getInstance();

        // START DETECTION
        startDetectionButton.setOnClickListener(v -> {

            Intent intent = new Intent(
                    HomeActivity.this,
                    DetectionActivity.class
            );

            startActivity(intent);
        });

        // DETECTION HISTORY
        historyButton.setOnClickListener(v -> {

            Intent intent = new Intent(
                    HomeActivity.this,
                    HistoryActivity.class
            );

            startActivity(intent);
        });

        // LOGOUT
        logoutButton.setOnClickListener(v -> {

            mAuth.signOut();

            Toast.makeText(
                    HomeActivity.this,
                    "Logged out successfully",
                    Toast.LENGTH_SHORT
            ).show();

            Intent intent = new Intent(
                    HomeActivity.this,
                    MainActivity.class
            );

            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            startActivity(intent);

            finish();
        });
    }
}