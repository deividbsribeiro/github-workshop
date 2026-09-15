package br.com.deivid.cftvlocal;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;

import androidx.media3.common.util.UnstableApi;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class HybridMainActivity extends Activity {
    private static final int BG = Color.rgb(10, 12, 14);
    private static final int TEXT = Color.rgb(245, 245, 245);
    private static final int OK = Color.rgb(80, 220, 130);
    private static final int OFFLINE = Color.rgb(240, 70, 70);
    private static final int MUTED = Color.rgb(170, 178, 186);
    private static final long RECONNECT_MS = 10_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final HybridCameraTile[] tiles = new HybridCameraTile[4];
    private final String[] urls = new String[4];
    private GridLayout grid;
    private int maximized = -1;
    private int generation;
    private boolean started;
    private Runnable reconnectTask;
    private AccessUtil.Mode mode = AccessUtil.Mode.CHECKING;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersive();
        importConfig(getIntent());
        loadConfig();
        buildUi();
        if (!hasLocalConfig()) handler.postDelayed(this::showSettings, 250);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (importConfig(intent)) {
            loadConfig();
            for (int i = 0; i < 4; i++) if (tiles[i] != null) tiles[i].setBaseUrl(urls[i]);
            restart();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        if (hasLocalConfig()) resolveAndStart();
    }

    @Override protected void onStop() {
        started = false;
        cancelReconnect();
        generation++;
        stopAll();
        super.onStop();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        enterImmersive();
    }

    @Override public void onBackPressed() {
        if (maximized != -1) restoreGrid(); else super.onBackPressed();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        grid = new GridLayout(this);
        grid.setRowCount(2);
        grid.setColumnCount(2);
        root.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        for (int i = 0; i < 4; i++) {
            final int index = i;
            tiles[i] = new HybridCameraTile(this, handler, i, urls[i], this::scheduleReconnect);
            tiles[i].setOnCameraClickListener(() -> maximize(index));
            addTile(i);
        }

        Button settings = new Button(this);
        settings.setText("⚙");
        settings.setTextSize(20);
        settings.setTextColor(TEXT);
        settings.setBackgroundColor(Color.argb(185, 22, 25, 29));
        settings.setOnClickListener(v -> showSettings());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(52), dp(48));
        lp.gravity = Gravity.TOP | Gravity.RIGHT;
        lp.setMargins(0, dp(8), dp(8), 0);
        root.addView(settings, lp);
        setContentView(root);
    }

    private void addTile(int i) {
        HybridCameraTile tile = tiles[i];
        if (tile.getParent() != null) ((ViewGroup) tile.getParent()).removeView(tile);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(i / 2, 1, 1f), GridLayout.spec(i % 2, 1, 1f));
        lp.width = 0;
        lp.height = 0;
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        grid.addView(tile, lp);
        tile.setVisibility(View.VISIBLE);
    }

    private void maximize(int index) {
        if (maximized == index) return;
        maximized = index;
        for (int i = 0; i < 4; i++) if (i != index) tiles[i].setVisibility(View.GONE);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(0, 2, 1f), GridLayout.spec(0, 2, 1f));
        lp.width = 0;
        lp.height = 0;
        lp.setMargins(0, 0, 0, 0);
        tiles[index].setLayoutParams(lp);
        tiles[index].setVisibility(View.VISIBLE);
        grid.requestLayout();
    }

    private void restoreGrid() {
        maximized = -1;
        grid.removeAllViews();
        for (int i = 0; i < 4; i++) addTile(i);
    }

    private void resolveAndStart() {
        if (!started || !hasLocalConfig()) return;
        cancelReconnect();
        stopAll();
        mode = AccessUtil.Mode.CHECKING;
        setAll("CONECTANDO", MUTED);
        int token = ++generation;
        String url = urls[0];
        worker.execute(() -> {
            boolean local = AccessUtil.probeRtsp(url, 900);
            handler.post(() -> {
                if (!started || token != generation) return;
                if (local) {
                    mode = AccessUtil.Mode.LOCAL;
                    startTiles();
                } else {
                    startP2p(token);
                }
            });
        });
    }

    private void startP2p(int token) {
        if (!RemoteConfig.isComplete(this)) {
            mode = AccessUtil.Mode.UNAVAILABLE;
            setAll("P2P PENDENTE", MUTED);
            return;
        }
        String[] credentials = AccessUtil.credentials(urls[0]);
        if (credentials == null) {
            mode = AccessUtil.Mode.UNAVAILABLE;
            setAll("SEM CREDENCIAL", OFFLINE);
            return;
        }
        setAll("CONECTANDO P2P", MUTED);
        try {
            FunSdkBridge.login(this, credentials[0], credentials[1], new FunSdkBridge.LoginCallback() {
                @Override public void onSuccess() {
                    handler.post(() -> {
                        if (!started || token != generation) return;
                        mode = AccessUtil.Mode.P2P;
                        startTiles();
                    });
                }
                @Override public void onFailure(int errorId) {
                    handler.post(() -> {
                        if (!started || token != generation) return;
                        mode = AccessUtil.Mode.UNAVAILABLE;
                        setAll("P2P ERRO " + errorId, OFFLINE);
                        scheduleReconnect();
                    });
                }
            });
        } catch (Throwable e) {
            mode = AccessUtil.Mode.UNAVAILABLE;
            setAll("P2P INDISPONÍVEL", OFFLINE);
            scheduleReconnect();
        }
    }

    private void startTiles() {
        for (HybridCameraTile tile : tiles) if (tile != null) tile.start(mode);
    }

    private void stopAll() {
        for (HybridCameraTile tile : tiles) if (tile != null) tile.release();
    }

    private void restart() {
        cancelReconnect();
        generation++;
        stopAll();
        mode = AccessUtil.Mode.CHECKING;
        if (started && hasLocalConfig()) resolveAndStart();
    }

    private void scheduleReconnect() {
        if (!started || reconnectTask != null) return;
        reconnectTask = () -> {
            reconnectTask = null;
            if (started) resolveAndStart();
        };
        handler.postDelayed(reconnectTask, RECONNECT_MS);
    }

    private void cancelReconnect() {
        if (reconnectTask != null) {
            handler.removeCallbacks(reconnectTask);
            reconnectTask = null;
        }
    }

    private void setAll(String text, int color) {
        for (HybridCameraTile tile : tiles) if (tile != null) tile.setStatus(text, color);
    }

    private void showSettings() {
        ConfigDialog.show(this, urls, values -> {
            for (int i = 0; i < 4; i++) {
                urls[i] = values[i];
                SecurePrefs.put(this, "cam" + (i + 1), urls[i]);
                tiles[i].setBaseUrl(urls[i]);
            }
            restart();
        });
    }

    private void loadConfig() {
        for (int i = 0; i < 4; i++) urls[i] = SecurePrefs.get(this, "cam" + (i + 1));
    }

    private boolean hasLocalConfig() {
        for (String url : urls) {
            if (url == null || !url.toLowerCase(Locale.ROOT).startsWith("rtsp://")) return false;
        }
        return true;
    }

    private boolean importConfig(Intent intent) {
        if (intent == null || !intent.getBooleanExtra("saveConfig", false)) return false;
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            String value = decode(intent, "cam" + (i + 1) + "b64");
            if (value != null && value.startsWith("rtsp://")) {
                SecurePrefs.put(this, "cam" + (i + 1), value);
                changed = true;
            }
        }
        changed |= importRemote(intent, "remoteSnb64", RemoteConfig.KEY_SN);
        changed |= importRemote(intent, "funUuidb64", RemoteConfig.KEY_UUID);
        changed |= importRemote(intent, "funKeyb64", RemoteConfig.KEY_APP_KEY);
        changed |= importRemote(intent, "funSecretb64", RemoteConfig.KEY_APP_SECRET);
        changed |= importRemote(intent, "funMovedCardb64", RemoteConfig.KEY_MOVED_CARD);
        return changed;
    }

    private boolean importRemote(Intent intent, String extra, String key) {
        String value = decode(intent, extra);
        if (value == null) return false;
        RemoteConfig.put(this, key, value);
        return true;
    }

    private String decode(Intent intent, String key) {
        String value = intent.getStringExtra(key);
        if (value == null || value.isEmpty()) return null;
        try {
            return new String(Base64.decode(value, Base64.DEFAULT), StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
