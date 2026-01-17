package com.inclusive.assist;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    TextToSpeech tts;
    Button btnBlind, btnDeaf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Find the buttons from the design
        btnBlind = findViewById(R.id.btnIamBlind);
        btnDeaf = findViewById(R.id.btnIamDeaf);
        Button btnLocation = findViewById(R.id.btnWhereAmI);
        Button btnQuick = findViewById(R.id.btnQuickMessages);

        // 2. Setup Text to Speech
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                speak("Welcome to Inclusive Assist. Please choose your mode.");
            }
        });

        // 3. Make the Blind Button work
        btnBlind.setOnClickListener(v -> {
            speak("Blind mode selected");
            Intent intent = new Intent(MainActivity.this, BlindMenuActivity.class);
            startActivity(intent);
        });

        // 4. Make the Deaf Button work
        btnDeaf.setOnClickListener(v -> {
            // No voice needed for deaf mode, just open the page
            Intent intent = new Intent(MainActivity.this, DeafMenuActivity.class);
            startActivity(intent);
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
    }

    // Helper function to make speaking easier
    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}