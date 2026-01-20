package com.inclusive.assist;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * SceneDescriptionActivity - Detailed Scene Analysis (Gemini Pro)
 * Uses a more powerful model for detailed scene descriptions.
 */
public class SceneDescriptionActivity extends AppCompatActivity {

    private static final String TAG = "SceneDescActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 102;
    
    // --- GEMINI CONFIG ---
    // Using valid key ...wHs and 2.5-flash model (same as Object Detection)
    private static final String GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY;
    private static final String GEMINI_MODEL = "gemini-2.5-flash"; 
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent?key=" + GEMINI_API_KEY;

    private PreviewView previewView;
    private TextView tvDescription;
    private ExecutorService cameraExecutor;
    private TextToSpeech tts;
    
    private OkHttpClient client;
    private boolean isProcessing = false;
    private long lastAnalysisTime = 0;
    private static final long ANALYSIS_DELAY = 4000; // Slower refresh for "Scene" mode (more expensive/detailed)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_blind_mode); // Reusing the same layout (viewfinder + text)

        previewView = findViewById(R.id.viewFinder);
        tvDescription = findViewById(R.id.tvDescription);
        
        cameraExecutor = Executors.newSingleThreadExecutor();
        
        client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS) // Longer timeout for Pro model
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        
        tts = new TextToSpeech(this, status -> {
             if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });

        tvDescription.setText("Initializing Scene Scanner...");

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                bindCameraUseCases(ProcessCameraProvider.getInstance(this).get());
            } catch (Exception e) {
                Log.e(TAG, "Camera Error", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::processImage);
        
        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Bind UseCase Error", e);
        }
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void processImage(ImageProxy imageProxy) {
        long now = System.currentTimeMillis();
        
        if (isProcessing || (now - lastAnalysisTime < ANALYSIS_DELAY)) {
            imageProxy.close();
            return;
        }

        try {
            Bitmap bitmap = Bitmap.createBitmap(imageProxy.getWidth(), imageProxy.getHeight(), Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(imageProxy.getPlanes()[0].getBuffer());
            
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            if (rotationDegrees != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationDegrees);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            // Gemini Pro can handle decent res, but for speed 800-1024 is good.
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            if (w > 800) {
                 float scale = 800f / w;
                 bitmap = Bitmap.createScaledBitmap(bitmap, 800, (int)(h * scale), true);
            }
            
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream);
            byte[] imageBytes = byteArrayOutputStream.toByteArray();
            String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            
            isProcessing = true;
            lastAnalysisTime = now;
            
            runOnUiThread(() -> tvDescription.setText("Analyzing Scene..."));

            sendToGemini(base64Image);

        } catch (Exception e) {
            Log.e(TAG, "Image Processing Error", e);
            isProcessing = false;
        } finally {
            imageProxy.close();
        }
    }

    private void sendToGemini(String base64Image) {
        JSONObject jsonBody = new JSONObject();
        try {
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();

            JSONObject textPart = new JSONObject();
            textPart.put("text", "Describe this scene in detail for a blind person. Include objects, layout, and atmosphere. Keep it under 30 words.");
            parts.put(textPart);

            JSONObject imageBlob = new JSONObject();
            imageBlob.put("mime_type", "image/jpeg");
            imageBlob.put("data", base64Image);
            
            JSONObject imagePart = new JSONObject();
            imagePart.put("inline_data", imageBlob);
            parts.put(imagePart);

            content.put("parts", parts);
            
            JSONArray contents = new JSONArray();
            contents.put(content);
            jsonBody.put("contents", contents);

        } catch (JSONException e) {
            e.printStackTrace();
            isProcessing = false;
            return;
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));
        
        Request request = new Request.Builder()
                .url(API_URL)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Gemini Request Failed", e);
                runOnUiThread(() -> {
                     tvDescription.setText("Connection Failed");
                     isProcessing = false;
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseData = response.body().string();
                        JSONObject json = new JSONObject(responseData);
                        
                        JSONArray candidates = json.optJSONArray("candidates");
                        if (candidates != null && candidates.length() > 0) {
                            JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
                            if (content != null) {
                                JSONArray parts = content.optJSONArray("parts");
                                if (parts != null && parts.length() > 0) {
                                    String text = parts.getJSONObject(0).getString("text");
                                    
                                    runOnUiThread(() -> {
                                        tvDescription.setText(text);
                                        speak(text);
                                    });
                                }
                            }
                        } else {
                            // Valid response but no candidates (Safety filter?)
                             runOnUiThread(() -> tvDescription.setText("No description generated. Try moving."));
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Parsing Error", e);
                        runOnUiThread(() -> tvDescription.setText("Parsing Error"));
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "Gemini Error: " + response.code() + " " + errorBody);
                    runOnUiThread(() -> {
                        if (response.code() == 404) {
                             tvDescription.setText("Error 404: Model unavailable. Check API Key.");
                        } else {
                             tvDescription.setText("Error " + response.code() + ": " + errorBody);
                        }
                    });
                }
                
                isProcessing = false; 
            }
        });
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }
}
