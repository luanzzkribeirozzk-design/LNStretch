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
import java.io.DataOutputStream;
import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS = "LNStretchPrefs";
    private static final String KEY_PCT = "stretch_percent";
    private static final String FF_PKG  = "com.dts.freefireth";
    private static final String FF_MAX  = "com.dts.freefiremax";
    private static final int SHIZUKU_REQ = 1001;

    private ActivityMainBinding b;
    private SharedPreferences prefs;
    private int pct = 20;
    private boolean stretched = false;
    private int origW, origH, origDpi;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        pct = prefs.getInt(KEY_PCT, 20);
        getMetrics();
        setupUI();
        checkShizuku();
    }

    private void getMetrics() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        origW = dm.widthPixels; origH = dm.heightPixels; origDpi = (int) dm.densityDpi;
    }

    private void setupUI() {
        b.seekBarStretch.setMax(30);
        b.seekBarStretch.setProgress(pct - 10);
        b.tvStretchValue.setText(pct + "%");
        b.seekBarStretch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean f) {
                pct = p + 10;
                b.tvStretchValue.setText(pct + "%");
                prefs.edit().putInt(KEY_PCT, pct).apply();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        b.btnStretch.setOnClickListener(v -> { if (checkOk()) stretch(); });
        b.btnUnstretch.setOnClickListener(v -> { if (checkOk()) unstretch(); });
        b.btnOptimize.setOnClickListener(v -> { if (checkOk()) optimize(); });
        b.btnOpenFF.setOnClickListener(v -> openFF());
        updateStatus();
    }

    private void checkShizuku() {
        boolean ok = Shizuku.pingBinder();
        b.tvShizukuStatus.setText(ok ? "✅ Shizuku: Ativo" : "❌ Shizuku: Inativo");
        b.tvShizukuStatus.setTextColor(ContextCompat.getColor(this,
            ok ? R.color.green_active : R.color.red_inactive));
    }

    private boolean checkOk() {
        if (!Shizuku.pingBinder()) { toast("⚠️ Ative o Shizuku primeiro!"); return false; }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_REQ); return false;
        }
        return true;
    }

    private void stretch() {
        try {
            float f = 1f + pct / 100f;
            int nw = (int)(origW * f);
            int nd = Math.max((int)(origDpi / f), 280);
            shell("wm size " + nw + "x" + origH + "\nwm density " + nd + "\n");
            stretched = true; updateStatus(); toast("✅ Esticado " + pct + "%!");
        } catch (Exception e) { toast("Erro: " + e.getMessage()); }
    }

    private void unstretch() {
        try {
            shell("wm size reset\nwm density reset\n");
            stretched = false; updateStatus(); toast("✅ Tela restaurada!");
        } catch (Exception e) { toast("Erro: " + e.getMessage()); }
    }

    private void optimize() {
        String pkg = ffPkg();
        if (pkg == null) { toast("❌ Free Fire não encontrado."); return; }
        try {
            shell("pm clear " + pkg
                + "\nsettings put global window_animation_scale 0.5"
                + "\nsettings put global transition_animation_scale 0.5"
                + "\nsettings put global animator_duration_scale 0.5\n");
            toast("✅ FF otimizado!");
        } catch (Exception e) { toast("Erro: " + e.getMessage()); }
    }

    private void openFF() {
        String pkg = ffPkg();
        if (pkg == null) { toast("❌ Free Fire não instalado."); return; }
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
    }

    private String ffPkg() {
        PackageManager pm = getPackageManager();
        try { pm.getPackageInfo(FF_PKG, 0); return FF_PKG; } catch (Exception ignored) {}
        try { pm.getPackageInfo(FF_MAX, 0); return FF_MAX; } catch (Exception ignored) {}
        return null;
    }

    private void shell(String cmd) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"sh"});
        DataOutputStream os = new DataOutputStream(p.getOutputStream());
        os.writeBytes(cmd); os.writeBytes("exit\n"); os.flush(); p.waitFor(); os.close();
    }

    private void updateStatus() {
        if (stretched) {
            b.tvStretchStatus.setText("🎯 Esticada (" + pct + "%)");
            b.tvStretchStatus.setTextColor(ContextCompat.getColor(this, R.color.orange_stretched));
            b.btnStretch.setEnabled(false); b.btnUnstretch.setEnabled(true);
        } else {
            b.tvStretchStatus.setText("📱 Tela Normal");
            b.tvStretchStatus.setTextColor(ContextCompat.getColor(this, R.color.green_active));
            b.btnStretch.setEnabled(true); b.btnUnstretch.setEnabled(false);
        }
    }

    private void toast(String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override protected void onResume() { super.onResume(); checkShizuku(); }
}