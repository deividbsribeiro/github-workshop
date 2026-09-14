package br.com.deivid.cftvlocal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@UnstableApi
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 12, 14);
    private static final int PANEL = Color.rgb(22, 25, 29);
    private static final int OK = Color.rgb(80, 220, 130);
    private static final int OFFLINE = Color.rgb(240, 70, 70);
    private static final int TEXT = Color.rgb(245, 245, 245);
    private static final int MUTED = Color.rgb(170, 178, 186);
    private static final long RECONNECT_MS = 10_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CameraTile[] tiles = new CameraTile[4];
    private final String[] urls = new String[4];
    private GridLayout grid;
    private Button settingsButton;
    private int maximized = -1;
    private boolean started = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersive();
        importConfigFromIntent(getIntent());
        loadConfig();
        buildUi();

        if (!hasCompleteConfig()) {
            handler.postDelayed(this::showSettings, 250);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (importConfigFromIntent(intent)) {
            loadConfig();
            for (int i = 0; i < 4; i++) {
                if (tiles[i] != null) tiles[i].setBaseUrl(urls[i]);
            }
            restartAll();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        if (hasCompleteConfig()) startAll();
    }

    @Override
    protected void onStop() {
        started = false;
        stopAll();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersive();
    }

    @Override
    public void onBackPressed() {
        if (maximized != -1) {
            restoreGrid();
        } else {
            super.onBackPressed();
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

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        grid = new GridLayout(this);
        grid.setRowCount(2);
        grid.setColumnCount(2);
        grid.setBackgroundColor(BG);
        root.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        for (int i = 0; i < 4; i++) {
            final int index = i;
            tiles[i] = new CameraTile(this, i, urls[i]);
            tiles[i].setOnCameraClickListener(() -> maximize(index));
            addTileToGrid(i);
        }

        settingsButton = new Button(this);
        settingsButton.setText("⚙");
        settingsButton.setTextSize(20);
        settingsButton.setTextColor(TEXT);
        settingsButton.setBackgroundColor(Color.argb(185, 22, 25, 29));
        settingsButton.setOnClickListener(v -> showSettings());
        FrameLayout.LayoutParams gearLp = new FrameLayout.LayoutParams(dp(52), dp(48));
        gearLp.gravity = Gravity.TOP | Gravity.RIGHT;
        gearLp.setMargins(0, dp(8), dp(8), 0);
        root.addView(settingsButton, gearLp);

        setContentView(root);
    }

    private void addTileToGrid(int i) {
        CameraTile tile = tiles[i];
        if (tile.getParent() != null) ((ViewGroup) tile.getParent()).removeView(tile);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(i / 2, 1, 1f),
                GridLayout.spec(i % 2, 1, 1f));
        lp.width = 0;
        lp.height = 0;
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        grid.addView(tile, lp);
        tile.setVisibility(View.VISIBLE);
    }

    private void maximize(int index) {
        if (maximized == index) return;
        maximized = index;
        for (int i = 0; i < 4; i++) {
            if (i != index) tiles[i].setVisibility(View.GONE);
        }
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(0, 2, 1f),
                GridLayout.spec(0, 2, 1f));
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
        for (int i = 0; i < 4; i++) addTileToGrid(i);
    }

    private void startAll() {
        if (!started) return;
        for (CameraTile tile : tiles) if (tile != null) tile.startVideo();
    }

    private void stopAll() {
        for (CameraTile tile : tiles) if (tile != null) tile.release();
    }

    private void restartAll() {
        stopAll();
        if (started && hasCompleteConfig()) startAll();
    }

    private boolean isOnWifi() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception e) {
            return false;
        }
    }

    private void loadConfig() {
        for (int i = 0; i < 4; i++) urls[i] = SecurePrefs.get(this, "cam" + (i + 1));
    }

    private boolean hasCompleteConfig() {
        for (String url : urls) {
            if (url == null || !url.toLowerCase(Locale.ROOT).startsWith("rtsp://")) return false;
        }
        return true;
    }

    private boolean importConfigFromIntent(Intent intent) {
        if (intent == null || !intent.getBooleanExtra("saveConfig", false)) return false;
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            String key = "cam" + (i + 1) + "b64";
            String encoded = intent.getStringExtra(key);
            if (encoded == null || encoded.isEmpty()) continue;
            try {
                String decoded = new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8);
                if (decoded.startsWith("rtsp://")) {
                    SecurePrefs.put(this, "cam" + (i + 1), decoded);
                    changed = true;
                }
            } catch (Exception ignored) {
            }
        }
        return changed;
    }

    private void showSettings() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        form.setPadding(p, p, p, p);
        scroll.addView(form);

        TextView note = new TextView(this);
        note.setText("URLs RTSP das 4 câmeras. As credenciais ficam criptografadas no tablet.");
        note.setTextColor(Color.DKGRAY);
        note.setTextSize(14);
        form.addView(note);

        EditText[] fields = new EditText[4];
        for (int i = 0; i < 4; i++) {
            TextView label = new TextView(this);
            label.setText("Câmera " + (i + 1));
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setPadding(0, dp(14), 0, dp(4));
            form.addView(label);

            fields[i] = new EditText(this);
            fields[i].setSingleLine(true);
            fields[i].setText(urls[i] == null ? "" : urls[i]);
            fields[i].setHint("rtsp://usuario:senha@192.168.x.x:554/...");
            form.addView(fields[i], new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Configuração local")
                .setView(scroll)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            boolean valid = true;
            for (int i = 0; i < 4; i++) {
                String value = fields[i].getText().toString().trim();
                if (!value.toLowerCase(Locale.ROOT).startsWith("rtsp://")) {
                    fields[i].setError("Informe uma URL RTSP válida");
                    valid = false;
                }
            }
            if (!valid) return;

            for (int i = 0; i < 4; i++) {
                urls[i] = fields[i].getText().toString().trim();
                SecurePrefs.put(this, "cam" + (i + 1), urls[i]);
                tiles[i].setBaseUrl(urls[i]);
            }
            dialog.dismiss();
            restartAll();
        }));
        dialog.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class CameraTile extends FrameLayout {
        private final int index;
        private String baseUrl;
        private PlayerView playerView;
        private ExoPlayer videoPlayer;
        private ExoPlayer audioPlayer;
        private TextView status;
        private Button hdButton;
        private Button audioButton;
        private boolean hd = false;
        private boolean audio = false;
        private Runnable reconnectTask;
        private Runnable clickListener;

        CameraTile(Context context, int index, String baseUrl) {
            super(context);
            this.index = index;
            this.baseUrl = baseUrl;
            setBackgroundColor(BG);
            build();
        }

        void setOnCameraClickListener(Runnable listener) {
            this.clickListener = listener;
        }

        void setBaseUrl(String url) {
            this.baseUrl = url;
        }

        private void build() {
            playerView = new PlayerView(MainActivity.this);
            playerView.setUseController(false);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            playerView.setShutterBackgroundColor(BG);
            addView(playerView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            playerView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.run();
            });

            TextView name = new TextView(MainActivity.this);
            name.setText("Câmera " + (index + 1));
            name.setTextColor(TEXT);
            name.setTextSize(16);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setBackgroundColor(Color.argb(145, 10, 12, 14));
            name.setPadding(dp(10), dp(6), dp(10), dp(6));
            FrameLayout.LayoutParams nameLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            nameLp.gravity = Gravity.TOP | Gravity.LEFT;
            nameLp.setMargins(dp(10), dp(10), 0, 0);
            addView(name, nameLp);

            LinearLayout controls = new LinearLayout(MainActivity.this);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            controls.setGravity(Gravity.CENTER_VERTICAL);

            status = new TextView(MainActivity.this);
            status.setText("CONECTANDO");
            status.setTextColor(MUTED);
            status.setTextSize(12);
            status.setTypeface(Typeface.DEFAULT_BOLD);
            status.setPadding(dp(8), 0, dp(8), 0);
            controls.addView(status);

            hdButton = makeButton("HD");
            hdButton.setOnClickListener(v -> {
                hd = !hd;
                refreshButtons();
                startVideo();
            });
            controls.addView(hdButton);

            audioButton = makeButton("ÁUDIO OFF");
            audioButton.setOnClickListener(v -> toggleAudio());
            controls.addView(audioButton);

            FrameLayout.LayoutParams controlsLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(48));
            controlsLp.gravity = Gravity.RIGHT | Gravity.BOTTOM;
            controlsLp.setMargins(0, 0, dp(10), dp(10));
            addView(controls, controlsLp);
            refreshButtons();
        }

        private Button makeButton(String label) {
            Button b = new Button(MainActivity.this);
            b.setText(label);
            b.setTextSize(12);
            b.setTextColor(TEXT);
            b.setAllCaps(false);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setPadding(dp(12), 0, dp(12), 0);
            b.setBackgroundColor(PANEL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(42));
            lp.setMargins(dp(4), 0, 0, 0);
            b.setLayoutParams(lp);
            return b;
        }

        private void refreshButtons() {
            hdButton.setText(hd ? "HD ON" : "HD");
            hdButton.setTextColor(hd ? OK : TEXT);
            audioButton.setText(audio ? "ÁUDIO ON" : "ÁUDIO OFF");
            audioButton.setTextColor(audio ? OK : TEXT);
        }

        private String videoUrl() {
            if (baseUrl == null) return null;
            if (hd) return baseUrl.replace("subtype=1", "subtype=0");
            return baseUrl.replace("subtype=0", "subtype=1");
        }

        private String audioUrl() {
            if (baseUrl == null) return null;
            return baseUrl.replace("subtype=1", "subtype=0");
        }

        void startVideo() {
            cancelReconnect();
            releaseVideo();

            if (!started) return;
            if (!isOnWifi()) {
                setStatus("FORA DA REDE", OFFLINE);
                scheduleReconnect();
                return;
            }

            String url = videoUrl();
            if (url == null || !url.startsWith("rtsp://")) {
                setStatus("SEM CONFIG", OFFLINE);
                return;
            }

            setStatus("CONECTANDO", MUTED);
            try {
                videoPlayer = new ExoPlayer.Builder(MainActivity.this).build();
                videoPlayer.setVolume(0f);
                playerView.setPlayer(videoPlayer);

                videoPlayer.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_READY) setStatus("ONLINE", OK);
                        else if (state == Player.STATE_BUFFERING) setStatus("CONECTANDO", MUTED);
                        else if (state == Player.STATE_ENDED) {
                            setStatus("OFFLINE", OFFLINE);
                            scheduleReconnect();
                        }
                    }

                    @Override
                    public void onPlayerError(PlaybackException error) {
                        setStatus("OFFLINE", OFFLINE);
                        scheduleReconnect();
                    }
                });

                MediaSource source = new RtspMediaSource.Factory()
                        .setForceUseRtpTcp(true)
                        .setTimeoutMs(5_000)
                        .createMediaSource(MediaItem.fromUri(url));
                videoPlayer.setMediaSource(source);
                videoPlayer.prepare();
                videoPlayer.play();
            } catch (Exception e) {
                setStatus("OFFLINE", OFFLINE);
                scheduleReconnect();
            }
        }

        private void toggleAudio() {
            if (audio) {
                releaseAudio();
                audio = false;
                refreshButtons();
                return;
            }

            if (!isOnWifi()) return;
            String url = audioUrl();
            if (url == null || !url.startsWith("rtsp://")) return;

            try {
                audioPlayer = new ExoPlayer.Builder(MainActivity.this).build();
                TrackSelectionParameters params = audioPlayer.getTrackSelectionParameters()
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                        .build();
                audioPlayer.setTrackSelectionParameters(params);
                audioPlayer.setVolume(1f);
                audioPlayer.addListener(new Player.Listener() {
                    @Override
                    public void onPlayerError(PlaybackException error) {
                        releaseAudio();
                        audio = false;
                        refreshButtons();
                    }
                });
                MediaSource source = new RtspMediaSource.Factory()
                        .setForceUseRtpTcp(true)
                        .setTimeoutMs(5_000)
                        .createMediaSource(MediaItem.fromUri(url));
                audioPlayer.setMediaSource(source);
                audioPlayer.prepare();
                audioPlayer.play();
                audio = true;
                refreshButtons();
            } catch (Exception e) {
                releaseAudio();
                audio = false;
                refreshButtons();
            }
        }

        private void setStatus(String text, int color) {
            status.setText(text);
            status.setTextColor(color);
        }

        private void scheduleReconnect() {
            cancelReconnect();
            reconnectTask = () -> {
                if (started) startVideo();
            };
            handler.postDelayed(reconnectTask, RECONNECT_MS);
        }

        private void cancelReconnect() {
            if (reconnectTask != null) {
                handler.removeCallbacks(reconnectTask);
                reconnectTask = null;
            }
        }

        private void releaseVideo() {
            if (videoPlayer != null) {
                try { videoPlayer.release(); } catch (Exception ignored) {}
                videoPlayer = null;
            }
            playerView.setPlayer(null);
        }

        private void releaseAudio() {
            if (audioPlayer != null) {
                try { audioPlayer.release(); } catch (Exception ignored) {}
                audioPlayer = null;
            }
        }

        void release() {
            cancelReconnect();
            releaseVideo();
            releaseAudio();
            audio = false;
            refreshButtons();
        }
    }
}
