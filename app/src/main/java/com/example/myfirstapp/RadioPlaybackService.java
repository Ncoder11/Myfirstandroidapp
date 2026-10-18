package com.example.myfirstapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

public class RadioPlaybackService extends Service {
    public static final String ACTION_PLAY = "com.example.myfirstapp.radio.PLAY";
    public static final String ACTION_STOP = "com.example.myfirstapp.radio.STOP";
    public static final String EXTRA_URL = "stream_url";
    public static final String EXTRA_STATION = "station_name";
    private static final String CHANNEL_ID = "travelkit_radio_playback";
    private static final int NOTIFICATION_ID = 4081;
    private MediaPlayer player;
    private String currentStation = "Tamil Nadu FM";

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "FM radio playback", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Controls for Travelkit FM playback");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopPlayback(); stopForeground(true); stopSelf(); return START_NOT_STICKY;
        }
        if (ACTION_PLAY.equals(intent.getAction())) {
            String url = intent.getStringExtra(EXTRA_URL);
            currentStation = intent.getStringExtra(EXTRA_STATION);
            if (currentStation == null || currentStation.trim().isEmpty()) currentStation = "Tamil Nadu FM";
            startForeground(NOTIFICATION_ID, buildNotification("Connecting…"));
            startPlayer(url);
        }
        return START_NOT_STICKY;
    }

    private void startPlayer(String url) {
        stopPlayback();
        if (url == null || url.trim().isEmpty()) { stopSelf(); return; }
        player = new MediaPlayer();
        try {
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(url);
            player.setOnPreparedListener(mp -> {
                mp.start();
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification("Playing live stream"));
            });
            player.setOnErrorListener((mp, what, extra) -> {
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification("Stream unavailable — select another station"));
                stopPlayback(); return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            stopPlayback();
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification("Could not play this stream"));
        }
    }

    private Notification buildNotification(String status) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Intent stop = new Intent(this, RadioPlaybackService.class); stop.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(currentStation).setContentText(status)
                .setContentIntent(openPending).setOngoing(player != null && player.isPlaying()).addAction(android.R.drawable.ic_media_pause, "Stop", stopPending);
        return builder.build();
    }

    private void stopPlayback() {
        if (player != null) {
            try { if (player.isPlaying()) player.stop(); } catch (Exception ignored) { }
            try { player.reset(); player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    @Override public void onDestroy() { stopPlayback(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
