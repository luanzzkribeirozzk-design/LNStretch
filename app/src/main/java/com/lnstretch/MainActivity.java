package com.lnstretch;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.SeekBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.lnstretch.databinding.ActivityMainBinding;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS      = "LNStretchPrefs";
    private static final String KEY_PCT    = "stretch_percent";
    private static final String FF_PKG     = "com.dts.freefireth";
    private static final String FF_MAX     = "com.dts.freefiremax";
    private static final int    SHIZUKU_REQ = 1001;

    private ActivityMainBinding b;
    private SharedPreferences   prefs;
    private int     pct       = 20;
    private boolean stretched = false;
    private int origW, origH, origDpi;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs  = getSharedPreferences(PREFS, MODE_PRIVATE);
        pct    = prefs.getInt(KEY_PCT, 20);
        getMetrics();
        setupUI();
        checkShizuku();
    }

    // ── pega métricas reais da tela ──────────────────────────────
    private void getMetrics() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        origW   = dm.widthPixels;
        origH   = dm.heightPixels;
        origDpi = (int) dm.densityDpi;
    }

    // ── configura todos os controles da UI ───────────────────────
    private void setupUI() {
        b.seekBarStretch.setMax(30);
        b.seekBarStretch.setProgress(pct - 10);
        b.tvStretchValue.setText(pct + "%");

        b.seekBarStretch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sk, int p, boolean f) {
                pct = p + 10;
                b.tvStretchValue.setText(pct + "%");
                prefs.edit().putInt(KEY_PCT, pct).apply();
            }
            public void onStartTrackingTouch(SeekBar sk) {}
            public void onStopTrackingTouch(SeekBar sk) {}
        });

        b.btnStretch.setOnClickListener(v   -> { if (checkOk()) stretch(); });
        b.btnUnstretch.setOnClickListener(v -> { if (checkOk()) unstretch(); });
        b.btnOptimize.setOnClickListener(v  -> { if (checkOk()) optimize(); });
        b.btnOpenFF.setOnClickListener(v    -> openFF());
        updateStatus();
    }

    // ── verifica estado do Shizuku ───────────────────────────────
    private void checkShizuku() {
        boolean ok = Shizuku.pingBinder();
        b.tvShizukuStatus.setText(ok ? "✅ Shizuku: Ativo" : "❌ Shizuku: Inativo — Abra o app Shizuku");
        b.tvShizukuStatus.setTextColor(ContextCompat.getColor(this,
                ok ? R.color.green_active : R.color.red_inactive));
    }

    private boolean checkOk() {
        if (!Shizuku.pingBinder()) {
            toast("⚠️ Shizuku não está ativo! Abra o app Shizuku.");
            return false;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_REQ);
            return false;
        }
        return true;
    }

    // ── ESTICAR ──────────────────────────────────────────────────
    private void stretch() {
        new Thread(() -> {
            try {
                float factor = 1f + pct / 100f;
                int newW = (int)(origW * factor);
                int newDpi = Math.max((int)(origDpi / factor), 280);

                // Usa Shizuku.newProcess() para rodar como shell privilegiado
                runShizuku("wm size " + newW + "x" + origH);
                runShizuku("wm density " + newDpi);

                stretched = true;
                runOnUiThread(() -> { updateStatus(); toast("✅ Tela esticada " + pct + "%!"); });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Erro ao esticar: " + e.getMessage()));
            }
        }).start();
    }

    // ── DESISTICAR ───────────────────────────────────────────────
    private void unstretch() {
        new Thread(() -> {
            try {
                runShizuku("wm size reset");
                runShizuku("wm density reset");

                stretched = false;
                runOnUiThread(() -> { updateStatus(); toast("✅ Tela restaurada!"); });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Erro ao restaurar: " + e.getMessage()));
            }
        }).start();
    }

    // ── OTIMIZAR FF ──────────────────────────────────────────────
    private void optimize() {
        new Thread(() -> {
            try {
                String pkg = ffPkg();
                if (pkg == null) { runOnUiThread(() -> toast("❌ Free Fire não encontrado.")); return; }

                runShizuku("pm clear " + pkg);
                runShizuku("settings put global window_animation_scale 0.5");
                runShizuku("settings put global transition_animation_scale 0.5");
                runShizuku("settings put global animator_duration_scale 0.5");

                runOnUiThread(() -> toast("✅ Free Fire otimizado!"));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Erro: " + e.getMessage()));
            }
        }).start();
    }

    // ── ABRIR FF ─────────────────────────────────────────────────
    private void openFF() {
        String pkg = ffPkg();
        if (pkg == null) { toast("❌ Free Fire não instalado."); return; }
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
        else toast("❌ Não foi possível abrir o Free Fire.");
    }

    // ── EXECUTOR VIA SHIZUKU ─────────────────────────────────────
    // Usa Shizuku.newProcess() — roda cada comando como root/shell
    private void runShizuku(String command) throws Exception {
        // split em array para evitar problemas com espaços
        String[] cmdArray = new String[]{"sh", "-c", command};
        Process process = Shizuku.newProcess(cmdArray, null, null);
        int exit = process.waitFor();

        // lê stderr para debug (ignorado silenciosamente em produção)
        InputStream err = process.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(err));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        process.destroy();

        if (exit != 0 && sb.length() > 0) {
            throw new Exception("cmd falhou [" + exit + "]: " + sb.toString());
        }
    }

    // ── HELPERS ──────────────────────────────────────────────────
    private String ffPkg() {
        PackageManager pm = getPackageManager();
        try { pm.getPackageInfo(FF_PKG, 0); return FF_PKG; } catch (Exception ignored) {}
        try { pm.getPackageInfo(FF_MAX, 0); return FF_MAX; } catch (Exception ignored) {}
        return null;
    }

    private void updateStatus() {
        if (stretched) {
            b.tvStretchStatus.setText("🎯 Tela ESTICADA (" + pct + "%)");
            b.tvStretchStatus.setTextColor(ContextCompat.getColor(this, R.color.orange_stretched));
            b.btnStretch.setEnabled(false);
            b.btnUnstretch.setEnabled(true);
        } else {
            b.tvStretchStatus.setText("📱 Tela Normal");
            b.tvStretchStatus.setTextColor(ContextCompat.getColor(this, R.color.green_active));
            b.btnStretch.setEnabled(true);
            b.btnUnstretch.setEnabled(false);
        }
    }

    private void toast(String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkShizuku();
    }
}