package com.robin.ipitivi;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny channel-logo loader: 3 threads, an in-memory cache, no library. */
final class ImageLoader {
    private ImageLoader() {}

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(80);
    private static final Set<String> FAILED = Collections.synchronizedSet(new HashSet<String>());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void load(final ImageView view, final String url) {
        view.setTag(url);
        view.setImageDrawable(null);
        if (url == null || url.isEmpty() || FAILED.contains(url)) return;
        Bitmap hit = CACHE.get(url);
        if (hit != null) {
            view.setImageBitmap(hit);
            return;
        }
        POOL.execute(new Runnable() {
            @Override public void run() {
                final Bitmap bm = fetch(url);
                if (bm != null) CACHE.put(url, bm); else FAILED.add(url);
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (bm != null && url.equals(view.getTag())) view.setImageBitmap(bm);
                    }
                });
            }
        });
    }

    private static Bitmap fetch(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 2_000_000) return null;
                out.write(buf, 0, n);
            }
            byte[] data = out.toByteArray();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 160 && o.outHeight / (sample * 2) >= 160) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
