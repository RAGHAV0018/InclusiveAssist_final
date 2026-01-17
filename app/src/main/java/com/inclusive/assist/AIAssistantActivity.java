package com.inclusive.assist;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

// --- GEMINI IMPORTS ---
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Locale;


public class AIAssistantActivity extends AppCompatActivity {

    // --- PASTE GEMINI API KEY HERE ---
    private static final String API_KEY = "AIzaSyBOCKnxvyOeXFZpRgt6hQZWPqX-prhhisg";
    // ---------------------------------

    private TextView tvChatHistory;
    private EditText etInput;
    private Button btnSend, btnMic, btnModeSwitch;
    private TextToSpeech tts;

    private boolean isVoiceMode = false; // False = Text Mode (Deaf), True = Voice Mode (Blind)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_assistant);

        // UI Setup
        tvChatHistory = findViewById(R.id.tvChatHistory);
        etInput = findViewById(R.id.etInput);
        btnSend = findViewById(R.id.btnSend);
        btnMic = findViewById(R.id.btnMic);
        btnModeSwitch = findViewById(R.id.btnModeSwitch);

        // 1. Initialize Voice (TTS)
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });

        // 2. Button Listeners
        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString();
            if (!text.isEmpty()) processUserCommand(text);
        });

        btnMic.setOnClickListener(v -> startVoiceInput());

        btnModeSwitch.setOnClickListener(v -> toggleMode());

        // Start in Text Mode by default
        updateUIForMode();
    }

    // --- MODE SWITCHING ---
    private void toggleMode() {
        isVoiceMode = !isVoiceMode;
        updateUIForMode();

        if (isVoiceMode) {
            speak("Voice Mode Enabled. Tap the mic to speak.");
        } else {
            speak("Text Mode Enabled.");
        }
    }

    private void updateUIForMode() {
        if (isVoiceMode) {
            // BLIND MODE: Hide text box, show big Mic button
            etInput.setVisibility(View.GONE);
            btnSend.setVisibility(View.GONE);
            btnMic.setVisibility(View.VISIBLE);
            btnModeSwitch.setText("Switch to Text Mode");
        } else {
            // DEAF MODE: Show text box, hide Mic
            etInput.setVisibility(View.VISIBLE);
            btnSend.setVisibility(View.VISIBLE);
            btnMic.setVisibility(View.GONE);
            btnModeSwitch.setText("Switch to Voice Mode");
        }
    }

    // --- VOICE INPUT ---
    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...");
        try {
            startActivityForResult(intent, 100);
        } catch (Exception e) {
            speak("Error starting voice input.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (!result.isEmpty()) {
                processUserCommand(result.get(0));
            }
        }
    }

    // --- 🧠 THE BRAIN: DECIDE WHAT TO DO ---
    private void processUserCommand(String input) {
        // Add user text to screen
        addToChat("You: " + input);
        etInput.setText("");

        String lowerInput = input.toLowerCase();

        // 1. EMERGENCY CHECK
        if (lowerInput.contains("help") || lowerInput.contains("sos") || lowerInput.contains("emergency")) {
            triggerEmergency();
            return;
        }

        // 2. NOTE CHECK
        if (lowerInput.startsWith("note") || lowerInput.contains("remind me")) {
            saveNote(input);
            return;
        }

        // 3. OTHERWISE: ASK GEMINI
        askGemini(input);
    }

    // --- FEATURE 1: EMERGENCY ---
    private void triggerEmergency() {
        String msg = "EMERGENCY DETECTED. Opening Dialer.";
        addToChat("System: " + msg);
        speak(msg);

        // Open Dialer with 911 (or 112) pre-filled
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:911"));
        startActivity(intent);
    }

    // --- FEATURE 2: NOTES ---
    private void saveNote(String note) {
        // Ideally save to a database, for now we just confirm it.
        String msg = "Note Saved: " + note;
        addToChat("System: " + msg);
        speak("I have saved that note.");
    }

    // --- FEATURE 3: GEMINI CHAT ---
    private void askGemini(String prompt) {
        addToChat("System: Thinking...");

        // Use new model name: gemini-1.5-flash
        GenerativeModel gm = new GenerativeModel("gemini-1.5-flash", API_KEY);
        GenerativeModelFutures model = GenerativeModelFutures.from(gm);

        Content content = new Content.Builder()
                .addText("You are a helpful assistant for a disabled user. Keep answers short, clear, and kind. Query: " + prompt)
                .build();

        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String text = result.getText();
                runOnUiThread(() -> {
                    addToChat("AI: " + text);
                    speak(text);
                });
            }

            @Override
            public void onFailure(Throwable t) {
                runOnUiThread(() -> {
                    addToChat("Error: " + t.getMessage());
                    speak("I lost connection.");
                });
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // --- HELPER FUNCTIONS ---
    private void addToChat(String text) {
        tvChatHistory.append("\n" + text);
    }

    private void speak(String text) {
        // Only speak if we are in Voice Mode (Blind Users)
        // OR if it's an emergency
        if (isVoiceMode || text.contains("EMERGENCY")) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }
}