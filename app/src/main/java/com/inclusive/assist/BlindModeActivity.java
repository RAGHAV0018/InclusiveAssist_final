
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
 * BlindModeActivity - Online Object Detection (Groq Llama Vision)
 * Uses Groq's llama-3.2-11b-vision-instruct model for object detection.
 */
public class BlindModeActivity extends AppCompatActivity {

    private static final String TAG = "BlindModeActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 101;
    
    // --- GROQ CONFIG ---
    private static final String GROQ_API_KEY = BuildConfig.GROQ_API_KEY;
    private static final String GROQ_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"; // Groq's vision model
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private PreviewView previewView;
    private TextView tvDescription;
    private ExecutorService cameraExecutor;
    private TextToSpeech tts;
    
    // Network Client
    private OkHttpClient client;
    private boolean isProcessing = false;
    private boolean isSpeaking = false;
    private long lastAnalysisTime = 0;
    private static final long ANALYSIS_DELAY = 5000; // 5 seconds between requests to allow TTS to complete

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_blind_mode);

        previewView = findViewById(R.id.viewFinder);
        tvDescription = findViewById(R.id.tvDescription);
        
        cameraExecutor = Executors.newSingleThreadExecutor();
        
        // Initialize OkHttp with timeouts
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        
        tts = new TextToSpeech(this, status -> {
             if (status == TextToSpeech.SUCCESS) {
                 tts.setLanguage(Locale.US);
                 // Set listener to track when TTS finishes speaking
                 tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                     @Override
                     public void onStart(String utteranceId) {
                         isSpeaking = true;
                     }
                     @Override
                     public void onDone(String utteranceId) {
                         isSpeaking = false;
                     }
                     @Override
                     public void onError(String utteranceId) {
                         isSpeaking = false;
                     }
                 });
             }
        });

        tvDescription.setText("Initializing Camera...");

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

        // Use RGBA_8888 for easier Bitmap conversion
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
        
        // Rate Limiting & Busy Check
        if (isProcessing || (now - lastAnalysisTime < ANALYSIS_DELAY)) {
            imageProxy.close();
            return;
        }

        try {
            // 1. Convert ImageProxy to Bitmap
            Bitmap bitmap = Bitmap.createBitmap(imageProxy.getWidth(), imageProxy.getHeight(), Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(imageProxy.getPlanes()[0].getBuffer());
            
            // 2. Rotate Bitmap (ImageAnalysis images are often unrotated)
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            if (rotationDegrees != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationDegrees);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }

            // 3. Compress to JPEG
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            // Scale down if necessary for speed/quota, e.g., to 640px width
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            // Simple resize if too big - Optimized for Gemini Speed (640px is sufficient)
            if (w > 640) {
                 float scale = 640f / w;
                 bitmap = Bitmap.createScaledBitmap(bitmap, 640, (int)(h * scale), true);
            }
            
            // Lower quality to 60 for faster upload (negligible accuracy loss for objects)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteArrayOutputStream);
            byte[] imageBytes = byteArrayOutputStream.toByteArray();
            String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            
            // Lock processing
            isProcessing = true;
            lastAnalysisTime = now;
            
            runOnUiThread(() -> tvDescription.setText("Analyzing..."));

            // 4. Send to Gemini
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
            // Groq API format
            jsonBody.put("model", GROQ_MODEL);
            
            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");
            
            // Content array with text and image
            JSONArray contentArray = new JSONArray();
            
            // Text part
            JSONObject textContent = new JSONObject();
            textContent.put("type", "text");
            textContent.put("text", "Identify the main object in this image. Respond in 5 words or less.");
            contentArray.put(textContent);
            
            // Image part
            JSONObject imageContent = new JSONObject();
            imageContent.put("type", "image_url");
            JSONObject imageUrl = new JSONObject();
            imageUrl.put("url", "data:image/jpeg;base64," + base64Image);
            imageContent.put("image_url", imageUrl);
            contentArray.put(imageContent);
            
            message.put("content", contentArray);
            messages.put(message);
            jsonBody.put("messages", messages);

        } catch (JSONException e) {
            e.printStackTrace();
            isProcessing = false;
            return;
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));
        
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + GROQ_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Groq Request Failed", e);
                runOnUiThread(() -> {
                     tvDescription.setText("Connection Failed");
                     speak("Connection failed");
                     isProcessing = false;
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseData = response.body().string();
                        JSONObject json = new JSONObject(responseData);
                        
                        // Parse Groq response format
                        JSONArray choices = json.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            JSONObject choice = choices.getJSONObject(0);
                            JSONObject message = choice.optJSONObject("message");
                            if (message != null) {
                                String text = message.optString("content", "No object detected");
                                
                                runOnUiThread(() -> {
                                    tvDescription.setText(text);
                                    speak(text);
                                });
                            }
                        } else {
                            runOnUiThread(() -> {
                                tvDescription.setText("No response from AI");
                                speak("No response");
                            });
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Parsing Error", e);
                        runOnUiThread(() -> {
                            tvDescription.setText("Error parsing response");
                            speak("Error occurred");
                        });
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "Groq Error: " + response.code() + " " + errorBody);
                    runOnUiThread(() -> {
                        String userMessage = "Error occurred";
                        if (response.code() == 401) {
                            userMessage = "Invalid API key";
                        } else if (response.code() == 429) {
                            userMessage = "Rate limit exceeded";
                        } else if (response.code() == 503) {
                            userMessage = "Service unavailable";
                        }
                        tvDescription.setText(userMessage);
                        speak(userMessage);
                    });
                }
                
                // Release lock
                isProcessing = false;
            }
        });
    }

    private void speak(String text) {
        if (tts != null && !isSpeaking) {
            // Create a unique utterance ID
            String utteranceId = String.valueOf(System.currentTimeMillis());
            android.os.Bundle params = new android.os.Bundle();
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
        }
    }
}
