package com.inclusive.assist;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    TextToSpeech tts;
    Button btnBlind, btnDeaf, btnVoice;
    private SpeechRecognizer speechRecognizer;
    private static final int PERMISSION_REQUEST_CODE = 200;
    private boolean isListeningForWakeWord = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Find the buttons from the design
        btnBlind = findViewById(R.id.btnIamBlind);
        btnDeaf = findViewById(R.id.btnIamDeaf);
        Button btnLocation = findViewById(R.id.btnWhereAmI);
        Button btnQuick = findViewById(R.id.btnQuickMessages);
        btnVoice = findViewById(R.id.btnVoiceAssistant);

        // 2. Setup Text to Speech
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                speak("Welcome to Inclusive Assist. Please choose your mode or use Voice Assistant.");
            }
        });

        // Initialize SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        // Update the RecognitionListener:
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) { Toast.makeText(MainActivity.this, "Listening...", Toast.LENGTH_SHORT).show(); }
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
                 speak("Try again.");
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

        // 3. Make the Blind Button work
        btnBlind.setOnClickListener(v -> {
            speak("Visually Impaired mode selected");
            startActivity(new Intent(MainActivity.this, BlindMenuActivity.class));
        });

        // 4. Make the Deaf Button work
        btnDeaf.setOnClickListener(v -> {
            // No voice needed for deaf mode, just open the page
            startActivity(new Intent(MainActivity.this, DeafMenuActivity.class));
        });

        // 5. Make the Where Am I Button work
        btnLocation.setOnClickListener(v -> {
            speak("Opening Location");
            startActivity(new Intent(MainActivity.this, LocationActivity.class));
        });

        // 6. Make the Quick Messages Button work
        btnQuick.setOnClickListener(v -> {
            speak("Opening Quick Messages");
            startActivity(new Intent(MainActivity.this, QuickTextActivity.class));
        });

        // 7. Voice Assistant Button
        btnVoice.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
            } else {
                listen();
            }
        });
    }

    private void listen() {
        speak("Listening");
        // Delay listening slightly so TTS doesn't overlap
        btnVoice.postDelayed(() -> {
             Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
             intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
             intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
             speechRecognizer.startListening(intent);
        }, 1000);
    }

    private void processVoiceCommand(String command) {
        if (command.contains("visually") || command.contains("blind") || command.contains("impaired")) {
            btnBlind.performClick();
        } else if (command.contains("hearing") || command.contains("deaf")) {
            btnDeaf.performClick();
        } else if (command.contains("read") && command.contains("text")) {
             speak("Opening Text Reader");
             startActivity(new Intent(MainActivity.this, ReadTextActivity.class));
        } else if (command.contains("currency") || command.contains("money")) {
             speak("Opening Currency Checker");
             startActivity(new Intent(MainActivity.this, CurrencyActivity.class));
        } else if (command.contains("where") || command.contains("location")) {
             startActivity(new Intent(MainActivity.this, LocationActivity.class));
        } else {
            speak("Command not understood.");
        }
    }
    
    // Override onResume not needed for click-to-speak
    
    @Override
    protected void onPause() {
        super.onPause();
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    // Helper function to make speaking easier
    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
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
}