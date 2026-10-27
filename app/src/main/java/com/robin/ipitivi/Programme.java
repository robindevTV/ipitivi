package com.robin.ipitivi;

/** One EPG entry. Times are epoch milliseconds. */
public final class Programme {
    public final long start;
    public final long stop;
    public final String title;

    public Programme(long start, long stop, String title) {
        this.start = start;
        this.stop = stop;
        this.title = title;
    }
}
