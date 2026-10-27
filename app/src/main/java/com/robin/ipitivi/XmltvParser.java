package com.robin.ipitivi;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Streams an XMLTV guide and keeps only what a TV screen needs: programmes that end after
 * {@code from} and start before {@code until}, so a 50 MB file does not become 50 MB of objects.
 */
public final class XmltvParser {
    private XmltvParser() {}

    public static Map<String, List<Programme>> parse(InputStream raw, long from, long until) throws Exception {
        BufferedInputStream in = new BufferedInputStream(raw, 64 * 1024);
        in.mark(4);
        int b1 = in.read();
        int b2 = in.read();
        in.reset();
        InputStream data = (b1 == 0x1f && b2 == 0x8b) ? new GZIPInputStream(in, 64 * 1024) : in;

        SAXParserFactory f = SAXParserFactory.newInstance();
        f.setNamespaceAware(false);
        SAXParser p = f.newSAXParser();
        final Map<String, List<Programme>> out = new HashMap<>();
        final long lo = from, hi = until;

        p.parse(new InputSource(data), new DefaultHandler() {
            String channel;
            long start, stop;
            boolean inTitle, keep;
            StringBuilder title = new StringBuilder();
            boolean haveTitle;

            @Override
            public void startElement(String uri, String local, String qName, Attributes a) {
                if ("programme".equals(qName)) {
                    channel = a.getValue("channel");
                    start = parseTime(a.getValue("start"));
                    stop = parseTime(a.getValue("stop"));
                    keep = channel != null && start > 0 && stop > start && stop > lo && start < hi;
                    haveTitle = false;
                    title.setLength(0);
                } else if (keep && "title".equals(qName) && !haveTitle) {
                    inTitle = true;
                }
            }

            @Override
            public void characters(char[] ch, int s, int len) {
                if (inTitle) title.append(ch, s, len);
            }

            @Override
            public void endElement(String uri, String local, String qName) {
                if ("title".equals(qName) && inTitle) {
                    inTitle = false;
                    haveTitle = true;
                } else if ("programme".equals(qName)) {
                    if (keep && haveTitle) {
                        List<Programme> l = out.get(channel);
                        if (l == null) { l = new ArrayList<>(); out.put(channel, l); }
                        l.add(new Programme(start, stop, title.toString().trim()));
                    }
                    keep = false;
                }
            }
        });
        for (List<Programme> l : out.values()) {
            Collections.sort(l, new Comparator<Programme>() {
                @Override public int compare(Programme x, Programme y) { return Long.compare(x.start, y.start); }
            });
        }
        return out;
    }

    /** "20261026203000 +0100" (offset optional, defaults to UTC) to epoch ms; 0 when unreadable. */
    static long parseTime(String s) {
        if (s == null) return 0;
        s = s.trim();
        if (s.length() < 14) return 0;
        try {
            int y = Integer.parseInt(s.substring(0, 4));
            int mo = Integer.parseInt(s.substring(4, 6));
            int d = Integer.parseInt(s.substring(6, 8));
            int h = Integer.parseInt(s.substring(8, 10));
            int mi = Integer.parseInt(s.substring(10, 12));
            int se = Integer.parseInt(s.substring(12, 14));
            long offsetMs = 0;
            String rest = s.substring(14).trim();
            if (rest.length() >= 5 && (rest.charAt(0) == '+' || rest.charAt(0) == '-')) {
                int oh = Integer.parseInt(rest.substring(1, 3));
                int om = Integer.parseInt(rest.substring(3, 5));
                offsetMs = (oh * 60L + om) * 60_000L;
                if (rest.charAt(0) == '-') offsetMs = -offsetMs;
            }
            java.util.Calendar c = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
            c.clear();
            c.set(y, mo - 1, d, h, mi, se);
            return c.getTimeInMillis() - offsetMs;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
