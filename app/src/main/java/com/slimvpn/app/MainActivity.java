package com.slimvpn.app;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.amnezia.awg.config.Config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private TextView tvStatus, tvProfile, btnConnect, btnImport, btnAuto;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private Config config;
    private String profileName = "";
    private boolean connected = false;

    private static final int VPN_REQUEST = 1001;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvProfile = findViewById(R.id.tvProfile);
        btnConnect = findViewById(R.id.btnConnect);
        btnImport = findViewById(R.id.btnImport);
        btnAuto = findViewById(R.id.btnAuto);

        try { VpnController.backend(this); }
        catch (Throwable t) { tvStatus.setText("Движок AmneziaWG недоступен: " + t.getClass().getSimpleName()); }

        // включаем сторожа: VPN сам поднимется при запуске YouTube
        try {
            startForegroundService(new Intent(this, AutoVpnService.class));
        } catch (Throwable ignored) { }

        // сохраняем путь к профилю для сторожа
        autoLoadFromDownload();
        updateUi();
        startWatcher();

        autoLoadFromDownload();
        updateUi();

        btnConnect.setOnClickListener(v -> toggle());
        btnImport.setOnClickListener(v -> { autoLoadFromDownload(); toast("Профиль перечитан из Download"); });
        if (btnAuto != null) {
            updateAutoButton();
            btnAuto.setOnClickListener(v -> {
                boolean on = getSharedPreferences("slimvpn", MODE_PRIVATE).getBoolean("auto_youtube", true);
                getSharedPreferences("slimvpn", MODE_PRIVATE).edit().putBoolean("auto_youtube", !on).apply();
                updateAutoButton();
                toast(!on ? "Автоподключение включено" : "Автоподключение выключено");
            });
        }
    }

    // ---------- профили ----------
    private File downloadDir() {
        return new File("/sdcard/Download");
    }

    private List<File> confFiles() {
        List<File> out = new ArrayList<>();
        File[] files = downloadDir().listFiles();
        if (files == null) return out;
        for (File f : files) {
            String n = f.getName().toLowerCase();
            if (f.isFile() && (n.endsWith(".conf") || n.endsWith(".config"))) out.add(f);
        }
        return out;
    }

    private void autoLoadFromDownload() {
        List<File> confs = confFiles();
        if (confs.isEmpty()) {
            tvStatus.setText("Профиль не найден: положите .conf в Download");
            return;
        }
        loadConfig(confs.get(0));
    }

    private void loadConfig(File file) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new FileInputStream(file)))) {
            config = Config.parse(r);
            profileName = file.getName();
            tvProfile.setText("Профиль: " + profileName);
            getSharedPreferences("slimvpn", MODE_PRIVATE).edit()
                    .putString("profile_path", file.getAbsolutePath()).apply();
            tvStatus.setText("Готов к подключению");
            updateUi();
        } catch (Throwable t) {
            tvStatus.setText("Не удалось прочитать профиль: " + t.getMessage());
        }
    }

    private void startWatcher() {
        try {
            Intent i = new Intent(this, AutoVpnService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                startForegroundService(i);
            else startService(i);
        } catch (Throwable ignored) { }
    }

    // ---------- подключение ----------
    private void toggle() {
        if (config == null) { toast("Сначала импортируйте .conf"); return; }
        if (connected) { disconnect(); return; }
        Intent prep = VpnService.prepare(this);
        if (prep != null) { startActivityForResult(prep, VPN_REQUEST); }
        else { connect(); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == VPN_REQUEST) {
            if (res == RESULT_OK) connect();
            else toast("Разрешение VPN не выдано");
        }
    }

    private void connect() {
        if (config == null) { toast("Сначала импортируйте .conf"); return; }
        tvStatus.setText("Подключение…");
        VpnController.setOwnedByWatcher(false);
        VpnController.connect(this, config, (ok, err) -> ui.post(() -> {
            connected = ok;
            if (!ok) tvStatus.setText("Ошибка подключения: " + err);
            updateUi();
        }));
    }

    private void disconnect() {
        VpnController.disconnect((ok, err) -> ui.post(() -> { connected = false; updateUi(); }));
    }

    private void updateUi() {
        btnConnect.setText(connected ? "Отключить" : "Подключить");
        btnConnect.setTextColor(connected ? 0xFFFF6B6B : 0xFF39D98A);
        tvStatus.setText(connected ? "VPN включён" : (config == null ? "Профиль не выбран" : "Готов к подключению"));
    }

    private void updateAutoButton() {
        if (btnAuto == null) return;
        boolean on = getSharedPreferences("slimvpn", MODE_PRIVATE).getBoolean("auto_youtube", true);
        btnAuto.setText("Автоподключение для YouTube: " + (on ? "включено" : "выключено"));
        btnAuto.setTextColor(on ? 0xFF39D98A : 0xFFFF6B6B);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override protected void onDestroy() {
        super.onDestroy();
    }
}