package com.bfp.alert;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID   = "SOS_CHANNEL";
    private static final String CHANNEL_NAME = "SOS Alerts";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        if (remoteMessage.getNotification() == null) return;

        String title = remoteMessage.getNotification().getTitle();
        String body  = remoteMessage.getNotification().getBody();

        // Tap notification → open admin dashboard
        Intent intent = new Intent(this, AdminDashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true);

        NotificationManagerCompat.from(this)
                .notify((int) System.currentTimeMillis(), builder.build());
    }

    // Called when FCM gives a new token — save it to Firestore
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        saveTokenToFirestore(token);
    }

    private void saveTokenToFirestore(String token) {
        com.google.firebase.auth.FirebaseAuth auth =
                com.google.firebase.auth.FirebaseAuth.getInstance();

        // Only save if an admin is logged in
        if (auth.getCurrentUser() == null) return;

        String uid = auth.getCurrentUser().getUid();
        Map<String, Object> data = new HashMap<>();
        data.put("fcmToken", token);

        FirebaseFirestore.getInstance()
                .collection("admin_tokens")
                .document(uid)
                .set(data);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("BFP SOS alert notifications");

            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}