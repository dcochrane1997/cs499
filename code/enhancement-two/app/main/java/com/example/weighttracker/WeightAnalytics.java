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
     * This structured data type replaces the loose coupling of parallel arrays
     * or cursor-based access, making the data sortable and searchable.
     *
     * Implements Comparable for natural ordering by date, which enables
     * efficient use with sorting and searching algorithms.
     */
    public static class WeightEntry implements Comparable<WeightEntry> {
        private final int entryId;
        private final String dateString;
        private final double weight;
        private final long dateMillis; // Parsed date for numeric comparison

        /**
         * Constructs a WeightEntry from database fields.
         *
         * @param entryId    The database primary key for this entry
         * @param dateString The date in MM/dd/yyyy format
         * @param weight     The weight measurement in pounds
         */
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

        /**
         * Natural ordering is by date (ascending), enabling binary search by date.
         */
        @Override
        public int compareTo(WeightEntry other) {
            return Long.compare(this.dateMillis, other.dateMillis);
        }

        /**
         * Parses a date string to milliseconds since epoch for numeric comparison.
         * Returns 0 if parsing fails, placing unparseable dates at the beginning.
         */
        private static long parseDateToMillis(String dateStr) {
            try {
                DATE_FORMAT.setLenient(false);
                Date parsed = DATE_FORMAT.parse(dateStr);
                return parsed != null ? parsed.getTime() : 0;
            } catch (ParseException e) {
                return 0;
            }
        }
    }

    // ==================== Sorting Algorithms ====================

    /**
     * Sorts a list of WeightEntry objects by date using merge sort.
     *
     * Algorithm choice rationale:
     * - Merge sort guarantees O(n log n) time in all cases (worst, average, best)
     * - Stable sort preserves the relative order of entries with the same date,
     *   which is important if a user logs multiple entries on the same day
     * - Preferred over quicksort because quicksort's O(n^2) worst case could
     *   occur with already-sorted data, which is common in a weight tracker
     *
     * Trade-off: Merge sort uses O(n) extra space for temporary arrays,
     * but for the expected dataset size (hundreds of entries, not millions),
     * this is negligible on a modern Android device.
     *
     * @param entries The list of entries to sort (modified in place)
     * @param ascending True for oldest-first, false for newest-first
     */
    public static void sortByDate(List<WeightEntry> entries, boolean ascending) {
        if (entries == null || entries.size() <= 1) {
            return; // Base case: already sorted
        }
        mergeSort(entries, 0, entries.size() - 1, ascending, true);
    }

    /**
     * Sorts a list of WeightEntry objects by weight value using merge sort.
     *
     * This allows the user to view their entries ordered from lightest to heaviest
     * (or vice versa), which is useful for identifying their lowest and highest
     * recorded weights at a glance.
     *
     * @param entries The list of entries to sort (modified in place)
     * @param ascending True for lightest-first, false for heaviest-first
     */
    public static void sortByWeight(List<WeightEntry> entries, boolean ascending) {
        if (entries == null || entries.size() <= 1) {
            return;
        }
        mergeSort(entries, 0, entries.size() - 1, ascending, false);
    }

    /**
     * Recursive merge sort implementation that works for both date and weight sorting.
     * The sortByDate flag determines which field is used for comparison.
     *
     * Time complexity: O(n log n) in all cases
     * Space complexity: O(n) for the temporary merge arrays
     *
     * @param entries   The list being sorted
     * @param left      Left boundary of the current subarray
     * @param right     Right boundary of the current subarray
     * @param ascending Sort direction
     * @param sortByDate True to compare by date, false to compare by weight
     */
    private static void mergeSort(List<WeightEntry> entries, int left, int right,
                                   boolean ascending, boolean sortByDate) {
        if (left < right) {
            int mid = left + (right - left) / 2; // Avoids integer overflow

            // Recursively sort the two halves
            mergeSort(entries, left, mid, ascending, sortByDate);
            mergeSort(entries, mid + 1, right, ascending, sortByDate);

            // Merge the sorted halves back together
            merge(entries, left, mid, right, ascending, sortByDate);
        }
    }

    /**
     * Merges two sorted subarrays back into the main list.
     * This is the core operation of merge sort where the actual comparisons happen.
     *
     * @param entries   The list being sorted
     * @param left      Start index of the left subarray
     * @param mid       End index of the left subarray (mid+1 starts the right)
     * @param right     End index of the right subarray
     * @param ascending Sort direction
     * @param sortByDate True to compare by date, false to compare by weight
     */
    private static void merge(List<WeightEntry> entries, int left, int mid, int right,
                               boolean ascending, boolean sortByDate) {
        // Create temporary lists for the two halves
        List<WeightEntry> leftList = new ArrayList<>(entries.subList(left, mid + 1));
        List<WeightEntry> rightList = new ArrayList<>(entries.subList(mid + 1, right + 1));

        int i = 0, j = 0, k = left;

        // Compare elements from both halves and place the smaller (or larger) one first
        while (i < leftList.size() && j < rightList.size()) {
            int comparison;
            if (sortByDate) {
                comparison = leftList.get(i).compareTo(rightList.get(j));
            } else {
                comparison = Double.compare(leftList.get(i).getWeight(),
                                            rightList.get(j).getWeight());
            }

            // Reverse comparison for descending order
            if (!ascending) {
                comparison = -comparison;
            }

            if (comparison <= 0) {
                entries.set(k++, leftList.get(i++));
            } else {
                entries.set(k++, rightList.get(j++));
            }
        }

        // Copy any remaining elements from the left half
        while (i < leftList.size()) {
            entries.set(k++, leftList.get(i++));
        }

        // Copy any remaining elements from the right half
        while (j < rightList.size()) {
            entries.set(k++, rightList.get(j++));
        }
    }

    // ==================== Search Algorithm ====================

    /**
     * Performs binary search to find a weight entry by date string.
     *
     * Precondition: The list MUST be sorted by date in ascending order.
     * Call sortByDate(entries, true) before using this method.
     *
     * Algorithm choice rationale:
     * - Binary search provides O(log n) lookup vs. O(n) for linear scan
     * - For a user with 365 entries (one year of daily tracking), binary search
     *   requires at most 9 comparisons vs. 365 for linear search
     * - The requirement for sorted input is acceptable because entries are
     *   typically displayed in date order anyway
     *
     * Trade-off: If the list is not sorted, this method will return incorrect
     * results. The caller must ensure sorted order, which is enforced by
     * requiring sortByDate() to be called first.
     *
     * @param entries The date-sorted list of entries to search
     * @param targetDate The date string to find (MM/dd/yyyy format)
     * @return The index of the matching entry, or -1 if not found
     */
    public static int binarySearchByDate(List<WeightEntry> entries, String targetDate) {
        if (entries == null || entries.isEmpty() || targetDate == null) {
            return -1;
        }

        long targetMillis = WeightEntry.parseDateToMillis(targetDate);
        if (targetMillis == 0) {
            return -1; // Invalid date format
        }

        int low = 0;
        int high = entries.size() - 1;

        while (low <= high) {
            int mid = low + (high - low) / 2; // Avoids integer overflow
            long midMillis = entries.get(mid).getDateMillis();

            if (midMillis == targetMillis) {
                return mid; // Found the target date
            } else if (midMillis < targetMillis) {
                low = mid + 1; // Target is in the right half
            } else {
                high = mid - 1; // Target is in the left half
            }
        }

        return -1; // Date not found in the list
    }

    // ==================== Statistical Algorithms ====================

    /**
     * Calculates the Simple Moving Average (SMA) for a list of weight entries.
     *
     * The SMA smooths out daily weight fluctuations (caused by water retention,
     * meal timing, etc.) to reveal the underlying trend. Each point in the
     * result represents the average of the surrounding 'windowSize' entries.
     *
     * Algorithm: For each position i where i >= windowSize - 1, compute the
     * average of entries[i - windowSize + 1] through entries[i].
     *
     * Time complexity: O(n) using a sliding window approach — each entry is
     * added once and removed once from the running sum, avoiding the O(n * w)
     * cost of recalculating each window from scratch.
     *
     * Trade-off: A larger window produces a smoother curve but is slower to
     * respond to real weight changes. A window of 7 (one week) balances
     * smoothness with responsiveness for daily weigh-ins.
     *
     * Precondition: Entries should be sorted by date for meaningful results.
     *
     * @param entries The list of weight entries (should be date-sorted)
     * @param windowSize The number of entries to average (e.g., 7 for weekly)
     * @return A list of averaged values, one per entry starting from index windowSize-1.
     *         Returns an empty list if there are fewer entries than the window size.
     */
    public static List<Double> calculateMovingAverage(List<WeightEntry> entries,
                                                       int windowSize) {
        List<Double> averages = new ArrayList<>();

        if (entries == null || entries.size() < windowSize || windowSize <= 0) {
            return averages; // Not enough data points for the requested window
        }

        // Initialize the running sum with the first 'windowSize' entries
        double windowSum = 0;
        for (int i = 0; i < windowSize; i++) {
            windowSum += entries.get(i).getWeight();
        }
        averages.add(windowSum / windowSize);

        // Slide the window forward: add the new entry, subtract the old one
        // This keeps each step O(1) instead of recalculating the full window
        for (int i = windowSize; i < entries.size(); i++) {
            windowSum += entries.get(i).getWeight();        // Add new entry
            windowSum -= entries.get(i - windowSize).getWeight(); // Remove oldest
            averages.add(windowSum / windowSize);
        }

        return averages;
    }

    /**
     * Performs linear regression on weight entries to calculate a trend line.
     *
     * This uses the least-squares method to find the line of best fit through
     * the weight data points. The x-axis represents day index (0, 1, 2, ...)
     * and the y-axis represents weight in pounds.
     *
     * Mathematical basis (least-squares formula):
     *   slope = (n * sum(x*y) - sum(x) * sum(y)) / (n * sum(x^2) - sum(x)^2)
     *   intercept = (sum(y) - slope * sum(x)) / n
     *
     * The slope indicates the daily rate of weight change:
     *   - Negative slope = losing weight (trending down)
     *   - Positive slope = gaining weight (trending up)
     *   - Near-zero slope = weight is stable
     *
     * Trade-off: Linear regression assumes a linear relationship, which may
     * not capture plateaus or non-linear patterns. However, for short-to-medium
     * term weight tracking (weeks to a few months), a linear model provides
     * a clear, interpretable trend that is easy to communicate to the user.
     *
     * Precondition: Entries should be sorted by date for meaningful day-indexing.
     *
     * @param entries The list of weight entries (should be date-sorted)
     * @return A TrendResult containing slope, intercept, and projected values,
     *         or null if there are fewer than 2 data points
     */
    public static TrendResult calculateTrend(List<WeightEntry> entries) {
        if (entries == null || entries.size() < 2) {
            return null; // Need at least 2 points to calculate a trend
        }

        int n = entries.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        // Use day index (0, 1, 2, ...) as the x-value to avoid date arithmetic
        for (int i = 0; i < n; i++) {
            double x = i;                         // Day index
            double y = entries.get(i).getWeight(); // Weight value

            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        // Calculate the denominator (check for zero to avoid division by zero)
        double denominator = n * sumX2 - sumX * sumX;
        if (Math.abs(denominator) < 1e-10) {
            // All x-values are the same (only one unique day) — cannot compute slope
            return null;
        }

        // Least-squares formulas
        double slope = (n * sumXY - sumX * sumY) / denominator;
        double intercept = (sumY - slope * sumX) / n;

        // Generate the fitted values for each data point
        List<Double> fittedValues = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            fittedValues.add(slope * i + intercept);
        }

        // Project the trend forward by 7 days and 30 days
        double projectedWeek = slope * (n + 6) + intercept;
        double projectedMonth = slope * (n + 29) + intercept;

        return new TrendResult(slope, intercept, fittedValues,
                               projectedWeek, projectedMonth);
    }

    // ==================== Result Data Structures ====================

    /**
     * TrendResult encapsulates the output of linear regression analysis.
     * It provides the mathematical parameters of the trend line as well as
     * human-readable projections for practical use.
     */
    public static class TrendResult {
        private final double slope;           // Daily weight change (lbs/day)
        private final double intercept;       // Starting weight of trend line
        private final List<Double> fittedValues; // Trend line value at each data point
        private final double projectedWeek;   // Projected weight in 7 days
        private final double projectedMonth;  // Projected weight in 30 days

        public TrendResult(double slope, double intercept, List<Double> fittedValues,
                           double projectedWeek, double projectedMonth) {
            this.slope = slope;
            this.intercept = intercept;
            this.fittedValues = fittedValues;
            this.projectedWeek = projectedWeek;
            this.projectedMonth = projectedMonth;
        }

        public double getSlope() { return slope; }
        public double getIntercept() { return intercept; }
        public List<Double> getFittedValues() { return fittedValues; }
        public double getProjectedWeek() { return projectedWeek; }
        public double getProjectedMonth() { return projectedMonth; }

        /**
         * Returns a human-readable description of the weight trend.
         * Classifies the trend as losing, gaining, or maintaining based on
         * the slope magnitude, using a threshold of 0.01 lbs/day to filter
         * out noise.
         */
        public String getTrendDescription() {
            double weeklyChange = slope * 7;
            if (slope < -0.01) {
                return String.format(Locale.US,
                        "Losing ~%.1f lbs/week", Math.abs(weeklyChange));
            } else if (slope > 0.01) {
                return String.format(Locale.US,
                        "Gaining ~%.1f lbs/week", weeklyChange);
            } else {
                return "Weight is stable";
            }
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Finds the minimum weight entry in a list.
     * Uses a single-pass linear scan: O(n) time, O(1) space.
     *
     * @param entries The list of weight entries to search
     * @return The entry with the lowest weight, or null if the list is empty
     */
    public static WeightEntry findMinWeight(List<WeightEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        WeightEntry min = entries.get(0);
        for (int i = 1; i < entries.size(); i++) {
            if (entries.get(i).getWeight() < min.getWeight()) {
                min = entries.get(i);
            }
        }
        return min;
    }

    /**
     * Finds the maximum weight entry in a list.
     * Uses a single-pass linear scan: O(n) time, O(1) space.
     *
     * @param entries The list of weight entries to search
     * @return The entry with the highest weight, or null if the list is empty
     */
    public static WeightEntry findMaxWeight(List<WeightEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        WeightEntry max = entries.get(0);
        for (int i = 1; i < entries.size(); i++) {
            if (entries.get(i).getWeight() > max.getWeight()) {
                max = entries.get(i);
            }
        }
        return max;
    }

    /**
     * Calculates the average weight across all entries.
     * Uses a running sum for O(n) time with no additional data structures.
     *
     * @param entries The list of weight entries
     * @return The arithmetic mean weight, or 0 if the list is empty
     */
    public static double calculateAverage(List<WeightEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (WeightEntry entry : entries) {
            sum += entry.getWeight();
        }
        return sum / entries.size();
    }
}
