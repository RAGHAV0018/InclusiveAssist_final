package com.inclusive.assist;

import android.Manifest;
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
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class CurrencyActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private PreviewView viewFinder;
    private TextView tvDescription;
    private TextRecognizer recognizer;
    private long lastSpeakTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Reuse the BlindMode layout (Camera + Text)
        setContentView(R.layout.activity_blind_mode);

        viewFinder = findViewById(R.id.viewFinder);
        tvDescription = findViewById(R.id.tvDescription);
        tvDescription.setText("Point camera at money...");

        // 1. Initialize Voice
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });

        // 2. Initialize Text Reader
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

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
                Log.e("Currency", "Camera Error", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.camera.core.ExperimentalGetImage
    private void processImage(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String fullText = visionText.getText().toLowerCase();
                        String moneyFound = "";

                        // --- SMART CURRENCY LOGIC ---
                        // We check for numbers appearing on Indian Notes
                        // We also look for "reserve bank" to make sure it's money

                        if (fullText.contains("500") && (fullText.contains("reserve") || fullText.contains("bank"))) {
                            moneyFound = "500 Rupees";
                        } else if (fullText.contains("200") && (fullText.contains("reserve") || fullText.contains("bank"))) {
                            moneyFound = "200 Rupees";
                        } else if (fullText.contains("100") && (fullText.contains("reserve") || fullText.contains("bank"))) {
                            moneyFound = "100 Rupees";
                        } else if (fullText.contains("50") && (fullText.contains("reserve") || fullText.contains("bank"))) {
                            moneyFound = "50 Rupees";
                        } else if (fullText.contains("20") && (fullText.contains("reserve") || fullText.contains("bank"))) {
                            moneyFound = "20 Rupees";
                        } else if (fullText.contains("10") && (fullText.contains("reserve") || fullText.contains("bank"))) {
                            moneyFound = "10 Rupees";
                        }

                        if (!moneyFound.isEmpty()) {
                            tvDescription.setText(moneyFound);

                            // Speak immediately but don't repeat too fast
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastSpeakTime > 2000) {
                                tts.speak(moneyFound, TextToSpeech.QUEUE_FLUSH, null, null);
                                lastSpeakTime = currentTime;
                            }
                        } else {
                            tvDescription.setText("Scanning for money...");
                        }
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
        }
    }
}