package com.inclusive.assist;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

public class SoundAlertActivity extends AppCompatActivity {

    private static final String TAG = "SoundAlertActivity";
    private TextView tvDecibel, tvStatus, tvTranscription;
    private ImageView ivSoundIcon;
    private LinearLayout layoutBackground;
    private Button btnToggle;
    
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private Vibrator vibrator;
    
    private boolean isListening = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final int PERMISSION_CODE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sound_alert);

        tvDecibel = findViewById(R.id.tvDecibel);
        tvStatus = findViewById(R.id.tvStatus);
        tvTranscription = findViewById(R.id.tvTranscription); // Added TextView
        ivSoundIcon = findViewById(R.id.ivSoundIcon);
        layoutBackground = findViewById(R.id.layoutBackground);
        btnToggle = findViewById(R.id.btnToggle);
        
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Check Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_CODE);
        } else {
            initSpeechRecognizer();
            startListening();
        }

        btnToggle.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            } else {
                startListening();
            }
        });
    }

    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                 tvStatus.setText("Listening for Announcements...");
                 ivSoundIcon.setColorFilter(Color.parseColor("#4CAF50")); // Green
                 layoutBackground.setBackgroundColor(Color.BLACK);
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {
                // RMS dB usually ranges from -2 to 10+ depending on device. 
                // We'll treat > 8 as "Loud" tentatively.
                int db = (int) rmsdB; // Simplified mapping
                // For UI, we might want to scale it to look like decibels (e.g., +40 offset?)
                // Let's just show the raw indicative value or a mapped one.
                // Assuming typical speech is ~5-6, loud is 8-10.
                
                // Let's map it roughly to SPL for display (Approximate)
                int displayDb = (int) (rmsdB * 4) + 40; 
                tvDecibel.setText(displayDb + " dB (Approx)");

                if (rmsdB > 10) { // Threshold for "Loud"
                    triggerAlert();
                } else {
                    // reset handled in onReady or manually? 
                    // We don't want to flash reset constantly.
                    // Keep detection logic simple.
                }
            }

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                // Restart? handled in default behavior usually, or needs explicit restart.
                // We will rely on onError or restart manually.
            }

            @Override
            public void onError(int error) {
                // Automatically restart listening on error (common in continuous listening)
                // Error 7 is No Match, Error 6 is Input timeout.
                Log.e(TAG, "Error: " + error);
                if (isListening) {
                    resetRecognizer();
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0);
                    tvTranscription.setText(text);
                    tvStatus.setText("Announcement Captured!");
                    
                    // If capturing actual speech, assume it's relevant -> Vibrate slightly?
                    vibrate(50);
                }
                // Continue listening
                if (isListening) startListening();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    tvTranscription.setText(matches.get(0));
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void startListening() {
        if (speechRecognizer == null) initSpeechRecognizer();
        
        try {
            speechRecognizer.startListening(speechIntent);
            isListening = true;
            btnToggle.setText("Stop Listening");
            tvStatus.setText("Listening...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
        }
        isListening = false;
        btnToggle.setText("Start Listening");
        tvStatus.setText("Paused");
        layoutBackground.setBackgroundColor(Color.BLACK);
    }
    
    // Helper to restart
    private void resetRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.startListening(speechIntent);
        }
    }

    private void triggerAlert() {
        ivSoundIcon.setColorFilter(Color.RED);
        layoutBackground.setBackgroundColor(Color.parseColor("#330000")); // Dark Red
        vibrate(100);
    }
    
    private void vibrate(long duration) {
        if (vibrator != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initSpeechRecognizer();
                startListening();
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}