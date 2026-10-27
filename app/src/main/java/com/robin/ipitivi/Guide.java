package com.robin.ipitivi;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Looks up what is on now and next for a channel. Immutable once built. */
public final class Guide {
    public static final Guide EMPTY = new Guide(Collections.<String, List<Programme>>emptyMap());

    private final Map<String, List<Programme>> byId = new HashMap<>();

    public Guide(Map<String, List<Programme>> source) {
        for (Map.Entry<String, List<Programme>> e : source.entrySet()) {
            byId.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
        }
    }

    public boolean isEmpty() { return byId.isEmpty(); }

    /** Returns {now, next}; either may be null. Never returns null itself. */
    public Programme[] nowNext(String epgId, long nowMs) {
        Programme[] r = new Programme[2];
        if (epgId == null || epgId.isEmpty()) return r;
        List<Programme> l = byId.get(epgId.toLowerCase(Locale.ROOT));
        if (l == null) return r;
        for (int i = 0; i < l.size(); i++) {
            Programme p = l.get(i);
            if (p.stop <= nowMs) continue;
            if (p.start <= nowMs) {
                r[0] = p;
                if (i + 1 < l.size()) r[1] = l.get(i + 1);
            } else {
                r[1] = p;   // a gap: nothing now, this is next
            }
            break;
        }
        return r;
    }

    public static float progress(Programme p, long nowMs) {
        if (p == null || p.stop <= p.start) return 0f;
        float f = (nowMs - p.start) / (float) (p.stop - p.start);
        return f < 0f ? 0f : (f > 1f ? 1f : f);
    }
}
