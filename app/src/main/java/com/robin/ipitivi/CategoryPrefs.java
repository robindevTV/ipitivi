package com.robin.ipitivi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The user's choices for one playlist's categories: their order, a custom name and whether each is
 * hidden. The playlist's own group names ("original") are the keys.
 */
public final class CategoryPrefs {
    public static final class Entry {
        public final String original;
        public String label = "";
        public boolean hidden;

        Entry(String original) { this.original = original; }

        public String display() { return label.isEmpty() ? original : label; }
    }

    private final List<Entry> entries = new ArrayList<>();

    public List<Entry> entries() { return entries; }

    /**
     * Brings the entries in line with the groups the playlist has now: known ones keep their place and
     * settings, new ones go at the end, vanished ones are dropped. An empty playlist changes nothing.
     * @return true when something changed and the prefs should be saved.
     */
    public boolean sync(Collection<String> groups) {
        if (groups.isEmpty()) return false;
        Set<String> present = new LinkedHashSet<>(groups);
        Set<String> known = new HashSet<>();
        boolean changed = false;
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (!present.contains(e.original)) {
                it.remove();
                changed = true;
            } else {
                known.add(e.original);
            }
        }
        for (String g : present) {
            if (!known.contains(g)) {
                entries.add(new Entry(g));
                changed = true;
            }
        }
        return changed;
    }

    /** Moves an entry up (-1) or down (+1); returns its new index (unchanged at the ends). */
    public int move(int index, int delta) {
        int to = index + delta;
        if (index < 0 || index >= entries.size() || to < 0 || to >= entries.size()) return index;
        Entry e = entries.remove(index);
        entries.add(to, e);
        return to;
    }

    public void showAll() {
        for (Entry e : entries) e.hidden = false;
    }

    public int hiddenCount() {
        int n = 0;
        for (Entry e : entries) if (e.hidden) n++;
        return n;
    }

    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            sb.append(SourceStore.enc(e.original)).append('\t')
                    .append(SourceStore.enc(e.label)).append('\t')
                    .append(e.hidden ? '1' : '0').append('\n');
        }
        return sb.toString();
    }

    public static CategoryPrefs decode(String text) {
        CategoryPrefs cp = new CategoryPrefs();
        if (text == null) return cp;
        for (String line : text.split("\n")) {
            if (line.isEmpty()) continue;
            String[] p = line.split("\t", -1);
            if (p.length < 3) continue;
            Entry e = new Entry(SourceStore.dec(p[0]));
            e.label = SourceStore.dec(p[1]);
            e.hidden = "1".equals(p[2]);
            cp.entries.add(e);
        }
        return cp;
    }
}
