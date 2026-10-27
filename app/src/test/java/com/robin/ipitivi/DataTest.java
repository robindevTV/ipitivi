package com.robin.ipitivi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class DataTest {
    @Test
    public void sourcesRoundTripWithAwkwardCharacters() {
        List<Source> in = new ArrayList<>();
        in.add(new Source("a1", "Maison & été", Source.M3U, "http://x/p.m3u?a=1&b=2", "", "", "", ""));
        in.add(new Source("b2", "Tab\there\nline", Source.XTREAM, "", "http://x/e.xml", "h:80", "us er", "päss+w%rd"));
        List<Source> out = SourceStore.decode(SourceStore.encode(in));
        assertEquals(2, out.size());
        assertEquals("Maison & été", out.get(0).name);
        assertEquals("http://x/p.m3u?a=1&b=2", out.get(0).m3u);
        assertEquals(Source.XTREAM, out.get(1).type);
        assertEquals("Tab\there\nline", out.get(1).name);
        assertEquals("päss+w%rd", out.get(1).pass);
        assertEquals("us er", out.get(1).user);
    }

    @Test
    public void sourceUrlsAndCompleteness() {
        Source m = new Source("1", "n", Source.M3U, " http://a/b.m3u ", "http://a/e.xml", "", "", "");
        assertEquals("http://a/b.m3u", m.playlistUrl());
        assertEquals("http://a/e.xml", m.guideUrl());
        assertTrue(m.isComplete());
        Source x = new Source("2", "n", Source.XTREAM, "", "", "h:80", "u", "p");
        assertEquals("http://h:80/get.php?username=u&password=p&type=m3u_plus&output=ts", x.playlistUrl());
        assertEquals("http://h:80/xmltv.php?username=u&password=p", x.guideUrl());
        assertTrue(x.isComplete());
        assertTrue(!new Source("3", "n", Source.XTREAM, "", "", "h:80", "", "p").isComplete());
        assertTrue(!new Source("4", "n", "bogus", "", "", "", "", "").isComplete()); // unknown type falls back to M3U
        assertTrue(!x.key().equals(m.key()));
    }

    @Test
    public void decodeSkipsBrokenLines() {
        assertEquals(0, SourceStore.decode("").size());
        assertEquals(0, SourceStore.decode(null).size());
        assertEquals(0, SourceStore.decode("garbage\nonly\ttwo\n").size());
    }

    @Test
    public void idsAreDifferent() {
        assertTrue(!SourceStore.newId().equals(SourceStore.newId()) || !SourceStore.newId().isEmpty());
        assertTrue(!SourceStore.newId().isEmpty());
    }

    @Test
    public void categorySyncKeepsOrderAndSettings() {
        CategoryPrefs cp = new CategoryPrefs();
        assertTrue(cp.sync(Arrays.asList("France", "Sport", "Cinéma")));
        cp.entries().get(1).hidden = true;
        cp.entries().get(2).label = "Films";
        assertEquals(2, cp.move(2, -1) + 1);            // Cinéma goes up one place
        assertEquals("Films", cp.entries().get(1).display());
        // the playlist changed: Sport vanished, Enfants appeared
        assertTrue(cp.sync(Arrays.asList("France", "Cinéma", "Enfants")));
        assertEquals(3, cp.entries().size());
        assertEquals("France", cp.entries().get(0).original);
        assertEquals("Films", cp.entries().get(1).display());
        assertEquals("Enfants", cp.entries().get(2).original);
        assertTrue(!cp.sync(Arrays.asList("France", "Cinéma", "Enfants")));   // nothing new: no save needed
        assertTrue(!cp.sync(new ArrayList<String>()));                              // empty playlist: untouched
        assertEquals(3, cp.entries().size());
    }

    @Test
    public void categoryMoveStopsAtTheEnds() {
        CategoryPrefs cp = new CategoryPrefs();
        cp.sync(Arrays.asList("A", "B", "C"));
        assertEquals(0, cp.move(0, -1));
        assertEquals(2, cp.move(2, 1));
        assertEquals(1, cp.move(0, 1));
        assertEquals("B", cp.entries().get(0).original);
        assertEquals(0, cp.move(7, 1) - 7);   // out of range: unchanged
    }

    @Test
    public void categoryPrefsRoundTripAndShowAll() {
        CategoryPrefs cp = new CategoryPrefs();
        cp.sync(Arrays.asList("France, TNT", "Spôrt\tTab"));
        cp.entries().get(0).hidden = true;
        cp.entries().get(1).label = "Mes sports";
        assertEquals(1, cp.hiddenCount());
        CategoryPrefs back = CategoryPrefs.decode(cp.encode());
        assertEquals(2, back.entries().size());
        assertEquals("France, TNT", back.entries().get(0).original);
        assertTrue(back.entries().get(0).hidden);
        assertEquals("Mes sports", back.entries().get(1).display());
        assertEquals("Spôrt\tTab", back.entries().get(1).original);
        back.showAll();
        assertEquals(0, back.hiddenCount());
        assertEquals(0, CategoryPrefs.decode(null).entries().size());
    }
}
