package com.hd.authz.common;

import java.util.ArrayList;
import java.util.List;

public final class UrlUtils {
    private UrlUtils() {}

    public static List<String> segments(String url) {
        List<String> out = new ArrayList<>();
        if (url == null) return out;
        int q = url.indexOf('?');
        String path = q >= 0 ? url.substring(0, q) : url;
        for (String s : path.split("/")) {
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    public static int depth(String url) {
        return segments(url).size();
    }

    public static String normalize(String url) {
        if (url == null) return "";
        int q = url.indexOf('?');
        String p = q >= 0 ? url.substring(0, q) : url;
        if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }
}
