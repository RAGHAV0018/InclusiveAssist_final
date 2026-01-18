package com.inclusive.assist;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Activity for Location features:
 * 1. "Where Am I?": Speaks current address.
 * 2. "Share Location": Shares address and map link via WhatsApp/SMS.
 * 3. "SOS": Calls emergency contact and shares location instantly.
 */
public class LocationActivity extends AppCompatActivity {

    private FusedLocationProviderClient fusedLocationClient;
    private TextToSpeech tts;
    private TextView tvLocation;
    private EditText etEmergencyContact;
    private String currentAddress = "Locating...";
    private String currentCoordinates = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        tvLocation = findViewById(R.id.tvLocation);
        etEmergencyContact = findViewById(R.id.etEmergencyContact);
        Button btnShare = findViewById(R.id.btnShare);
        Button btnDestination = findViewById(R.id.btnDestination); // NEW
        Button btnSOS = findViewById(R.id.btnSOS);
        Button btnSaveContact = findViewById(R.id.btnSaveContact);

        // Load previously saved Emergency Number
        loadEmergencyNumber();

        // 1. Initialize Voice
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                checkPermissionsAndLocate();
            }
        });

        // 2. Initialize Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 3. Setup Buttons
        btnShare.setOnClickListener(v -> shareLocation());
        btnDestination.setOnClickListener(v -> startActivity(new Intent(this, DestinationActivity.class))); // NEW
        btnSaveContact.setOnClickListener(v -> saveEmergencyNumber());
        btnSOS.setOnClickListener(v -> triggerSOS());
    }

    /**
     * Checks for location permission. If granted, fetches location.
     */
    private void checkPermissionsAndLocate() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CALL_PHONE}, 100);
        } else {
            getLocation();
        }
    }

    /**
     * Gets the last known GPS location.
     */
    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        tvLocation.setText("Getting GPS signal...");
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        getReadableAddress(location.getLatitude(), location.getLongitude());
                    } else {
                        String error = "Could not find location. Try going outside.";
                        tvLocation.setText(error);
                        speak(error);
                    }
                });
    }

    /**
     * Converts GPS coordinates to a readable address using Geocoder.
     */
    private void getReadableAddress(double lat, double lon) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String addressText = address.getAddressLine(0);
                
                currentAddress = addressText;
                currentCoordinates = "https://maps.google.com/?q=" + lat + "," + lon;

                String finalSpeech = "You are at: " + addressText;
                tvLocation.setText(finalSpeech);
                speak(finalSpeech);
            } else {
                speak("Found coordinates, but cannot find address name.");
            }
        } catch (IOException e) {
            speak("Network error. Cannot load address.");
        }
    }

    /**
     * Shares location info via system Share Sheet (WhatsApp, SMS, etc).
     */
    private void shareLocation() {
        String msg = "I am at: " + currentAddress + "\nMap: " + currentCoordinates;
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, msg);
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Share Location"));
        speak("Sharing Location");
    }

    /**
     * Saves emergency contact number to SharedPreferences.
     */
    private void saveEmergencyNumber() {
        String number = etEmergencyContact.getText().toString().trim();
        if (!number.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences("InclusiveAssist", Context.MODE_PRIVATE);
            prefs.edit().putString("EmergencyNumber", number).apply();
            speak("Emergency Number Saved.");
            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show();
        } else {
            speak("Please enter a number first.");
        }
    }

    private void loadEmergencyNumber() {
        SharedPreferences prefs = getSharedPreferences("InclusiveAssist", Context.MODE_PRIVATE);
        String number = prefs.getString("EmergencyNumber", "");
        etEmergencyContact.setText(number);
    }

    /**
     * Triggers SOS: Calls the saved number and opens WhatsApp with location.
     */
    private void triggerSOS() {
        SharedPreferences prefs = getSharedPreferences("InclusiveAssist", Context.MODE_PRIVATE);
        String number = prefs.getString("EmergencyNumber", "");

        if (number.isEmpty()) {
            speak("No emergency number saved. Please enter one.");
            return;
        }

        speak("Calling Emergency Contact and Sharing Location.");

        // 1. WhatsApp Location Share (via Direct Link)
        try {
            String msg = "SOS! I need help! I am at: " + currentAddress + " " + currentCoordinates;
            String url = "https://api.whatsapp.com/send?phone=" + number + "&text=" + java.net.URLEncoder.encode(msg, "UTF-8");
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
        }

        // 2. Make Direct Phone Call
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + number));
            startActivity(callIntent);
        } else {
            // Fallback to Dial Pad if permission denied
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + number));
            startActivity(callIntent);
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
        if (requestCode == 100) {
            getLocation();
        }
    }
}