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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

// --- GROQ IMPORTS ---
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Locale;


public class AIAssistantActivity extends AppCompatActivity {

    // --- GROQ API KEY ---
    private static final String GROQ_API_KEY = BuildConfig.GROQ_API_KEY;
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
            // BLIND MODE: Auto-speak instruction
            speak("Voice Mode Enabled. Tap the mic to speak.");
        } else {
            // DEAF MODE
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

        // 3. OTHERWISE: ASK GROQ
        askGroq(input);
    }

    // --- FEATURE 1: EMERGENCY ---
    private void triggerEmergency() {
        String msg = "EMERGENCY DETECTED. Opening Dialer.";
        addToChat("System: " + msg);
        speak(msg);

        // Open Dialer with pre-filled number
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:7992384703"));
        startActivity(intent);
    }

    // --- FEATURE 2: NOTES ---
    private void saveNote(String note) {
        // Ideally save to a database, for now we just confirm it.
        String msg = "Note Saved: " + note;
        addToChat("System: " + msg);
        speak("I have saved that note.");
    }

    // --- FEATURE 3: GROQ CHAT (Using Llama 3) ---
    private void askGroq(String prompt) {
        addToChat("System: Thinking...");

        OkHttpClient client = new OkHttpClient();
        
        // Construct JSON Body
        JSONObject jsonBody = new JSONObject();
        try {
            // Use Llama 3.3 Versatile - Current stable model (2025)
            jsonBody.put("model", "llama-3.3-70b-versatile"); 
            
            JSONArray messages = new JSONArray();
            JSONObject msg = new JSONObject();
            msg.put("role", "user");
            msg.put("content", "You are a helpful assistant for a blind user. Keep answers short, clear, and kind. " + prompt);
            messages.put(msg);
            
            jsonBody.put("messages", messages);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));
        
        Request request = new Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + GROQ_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                 runOnUiThread(() -> {
                    addToChat("Error: " + e.getMessage());
                    speak("I could not connect to Groq.");
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseData = response.body().string();
                        JSONObject json = new JSONObject(responseData);
                        JSONArray choices = json.getJSONArray("choices");
                        String text = choices.getJSONObject(0).getJSONObject("message").getString("content");
                        
                        runOnUiThread(() -> {
                            addToChat("AI: " + text);
                            speak(text);
                        });
                    } catch (JSONException e) {
                         runOnUiThread(() -> {
                            addToChat("Parsing Error");
                            speak("I did not understand the response.");
                        });
                    }
                } else {
                     String errorBody = response.body().string();
                     runOnUiThread(() -> {
                        addToChat("Error " + response.code() + ": " + errorBody);
                        speak("Groq returned an error.");
                    });
                }
            }
        });
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