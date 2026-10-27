package com.robin.ipitivi;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen player. Up / Down (or CH+ / CH-) zap inside the list the user was browsing,
 * digits jump to a channel number, OK shows the info banner, long OK toggles the favourite.
 */
public class PlayerActivity extends Activity {
    private static final int MAX_RETRIES = 3;

    private ExoPlayer player;
    private PlayerView playerView;
    private LinearLayout overlay;
    private TextView numberView, nameView, nowView, nextView, hintView, messageView;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final StringBuilder digits = new StringBuilder();
    private int retries;
    private boolean failed;

    private final Runnable hideOverlay = new Runnable() {
        @Override public void run() { overlay.setVisibility(View.GONE); }
    };
    private final Runnable hideMessage = new Runnable() {
        @Override public void run() { if (!failed) messageView.setVisibility(View.GONE); }
    };
    private final Runnable retry = new Runnable() {
        @Override public void run() { if (player != null) player.prepare(); }
    };
    private final Runnable commitDigits = new Runnable() {
        @Override public void run() { jumpToDigits(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Repo.I.init(this);
        Ui.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Repo.I.playing.isEmpty()) {
            finish();
            return;
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setShutterBackgroundColor(Color.BLACK);
        root.addView(playerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setBackground(Ui.glassPane(this, 0xC0303450, 0xC01A1C32, 40, 0x59FFFFFF, 1));
        overlay.setPadding(Ui.dp(this, 32), Ui.dp(this, 22), Ui.dp(this, 32), Ui.dp(this, 22));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        numberView = Ui.text(this, "", 38, Ui.MUTED);
        numberView.setTypeface(Ui.LIGHT);
        nameView = Ui.text(this, "", 40, Ui.TEXT);
        nameView.setTypeface(Ui.LIGHT);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameView.setPadding(Ui.dp(this, 18), 0, 0, 0);
        head.addView(numberView);
        head.addView(nameView);
        overlay.addView(head);
        nowView = Ui.text(this, "", 24, Ui.TEXT);
        nowView.setSingleLine(true);
        nowView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nowView.setPadding(0, Ui.dp(this, 8), 0, 0);
        overlay.addView(nowView);
        nextView = Ui.text(this, "", 20, Ui.MUTED);
        nextView.setSingleLine(true);
        nextView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        overlay.addView(nextView);
        hintView = Ui.text(this, "▲ ▼  changer de chaîne      OK  infos      Garder OK  favori      Retour  liste", 16, Ui.MUTED);
        hintView.setPadding(0, Ui.dp(this, 14), 0, 0);
        overlay.addView(hintView);
        FrameLayout.LayoutParams op = new FrameLayout.LayoutParams(Ui.dp(this, 980),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        op.leftMargin = Ui.dp(this, 56);
        op.bottomMargin = Ui.dp(this, 48);
        root.addView(overlay, op);

        messageView = Ui.text(this, "", 26, Ui.TEXT);
        messageView.setGravity(Gravity.CENTER);
        messageView.setBackground(Ui.glassPane(this, 0xC0303450, 0xC01A1C32, 28, 0x59FFFFFF, 1));
        messageView.setPadding(Ui.dp(this, 32), Ui.dp(this, 20), Ui.dp(this, 32), Ui.dp(this, 20));
        messageView.setVisibility(View.GONE);
        root.addView(messageView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        setContentView(root);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (playerView == null) return;
        createPlayer();
        playCurrent();
    }

    @Override
    protected void onStop() {
        super.onStop();
        h.removeCallbacksAndMessages(null);
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void createPlayer() {
        DefaultHttpDataSource.Factory ds = new DefaultHttpDataSource.Factory()
                .setUserAgent(Repo.UA)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(ds))
                .build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    if (!failed) showMessage("Chargement…");
                } else if (state == Player.STATE_READY) {
                    retries = 0;
                    failed = false;
                    messageView.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                h.removeCallbacks(retry);
                if (retries < MAX_RETRIES) {
                    retries++;
                    showMessage("Reconnexion… (" + retries + "/" + MAX_RETRIES + ")");
                    h.postDelayed(retry, 2000);
                } else {
                    failed = true;
                    showMessage("Cette chaîne ne répond pas\nAppuie sur ▲ ou ▼ pour en essayer une autre");
                }
            }
        });
        playerView.setPlayer(player);
    }

    private void playCurrent() {
        Channel c = Repo.I.playingChannel();
        if (c == null || player == null) {
            finish();
            return;
        }
        retries = 0;
        failed = false;
        h.removeCallbacks(retry);
        messageView.setVisibility(View.GONE);
        Repo.I.saveLast(c);
        player.setMediaItem(MediaItem.fromUri(c.url));
        player.prepare();
        player.setPlayWhenReady(true);
        showOverlay();
    }

    private void zap(int delta) {
        List<Channel> l = Repo.I.playing;
        if (l.isEmpty()) return;
        int n = l.size();
        Repo.I.playingIndex = ((Repo.I.playingIndex + delta) % n + n) % n;
        playCurrent();
    }

    // ---- overlay and messages ----

    private void showOverlay() {
        Channel c = Repo.I.playingChannel();
        if (c == null) return;
        numberView.setText(String.valueOf(Repo.I.playingIndex + 1));
        nameView.setText((Repo.I.isFav(c.id) ? "★ " : "") + c.name);
        long now = System.currentTimeMillis();
        Programme[] nn = Repo.I.guide.nowNext(c.epgId, now);
        if (nn[0] != null) {
            nowView.setText(hm.format(new Date(nn[0].start)) + " – " + hm.format(new Date(nn[0].stop))
                    + "   " + nn[0].title);
        } else {
            nowView.setText(c.group);
        }
        nextView.setText(nn[1] != null ? "Ensuite   " + hm.format(new Date(nn[1].start)) + "   " + nn[1].title : "");
        nextView.setVisibility(nn[1] != null ? View.VISIBLE : View.GONE);
        overlay.setVisibility(View.VISIBLE);
        h.removeCallbacks(hideOverlay);
        h.postDelayed(hideOverlay, 4000);
    }

    private void showMessage(String s) {
        messageView.setText(s);
        messageView.setVisibility(View.VISIBLE);
    }

    private void flash(String s) {
        showMessage(s);
        h.removeCallbacks(hideMessage);
        h.postDelayed(hideMessage, 1500);
    }

    // ---- keys ----

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                if (e.getRepeatCount() == 0) zap(+1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (e.getRepeatCount() == 0) zap(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                showOverlay();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                e.startTracking();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (player != null) player.setPlayWhenReady(!player.getPlayWhenReady());
                return true;
            default:
                break;
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9 && e.getRepeatCount() == 0) {
            if (digits.length() < 4) digits.append(keyCode - KeyEvent.KEYCODE_0);
            showMessage(digits.toString());
            h.removeCallbacks(commitDigits);
            h.postDelayed(commitDigits, 1500);
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            Channel c = Repo.I.playingChannel();
            if (c != null) {
                boolean now = Repo.I.toggleFav(c.id);
                flash(now ? "★ Ajoutée aux favoris" : "Retirée des favoris");
                showOverlay();
            }
            return true;
        }
        return super.onKeyLongPress(keyCode, e);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                && e.isTracking() && !e.isCanceled()) {
            showOverlay();
            return true;
        }
        return super.onKeyUp(keyCode, e);
    }

    private void jumpToDigits() {
        int n = 0;
        try {
            n = Integer.parseInt(digits.toString());
        } catch (NumberFormatException ignored) {
        }
        digits.setLength(0);
        if (n >= 1 && n <= Repo.I.playing.size()) {
            Repo.I.playingIndex = n - 1;
            playCurrent();
        } else {
            flash("Chaîne introuvable");
        }
    }
}
