package com.robin.ipitivi;

/** One playlist the user added: a plain M3U link, or an Xtream Codes account. Immutable. */
public final class Source {
    public static final String M3U = "m3u";
    public static final String XTREAM = "xtream";

    public final String id;
    public final String name;
    public final String type;
    public final String m3u;
    public final String epg;
    public final String server;
    public final String user;
    public final String pass;

    public Source(String id, String name, String type, String m3u, String epg,
                  String server, String user, String pass) {
        this.id = n(id);
        this.name = n(name);
        this.type = XTREAM.equals(type) ? XTREAM : M3U;
        this.m3u = n(m3u);
        this.epg = n(epg);
        this.server = n(server);
        this.user = n(user);
        this.pass = n(pass);
    }

    private static String n(String s) { return s == null ? "" : s.trim(); }

    public boolean isXtream() { return XTREAM.equals(type); }

    public String playlistUrl() {
        return isXtream() ? Xtream.playlistUrl(server, user, pass) : m3u;
    }

    public String guideUrl() {
        if (isXtream()) return epg.isEmpty() ? Xtream.guideUrl(server, user, pass) : epg;
        return epg;
    }

    /** Changes when the playlist or the guide address changes, so caches can tell. */
    public String key() { return playlistUrl() + "|" + guideUrl(); }

    public String kindLabel() { return isXtream() ? "Compte (serveur + identifiant)" : "Lien web (M3U)"; }

    /** Enough filled in to try loading it? */
    public boolean isComplete() {
        return isXtream() ? !server.isEmpty() && !user.isEmpty() && !pass.isEmpty() : !m3u.isEmpty();
    }
}
