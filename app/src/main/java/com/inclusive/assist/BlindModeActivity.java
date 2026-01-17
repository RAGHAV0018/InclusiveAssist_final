package com.inclusive.assist;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.Image;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * Activity for "Object Detection" (Blind Mode).
 * Uses Google ML Kit (Image Labeling) to identify objects in standard camera stream offline.
 * Includes "Stability Logic" to prevent speaking objects unless they are detected consistently.
 */
public class BlindModeActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private PreviewView viewFinder;
    private TextView tvDescription;
    private ImageLabeler labeler;
    private long lastSpeakTime = 0;

    // STABILITY VARIABLES (To stop the jitter)
    private String lastSeenObject = "";
    private int stabilityCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blind_mode);

        viewFinder = findViewById(R.id.viewFinder);
        tvDescription = findViewById(R.id.tvDescription);

        // 1. Initialize Voice
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });

        // 2. Load ML Kit Default Model (Google Play Services)
        // This uses the on-device model provided by Google, which covers 400+ objects.
        ImageLabelerOptions options = new ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.7f) // Good balance for general objects
                .build();

        labeler = ImageLabeling.getClient(options);

        // 3. Start Camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), this::processImage);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("BlindMode", "Camera Error", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void processImage(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();

        if (mediaImage != null && labeler != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            labeler.process(image)
                    .addOnSuccessListener(labels -> {
                        if (!labels.isEmpty()) {
                            // Get the top result
                            ImageLabel bestLabel = labels.get(0);
                            String rawName = bestLabel.getText();
                            String displayName = rawName;

                            // --- SMART CORRECTIONS ---
                            // Normalize some common labels to be more friendly
                            String lowerName = rawName.toLowerCase();

                            if (lowerName.contains("notebook") ||
                                    lowerName.contains("computer") ||
                                    lowerName.contains("screen") ||
                                    lowerName.contains("monitor")) {
                                displayName = "Laptop";
                            }

                            if (lowerName.contains("mug") || lowerName.contains("cup")) displayName = "Cup";
                            if (lowerName.contains("bottle")) displayName = "Bottle";
                            if (lowerName.contains("cellular")) displayName = "Phone";

                            // --- STABILIZER ---
                            // Only speak if we see the SAME thing 5 frames in a row
                            if (displayName.equals(lastSeenObject)) {
                                stabilityCounter++;
                            } else {
                                stabilityCounter = 0;
                                lastSeenObject = displayName;
                            }

                            if (stabilityCounter >= 5) { // Locked on!
                                tvDescription.setText(displayName);

                                long currentTime = System.currentTimeMillis();
                                if (currentTime - lastSpeakTime > 2000) {
                                    tts.speak(displayName, TextToSpeech.QUEUE_FLUSH, null, null);
                                    lastSpeakTime = currentTime;
                                }
                            } else {
                                // While stabilizing, show what it sees in brackets
                                tvDescription.setText("Thinking... (" + displayName + ")");
                            }

                        } else {
                            tvDescription.setText("Scanning...");
                            stabilityCounter = 0;
                        }
                    })
                    .addOnFailureListener(e -> Log.e("BlindMode", "AI Error", e))
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
        }
    }
}