package com.example.visionguard;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private ListView historyListView;

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    private ArrayList<String> historyList;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        historyListView = findViewById(R.id.historyListView);

        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        historyList = new ArrayList<>();

        adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                historyList
        );

        historyListView.setAdapter(adapter);

        loadHistory();
    }

    private void loadHistory() {

        if (auth.getCurrentUser() == null) {
            Toast.makeText(
                    this,
                    "Please login first",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String userId = auth.getCurrentUser().getUid();

        firestore.collection("detection_history")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    historyList.clear();

                    if (querySnapshot.isEmpty()) {

                        historyList.add("No detection history yet.");

                    } else {

                        for (DocumentSnapshot document :
                                querySnapshot.getDocuments()) {

                            String objectName =
                                    document.getString("objectName");

                            Long confidence =
                                    document.getLong("confidence");

                            Long timestamp =
                                    document.getLong("timestamp");

                            if (objectName == null) {
                                objectName = "Unknown object";
                            }

                            if (confidence == null) {
                                confidence = 0L;
                            }

                            String time = "Unknown";

                            if (timestamp != null) {

                                Date date = new Date(timestamp);

                                SimpleDateFormat format =
                                        new SimpleDateFormat(
                                                "dd-MM-yyyy HH:mm",
                                                Locale.getDefault()
                                        );

                                time = format.format(date);
                            }

                            String result =
                                    "Object: " + objectName +
                                            "\nConfidence: " + confidence + "%" +
                                            "\nTime: " + time;

                            historyList.add(result);
                        }
                    }

                    adapter.notifyDataSetChanged();

                })
                .addOnFailureListener(error -> {

                    Toast.makeText(
                            HistoryActivity.this,
                            "Error: " + error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }
}