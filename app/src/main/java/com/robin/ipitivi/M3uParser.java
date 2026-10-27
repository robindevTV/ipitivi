package com.robin.ipitivi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Parses #EXTM3U playlists (the format IPTV providers hand out). */
public final class M3uParser {
    private M3uParser() {}

    public static List<Channel> parse(Reader reader) throws IOException {
        BufferedReader in = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
        List<Channel> out = new ArrayList<>();
        String name = null, group = null, logo = null, epgId = null;
        String line;
        boolean first = true;
        while ((line = in.readLine()) != null) {
            if (first) {
                first = false;
                if (!line.isEmpty() && line.charAt(0) == '﻿') line = line.substring(1);
            }
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF")) {
                name = null; group = null; logo = null; epgId = null;
                int comma = firstCommaOutsideQuotes(line);
                if (comma >= 0) name = line.substring(comma + 1).trim();
                group = attr(line, "group-title");
                logo = attr(line, "tvg-logo");
                epgId = attr(line, "tvg-id");
                String tvgName = attr(line, "tvg-name");
                if ((name == null || name.isEmpty()) && !tvgName.isEmpty()) name = tvgName;
            } else if (line.startsWith("#EXTGRP:")) {
                if (group == null || group.isEmpty()) group = line.substring(8).trim();
            } else if (line.startsWith("#")) {
                // #EXTVLCOPT, #EXTM3U, comments: ignored
            } else if (name != null) {
                // a URL line closes the pending #EXTINF
                String url = line;
                if (name.isEmpty()) name = url;
                String g = (group == null || group.isEmpty()) ? "Autres" : group;
                String id = (epgId != null && !epgId.isEmpty()) ? epgId + "|" + name : url;
                out.add(new Channel(id, name, g, logo == null ? "" : logo, epgId == null ? "" : epgId, url));
                name = null; group = null; logo = null; epgId = null;
            }
        }
        return out;
    }

    /** The channel name follows the first comma that is not inside a quoted attribute value. */
    static int firstCommaOutsideQuotes(String line) {
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') inQuotes = !inQuotes;
            else if (c == ',' && !inQuotes) return i;
        }
        return -1;
    }

    /** Value of key="..." in an #EXTINF line, or "" when absent. */
    static String attr(String line, String key) {
        String needle = key + "=\"";
        int i = line.indexOf(needle);
        if (i < 0) return "";
        int start = i + needle.length();
        int end = line.indexOf('"', start);
        if (end < 0) return "";
        return line.substring(start, end).trim();
    }
}
