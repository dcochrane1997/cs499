package com.example.weighttracker;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * WeightAnalytics provides algorithmic analysis of weight entry data for the
 * Weight Tracker app. This class was created during the CS 499 Capstone
 * Enhancement Two (Algorithms and Data Structures) to add computational
 * intelligence to the app beyond simple CRUD operations.
 *
 * Enhancement: Algorithms and Data Structures (CS 499 Capstone)
 * Key algorithms implemented:
 * - WeightEntry: Structured data type encapsulating date-weight pairs
 * - Merge Sort: O(n log n) stable sorting by date or by weight value
 * - Binary Search: O(log n) lookup of entries by target date on sorted data
 * - Simple Moving Average (SMA): Smooths weight fluctuations over a configurable window
 * - Linear Regression: Least-squares trend line to project future weight
 *
 * Design trade-offs:
 * - Merge sort chosen over quicksort for stability (preserves insertion order of equal dates)
 * - Binary search requires sorted input, so it is always paired with sortByDate()
 * - SMA window size is configurable to balance responsiveness vs. smoothness
 * - Linear regression uses day-index encoding to avoid floating-point date arithmetic
 *
 * Course Outcome 3: Designs and evaluates computing solutions using algorithmic
 * principles while managing trade-offs involved in design choices.
 */
public class WeightAnalytics {

    // Date format consistent with the rest of the application
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat(InputValidator.DATE_FORMAT, Locale.US);

    // ==================== Data Structure ====================

    /**
     * WeightEntry encapsulates a single weight measurement with its date.
     * Implements Comparable for natural ordering by date.
     */
    public static class WeightEntry implements Comparable<WeightEntry> {
        private final int entryId;
        private final String dateString;
        private final double weight;
        private final long dateMillis;

        public WeightEntry(int entryId, String dateString, double weight) {
            this.entryId = entryId;
            this.dateString = dateString;
            this.weight = weight;
            this.dateMillis = parseDateToMillis(dateString);
        }

        public int getEntryId() { return entryId; }
        public String getDateString() { return dateString; }
        public double getWeight() { return weight; }
        public long getDateMillis() { return dateMillis; }

        @Override
        public int compareTo(WeightEntry other) {
            return Long.compare(this.dateMillis, other.dateMillis);
        }

        private static long parseDateToMillis(String dateStr) {
            try {
                DATE_FORMAT.setLenient(false);
                Date d = DATE_FORMAT.parse(dateStr);
                return d != null ? d.getTime() : 0;
            } catch (ParseException e) {
                return 0;
            }
        }
    }

    // ==================== Trend Result ====================

    /**
     * TrendResult encapsulates the output of linear regression analysis.
     */
    public static class TrendResult {
        private final double slope;
        private final double intercept;
        private final double projectedWeek;
        private final double projectedMonth;

        public TrendResult(double slope, double intercept,
                           double projectedWeek, double projectedMonth) {
            this.slope = slope;
            this.intercept = intercept;
            this.projectedWeek = projectedWeek;
            this.projectedMonth = projectedMonth;
        }

        public double getSlope() { return slope; }
        public double getIntercept() { return intercept; }
        public double getProjectedWeek() { return projectedWeek; }
        public double getProjectedMonth() { return projectedMonth; }

        public String getTrendDescription() {
            if (Math.abs(slope) < 0.01) return "Stable";
            return slope < 0 ? "Losing weight" : "Gaining weight";
        }
    }

    // ==================== Sorting Algorithms ====================

    /**
     * Sorts weight entries by date using merge sort.
     * O(n log n) time, O(n) space. Stable sort preserves insertion order of equal dates.
     */
    public static void sortByDate(List<WeightEntry> entries, boolean ascending) {
        if (entries == null || entries.size() <= 1) return;
        mergeSort(entries, 0, entries.size() - 1, ascending, true);
    }

    /**
     * Sorts weight entries by weight value using merge sort.
     */
    public static void sortByWeight(List<WeightEntry> entries, boolean ascending) {
        if (entries == null || entries.size() <= 1) return;
        mergeSort(entries, 0, entries.size() - 1, ascending, false);
    }

    private static void mergeSort(List<WeightEntry> entries, int left, int right,
                                   boolean ascending, boolean sortByDate) {
        if (left < right) {
            int mid = left + (right - left) / 2;
            mergeSort(entries, left, mid, ascending, sortByDate);
            mergeSort(entries, mid + 1, right, ascending, sortByDate);
            merge(entries, left, mid, right, ascending, sortByDate);
        }
    }

    private static void merge(List<WeightEntry> entries, int left, int mid, int right,
                               boolean ascending, boolean sortByDate) {
        List<WeightEntry> leftList = new ArrayList<>(entries.subList(left, mid + 1));
        List<WeightEntry> rightList = new ArrayList<>(entries.subList(mid + 1, right + 1));

        int i = 0, j = 0, k = left;
        while (i < leftList.size() && j < rightList.size()) {
            int comparison;
            if (sortByDate) {
                comparison = leftList.get(i).compareTo(rightList.get(j));
            } else {
                comparison = Double.compare(leftList.get(i).getWeight(),
                                            rightList.get(j).getWeight());
            }
            if (!ascending) comparison = -comparison;

            if (comparison <= 0) {
                entries.set(k++, leftList.get(i++));
            } else {
                entries.set(k++, rightList.get(j++));
            }
        }
        while (i < leftList.size()) entries.set(k++, leftList.get(i++));
        while (j < rightList.size()) entries.set(k++, rightList.get(j++));
    }

    // ==================== Search Algorithm ====================

    /**
     * Binary search to find a weight entry by date string.
     * Precondition: list MUST be sorted by date ascending.
     * O(log n) lookup vs O(n) for linear scan.
     */
    public static WeightEntry searchByDate(List<WeightEntry> entries, String targetDate) {
        if (entries == null || entries.isEmpty() || targetDate == null) return null;

        long targetMillis;
        try {
            DATE_FORMAT.setLenient(false);
            Date d = DATE_FORMAT.parse(targetDate);
            targetMillis = d != null ? d.getTime() : 0;
        } catch (ParseException e) {
            return null;
        }

        int low = 0, high = entries.size() - 1;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            long midMillis = entries.get(mid).getDateMillis();
            if (midMillis == targetMillis) return entries.get(mid);
            else if (midMillis < targetMillis) low = mid + 1;
            else high = mid - 1;
        }
        return null;
    }

    // ==================== Statistical Analysis ====================

    /**
     * Calculates a simple moving average over a sliding window.
     * Window size is configurable to balance responsiveness vs. smoothness.
     */
    public static List<Double> calculateMovingAverage(List<WeightEntry> entries, int windowSize) {
        List<Double> result = new ArrayList<>();
        if (entries == null || entries.size() < windowSize || windowSize <= 0) return result;

        double windowSum = 0;
        for (int i = 0; i < windowSize; i++) {
            windowSum += entries.get(i).getWeight();
        }
        result.add(windowSum / windowSize);

        for (int i = windowSize; i < entries.size(); i++) {
            windowSum += entries.get(i).getWeight() - entries.get(i - windowSize).getWeight();
            result.add(windowSum / windowSize);
        }
        return result;
    }

    /**
     * Performs least-squares linear regression on weight data.
     * Uses day-index encoding to avoid floating-point date arithmetic.
     */
    public static TrendResult calculateTrend(List<WeightEntry> entries) {
        if (entries == null || entries.size() < 2) return null;

        int n = entries.size();
        long baseMillis = entries.get(0).getDateMillis();
        double msPerDay = 86400000.0;

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            double x = (entries.get(i).getDateMillis() - baseMillis) / msPerDay;
            double y = entries.get(i).getWeight();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double denominator = n * sumX2 - sumX * sumX;
        if (Math.abs(denominator) < 1e-10) return null;

        double slope = (n * sumXY - sumX * sumY) / denominator;
        double intercept = (sumY - slope * sumX) / n;

        double lastDay = (entries.get(n - 1).getDateMillis() - baseMillis) / msPerDay;
        double projWeek = intercept + slope * (lastDay + 7);
        double projMonth = intercept + slope * (lastDay + 30);

        return new TrendResult(slope, intercept, projWeek, projMonth);
    }

    // ==================== Utility Methods ====================

    public static WeightEntry findMinWeight(List<WeightEntry> entries) {
        if (entries == null || entries.isEmpty()) return null;
        WeightEntry min = entries.get(0);
        for (int i = 1; i < entries.size(); i++) {
            if (entries.get(i).getWeight() < min.getWeight()) min = entries.get(i);
        }
        return min;
    }

    public static WeightEntry findMaxWeight(List<WeightEntry> entries) {
        if (entries == null || entries.isEmpty()) return null;
        WeightEntry max = entries.get(0);
        for (int i = 1; i < entries.size(); i++) {
            if (entries.get(i).getWeight() > max.getWeight()) max = entries.get(i);
        }
        return max;
    }

    public static double calculateAverage(List<WeightEntry> entries) {
        if (entries == null || entries.isEmpty()) return 0;
        double sum = 0;
        for (WeightEntry entry : entries) sum += entry.getWeight();
        return sum / entries.size();
    }
}
