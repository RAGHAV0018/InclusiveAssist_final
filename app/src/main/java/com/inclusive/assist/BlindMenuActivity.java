package com.inclusive.assist;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;
import android.view.View;

/**
 * Main Menu for the "Blind Mode" features.
 * Features include:
 * - Text Reading (ReadTextActivity)
 * - Object Detection (BlindModeActivity)
 * - Currency Recognition (CurrencyActivity)
 * - Light Detection (LightDetectorActivity)
 * - Location/SOS (LocationActivity)
 *
 * Implements Swipe Gestures for easier navigation.
 */
public class BlindMenuActivity extends AppCompatActivity {

    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blind_menu);

        // --- SETUP SWIPE GESTURES ---
        // We find the "Root" view (the background) to listen for swipes anywhere
        View rootView = findViewById(android.R.id.content);

        rootView.setOnTouchListener(new SwipeListener(this) {
            @Override
            public void onSwipeLeft() {
                // Feature: SWIPE LEFT TO GO BACK
                speak("Going Back");
                finish(); // Closes this screen
            }

            @Override
            public void onSwipeRight() {
                // Feature: SWIPE RIGHT TO REPEAT INSTRUCTIONS
                speak("Menu Open. Options are: Read Text, Currency, Object, Light, Location.");
            }
        });

        // Initialize Voice (TTS)
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });

        // 1. READ TEXT BUTTON
        Button btnRead = findViewById(R.id.btnRead);
        if (btnRead != null) {
            btnRead.setOnClickListener(v -> {
                speak("Opening Text Reader");
                startActivity(new Intent(this, ReadTextActivity.class));
            });
        }

        // 2. DETECT OBJECTS BUTTON
        Button btnDetect = findViewById(R.id.btnDetect);
        if (btnDetect != null) {
            btnDetect.setOnClickListener(v -> {
                speak("Opening Object Detection");
                startActivity(new Intent(this, BlindModeActivity.class));
            });
        }

        // 3. CURRENCY BUTTON
        Button btnCurrency = findViewById(R.id.btnCurrency);
        if (btnCurrency != null) {
            btnCurrency.setOnClickListener(v -> {
                speak("Opening Currency Checker");
                startActivity(new Intent(this, CurrencyActivity.class));
            });
        }

        // 4. LIGHT DETECTOR BUTTON
        Button btnLight = findViewById(R.id.btnLight);
        if (btnLight != null) {
            btnLight.setOnClickListener(v -> {
                speak("Opening Light Detector");
                startActivity(new Intent(this, LightDetectorActivity.class));
            });
        }

        // 5. LOCATION BUTTON (Where Am I?)
        Button btnLocation = findViewById(R.id.btnLocation);
        if (btnLocation != null) {
            btnLocation.setOnClickListener(v -> {
                speak("Checking Location...");
                startActivity(new Intent(this, LocationActivity.class));
            });
        }
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }
}