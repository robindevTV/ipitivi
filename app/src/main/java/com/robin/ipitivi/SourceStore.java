package com.robin.ipitivi;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Saves a list of sources as text: one line each, fields percent-encoded and joined by tabs. */
public final class SourceStore {
    private SourceStore() {}

    private static final Random RANDOM = new Random();

    public static String newId() {
        return Long.toString(System.currentTimeMillis(), 36) + Integer.toString(RANDOM.nextInt(46656), 36);
    }

    public static String encode(List<Source> list) {
        StringBuilder sb = new StringBuilder();
        for (Source s : list) {
            String[] f = {s.id, s.name, s.type, s.m3u, s.epg, s.server, s.user, s.pass};
            for (int i = 0; i < f.length; i++) {
                if (i > 0) sb.append('\t');
                sb.append(enc(f[i]));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public static List<Source> decode(String text) {
        List<Source> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            if (line.isEmpty()) continue;
            String[] p = line.split("\t", -1);
            if (p.length < 8) continue;
            String id = dec(p[0]);
            if (id.isEmpty()) continue;
            out.add(new Source(id, dec(p[1]), dec(p[2]), dec(p[3]), dec(p[4]), dec(p[5]), dec(p[6]), dec(p[7])));
        }
        return out;
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String dec(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return "";
        }
    }
}
