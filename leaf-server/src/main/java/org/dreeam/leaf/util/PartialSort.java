package org.dreeam.leaf.util;

public final class PartialSort {

    private static final int INSERTION_SORT_THRESHOLD = 12;
    private static final int PSEUDO_MEDIAN_REC_THRESHOLD = 64;

    public static void nthElement(final int[] indices, final double[] coord, int left, int right, final int k) {
        while (left < right) {
            final int len = right + 1 - left;
            if (len <= INSERTION_SORT_THRESHOLD) {
                insertionSort(indices, coord, left, right);
                return;
            }
            selectPivot(indices, coord, left, len);
            final int p = partition(indices, coord, left, right + 1, coord[indices[left]]);
            if (k < p) {
                right = p - 1;
            } else if (k != p) {
                left = p + 1;
            } else {
                return;
            }
        }
    }

    private static void insertionSort(final int[] indices, final double[] coord, final int left, final int right) {
        for (int i = left + 1; i <= right; i++) {
            final int key = indices[i];
            final double val = coord[key];
            int j = i - 1;

            while (j >= left && coord[indices[j]] > val) {
                indices[j + 1] = indices[j];
                j--;
            }
            indices[j + 1] = key;
        }
    }

    private static void selectPivot(final int[] indices, final double[] coord, final int a, final int len) {
        final int lenDiv8 = len / 8;
        final int b = a + lenDiv8 * 4;
        final int c = a + lenDiv8 * 7;
        if (len < PSEUDO_MEDIAN_REC_THRESHOLD) {
            final double va = coord[indices[a]];
            final double vb = coord[indices[b]];
            final double vc = coord[indices[c]];
            final boolean m = va < vb;
            final boolean n = va < vc;
            final int pivotIdx = m == n ? (vb < vc ^ m) ? c : b : a;
            final int tmp = indices[pivotIdx];
            indices[pivotIdx] = indices[a];
            indices[a] = tmp;
        } else {
            final int pivotIdx = med3Rec(indices, coord, a, b, c, lenDiv8);
            final int tmp = indices[pivotIdx];
            indices[pivotIdx] = indices[a];
            indices[a] = tmp;
        }
    }

    private static int partition(final int[] indices, final double[] x, final int start, final int end, final double pivot) {
        if (start >= end) {
            return 0;
        }

        int lt = start;
        int rt = start + 1;
        int gap = start;
        final int base = indices[start];

        final int unrollEnd = end - 1;
        while (rt < unrollEnd) {
            int rtVal = indices[rt];

            boolean rightIsLt = x[rtVal] < pivot;

            indices[gap] = indices[lt];
            indices[lt] = rtVal;

            gap = rt;
            lt += rightIsLt ? 1 : 0;
            rt++;
            rtVal = indices[rt];

            rightIsLt = x[rtVal] < pivot;

            indices[gap] = indices[lt];
            indices[lt] = rtVal;

            gap = rt;
            lt += rightIsLt ? 1 : 0;
            rt++;
        }

        while (true) {
            final boolean isDone = rt == end;
            final int rtVal = isDone ? base : indices[rt];

            final boolean rightIsLt = x[rtVal] < pivot;

            indices[gap] = indices[lt];
            indices[lt] = rtVal;

            gap = rt;
            lt += rightIsLt ? 1 : 0;
            rt++;

            if (isDone) {
                break;
            }
        }

        return lt;
    }

    private static int med3Rec(final int[] indices, final double[] x, int a, int b, int c, final int n) {
        if (n * 8 >= PSEUDO_MEDIAN_REC_THRESHOLD) {
            final int n8 = n / 8;
            a = med3Rec(indices, x, a, a + n8 * 4, a + n8 * 7, n8);
            b = med3Rec(indices, x, b, b + n8 * 4, b + n8 * 7, n8);
            c = med3Rec(indices, x, c, c + n8 * 4, c + n8 * 7, n8);
        }
        final double va = x[indices[a]];
        final double vb = x[indices[b]];
        final double vc = x[indices[c]];
        final boolean m = va < vb;
        final boolean n1 = va < vc;
        return m == n1 ? (vb < vc ^ m) ? c : b : a;
    }
}
