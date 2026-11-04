package com.slimvpn.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;

import org.amnezia.awg.config.Config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/** Сторож: включает VPN при запуске YouTube и выключает, когда YouTube закрыли. */
public class AutoVpnService extends Service {

    private static final String CHANNEL = "slimvpn_watch";
    private static final int NOTIF_ID = 42;
    private static final String[] TARGETS = {"org.smarttube.stable", "com.google.android.youtube", "com.slimvpn.app"};

    private volatile boolean running = true;
    private Thread worker;
    private String lastForeground = "";

    @Override public void onCreate() {
        super.onCreate();
        startForeground(NOTIF_ID, notification("Слежу за YouTube"));
        worker = new Thread(this::loop, "vpn-watcher");
        worker.start();
    }

    private Notification notification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "SlimVPN", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        return new Notification.Builder(this, CHANNEL)
                .setContentTitle("SlimVPN")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(true)
                .build();
    }

    private void loop() {
        while (running) {
            try {
                String fg = foregroundPackage();
                if (fg != null && !fg.equals(lastForeground)) {
                    lastForeground = fg;
                    boolean enabled = getSharedPreferences("slimvpn", MODE_PRIVATE).getBoolean("auto_youtube", true);
                    boolean isTarget = enabled && isTarget(fg);
                    if (isTarget) {
                        if (!VpnController.isConnected()) {
                            Config cfg = loadConfig();
                            if (cfg != null) {
                                VpnController.setOwnedByWatcher(true);
                                VpnController.connect(this, cfg, (ok, err) -> { });
                            }
                        }
                    } else {
                        boolean protectedNow = System.currentTimeMillis()
                                < getSharedPreferences("slimvpn", MODE_PRIVATE).getLong("protect_until", 0L);
                        if (!protectedNow && VpnController.isConnected() && VpnController.isOwnedByWatcher()) {
                            VpnController.disconnect(null);
                        }
                    }
                }
            } catch (Throwable ignored) { }
            try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
        }
    }

    private boolean isTarget(String pkg) {
        for (String t : TARGETS) if (pkg.startsWith(t)) return true;
        return false;
    }

    private String foregroundPackage() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return null;
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - 6000, now);
        if (events == null) return null;
        UsageEvents.Event e = new UsageEvents.Event();
        String last = null;
        long lastTs = 0;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND && e.getTimeStamp() >= lastTs) {
                lastTs = e.getTimeStamp();
                last = e.getPackageName();
            }
        }
        return last != null ? last : lastForeground;
    }

    private Config loadConfig() {
        SharedPreferences p = getSharedPreferences("slimvpn", MODE_PRIVATE);
        String path = p.getString("profile_path", null);
        File f = path != null ? new File(path) : null;
        if (f == null || !f.exists()) {
            File dl = new File("/sdcard/Download");
            File[] files = dl.listFiles();
            if (files != null) {
                for (File c : files) {
                    if (c.isFile() && c.getName().toLowerCase().endsWith(".conf")) { f = c; break; }
                }
            }
        }
        if (f == null || !f.exists()) return null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
            return Config.parse(r);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}