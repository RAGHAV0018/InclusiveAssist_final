package com.inclusive.assist;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.Locale;

public class DestinationActivity extends AppCompatActivity {

    private static final double DEST_LAT = 12.918026;
    private static final double DEST_LON = 77.500007;
    private static final float TRIGGER_DISTANCE_METERS = 500f;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private TextView tvStatus;
    private Button btnToggle;
    
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private TextToSpeech tts;
    private Vibrator vibrator;
    
    private boolean isTracking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_destination);

        tvStatus = findViewById(R.id.tvStatus);
        btnToggle = findViewById(R.id.btnToggle);
        
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });

        // Setup Location Callback
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    handleLocationUpdate(location);
                }
            }
        };

        btnToggle.setOnClickListener(v -> toggleTracking());
    }

    private void toggleTracking() {
        if (isTracking) {
            stopTracking();
        } else {
            startTracking();
        }
    }

    private void startTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(5000); // 5 seconds
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
        
        isTracking = true;
        btnToggle.setText("STOP TRACKING");
        btnToggle.setBackgroundColor(0xFFFF0000); // Red
        tvStatus.setText("Distance: Calculating...");
        speak("Location tracking started.");
    }

    private void stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        isTracking = false;
        btnToggle.setText("START ALARM");
        btnToggle.setBackgroundColor(0xFF4CAF50); // Green
        tvStatus.setText("Distance: Stopped");
        speak("Location tracking stopped.");
    }

    private void handleLocationUpdate(Location currentLocation) {
        float[] results = new float[1];
        Location.distanceBetween(currentLocation.getLatitude(), currentLocation.getLongitude(), DEST_LAT, DEST_LON, results);
        float distanceInMeters = results[0];

        tvStatus.setText(String.format(Locale.US, "Distance: %.0f meters", distanceInMeters));

        if (distanceInMeters < TRIGGER_DISTANCE_METERS) {
            triggerAlarm();
        }
    }

    private void triggerAlarm() {
        // Stop tracking immediately to save battery (and prevent multiple triggers)
        stopTracking();

        // 1. Text to Speech
        String message = "Wake up! You are approaching your destination!";
        speak(message);

        // 2. Vibrate Heavily
        if (vibrator != null) {
            long[] pattern = {0, 1000, 500, 1000, 500, 1000}; // Wait 0, Vibrate 1s, Pause 0.5s, Vibrate 1s...
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1)); // -1 means do not repeat
            } else {
                vibrator.vibrate(pattern, -1);
            }
        }
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startTracking();
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
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
