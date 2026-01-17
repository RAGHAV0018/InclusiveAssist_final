package com.inclusive.assist;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;

public class SOSHelper {

    public static void triggerSOS(Context context, TextToSpeech tts) {
        SharedPreferences prefs = context.getSharedPreferences("InclusiveAssist", Context.MODE_PRIVATE);
        String number = prefs.getString("EmergencyNumber", "");

        if (number.isEmpty()) {
            speak(tts, "No emergency number saved.");
            Toast.makeText(context, "No emergency number saved.", Toast.LENGTH_SHORT).show();
            return;
        }

        speak(tts, "Emergency! Calling now.");

        // 1. WhatsApp Location Share (Simplified for generic context, ideally needs loc)
        // We skip location usage here to keep it simple and fast for shake, 
        // or we could add it if we passed location. For now just the call is critical.
        
        // 2. Make Direct Phone Call
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            try {
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse("tel:" + number));
                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Needed for non-Activity context if any
                context.startActivity(callIntent);
            } catch (Exception e) {
                speak(tts, "Call failed.");
            }
        } else {
            // Fallback to Dial Pad if permission denied
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + number));
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(callIntent);
        }
    }

    private static void speak(TextToSpeech tts, String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }
}
