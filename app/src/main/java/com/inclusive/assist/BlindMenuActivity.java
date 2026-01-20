package com.inclusive.assist;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Main Menu for the "Blind Mode" features.
 * Features include:
 * - Text Reading (ReadTextActivity)
 * - Object Detection (BlindModeActivity)
 * - Currency Recognition (CurrencyActivity)
 * - Currency Recognition (CurrencyActivity)
 * - Light Detection (LightDetectorActivity)
 *
 * Implements Swipe Gestures for easier navigation.
 */
public class BlindMenuActivity extends AppCompatActivity {

    private TextToSpeech tts;


    private ShakeDetector shakeDetector;
    private android.hardware.SensorManager sensorManager;

    private SpeechRecognizer speechRecognizer;
    private static final int PERMISSION_REQUEST_CODE = 200;

    private boolean isNavigating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blind_menu);

        sensorManager = (android.hardware.SensorManager) getSystemService(android.content.Context.SENSOR_SERVICE);
        shakeDetector = new ShakeDetector();
        shakeDetector.setOnShakeListener(count -> {
            if (count >= 5) {
                SOSHelper.triggerSOS(BlindMenuActivity.this, tts);
            }
        });

        // Initialize Voice (TTS)
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });

        // Setup Speech Recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) { Toast.makeText(BlindMenuActivity.this, "Listening...", Toast.LENGTH_SHORT).show(); }
            @Override
            public void onBeginningOfSpeech() {}
            @Override
            public void onRmsChanged(float rmsdB) {}
            @Override
            public void onBufferReceived(byte[] buffer) {}
            @Override
            public void onEndOfSpeech() {}
            @Override
            public void onError(int error) { 
                if (!isNavigating) {
                    speak("Try again."); 
                }
            }
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    processVoiceCommand(matches.get(0).toLowerCase());
                }
            }
            @Override
            public void onPartialResults(Bundle partialResults) {}
            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        // 1. READ TEXT BUTTON
        Button btnRead = findViewById(R.id.btnRead);
        if (btnRead != null) {
            btnRead.setOnClickListener(v -> {
                speak("Opening Text Reader");
                isNavigating = true;
                startActivity(new Intent(this, ReadTextActivity.class));
            });
        }

        // 2. DETECT OBJECTS BUTTON
        Button btnDetect = findViewById(R.id.btnDetect);
        if (btnDetect != null) {
            btnDetect.setOnClickListener(v -> {
                speak("Opening Object Detection");
                isNavigating = true;
                startActivity(new Intent(this, BlindModeActivity.class));
            });
        }

        // 3. CURRENCY BUTTON
        Button btnCurrency = findViewById(R.id.btnCurrency);
        if (btnCurrency != null) {
            btnCurrency.setOnClickListener(v -> {
                speak("Opening Currency Checker");
                isNavigating = true;
                startActivity(new Intent(this, CurrencyActivity.class));
            });
        }

        // 4. LIGHT DETECTOR BUTTON
        Button btnLight = findViewById(R.id.btnLight);
        if (btnLight != null) {
            btnLight.setOnClickListener(v -> {
                speak("Opening Light Detector");
                isNavigating = true;
                startActivity(new Intent(this, LightDetectorActivity.class));
            });
        }

        // 5. AI ASSISTANT BUTTON
        Button btnAssistant = findViewById(R.id.btnAssistant);
        if (btnAssistant != null) {
            btnAssistant.setOnClickListener(v -> {
                speak("Opening AI Assistant");
                isNavigating = true;
                startActivity(new Intent(this, AIAssistantActivity.class));
            });
        }
        
        // 6. VOICE CONTROL BUTTON
        Button btnVoiceControl = findViewById(R.id.btnVoiceControl);
        if (btnVoiceControl != null) {
            btnVoiceControl.setOnClickListener(v -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
                } else {
                    listen();
                }
            });
        }
        
        // 7. SCENE DESCRIPTION BUTTON
        Button btnScene = findViewById(R.id.btnScene);
        if (btnScene != null) {
            btnScene.setOnClickListener(v -> {
                speak("Opening Scene Description");
                isNavigating = true;
                startActivity(new Intent(this, SceneDescriptionActivity.class));
            });
        }
    }
    
    private void listen() {
        if (isNavigating) return;
        speak("Listening");
        new android.os.Handler().postDelayed(() -> {
             Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
             intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
             intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
             speechRecognizer.startListening(intent);
        }, 1000);
    }
    
    private void processVoiceCommand(String command) {
        if (command.contains("read") || command.contains("text")) {
             speak("Opening Text Reader");
             isNavigating = true;
             if (speechRecognizer != null) speechRecognizer.cancel();
             startActivity(new Intent(this, ReadTextActivity.class));
        } else if (command.contains("object") || command.contains("detect")) {
             speak("Opening Object Detection");
             isNavigating = true;
             if (speechRecognizer != null) speechRecognizer.cancel();
             startActivity(new Intent(this, BlindModeActivity.class));
        } else if (command.contains("currency") || command.contains("money")) {
             speak("Opening Currency Checker");
             isNavigating = true;
             if (speechRecognizer != null) speechRecognizer.cancel();
             startActivity(new Intent(this, CurrencyActivity.class));
        } else if (command.contains("light")) {
             speak("Opening Light Detector");
             isNavigating = true;
             if (speechRecognizer != null) speechRecognizer.cancel();
             startActivity(new Intent(this, LightDetectorActivity.class));
        } else if (command.contains("assistant") || command.contains("ai")) {
             speak("Opening AI Assistant");
             isNavigating = true;
             if (speechRecognizer != null) speechRecognizer.cancel();
             startActivity(new Intent(this, AIAssistantActivity.class));
        } else if (command.contains("scene") || command.contains("describe")) {
             speak("Opening Scene Description");
             isNavigating = true;
             if (speechRecognizer != null) speechRecognizer.cancel();
             startActivity(new Intent(this, SceneDescriptionActivity.class));
        } else {
            speak("Order not understood.");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        isNavigating = false;
        if (sensorManager != null && shakeDetector != null) {
            sensorManager.registerListener(shakeDetector,
                    sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER),
                    android.hardware.SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onPause() {
        if (sensorManager != null && shakeDetector != null) {
            sensorManager.unregisterListener(shakeDetector);
        }
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
        super.onPause();
    }
    
    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }
}