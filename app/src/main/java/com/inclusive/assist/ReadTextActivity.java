package com.inclusive.assist;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
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

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity for the "Read Text" feature (Blind Mode).
 * Uses Google ML Kit (Text Recognition V2) to read text from the live camera feed offline.
 */
public class ReadTextActivity extends AppCompatActivity {

    // -----------------------------------------------------------
    // USING ML KIT (OFFLINE & FREE)
    // -----------------------------------------------------------

    private TextToSpeech tts;
    private PreviewView viewFinder;
    private TextView tvDescription;
    private Bitmap currentImageBitmap;
    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blind_mode); // Reusing Blind Mode layout as it fits perfectly

        viewFinder = findViewById(R.id.viewFinder);
        tvDescription = findViewById(R.id.tvDescription);
        tvDescription.setText("Vision Reader Ready.\nTap screen to read.");

        cameraExecutor = Executors.newSingleThreadExecutor();

        // 1. Initialize Text-To-Speech (TTS)
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });

        // 2. Start Camera (Check permissions first)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
        }

        // 3. Setup Tap Listener (Tap screen to analyze)
        viewFinder.setOnClickListener(v -> {
            if (currentImageBitmap != null) {
                tvDescription.setText("Reading text...");
                tts.speak("Reading...", TextToSpeech.QUEUE_FLUSH, null, null);

                analyzeImageWithMLKit(currentImageBitmap);
            } else {
                tts.speak("Camera not ready.", TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });
    }

    /**
     * Analyzes the given bitmap using ML Kit Text Recognition.
     * This runs OFFLINE and does not require an API key.
     */
    private void analyzeImageWithMLKit(Bitmap bitmap) {
        // 1. Get the Text Recognizer (Latin script / English)
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        // 2. Prepare the image for ML Kit
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        // 3. Process the image
        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String resultText = visionText.getText();
                    if (resultText == null || resultText.trim().isEmpty()) {
                        resultText = "No text found.";
                    }
                    
                    // Display and Speak the result
                    final String textToSpeak = resultText;
                    tvDescription.setText(textToSpeak);
                    tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null);
                })
                .addOnFailureListener(e -> {
                    String err = "Error: " + e.getMessage();
                    tvDescription.setText(err);
                    tts.speak("Could not read text.", TextToSpeech.QUEUE_FLUSH, null, null);
                });
    }

    /**
     * Starts the CameraX preview and image analysis.
     */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                // Using RGBA format to ensure compatibility
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                // Set analyzer to capture latest frame as Bitmap
                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    currentImageBitmap = toBitmap(image);
                    image.close();
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                Log.e("ReadText", "Camera Error", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Converts CameraX ImageProxy to a standard Android Bitmap.
     * Handles rotation and format conversion.
     */
    private Bitmap toBitmap(ImageProxy image) {
        if (image == null) return null;

        Bitmap bitmap = Bitmap.createBitmap(
                image.getWidth(),
                image.getHeight(),
                Bitmap.Config.ARGB_8888
        );

        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        buffer.rewind();
        bitmap.copyPixelsFromBuffer(buffer);

        Matrix matrix = new Matrix();
        matrix.postRotate(image.getImageInfo().getRotationDegrees());

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
}