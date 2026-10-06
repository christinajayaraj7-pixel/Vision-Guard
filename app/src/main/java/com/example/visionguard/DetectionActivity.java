package com.example.visionguard;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetectionActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView detectionResultText;

    private ObjectDetector objectDetector;
    private ExecutorService cameraExecutor;

    // Voice Assistant
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;

    // Firebase
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    // Last spoken object
    private String lastSpokenObject = "";

    // Last voice time
    private long lastSpokenTime = 0;

    // Same object can be spoken again after 3 seconds
    private static final long VOICE_COOLDOWN = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.detection_screen);

        previewView = findViewById(R.id.previewView);
        detectionResultText = findViewById(R.id.detectionResultText);

        cameraExecutor = Executors.newSingleThreadExecutor();

        // Firebase
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Text To Speech
        textToSpeech = new TextToSpeech(this, status -> {

            if (status == TextToSpeech.SUCCESS) {

                int result = textToSpeech.setLanguage(Locale.US);

                if (result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED) {

                    ttsReady = true;

                    Log.d(
                            "VisionGuard",
                            "Voice Assistant Ready"
                    );
                }
            }
        });

        createObjectDetector();
        startCamera();
    }

    private void createObjectDetector() {

        ObjectDetectorOptions options =
                new ObjectDetectorOptions.Builder()
                        .setDetectorMode(
                                ObjectDetectorOptions.STREAM_MODE
                        )
                        .enableClassification()
                        .build();

        objectDetector =
                ObjectDetection.getClient(options);
    }

    private void startCamera() {

        ListenableFuture<ProcessCameraProvider>
                cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {

            try {

                ProcessCameraProvider cameraProvider =
                        cameraProviderFuture.get();

                Preview preview =
                        new Preview.Builder().build();

                CameraSelector cameraSelector =
                        CameraSelector.DEFAULT_BACK_CAMERA;

                preview.setSurfaceProvider(
                        previewView.getSurfaceProvider()
                );

                ImageAnalysis imageAnalysis =
                        new ImageAnalysis.Builder()
                                .setBackpressureStrategy(
                                        ImageAnalysis
                                                .STRATEGY_KEEP_ONLY_LATEST
                                )
                                .build();

                imageAnalysis.setAnalyzer(
                        cameraExecutor,
                        this::analyzeImage
                );

                cameraProvider.unbindAll();

                cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalysis
                );

            } catch (Exception e) {

                Log.e(
                        "VisionGuard",
                        "Camera error",
                        e
                );
            }

        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(ImageProxy imageProxy) {

        if (imageProxy.getImage() == null) {

            imageProxy.close();
            return;
        }

        InputImage image =
                InputImage.fromMediaImage(
                        imageProxy.getImage(),
                        imageProxy
                                .getImageInfo()
                                .getRotationDegrees()
                );

        objectDetector
                .process(image)

                .addOnSuccessListener(objects -> {

                    if (objects.isEmpty()) {

                        runOnUiThread(() ->
                                detectionResultText.setText(
                                        "Scanning..."
                                )
                        );

                        return;
                    }

                    StringBuilder allObjects =
                            new StringBuilder();

                    for (DetectedObject object : objects) {

                        if (!object.getLabels().isEmpty()) {

                            String objectName =
                                    object.getLabels()
                                            .get(0)
                                            .getText();

                            float confidence =
                                    object.getLabels()
                                            .get(0)
                                            .getConfidence();

                            int percentage =
                                    (int) (confidence * 100);

                            allObjects
                                    .append(objectName)
                                    .append(" - ")
                                    .append(percentage)
                                    .append("%\n");

                            Log.d(
                                    "VisionGuard",
                                    "Detected: "
                                            + objectName
                                            + " "
                                            + percentage
                                            + "%"
                            );

                            // Voice + Firebase
                            speakAndSave(
                                    objectName,
                                    percentage
                            );
                        }
                    }

                    runOnUiThread(() ->
                            detectionResultText.setText(
                                    allObjects.toString()
                            )
                    );

                })

                .addOnFailureListener(e ->
                        Log.e(
                                "VisionGuard",
                                "Detection error",
                                e
                        )
                )

                .addOnCompleteListener(task ->
                        imageProxy.close()
                );
    }

    private void speakAndSave(
            String objectName,
            int confidence
    ) {

        if (!ttsReady) {
            return;
        }

        long currentTime =
                System.currentTimeMillis();

        boolean isNewObject =
                !objectName.equals(lastSpokenObject);

        boolean cooldownFinished =
                currentTime - lastSpokenTime
                        >= VOICE_COOLDOWN;

        if (isNewObject || cooldownFinished) {

            lastSpokenObject = objectName;
            lastSpokenTime = currentTime;

            // Voice
            String message =
                    objectName + " detected";

            textToSpeech.speak(
                    message,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "VisionGuardVoice"
            );

            // Save to Firebase
            saveDetection(
                    objectName,
                    confidence
            );
        }
    }

    private void saveDetection(
            String objectName,
            int confidence
    ) {

        if (auth.getCurrentUser() == null) {

            Log.d(
                    "VisionGuard",
                    "User not logged in"
            );

            return;
        }

        String userId =
                auth.getCurrentUser().getUid();

        Map<String, Object> detection =
                new HashMap<>();

        detection.put(
                "userId",
                userId
        );

        detection.put(
                "objectName",
                objectName
        );

        detection.put(
                "confidence",
                confidence
        );

        detection.put(
                "timestamp",
                System.currentTimeMillis()
        );

        firestore
                .collection("detection_history")
                .add(detection)

                .addOnSuccessListener(documentReference -> {

                    Log.d(
                            "VisionGuard",
                            "Detection saved to Firebase"
                    );
                })

                .addOnFailureListener(e -> {

                    Log.e(
                            "VisionGuard",
                            "Failed to save detection",
                            e
                    );
                });
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }

        if (objectDetector != null) {
            objectDetector.close();
        }

        if (textToSpeech != null) {

            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
}