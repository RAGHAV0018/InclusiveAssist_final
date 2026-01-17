package com.inclusive.assist;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Legacy Activity for "Deaf Mode".
 * Currently effectively replaced by DeafMenuActivity, but kept for compatibility.
 * Can be used as a splash screen or entry point if needed later.
 */
public class DeafModeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Minimal implementation to fix build
        setTitle("Deaf Mode");
    }
}
