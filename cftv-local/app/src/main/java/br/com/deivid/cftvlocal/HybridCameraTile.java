package br.com.deivid.cftvlocal;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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

import com.lib.MsgContent;
import com.lib.SDKCONST;
import com.manager.device.DeviceManager;
import com.manager.device.media.MediaManager;
import com.manager.device.media.attribute.PlayerAttribute;
import com.manager.device.media.monitor.MonitorManager;

import java.util.Locale;

@UnstableApi
final class HybridCameraTile extends FrameLayout {
    private static final int BG = Color.rgb(10, 12, 14);
    private static final int PANEL = Color.rgb(22, 25, 29);
    private static final int OK = Color.rgb(80, 220, 130);
    private static final int OFFLINE = Color.rgb(240, 70, 70);
    private static final int TEXT = Color.rgb(245, 245, 245);
    private static final int MUTED = Color.rgb(170, 178, 186);

    private final Activity activity;
    private final Handler handler;
    private final int index;
    private final Runnable reconnectCallback;
    private FrameLayout videoHost;
    private TextView status;
    private Button hdButton;
    private Button audioButton;
    private PlayerView playerView;
    private ExoPlayer videoPlayer;
    private ExoPlayer audioPlayer;
    private MonitorManager p2pPlayer;
    private AccessUtil.Mode mode = AccessUtil.Mode.CHECKING;
    private String baseUrl;
    private boolean hd;
    private boolean audio;
    private Runnable clickListener;

    HybridCameraTile(Activity activity, Handler handler, int index, String baseUrl, Runnable reconnectCallback) {
        super(activity);
        this.activity = activity;
        this.handler = handler;
        this.index = index;
        this.baseUrl = baseUrl;
        this.reconnectCallback = reconnectCallback;
        setBackgroundColor(BG);
        build();
    }

    void setBaseUrl(String url) { baseUrl = url; }
    void setOnCameraClickListener(Runnable listener) { clickListener = listener; }

    private void build() {
        videoHost = new FrameLayout(activity);
        videoHost.setBackgroundColor(BG);
        addView(videoHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View tap = new View(activity);
        tap.setBackgroundColor(Color.TRANSPARENT);
        tap.setOnClickListener(v -> { if (clickListener != null) clickListener.run(); });
        addView(tap, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView name = new TextView(activity);
        name.setText("Câmera " + (index + 1));
        name.setTextColor(TEXT);
        name.setTextSize(16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setBackgroundColor(Color.argb(145, 10, 12, 14));
        name.setPadding(dp(10), dp(6), dp(10), dp(6));
        FrameLayout.LayoutParams nameLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.gravity = Gravity.TOP | Gravity.LEFT;
        nameLp.setMargins(dp(10), dp(10), 0, 0);
        addView(name, nameLp);

        LinearLayout controls = new LinearLayout(activity);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        status = new TextView(activity);
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
            start(mode);
        });
        controls.addView(hdButton);

        audioButton = makeButton("ÁUDIO OFF");
        audioButton.setOnClickListener(v -> toggleAudio());
        controls.addView(audioButton);

        FrameLayout.LayoutParams controlsLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        controlsLp.gravity = Gravity.RIGHT | Gravity.BOTTOM;
        controlsLp.setMargins(0, 0, dp(10), dp(10));
        addView(controls, controlsLp);
        refreshButtons();
    }

    private Button makeButton(String label) {
        Button b = new Button(activity);
        b.setText(label);
        b.setTextSize(12);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        lp.setMargins(dp(4), 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    void start(AccessUtil.Mode newMode) {
        mode = newMode;
        if (mode == AccessUtil.Mode.LOCAL) startLocal();
        else if (mode == AccessUtil.Mode.P2P) startP2p();
    }

    void setStatus(String text, int color) {
        status.setText(text);
        status.setTextColor(color);
    }

    private void startLocal() {
        releaseVideoOnly();
        String url = videoUrl();
        if (url == null) { setStatus("SEM CONFIG", OFFLINE); return; }
        setStatus("CONECTANDO", MUTED);
        try {
            playerView = new PlayerView(activity);
            playerView.setUseController(false);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            playerView.setShutterBackgroundColor(BG);
            videoHost.removeAllViews();
            videoHost.addView(playerView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            videoPlayer = new ExoPlayer.Builder(activity).build();
            videoPlayer.setVolume(0f);
            playerView.setPlayer(videoPlayer);
            videoPlayer.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) setStatus("LOCAL", OK);
                    else if (state == Player.STATE_BUFFERING) setStatus("CONECTANDO", MUTED);
                    else if (state == Player.STATE_ENDED) fail("OFFLINE");
                }
                @Override public void onPlayerError(PlaybackException error) { fail("OFFLINE"); }
            });
            MediaSource source = new RtspMediaSource.Factory()
                    .setForceUseRtpTcp(true)
                    .setTimeoutMs(5_000)
                    .createMediaSource(MediaItem.fromUri(url));
            videoPlayer.setMediaSource(source);
            videoPlayer.prepare();
            videoPlayer.play();
        } catch (Exception e) {
            fail("OFFLINE");
        }
    }

    private void startP2p() {
        releaseVideoOnly();
        String devId = RemoteConfig.sn(activity).toLowerCase(Locale.ROOT);
        if (devId.isEmpty()) { setStatus("P2P PENDENTE", MUTED); return; }
        setStatus("CONECTANDO P2P", MUTED);
        try {
            videoHost.removeAllViews();
            p2pPlayer = DeviceManager.getInstance().createMonitorPlayer(videoHost, devId);
            p2pPlayer.setHardDecode(false);
            p2pPlayer.setChnId(index);
            p2pPlayer.setVideoFullScreen(true);
            p2pPlayer.setStreamType(hd ? SDKCONST.StreamType.Main : SDKCONST.StreamType.Extra);
            p2pPlayer.setOnMediaManagerListener(new MediaManager.OnMediaManagerListener() {
                @Override public void onMediaPlayState(PlayerAttribute attribute, int state) {
                    handler.post(() -> setStatus("P2P", OK));
                }
                @Override public void onFailed(PlayerAttribute attribute, int msgId, int errorId) {
                    handler.post(() -> fail("P2P ERRO " + errorId));
                }
                @Override public void onVideoBufferEnd(PlayerAttribute attribute, MsgContent ex) {
                    handler.post(() -> setStatus("P2P", OK));
                }
                @Override public void onPlayStateClick(View view) {
                    // The SDK exposes this callback for its optional player-state overlay.
                }
            });
            p2pPlayer.startMonitor();
            if (audio) p2pPlayer.openVoiceBySound();
        } catch (Throwable e) {
            fail("P2P OFFLINE");
        }
    }

    private void toggleAudio() {
        if (mode == AccessUtil.Mode.P2P) {
            if (p2pPlayer == null) return;
            try {
                if (audio) p2pPlayer.closeVoiceBySound(); else p2pPlayer.openVoiceBySound();
                audio = !audio;
            } catch (Throwable ignored) { audio = false; }
            refreshButtons();
            return;
        }
        if (mode != AccessUtil.Mode.LOCAL) return;
        if (audio) {
            releaseAudio();
            audio = false;
            refreshButtons();
            return;
        }
        String url = audioUrl();
        if (url == null) return;
        try {
            audioPlayer = new ExoPlayer.Builder(activity).build();
            TrackSelectionParameters params = audioPlayer.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build();
            audioPlayer.setTrackSelectionParameters(params);
            audioPlayer.setVolume(1f);
            audioPlayer.addListener(new Player.Listener() {
                @Override public void onPlayerError(PlaybackException error) {
                    releaseAudio(); audio = false; refreshButtons();
                }
            });
            MediaSource source = new RtspMediaSource.Factory()
                    .setForceUseRtpTcp(true).setTimeoutMs(5_000)
                    .createMediaSource(MediaItem.fromUri(url));
            audioPlayer.setMediaSource(source);
            audioPlayer.prepare();
            audioPlayer.play();
            audio = true;
            refreshButtons();
        } catch (Exception e) {
            releaseAudio(); audio = false; refreshButtons();
        }
    }

    private void fail(String text) {
        setStatus(text, OFFLINE);
        if (reconnectCallback != null) reconnectCallback.run();
    }

    private String videoUrl() {
        if (baseUrl == null || !baseUrl.startsWith("rtsp://")) return null;
        return hd ? baseUrl.replace("subtype=1", "subtype=0")
                : baseUrl.replace("subtype=0", "subtype=1");
    }

    private String audioUrl() {
        if (baseUrl == null || !baseUrl.startsWith("rtsp://")) return null;
        return baseUrl.replace("subtype=1", "subtype=0");
    }

    private void refreshButtons() {
        hdButton.setText(hd ? "HD ON" : "HD");
        hdButton.setTextColor(hd ? OK : TEXT);
        audioButton.setText(audio ? "ÁUDIO ON" : "ÁUDIO OFF");
        audioButton.setTextColor(audio ? OK : TEXT);
    }

    private void releaseVideoOnly() {
        if (videoPlayer != null) {
            try { videoPlayer.release(); } catch (Exception ignored) {}
            videoPlayer = null;
        }
        if (playerView != null) {
            try { playerView.setPlayer(null); } catch (Exception ignored) {}
            playerView = null;
        }
        if (p2pPlayer != null) {
            try { p2pPlayer.closeVoiceBySound(); } catch (Throwable ignored) {}
            try { p2pPlayer.stopPlay(); } catch (Throwable ignored) {}
            try { p2pPlayer.destroyPlay(); } catch (Throwable ignored) {}
            p2pPlayer = null;
        }
        videoHost.removeAllViews();
    }

    private void releaseAudio() {
        if (audioPlayer != null) {
            try { audioPlayer.release(); } catch (Exception ignored) {}
            audioPlayer = null;
        }
    }

    void release() {
        releaseVideoOnly();
        releaseAudio();
        audio = false;
        refreshButtons();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}