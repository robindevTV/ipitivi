package com.robin.ipitivi;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Everything Ipitivi remembers: lists, the active one, categories, favourites, text size, last channel. */
final class Prefs {
    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("ipitivi", Context.MODE_PRIVATE);
    }

    // ---- playlists ----

    List<Source> sources() { return SourceStore.decode(sp.getString("sources", "")); }

    void saveSources(List<Source> l) { sp.edit().putString("sources", SourceStore.encode(l)).apply(); }

    String activeId() { return sp.getString("active", ""); }

    void setActive(String id) { sp.edit().putString("active", id).apply(); }

    /** The list in use: the chosen one, else the first, else null. */
    Source active() {
        List<Source> l = sources();
        String id = activeId();
        for (Source s : l) if (s.id.equals(id)) return s;
        return l.isEmpty() ? null : l.get(0);
    }

    boolean configured() {
        Source s = active();
        return s != null && s.isComplete();
    }

    /** Adds or replaces (same id) and returns the saved list. */
    List<Source> upsert(Source s) {
        List<Source> l = sources();
        boolean found = false;
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).id.equals(s.id)) {
                l.set(i, s);
                found = true;
            }
        }
        if (!found) l.add(s);
        saveSources(l);
        return l;
    }

    void remove(String id) {
        List<Source> l = new ArrayList<>();
        for (Source s : sources()) if (!s.id.equals(id)) l.add(s);
        saveSources(l);
        sp.edit().remove("cats." + id).apply();
        if (id.equals(activeId())) setActive(l.isEmpty() ? "" : l.get(0).id);
    }

    // ---- categories (per list) ----

    CategoryPrefs categories(String sourceId) { return CategoryPrefs.decode(sp.getString("cats." + sourceId, "")); }

    void saveCategories(String sourceId, CategoryPrefs cp) {
        sp.edit().putString("cats." + sourceId, cp.encode()).apply();
    }

    // ---- favourites ----

    Set<String> favorites() { return new HashSet<>(sp.getStringSet("favs", new HashSet<String>())); }

    void saveFavorites(Set<String> favs) { sp.edit().putStringSet("favs", new HashSet<>(favs)).apply(); }

    // ---- last channel: "sourceId \t channelId \t name" ----

    void saveLast(String sourceId, Channel c) {
        sp.edit().putString("last", SourceStore.enc(sourceId) + "\t" + SourceStore.enc(c.id) + "\t"
                + SourceStore.enc(c.name)).apply();
    }

    /** {sourceId, channelId, name} or null. */
    String[] last() {
        String s = sp.getString("last", "");
        String[] p = s.split("\t", -1);
        if (p.length < 3) return null;
        return new String[] {SourceStore.dec(p[0]), SourceStore.dec(p[1]), SourceStore.dec(p[2])};
    }

    // ---- text size ----

    float textScale() { return sp.getFloat("scale", 1f); }

    void setTextScale(float v) { sp.edit().putFloat("scale", v).apply(); }
}
