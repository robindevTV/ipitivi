package com.robin.ipitivi;

/** One live channel from a playlist. Plain data, no Android classes. */
public final class Channel {
    public final String id;      // stable key: tvg-id when present, else the stream URL
    public final String name;
    public final String group;   // never null, "Autres" when the playlist gives none
    public final String logo;    // may be empty
    public final String epgId;   // tvg-id, may be empty
    public final String url;

    public Channel(String id, String name, String group, String logo, String epgId, String url) {
        this.id = id;
        this.name = name;
        this.group = group;
        this.logo = logo;
        this.epgId = epgId;
        this.url = url;
    }
}
