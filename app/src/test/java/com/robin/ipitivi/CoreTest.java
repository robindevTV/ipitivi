package com.robin.ipitivi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.Test;

public class CoreTest {
    private static final String M3U = "﻿#EXTM3U\n"
            + "#EXTINF:-1 tvg-id=\"TF1.fr\" tvg-name=\"TF1\" tvg-logo=\"http://x/tf1.png\" group-title=\"France, TNT\",TF1 HD\n"
            + "http://srv:8080/user/pass/101\n"
            + "\n"
            + "#EXTINF:-1 group-title=\"Sport\",Canal, le sport\n"
            + "#EXTVLCOPT:http-user-agent=foo\n"
            + "http://srv/live/202.m3u8\n"
            + "#EXTINF:-1,Sans groupe\n"
            + "http://srv/303.ts\n"
            + "#EXTINF:-1 tvg-name=\"Nom seul\",\n"
            + "http://srv/404.ts\n";

    @Test
    public void m3uParsesAttributesAndNames() throws Exception {
        List<Channel> c = M3uParser.parse(new StringReader(M3U));
        assertEquals(4, c.size());
        assertEquals("TF1 HD", c.get(0).name);
        assertEquals("France, TNT", c.get(0).group);   // comma inside quotes must not split
        assertEquals("http://x/tf1.png", c.get(0).logo);
        assertEquals("TF1.fr", c.get(0).epgId);
        assertEquals("http://srv:8080/user/pass/101", c.get(0).url);
        assertEquals("Canal, le sport", c.get(1).name); // comma inside the name
        assertEquals("Sport", c.get(1).group);
        assertEquals("http://srv/live/202.m3u8", c.get(1).url);
        assertEquals("Autres", c.get(2).group);
        assertEquals("Nom seul", c.get(3).name);        // falls back to tvg-name
    }

    @Test
    public void m3uIdsAreStableAndDistinct() throws Exception {
        List<Channel> a = M3uParser.parse(new StringReader(M3U));
        List<Channel> b = M3uParser.parse(new StringReader(M3U));
        for (int i = 0; i < a.size(); i++) assertEquals(a.get(i).id, b.get(i).id);
        assertEquals(4, new java.util.HashSet<>(java.util.Arrays.asList(
                a.get(0).id, a.get(1).id, a.get(2).id, a.get(3).id)).size());
    }

    @Test
    public void xtreamUrls() {
        assertEquals("http://h:80", Xtream.base("h:80"));
        assertEquals("http://h:80", Xtream.base(" http://h:80/c/ "));
        assertEquals("https://h", Xtream.base("https://h/"));
        assertEquals("http://h:80/get.php?username=u&password=p%26x&type=m3u_plus&output=ts",
                Xtream.playlistUrl("h:80", "u", "p&x"));
        assertEquals("http://h:80/xmltv.php?username=u&password=p", Xtream.guideUrl("http://h:80/", "u", "p"));
    }

    @Test
    public void xmltvTimeHandlesOffsets() {
        // 20:30 in UTC+1 is 19:30 UTC
        long a = XmltvParser.parseTime("20261026203000 +0100");
        long b = XmltvParser.parseTime("20261026193000 +0000");
        long c = XmltvParser.parseTime("20261026193000");
        assertEquals(a, b);
        assertEquals(b, c);
        assertEquals(1793043000000L, b);
        assertEquals(a + 3_600_000L, XmltvParser.parseTime("20261026203000 +0000"));
        assertEquals(0L, XmltvParser.parseTime("garbage"));
        assertEquals(0L, XmltvParser.parseTime(null));
    }

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv>"
            + "<channel id=\"TF1.fr\"><display-name>TF1</display-name></channel>"
            + "<programme start=\"20261026190000 +0000\" stop=\"20261026200000 +0000\" channel=\"TF1.fr\">"
            + "<title lang=\"fr\">Journal &amp; m&#233;t&#233;o</title><title lang=\"en\">News</title></programme>"
            + "<programme start=\"20261026200000 +0000\" stop=\"20261026210000 +0000\" channel=\"TF1.fr\">"
            + "<title>Film</title><desc>x</desc></programme>"
            + "<programme start=\"20261025100000 +0000\" stop=\"20261025110000 +0000\" channel=\"TF1.fr\">"
            + "<title>Hier</title></programme>"
            + "<programme start=\"20261030100000 +0000\" stop=\"20261030110000 +0000\" channel=\"TF1.fr\">"
            + "<title>Trop loin</title></programme>"
            + "<programme start=\"20261026190000 +0000\" stop=\"20261026200000 +0000\" channel=\"M6.fr\">"
            + "<title>Autre</title></programme></tv>";

    private static long utc(String s) { return XmltvParser.parseTime(s + " +0000"); }

    @Test
    public void xmltvKeepsOnlyTheWindowAndFirstTitle() throws Exception {
        long from = utc("20261026180000"), until = utc("20261027180000");
        Map<String, List<Programme>> m = XmltvParser.parse(
                new ByteArrayInputStream(XML.getBytes("UTF-8")), from, until);
        assertEquals(2, m.size());
        List<Programme> tf1 = m.get("TF1.fr");
        assertEquals(2, tf1.size());                       // "Hier" and "Trop loin" dropped
        assertEquals("Journal & météo", tf1.get(0).title); // first <title> only, entities decoded
        assertEquals("Film", tf1.get(1).title);
        assertTrue(tf1.get(0).start < tf1.get(1).start);
    }

    @Test
    public void xmltvReadsGzip() throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        GZIPOutputStream gz = new GZIPOutputStream(bo);
        gz.write(XML.getBytes("UTF-8"));
        gz.close();
        Map<String, List<Programme>> m = XmltvParser.parse(
                new ByteArrayInputStream(bo.toByteArray()), utc("20261026180000"), utc("20261027180000"));
        assertEquals(2, m.get("TF1.fr").size());
    }

    @Test
    public void guideFindsNowAndNext() {
        java.util.Map<String, List<Programme>> m = new java.util.HashMap<>();
        List<Programme> l = new java.util.ArrayList<>();
        l.add(new Programme(0, 100, "A"));
        l.add(new Programme(100, 200, "B"));
        l.add(new Programme(300, 400, "C"));
        m.put("TF1.FR", l);
        Guide g = new Guide(m);
        Programme[] r = g.nowNext("tf1.fr", 150);            // case-insensitive id
        assertEquals("B", r[0].title);
        assertEquals("C", r[1].title);
        r = g.nowNext("TF1.fr", 250);                        // gap: nothing now, C next
        assertEquals(null, r[0]);
        assertEquals("C", r[1].title);
        r = g.nowNext("TF1.fr", 500);                        // after everything
        assertEquals(null, r[0]);
        assertEquals(null, r[1]);
        assertEquals(null, g.nowNext("", 150)[0]);
        assertEquals(null, g.nowNext("unknown", 150)[0]);
        assertTrue(Guide.progress(l.get(1), 150) == 0.5f);
        assertTrue(Guide.progress(null, 150) == 0f);
        assertTrue(Guide.progress(l.get(1), 999) == 1f);
    }
}
