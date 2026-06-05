package com.dylanjohnpratt.paradise.be.health.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Orders a metric's parallel {@code labels} / {@code data} / {@code datasets[*].data}
 * arrays ascending by label. Labels are the user-supplied x-axis values
 * (typically {@code YYYY-MM-DD} dates), so lexicographic order matches chronological order.
 */
public final class HealthMetricPointSorter {

    private HealthMetricPointSorter() {}

    /**
     * Returns indices that would sort {@code labels} ascending, or {@code null} if
     * the list is null/empty or already sorted (callers can skip the reorder).
     */
    public static int[] sortIndicesByLabelAscending(List<String> labels) {
        if (labels == null || labels.size() < 2) {
            return null;
        }
        int n = labels.size();
        Integer[] boxed = new Integer[n];
        for (int i = 0; i < n; i++) {
            boxed[i] = i;
        }
        java.util.Arrays.sort(boxed, (a, b) -> {
            String la = labels.get(a);
            String lb = labels.get(b);
            if (la == null && lb == null) return 0;
            if (la == null) return -1;
            if (lb == null) return 1;
            return la.compareTo(lb);
        });
        boolean changed = false;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = boxed[i];
            if (out[i] != i) changed = true;
        }
        return changed ? out : null;
    }

    /** Returns a new list with elements reordered by {@code indices}. */
    public static <T> List<T> applyIndices(List<T> source, int[] indices) {
        List<T> out = new ArrayList<>(indices.length);
        for (int idx : indices) {
            out.add(source.get(idx));
        }
        return out;
    }
}
