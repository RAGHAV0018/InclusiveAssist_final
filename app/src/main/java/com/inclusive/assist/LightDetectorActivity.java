package com.inclusive.assist;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Activity for "Light Detector" feature.
 * Uses the device's Ambient Light Sensor.
 * Beeps faster as the light gets brighter.
 */
public class LightDetectorActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor lightSensor;
    private TextView tvLightLevel;
    private ToneGenerator toneGenerator;
    private long lastBeepTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Simple layout created in code (no XML needed for this tool)
        tvLightLevel = new TextView(this);
        tvLightLevel.setTextSize(32);
        tvLightLevel.setPadding(50, 50, 50, 50);
        tvLightLevel.setText("Waiting for sensor...");
        setContentView(tvLightLevel);

        // 1. Setup Sensor
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        // 2. Setup Sound
        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float lux = event.values[0]; // Light level
        tvLightLevel.setText("Light Level:\n" + (int)lux + " lux");

        // 3. Logic: Beep faster if light is brighter
        int delay = 0;
        if (lux < 10) delay = 99999;       // Dark (No sound)
        else if (lux < 200) delay = 1000;  // Dim (Slow beep)
        else if (lux < 1000) delay = 400;  // Bright (Medium beep)
        else delay = 100;                  // Very Bright (Fast beep)

        long currentTime = System.currentTimeMillis();
        if (delay < 5000 && (currentTime - lastBeepTime > delay)) {
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
            lastBeepTime = currentTime;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            tvLightLevel.setText("Error: No Light Sensor on this device.");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }
}