package com.inclusive.assist;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BusStopActivity extends AppCompatActivity {

    private ListView lvBusStops;
    private Button btnVoiceSearch;
    private TextView tvBusTitle;
    private TextToSpeech tts;
    
    private List<JSONObject> stopsList = new ArrayList<>();
    private List<String> displayStops = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private String busNumber = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bus_stop);

        lvBusStops = findViewById(R.id.lvBusStops);
        btnVoiceSearch = findViewById(R.id.btnVoiceSearch);
        tvBusTitle = findViewById(R.id.tvBusTitle);

        String routeJson = getIntent().getStringExtra("ROUTE_DATA");
        parseRouteData(routeJson);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                speak("Bus " + busNumber + " selected. Select your destination stop or say it.");
            }
        });

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayStops);
        lvBusStops.setAdapter(adapter);

        lvBusStops.setOnItemClickListener((parent, view, position, id) -> {
            JSONObject selectedStop = stopsList.get(position);
            confirmStop(selectedStop);
        });

        btnVoiceSearch.setOnClickListener(v -> startVoiceInput());
    }

    private void parseRouteData(String json) {
        try {
            JSONObject route = new JSONObject(json);
            busNumber = route.getString("bus_number");
            tvBusTitle.setText("Route: " + busNumber);
            
            JSONArray stops = route.getJSONArray("stops");
            stopsList.clear();
            displayStops.clear();
            
            for (int i = 0; i < stops.length(); i++) {
                JSONObject stop = stops.getJSONObject(i);
                stopsList.add(stop);
                displayStops.add(stop.getString("name"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void confirmStop(JSONObject stop) {
        try {
            String name = stop.getString("name");
            double lat = stop.getDouble("lat");
            double lon = stop.getDouble("lon");
            
            speak("Setting destination to " + name);
            
            Intent intent = new Intent(this, DestinationActivity.class);
            intent.putExtra("DEST_NAME", name);
            intent.putExtra("DEST_LAT", lat);
            intent.putExtra("DEST_LON", lon);
            startActivity(intent);
            finish(); // Close stop list
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say Stop Name (e.g., HSR Layout)...");
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
                String spokenText = result.get(0).toLowerCase();
                filterStops(spokenText);
            }
        }
    }

    private void filterStops(String query) {
        try {
            for (JSONObject stop : stopsList) {
                String name = stop.getString("name");
                if (name.toLowerCase().contains(query)) {
                    confirmStop(stop);
                    return;
                }
            }
            speak("Stop not found. Please try again.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
