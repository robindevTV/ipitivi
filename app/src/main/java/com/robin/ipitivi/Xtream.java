package com.robin.ipitivi;

/** Turns Xtream Codes credentials into the playlist and guide URLs every such server exposes. */
public final class Xtream {
    private Xtream() {}

    /** "host:port", "http://host:port/" and "http://host:port/c/" all become "http://host:port". */
    public static String base(String server) {
        String s = server == null ? "" : server.trim();
        if (s.isEmpty()) return "";
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        int schemeEnd = s.indexOf("://") + 3;
        int slash = s.indexOf('/', schemeEnd);
        return slash < 0 ? s : s.substring(0, slash);
    }

    public static String playlistUrl(String server, String user, String pass) {
        return base(server) + "/get.php?username=" + enc(user) + "&password=" + enc(pass)
                + "&type=m3u_plus&output=ts";
    }

    public static String guideUrl(String server, String user, String pass) {
        return base(server) + "/xmltv.php?username=" + enc(user) + "&password=" + enc(pass);
    }

    static String enc(String v) {
        try {
            return java.net.URLEncoder.encode(v == null ? "" : v.trim(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
