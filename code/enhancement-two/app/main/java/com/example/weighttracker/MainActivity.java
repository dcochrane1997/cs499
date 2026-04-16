package com.example.weighttracker;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity is the primary screen of the Weight Tracker app.
 * It displays the user's weight entries in a scrollable grid, allows adding,
 * editing, and deleting entries via the SQLite database, and triggers SMS
 * notifications when the user reaches their goal weight.
 *
 * Enhancement One: Software Engineering and Design (CS 499 Capstone)
 * - Input validation delegated to InputValidator class (separation of concerns)
 * - Date format enforced as MM/DD/YYYY with strict parsing
 * - Weight values validated against realistic min/max range
 * - Cursor management improved with try-finally blocks to prevent leaks
 * - Event handlers extracted to named methods for readability
 *
 * Enhancement Two: Algorithms and Data Structures (CS 499 Capstone)
 * - Added WeightAnalytics integration for sorting, searching, and trend analysis
 * - Statistics summary panel shows min, max, average, trend, and projections
 * - Sort controls allow viewing entries by date or by weight (asc/desc)
 * - Moving average smooths daily fluctuations for clearer trend visibility
 * - Binary search enables efficient date-based entry lookup
 */
public class MainActivity extends AppCompatActivity {

    // UI element references
    private EditText editDate;
    private EditText editWeight;
    private Button buttonAddWeight;
    private LinearLayout gridContainer;
    private TextView textGoalWeight;
    private Button buttonSetGoal;
    private Button buttonNavHome;
    private Button buttonNavNotifications;

    // Enhancement Two: Statistics and sort UI elements
    private TextView textStatsSummary;
    private TextView textTrendInfo;
    private Button buttonSortDateAsc;
    private Button buttonSortDateDesc;
    private Button buttonSortWeightAsc;
    private Button buttonSortWeightDesc;
    private Button buttonToggleStats;
    private LinearLayout statsContainer;
    private LinearLayout sortButtonsContainer;

    // Database helper for persistent data storage
    private DatabaseHelper databaseHelper;

    // Current logged-in user's ID, passed from LoginActivity
    private int currentUserId;

    // Enhancement Two: Cached list of entries for algorithmic operations
    // This avoids re-querying the database for each sort or analysis operation
    private List<WeightAnalytics.WeightEntry> cachedEntries = new ArrayList<>();

    // Enhancement Two: Tracks the current sort state for UI feedback
    private String currentSortMode = "date_asc";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Retrieve the user ID and username from the login intent
        currentUserId = getIntent().getIntExtra("USER_ID", -1);

        // Initialize the database helper
        databaseHelper = new DatabaseHelper(this);

        // Initialize UI elements by finding views from the layout
        editDate = findViewById(R.id.editDate);
        editWeight = findViewById(R.id.editWeight);
        buttonAddWeight = findViewById(R.id.buttonAddWeight);
        gridContainer = findViewById(R.id.gridContainer);
        textGoalWeight = findViewById(R.id.textGoalWeight);
        buttonSetGoal = findViewById(R.id.buttonSetGoal);
        buttonNavHome = findViewById(R.id.buttonNavHome);
        buttonNavNotifications = findViewById(R.id.buttonNavNotifications);

        // Enhancement Two: Initialize analytics UI elements
        textStatsSummary = findViewById(R.id.textStatsSummary);
        textTrendInfo = findViewById(R.id.textTrendInfo);
        buttonSortDateAsc = findViewById(R.id.buttonSortDateAsc);
        buttonSortDateDesc = findViewById(R.id.buttonSortDateDesc);
        buttonSortWeightAsc = findViewById(R.id.buttonSortWeightAsc);
        buttonSortWeightDesc = findViewById(R.id.buttonSortWeightDesc);
        buttonToggleStats = findViewById(R.id.buttonToggleStats);
        statsContainer = findViewById(R.id.statsContainer);
        sortButtonsContainer = findViewById(R.id.sortButtonsContainer);

        // Load the user's goal weight from the database
        loadGoalWeight();

        // Load all existing weight entries from the database into the grid
        loadWeightEntries();

        // Enhancement: Set hint to show expected date format
        editDate.setHint("Date (MM/DD/YYYY)");

        // Add weight entry button - saves new entry to database and refreshes the grid
        buttonAddWeight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleAddWeight();
            }
        });

        // Set Goal button - opens a dialog for the user to enter their goal weight
        buttonSetGoal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showGoalWeightDialog();
            }
        });

        // Bottom navigation - Notifications button navigates to SMS settings
        buttonNavNotifications.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SmsSettingsActivity.class);
                intent.putExtra("USER_ID", currentUserId);
                startActivity(intent);
            }
        });

        // Enhancement Two: Sort button listeners
        // Each button sorts the cached entries using WeightAnalytics merge sort
        // then refreshes the grid display without re-querying the database
        buttonSortDateAsc.setOnClickListener(v -> applySortAndRefresh("date_asc"));
        buttonSortDateDesc.setOnClickListener(v -> applySortAndRefresh("date_desc"));
        buttonSortWeightAsc.setOnClickListener(v -> applySortAndRefresh("weight_asc"));
        buttonSortWeightDesc.setOnClickListener(v -> applySortAndRefresh("weight_desc"));

        // Enhancement Two: Toggle statistics panel visibility
        buttonToggleStats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleStatsVisibility();
            }
        });
    }

    /**
     * Handles the Add Weight button click.
     * Enhancement One: Uses InputValidator for both date and weight validation.
     * Enhancement Two: Refreshes analytics after adding a new entry.
     */
    private void handleAddWeight() {
        String date = editDate.getText().toString().trim();
        String weightStr = editWeight.getText().toString().trim();

        // Validate date using centralized InputValidator
        InputValidator.ValidationResult dateResult =
                InputValidator.validateDate(date);
        if (!dateResult.isValid()) {
            Toast.makeText(MainActivity.this,
                    dateResult.getErrorMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate weight using centralized InputValidator
        InputValidator.ValidationResult weightResult =
                InputValidator.validateWeight(weightStr);
        if (!weightResult.isValid()) {
            Toast.makeText(MainActivity.this,
                    weightResult.getErrorMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        double weight = Double.parseDouble(weightStr);

        // Add the new entry to the database
        if (databaseHelper.addWeightEntry(currentUserId, date, weight)) {
            Toast.makeText(MainActivity.this,
                    "Weight entry added", Toast.LENGTH_SHORT).show();

            // Clear input fields after successful add
            editDate.setText("");
            editWeight.setText("");

            // Refresh the grid and analytics to include the new entry
            loadWeightEntries();

            // Check if the user has reached their goal weight
            checkGoalWeight(weight);
        } else {
            Toast.makeText(MainActivity.this,
                    "Error adding entry", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Refreshes the goal weight display when returning from another activity.
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadGoalWeight();
    }

    /**
     * Loads the user's goal weight from the database and updates the display.
     */
    private void loadGoalWeight() {
        double goalWeight = databaseHelper.getGoalWeight(currentUserId);
        if (goalWeight > 0) {
            textGoalWeight.setText(String.format("%.1f lbs", goalWeight));
        } else {
            textGoalWeight.setText("Not set");
        }
    }

    /**
     * READ operation: Loads all weight entries from the database, caches them
     * as WeightEntry objects for algorithmic operations, displays them in the grid,
     * and updates the statistics summary.
     *
     * Enhancement One: Added try-finally for guaranteed cursor cleanup.
     * Enhancement Two: Builds the cachedEntries list and triggers analytics update.
     */
    private void loadWeightEntries() {
        // Clear all existing rows from the grid before reloading
        gridContainer.removeAllViews();

        // Enhancement Two: Clear and rebuild the cached entries list
        cachedEntries.clear();

        // Query the database for all weight entries belonging to this user
        Cursor cursor = databaseHelper.getAllWeightEntries(currentUserId);

        // Enhancement: try-finally ensures cursor is always closed
        try {
            // Iterate through each entry and build both the grid and the cache
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int entryId = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ENTRY_ID));
                    String date = cursor.getString(
                            cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_DATE));
                    double weight = cursor.getDouble(
                            cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_WEIGHT));

                    // Enhancement Two: Add to the structured cache for algorithmic use
                    cachedEntries.add(
                            new WeightAnalytics.WeightEntry(entryId, date, weight));

                    // Add a visual row to the grid for this database entry
                    addWeightRow(entryId, date, String.format("%.1f", weight));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // Enhancement Two: Update statistics after loading entries
        updateStatistics();
    }

    // ==================== Enhancement Two: Analytics Methods ====================

    /**
     * Applies the selected sort algorithm to the cached entries and refreshes
     * the grid display. Uses WeightAnalytics merge sort to avoid re-querying
     * the database, which is more efficient for repeated sort operations.
     *
     * Enhancement Two: Demonstrates algorithmic sorting with O(n log n) merge sort.
     *
     * @param sortMode The sort mode string: "date_asc", "date_desc",
     *                 "weight_asc", or "weight_desc"
     */
    private void applySortAndRefresh(String sortMode) {
        currentSortMode = sortMode;

        // Apply the appropriate merge sort based on the selected mode
        switch (sortMode) {
            case "date_asc":
                WeightAnalytics.sortByDate(cachedEntries, true);
                break;
            case "date_desc":
                WeightAnalytics.sortByDate(cachedEntries, false);
                break;
            case "weight_asc":
                WeightAnalytics.sortByWeight(cachedEntries, true);
                break;
            case "weight_desc":
                WeightAnalytics.sortByWeight(cachedEntries, false);
                break;
        }

        // Rebuild the grid from the sorted cache (no database query needed)
        gridContainer.removeAllViews();
        for (WeightAnalytics.WeightEntry entry : cachedEntries) {
            addWeightRow(entry.getEntryId(), entry.getDateString(),
                         String.format("%.1f", entry.getWeight()));
        }

        // Update sort button visual feedback
        updateSortButtonHighlight(sortMode);
    }

    /**
     * Updates the visual state of sort buttons to indicate the active sort.
     * The active button gets a highlighted color while others are neutral.
     *
     * @param activeMode The currently active sort mode
     */
    private void updateSortButtonHighlight(String activeMode) {
        int activeColor = 0xFF1565C0;  // Blue for active
        int normalColor = 0xFF757575;  // Grey for inactive

        buttonSortDateAsc.setTextColor(
                activeMode.equals("date_asc") ? activeColor : normalColor);
        buttonSortDateDesc.setTextColor(
                activeMode.equals("date_desc") ? activeColor : normalColor);
        buttonSortWeightAsc.setTextColor(
                activeMode.equals("weight_asc") ? activeColor : normalColor);
        buttonSortWeightDesc.setTextColor(
                activeMode.equals("weight_desc") ? activeColor : normalColor);
    }

    /**
     * Computes and displays statistical analysis of the user's weight data.
     * Uses WeightAnalytics methods for all calculations:
     * - Min/max weight (linear scan)
     * - Average weight (running sum)
     * - 7-day moving average (sliding window)
     * - Linear regression trend with weekly/monthly projections
     *
     * Enhancement Two: Core demonstration of algorithmic data analysis.
     */
    private void updateStatistics() {
        if (cachedEntries.isEmpty()) {
            textStatsSummary.setText("No entries yet. Add weight data to see statistics.");
            textTrendInfo.setText("");
            return;
        }

        // Sort entries by date for accurate trend analysis
        List<WeightAnalytics.WeightEntry> dateSorted = new ArrayList<>(cachedEntries);
        WeightAnalytics.sortByDate(dateSorted, true);

        // Calculate basic statistics using WeightAnalytics utility methods
        WeightAnalytics.WeightEntry minEntry = WeightAnalytics.findMinWeight(dateSorted);
        WeightAnalytics.WeightEntry maxEntry = WeightAnalytics.findMaxWeight(dateSorted);
        double avgWeight = WeightAnalytics.calculateAverage(dateSorted);

        // Build the summary string
        StringBuilder stats = new StringBuilder();
        stats.append(String.format("Entries: %d", dateSorted.size()));
        if (minEntry != null && maxEntry != null) {
            stats.append(String.format("\nLow: %.1f lbs (%s)",
                    minEntry.getWeight(), minEntry.getDateString()));
            stats.append(String.format("\nHigh: %.1f lbs (%s)",
                    maxEntry.getWeight(), maxEntry.getDateString()));
        }
        stats.append(String.format("\nAverage: %.1f lbs", avgWeight));

        // Calculate 7-day moving average if enough data exists
        List<Double> movingAvg = WeightAnalytics.calculateMovingAverage(dateSorted, 7);
        if (!movingAvg.isEmpty()) {
            double latestSma = movingAvg.get(movingAvg.size() - 1);
            stats.append(String.format("\n7-Day Moving Avg: %.1f lbs", latestSma));
        }

        textStatsSummary.setText(stats.toString());

        // Calculate trend using linear regression
        WeightAnalytics.TrendResult trend = WeightAnalytics.calculateTrend(dateSorted);
        if (trend != null) {
            StringBuilder trendText = new StringBuilder();
            trendText.append("Trend: ").append(trend.getTrendDescription());
            trendText.append(String.format("\n7-day projection: %.1f lbs",
                    trend.getProjectedWeek()));
            trendText.append(String.format("\n30-day projection: %.1f lbs",
                    trend.getProjectedMonth()));
            textTrendInfo.setText(trendText.toString());
        } else {
            textTrendInfo.setText("Add more entries to see trend analysis.");
        }
    }

    /**
     * Toggles the visibility of the statistics panel.
     * Enhancement Two: Allows users to show/hide analytics to keep the UI clean.
     */
    private void toggleStatsVisibility() {
        if (statsContainer.getVisibility() == View.VISIBLE) {
            statsContainer.setVisibility(View.GONE);
            buttonToggleStats.setText("Show Stats");
        } else {
            statsContainer.setVisibility(View.VISIBLE);
            buttonToggleStats.setText("Hide Stats");
            // Refresh statistics when panel is shown
            updateStatistics();
        }
    }

    /**
     * Creates and adds a single weight entry row to the data grid.
     * Each row displays the date and weight, with delete and edit buttons.
     * The row is linked to the database via the entry ID.
     *
     * @param entryId The database ID of the weight entry
     * @param date    The date string to display
     * @param weight  The weight string to display
     */
    private void addWeightRow(int entryId, String date, String weight) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(32, 24, 32, 24);
        row.setBackgroundColor(0xFFFFFFFF);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setTag(entryId);

        TextView dateText = new TextView(this);
        LinearLayout.LayoutParams dateParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2);
        dateText.setLayoutParams(dateParams);
        dateText.setText(date);
        dateText.setTextSize(14);
        dateText.setTextColor(0xFF424242);

        TextView weightText = new TextView(this);
        LinearLayout.LayoutParams weightParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2);
        weightText.setLayoutParams(weightParams);
        weightText.setText(weight);
        weightText.setTextSize(14);
        weightText.setTextColor(0xFF424242);

        LinearLayout actionsContainer = new LinearLayout(this);
        LinearLayout.LayoutParams actionsParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        actionsContainer.setLayoutParams(actionsParams);
        actionsContainer.setOrientation(LinearLayout.HORIZONTAL);
        actionsContainer.setGravity(android.view.Gravity.CENTER);

        ImageButton editButton = new ImageButton(this);
        LinearLayout.LayoutParams editBtnParams = new LinearLayout.LayoutParams(80, 80);
        editBtnParams.setMarginEnd(8);
        editButton.setLayoutParams(editBtnParams);
        editButton.setImageResource(android.R.drawable.ic_menu_edit);
        editButton.setBackgroundColor(0x00000000);
        editButton.setContentDescription("Edit entry");
        editButton.setOnClickListener(v -> showEditEntryDialog(entryId, date, weight));

        ImageButton deleteButton = new ImageButton(this);
        deleteButton.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
        deleteButton.setImageResource(android.R.drawable.ic_menu_delete);
        deleteButton.setBackgroundColor(0x00000000);
        deleteButton.setContentDescription("Delete entry");
        deleteButton.setOnClickListener(v ->
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("Delete Entry")
                .setMessage("Are you sure you want to delete this weight entry?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (databaseHelper.deleteWeightEntry(entryId)) {
                        Toast.makeText(MainActivity.this,
                                "Entry deleted", Toast.LENGTH_SHORT).show();
                        loadWeightEntries();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show()
        );

        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(0xFFE0E0E0);

        actionsContainer.addView(editButton);
        actionsContainer.addView(deleteButton);
        row.addView(dateText);
        row.addView(weightText);
        row.addView(actionsContainer);

        if (gridContainer.getChildCount() > 0) {
            gridContainer.addView(divider);
        }
        gridContainer.addView(row);
    }

    /**
     * UPDATE operation: Shows a dialog that allows the user to edit the date
     * and weight of an existing entry, then updates the database.
     */
    private void showEditEntryDialog(int entryId, String currentDate, String currentWeight) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Weight Entry");

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(48, 32, 48, 16);

        EditText dateInput = new EditText(this);
        dateInput.setHint("Date (MM/DD/YYYY)");
        dateInput.setText(currentDate);
        dateInput.setInputType(android.text.InputType.TYPE_CLASS_DATETIME);
        dialogLayout.addView(dateInput);

        EditText weightInput = new EditText(this);
        weightInput.setHint("Weight (lbs)");
        weightInput.setText(currentWeight);
        weightInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        dialogLayout.addView(weightInput);

        builder.setView(dialogLayout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newDate = dateInput.getText().toString().trim();
            String newWeightStr = weightInput.getText().toString().trim();

            InputValidator.ValidationResult dateResult =
                    InputValidator.validateDate(newDate);
            if (!dateResult.isValid()) {
                Toast.makeText(this, dateResult.getErrorMessage(),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            InputValidator.ValidationResult weightResult =
                    InputValidator.validateWeight(newWeightStr);
            if (!weightResult.isValid()) {
                Toast.makeText(this, weightResult.getErrorMessage(),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            double newWeight = Double.parseDouble(newWeightStr);

            if (databaseHelper.updateWeightEntry(entryId, newDate, newWeight)) {
                Toast.makeText(this, "Entry updated",
                        Toast.LENGTH_SHORT).show();
                loadWeightEntries();
                checkGoalWeight(newWeight);
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Shows a dialog for the user to set or update their goal weight.
     */
    private void showGoalWeightDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Goal Weight");

        EditText goalInput = new EditText(this);
        goalInput.setHint("Enter goal weight (lbs)");
        goalInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        goalInput.setPadding(48, 32, 48, 16);

        double currentGoal = databaseHelper.getGoalWeight(currentUserId);
        if (currentGoal > 0) {
            goalInput.setText(String.format("%.1f", currentGoal));
        }

        builder.setView(goalInput);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String goalStr = goalInput.getText().toString().trim();

            InputValidator.ValidationResult goalResult =
                    InputValidator.validateGoalWeight(goalStr);
            if (!goalResult.isValid()) {
                Toast.makeText(this, goalResult.getErrorMessage(),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            double goalWeight = Double.parseDouble(goalStr);
            databaseHelper.updateGoalWeight(currentUserId, goalWeight);
            textGoalWeight.setText(String.format("%.1f lbs", goalWeight));
            Toast.makeText(this, "Goal weight updated",
                    Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Checks if the entered weight has reached the user's goal weight.
     */
    private void checkGoalWeight(double currentWeight) {
        double goalWeight = databaseHelper.getGoalWeight(currentUserId);
        if (goalWeight <= 0) {
            return;
        }
        if (currentWeight <= goalWeight) {
            Toast.makeText(this,
                    "Congratulations! You've reached your goal weight!",
                    Toast.LENGTH_LONG).show();
            sendGoalReachedSms(currentWeight, goalWeight);
        }
    }

    /**
     * Sends an SMS notification when the user reaches their goal weight.
     */
    private void sendGoalReachedSms(double currentWeight, double goalWeight) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Cursor settingsCursor = null;
        try {
            settingsCursor = databaseHelper.getNotificationSettings(currentUserId);
            if (settingsCursor != null && settingsCursor.moveToFirst()) {
                int notifyGoal = settingsCursor.getInt(
                        settingsCursor.getColumnIndexOrThrow(
                                DatabaseHelper.COLUMN_NOTIFY_GOAL_REACHED));
                if (notifyGoal != 1) {
                    return;
                }
            } else {
                return;
            }
        } finally {
            if (settingsCursor != null) {
                settingsCursor.close();
            }
        }

        String phoneNumber = databaseHelper.getPhoneNumber(currentUserId);
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return;
        }

        try {
            SmsManager smsManager = SmsManager.getDefault();
            String message = "Weight Tracker: Congratulations! You've reached your goal weight of "
                    + String.format("%.1f", goalWeight) + " lbs! Current weight: "
                    + String.format("%.1f", currentWeight) + " lbs.";
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Toast.makeText(this, "Goal reached notification sent!",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not send SMS notification",
                    Toast.LENGTH_SHORT).show();
        }
    }
}
