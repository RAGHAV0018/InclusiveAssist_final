package com.inclusive.assist;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BusRouteActivity extends AppCompatActivity {

    private ListView lvBusRoutes;
    private Button btnVoiceSearch;
    private TextToSpeech tts;
    
    private List<JSONObject> allRoutes = new ArrayList<>();
    private List<String> displayRoutes = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bus_route);

        lvBusRoutes = findViewById(R.id.lvBusRoutes);
        btnVoiceSearch = findViewById(R.id.btnVoiceSearch);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                speak("Select your bus route. You can tap the microphone button to say the bus number.");
            }
        });

        loadBusData();

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayRoutes);
        lvBusRoutes.setAdapter(adapter);

        lvBusRoutes.setOnItemClickListener((parent, view, position, id) -> {
            JSONObject selectedRoute = allRoutes.get(position);
            openStopSelection(selectedRoute);
        });

        btnVoiceSearch.setOnClickListener(v -> startVoiceInput());
    }

    private void loadBusData() {
        try {
            InputStream is = getAssets().open("bus_routes.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);
            
            JSONObject obj = new JSONObject(json);
            JSONArray routes = obj.getJSONArray("routes");

            allRoutes.clear();
            displayRoutes.clear();

            for (int i = 0; i < routes.length(); i++) {
                JSONObject route = routes.getJSONObject(i);
                allRoutes.add(route);
                displayRoutes.add(route.getString("bus_number") + " - " + route.getString("description"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading routes", Toast.LENGTH_SHORT).show();
        }
    }

    private void openStopSelection(JSONObject route) {
        try {
            String busNum = route.getString("bus_number");
            speak("Selected bus " + busNum);
            
            Intent intent = new Intent(this, BusStopActivity.class);
            intent.putExtra("ROUTE_DATA", route.toString());
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say Bus Number (e.g., 500 D)...");
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
                filterRoutes(spokenText);
            }
        }
    }

    private void filterRoutes(String query) {
        // Simple search: find first match
        try {
            for (JSONObject route : allRoutes) {
                String busNum = route.getString("bus_number").toLowerCase();
                String desc = route.getString("description").toLowerCase();
                
                // Allow "500 d" or "five hundred d" matches
                if (busNum.contains(query.replace(" ", "")) || 
                    busNum.replace("-", " ").contains(query) ||
                    desc.contains(query)) {
                    
                    openStopSelection(route);
                    return;
                }
            }
            speak("Bus not found. Please try again.");
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
