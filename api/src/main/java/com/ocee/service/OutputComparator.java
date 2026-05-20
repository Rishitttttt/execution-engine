package com.ocee.service;

import java.util.ArrayList;
import java.util.List;

public final class OutputComparator {
    private OutputComparator() {}

    public static boolean matches(String actual, String expected) {
        if (expected == null) return true;
        if (actual == null) return false;
        return normalize(actual).equals(normalize(expected));
    }

    private static List<String> normalize(String s) {
        String[] lines = s.split("\n", -1);
        List<String> out = new ArrayList<>(lines.length);
        for (String l : lines) out.add(rstrip(l));
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    private static String rstrip(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }
}
