package com.inclusive.assist;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BlindModeActivity - Real-time Object Detection using TensorFlow Lite
 * 
 * This activity provides reliable object detection for visually impaired users.
 * Uses CameraX for camera access and TensorFlow Lite MobileNet SSD for detection.
 */
public class BlindModeActivity extends AppCompatActivity {

    private static final String TAG = "BlindModeActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 101;
    
    // Detection Settings
    private static final float CONFIDENCE_THRESHOLD = 0.6f; // 60% confidence minimum
    private static final int INPUT_SIZE = 300; // MobileNet SSD input size
    private static final int NUM_DETECTIONS = 10; // Max detections per frame
    private static final long SPEAK_INTERVAL_MS = 3000; // 3 seconds between announcements
    
    // UI Components
    private PreviewView previewView;
    private TextView tvDescription;
    
    // Camera Components
    private Camera camera;
    private ExecutorService cameraExecutor;
    
    // AI Components
    private Interpreter tflite;
    private List<String> labels;
    private TextToSpeech tts;
    
    // State Management
    private boolean isModelLoaded = false;
    private long lastSpeakTime = 0;
    private int frameCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_blind_mode);

        // Initialize UI
        previewView = findViewById(R.id.viewFinder);
        tvDescription = findViewById(R.id.tvDescription);
        
        if (previewView == null || tvDescription == null) {
            Log.e(TAG, "✗ Failed to find UI components");
            Toast.makeText(this, "UI initialization error", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Initialize TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "✗ TTS language not supported");
                } else {
                    Log.i(TAG, "✓ TTS initialized successfully");
                }
            } else {
                Log.e(TAG, "✗ TTS initialization failed");
            }
        });

        // Load TensorFlow Lite model
        loadModel();

        // Request camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
    }

    /**
     * Load TensorFlow Lite model and labels
     */
    private void loadModel() {
        tvDescription.setText("Loading AI model...");
        
        new Thread(() -> {
            try {
                // Load TFLite model
                ByteBuffer model = FileUtil.loadMappedFile(this, "detect.tflite");
                Interpreter.Options options = new Interpreter.Options();
                options.setNumThreads(4); // Use 4 CPU threads
                tflite = new Interpreter(model, options);
                
                Log.i(TAG, "✓ TFLite model loaded successfully");

                // Load labels
                labels = loadLabels("labelmap.txt");
                Log.i(TAG, "✓ Loaded " + labels.size() + " labels");

                isModelLoaded = true;
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "AI Model Ready", Toast.LENGTH_SHORT).show();
                    tvDescription.setText("Point camera at objects");
                });

            } catch (IOException e) {
                Log.e(TAG, "✗ Error loading model: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Failed to load AI model", Toast.LENGTH_LONG).show();
                    tvDescription.setText("Model loading failed");
                });
            }
        }).start();
    }

    /**
     * Load labels from assets
     */
    private List<String> loadLabels(String filename) throws IOException {
        List<String> labelList = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getAssets().open(filename)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                labelList.add(line.trim());
            }
        }
        
        return labelList;
    }

    /**
     * Start camera with CameraX
     */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
            ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "✗ Camera initialization failed: " + e.getMessage(), e);
                Toast.makeText(this, "Camera failed to start", Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Bind camera use cases (Preview + Image Analysis)
     */
    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        // Preview use case
        Preview preview = new Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build();
        
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Image analysis use case for object detection
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        // Camera selector (back camera)
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            // Unbind all use cases before rebinding
            cameraProvider.unbindAll();

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalysis);

            Log.i(TAG, "✓ Camera started successfully");

        } catch (Exception e) {
            Log.e(TAG, "✗ Camera binding failed: " + e.getMessage(), e);
            Toast.makeText(this, "Camera binding failed", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Analyze each camera frame for object detection
     */
    private void analyzeImage(ImageProxy image) {
        // Skip frames for performance (process every 3rd frame)
        frameCounter++;
        if (frameCounter % 3 != 0) {
            image.close();
            return;
        }

        // Only process if model is loaded
        if (!isModelLoaded || tflite == null) {
            image.close();
            return;
        }

        try {
            // Convert ImageProxy to Bitmap
            Bitmap bitmap = imageProxyToBitmap(image);
            
            // Run object detection
            List<Detection> detections = detectObjects(bitmap);
            
            // Announce detection if found
            if (!detections.isEmpty()) {
                announceDetection(detections.get(0)); // Announce highest confidence detection
            }

        } catch (Exception e) {
            Log.e(TAG, "Error analyzing image: " + e.getMessage());
        } finally {
            image.close();
        }
    }

    /**
     * Convert ImageProxy to Bitmap
     */
    private Bitmap imageProxyToBitmap(ImageProxy image) {
        // Get the image buffer
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(nv21, 
            android.graphics.ImageFormat.NV21, 
            image.getWidth(), 
            image.getHeight(), 
            null);
        
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, 
            image.getWidth(), image.getHeight()), 100, out);
        
        byte[] imageBytes = out.toByteArray();
        Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        
        return bitmap;
    }

    /**
     * Run object detection on bitmap using TensorFlow Lite
     */
    private List<Detection> detectObjects(Bitmap bitmap) {
        List<Detection> detections = new ArrayList<>();
        
        try {
            // Prepare input
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
            ByteBuffer inputBuffer = convertBitmapToByteBuffer(resizedBitmap);

            // Prepare output buffers
            float[][][] outputLocations = new float[1][NUM_DETECTIONS][4]; // Bounding boxes
            float[][] outputClasses = new float[1][NUM_DETECTIONS]; // Class IDs
            float[][] outputScores = new float[1][NUM_DETECTIONS]; // Confidence scores
            float[] numDetections = new float[1]; // Number of detections

            // Run inference
            Object[] inputs = {inputBuffer};
            java.util.Map<Integer, Object> outputs = new java.util.HashMap<>();
            outputs.put(0, outputLocations);
            outputs.put(1, outputClasses);
            outputs.put(2, outputScores);
            outputs.put(3, numDetections);

            tflite.runForMultipleInputsOutputs(inputs, outputs);

            // Parse results
            int numDetected = Math.min((int) numDetections[0], NUM_DETECTIONS);
            
            for (int i = 0; i < numDetected; i++) {
                float confidence = outputScores[0][i];
                
                // Only include high-confidence detections
                if (confidence >= CONFIDENCE_THRESHOLD) {
                    int classId = (int) outputClasses[0][i];
                    
                    // Get label (add 1 because labelmap starts at index 1)
                    String label = (classId + 1 < labels.size()) ? 
                        labels.get(classId + 1) : "Unknown";
                    
                    // Get bounding box
                    float top = outputLocations[0][i][0];
                    float left = outputLocations[0][i][1];
                    float bottom = outputLocations[0][i][2];
                    float right = outputLocations[0][i][3];
                    
                    RectF boundingBox = new RectF(left, top, right, bottom);
                    
                    detections.add(new Detection(label, confidence, boundingBox));
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in object detection: " + e.getMessage());
        }
        
        return detections;
    }

    /**
     * Convert bitmap to ByteBuffer for TFLite input
     */
    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        
        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        
        int pixel = 0;
        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < INPUT_SIZE; j++) {
                int val = intValues[pixel++];
                
                // Normalize to [0, 1] for quantized model
                byteBuffer.put((byte) ((val >> 16) & 0xFF));
                byteBuffer.put((byte) ((val >> 8) & 0xFF));
                byteBuffer.put((byte) (val & 0xFF));
            }
        }
        
        return byteBuffer;
    }

    /**
     * Announce detected object via TTS (throttled)
     */
    private void announceDetection(Detection detection) {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastSpeakTime > SPEAK_INTERVAL_MS) {
            runOnUiThread(() -> {
                String message = "I see: " + detection.label;
                String confidenceText = String.format("%.0f%% confidence", detection.confidence * 100);
                
                tvDescription.setText(message + " (" + confidenceText + ")");
                
                if (tts != null) {
                    tts.speak(detection.label, TextToSpeech.QUEUE_FLUSH, null, null);
                }
                
                Log.i(TAG, "Detected: " + detection.label + " (" + confidenceText + ")");
            });
            
            lastSpeakTime = currentTime;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "✓ Camera permission granted");
                startCamera();
            } else {
                Log.e(TAG, "✗ Camera permission denied");
                Toast.makeText(this, "Camera permission required for object detection", 
                    Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up camera executor
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        
        // Clean up TTS
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        
        // Clean up TFLite interpreter
        if (tflite != null) {
            tflite.close();
        }
        
        Log.i(TAG, "Activity destroyed");
    }

    /**
     * Helper class to store detection results
     */
    private static class Detection {
        String label;
        float confidence;
        RectF boundingBox;

        Detection(String label, float confidence, RectF boundingBox) {
            this.label = label;
            this.confidence = confidence;
            this.boundingBox = boundingBox;
        }
    }
}