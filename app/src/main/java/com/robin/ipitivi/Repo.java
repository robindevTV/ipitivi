package com.robin.ipitivi;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The one place that owns the active list's channels, the guide, the favourites and what is playing.
 * Loading runs on a single background thread; the listener is told on the main thread.
 */
final class Repo {
    static final Repo I = new Repo();
    static final String UA = "Mozilla/5.0 (Linux; Android 9; TV) AppleWebKit/537.36 Chrome/120 Safari/537.36";
    private static final long STALE_MS = 6L * 3600_000L;

    interface Listener { void onChanged(); }

    /** A category as the user sees it: their name for it, and its channels. */
    static final class Category {
        final String name;
        final List<Channel> channels;

        Category(String name, List<Channel> channels) {
            this.name = name;
            this.channels = channels;
        }
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Context app;
    private Prefs prefs;
    private Listener listener;
    private Set<String> favs;
    private int dataVersion;

    volatile List<Channel> all = Collections.emptyList();
    volatile Guide guide = Guide.EMPTY;
    volatile String status = "";
    volatile boolean loading;

    private String loadedKey = "";
    private long loadedAt;

    /** The list the user was browsing when they pressed OK, so Up/Down can zap inside it. */
    volatile List<Channel> playing = Collections.emptyList();
    volatile int playingIndex;

    synchronized void init(Context c) {
        if (app == null) {
            app = c.getApplicationContext();
            prefs = new Prefs(app);
        }
    }

    Prefs prefs() { return prefs; }

    void setListener(Listener l) { listener = l; }

    private void changed(String newStatus, boolean isLoading) {
        status = newStatus;
        loading = isLoading;
        main.post(new Runnable() {
            @Override public void run() {
                Listener l = listener;
                if (l != null) l.onChanged();
            }
        });
    }

    Channel playingChannel() {
        List<Channel> p = playing;
        if (p.isEmpty()) return null;
        int i = Math.max(0, Math.min(playingIndex, p.size() - 1));
        return p.get(i);
    }

    // ---- versioning: screens redraw when this changes ----

    synchronized int dataVersion() { return dataVersion; }

    synchronized void bump() { dataVersion++; }

    // ---- favourites ----

    private synchronized void ensureFavs() {
        if (favs == null) favs = prefs.favorites();
    }

    synchronized boolean isFav(String id) {
        ensureFavs();
        return favs.contains(id);
    }

    /** @return true when the channel is now a favourite. */
    synchronized boolean toggleFav(String id) {
        ensureFavs();
        boolean added = favs.add(id);
        if (!added) favs.remove(id);
        prefs.saveFavorites(favs);
        dataVersion++;
        return added;
    }

    List<Channel> favorites() {
        List<Channel> out = new ArrayList<>();
        for (Channel c : all) if (isFav(c.id)) out.add(c);
        return out;
    }

    // ---- categories ----

    /** Original group names with their channel counts, in playlist order. */
    Map<String, List<Channel>> groups() {
        Map<String, List<Channel>> g = new LinkedHashMap<>();
        for (Channel c : all) {
            List<Channel> l = g.get(c.group);
            if (l == null) {
                l = new ArrayList<>();
                g.put(c.group, l);
            }
            l.add(c);
        }
        return g;
    }

    /** The user's categories for the active list: renamed, reordered, hidden ones left out. */
    List<Category> categories() {
        List<Category> out = new ArrayList<>();
        Source s = prefs.active();
        if (s == null) return out;
        Map<String, List<Channel>> g = groups();
        CategoryPrefs cp = prefs.categories(s.id);
        if (cp.sync(g.keySet())) prefs.saveCategories(s.id, cp);
        for (CategoryPrefs.Entry e : cp.entries()) {
            List<Channel> l = g.get(e.original);
            if (!e.hidden && l != null) out.add(new Category(e.display(), l));
        }
        return out;
    }

    /** Every channel of every visible category, in the user's order: what Up/Down zap through. */
    List<Channel> visibleChannels() {
        List<Channel> out = new ArrayList<>();
        for (Category c : categories()) out.addAll(c.channels);
        return out;
    }

    // ---- last channel ----

    void saveLast(Channel c) {
        Source s = prefs.active();
        if (s != null) prefs.saveLast(s.id, c);
    }

    /** The channel watched last on the active list, if it still exists there. */
    Channel lastChannel() {
        Source s = prefs.active();
        String[] l = prefs.last();
        if (s == null || l == null || !s.id.equals(l[0])) return null;
        for (Channel c : all) if (c.id.equals(l[1])) return c;
        return null;
    }

    // ---- loading ----

    /** Makes another list the active one and loads it (from its cache when there is one). */
    void switchTo(String sourceId) {
        prefs.setActive(sourceId);
        all = Collections.emptyList();
        guide = Guide.EMPTY;
        playing = Collections.emptyList();
        loadedKey = "";
        bump();
        load(false);
    }

    /** Forget everything cached for a list that was deleted. */
    void forget(String sourceId) {
        new File(app.getCacheDir(), "pl-" + sourceId + ".m3u").delete();
        new File(app.getCacheDir(), "pl-" + sourceId + ".xml").delete();
        new File(app.getCacheDir(), "pl-" + sourceId + ".key").delete();
    }

    void load(final boolean force) {
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    doLoad(force);
                } catch (Throwable t) {
                    changed("Impossible de charger la liste. (" + t.getClass().getSimpleName() + ")", false);
                }
            }
        });
    }

    private static String friendly(IOException e) {
        String m = String.valueOf(e.getMessage());
        if (m.startsWith("HTTP 401") || m.startsWith("HTTP 403")) {
            return "Le serveur refuse l'accès. Vérifie l'identifiant et le mot de passe.";
        }
        if (m.startsWith("HTTP 404")) {
            return "Adresse introuvable. Vérifie le lien de ta liste.";
        }
        return "Impossible de joindre la liste. Vérifie ta connexion internet et le lien. (" + m + ")";
    }

    private void doLoad(boolean force) throws Exception {
        Source src = prefs.active();
        if (src == null || !src.isComplete()) {
            all = Collections.emptyList();
            guide = Guide.EMPTY;
            changed("", false);
            return;
        }
        String plUrl = src.playlistUrl();
        String epgUrl = src.guideUrl();
        String key = src.key();
        long now = System.currentTimeMillis();
        if (!force && key.equals(loadedKey) && now - loadedAt < STALE_MS && !all.isEmpty()) {
            changed(status, false);
            return;
        }
        File plFile = new File(app.getCacheDir(), "pl-" + src.id + ".m3u");
        File epgFile = new File(app.getCacheDir(), "pl-" + src.id + ".xml");
        File keyFile = new File(app.getCacheDir(), "pl-" + src.id + ".key");
        boolean sameSource = key.equals(readSmall(keyFile));
        if (!sameSource) {
            plFile.delete();
            epgFile.delete();
        }
        boolean plFresh = sameSource && plFile.exists() && now - plFile.lastModified() < STALE_MS;

        // 1. Show the cached list at once so the screen is never empty while the network works.
        if (!force && plFile.exists() && all.isEmpty()) {
            List<Channel> cached = parsePlaylist(plFile);
            if (!cached.isEmpty()) {
                all = cached;
                changed("Mise à jour…", true);
            }
        }

        // 2. Playlist.
        String note = "";
        if (force || !plFresh) {
            changed("Chargement de la liste…", true);
            try {
                download(plUrl, plFile);
                writeSmall(keyFile, key);
            } catch (IOException e) {
                if (!plFile.exists()) {
                    all = Collections.emptyList();
                    changed(friendly(e), false);
                    return;
                }
                note = " · hors ligne";
            }
        }
        List<Channel> list = parsePlaylist(plFile);
        all = list;
        bump();
        if (list.isEmpty()) {
            changed("La liste est vide ou illisible. Vérifie le lien.", false);
            return;
        }
        changed(list.size() + " chaînes" + note, true);

        // 3. Guide (optional; a failure never hides the channels).
        if (!epgUrl.isEmpty()) {
            boolean epgFresh = epgFile.exists() && now - epgFile.lastModified() < STALE_MS;
            if (force || !epgFresh) {
                try {
                    download(epgUrl, epgFile);
                } catch (IOException e) {
                    note += " · guide indisponible";
                }
            }
            if (epgFile.exists()) {
                try {
                    long t = System.currentTimeMillis();
                    InputStream in = new FileInputStream(epgFile);
                    try {
                        guide = new Guide(XmltvParser.parse(in, t - 3 * 3600_000L, t + 36 * 3600_000L));
                    } finally {
                        in.close();
                    }
                } catch (Exception e) {
                    guide = Guide.EMPTY;
                    note += " · guide illisible";
                }
            }
        } else {
            guide = Guide.EMPTY;
        }
        loadedKey = key;
        loadedAt = System.currentTimeMillis();
        changed(list.size() + " chaînes" + note, false);
    }

    private static List<Channel> parsePlaylist(File f) throws IOException {
        InputStreamReader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8);
        try {
            return M3uParser.parse(r);
        } finally {
            r.close();
        }
    }

    static HttpURLConnection open(String url) throws IOException {
        String u = url;
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", UA);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("redirection sans adresse");
                u = new URL(new URL(u), loc).toString();
                continue;
            }
            if (code != 200) {
                c.disconnect();
                throw new IOException("HTTP " + code);
            }
            return c;
        }
        throw new IOException("trop de redirections");
    }

    private static void download(String url, File dest) throws IOException {
        File tmp = new File(dest.getPath() + ".tmp");
        HttpURLConnection c = open(url);
        try {
            InputStream in = c.getInputStream();
            OutputStream out = new FileOutputStream(tmp);
            try {
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                out.close();
                in.close();
            }
        } finally {
            c.disconnect();
        }
        if (!tmp.renameTo(dest)) {
            dest.delete();
            if (!tmp.renameTo(dest)) throw new IOException("écriture impossible");
        }
    }

    private static String readSmall(File f) {
        try {
            InputStream in = new FileInputStream(f);
            try {
                byte[] b = new byte[(int) Math.min(f.length(), 8192)];
                int n = in.read(b);
                return n <= 0 ? "" : new String(b, 0, n, StandardCharsets.UTF_8);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return "";
        }
    }

    private static void writeSmall(File f, String s) {
        try {
            OutputStream out = new FileOutputStream(f);
            try {
                out.write(s.getBytes(StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
        } catch (IOException ignored) {
        }
    }
}
