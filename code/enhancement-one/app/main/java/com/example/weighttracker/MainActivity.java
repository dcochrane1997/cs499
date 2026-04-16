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

/**
 * MainActivity is the primary screen of the Weight Tracker app.
 * It displays the user's weight entries in a scrollable grid, allows adding,
 * editing, and deleting entries via the SQLite database, and triggers SMS
 * notifications when the user reaches their goal weight.
 *
 * Enhancement: Software Engineering & Design (CS 499 Capstone)
 * Changes from original:
 * - Input validation delegated to InputValidator class (separation of concerns)
 * - Date format enforced as MM/DD/YYYY with strict parsing
 * - Weight values validated against realistic min/max range
 * - Goal weight validation uses same InputValidator logic (no duplication)
 * - Cursor management improved with try-finally blocks to prevent leaks
 * - Event handlers extracted to named methods for readability
 * - SMS notification logic uses safe cursor handling
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

    // Database helper for persistent data storage
    private DatabaseHelper databaseHelper;

    // Current logged-in user's ID, passed from LoginActivity
    private int currentUserId;

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
    }

    /**
     * Handles the Add Weight button click.
     * Enhancement: Extracted from anonymous inner class for readability.
     * Uses InputValidator for both date and weight validation instead of
     * inline checks, ensuring consistent validation rules across the app.
     */
    private void handleAddWeight() {
        String date = editDate.getText().toString().trim();
        String weightStr = editWeight.getText().toString().trim();

        // Validate date using centralized InputValidator
        // Enhancement: Enforces MM/DD/YYYY format with strict parsing
        InputValidator.ValidationResult dateResult =
                InputValidator.validateDate(date);
        if (!dateResult.isValid()) {
            Toast.makeText(MainActivity.this,
                    dateResult.getErrorMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate weight using centralized InputValidator
        // Enhancement: Enforces realistic weight range (1.0 - 1500.0 lbs)
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

            // Refresh the grid to show the new entry
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
     * READ operation: Loads all weight entries from the database and displays
     * them as rows in the scrollable grid. Clears any existing rows first
     * to avoid duplicates.
     *
     * Enhancement: Added try-finally for guaranteed cursor cleanup even if
     * an exception occurs while processing entries.
     */
    private void loadWeightEntries() {
        // Clear all existing rows from the grid before reloading
        gridContainer.removeAllViews();

        // Query the database for all weight entries belonging to this user
        Cursor cursor = databaseHelper.getAllWeightEntries(currentUserId);

        // Enhancement: try-finally ensures cursor is always closed
        try {
            // Iterate through each entry and create a row in the grid
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int entryId = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ENTRY_ID));
                    String date = cursor.getString(
                            cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_DATE));
                    double weight = cursor.getDouble(
                            cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_WEIGHT));

                    // Add a visual row to the grid for this database entry
                    addWeightRow(entryId, date, String.format("%.1f", weight));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
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
        // Create the row container with horizontal layout
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(32, 24, 32, 24);
        row.setBackgroundColor(0xFFFFFFFF);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Store the entry ID as a tag so it can be retrieved for database operations
        row.setTag(entryId);

        // Date text column
        TextView dateText = new TextView(this);
        LinearLayout.LayoutParams dateParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2);
        dateText.setLayoutParams(dateParams);
        dateText.setText(date);
        dateText.setTextSize(14);
        dateText.setTextColor(0xFF424242);

        // Weight text column
        TextView weightText = new TextView(this);
        LinearLayout.LayoutParams weightParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2);
        weightText.setLayoutParams(weightParams);
        weightText.setText(weight);
        weightText.setTextSize(14);
        weightText.setTextColor(0xFF424242);

        // Actions container to hold edit and delete buttons side by side
        LinearLayout actionsContainer = new LinearLayout(this);
        LinearLayout.LayoutParams actionsParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        actionsContainer.setLayoutParams(actionsParams);
        actionsContainer.setOrientation(LinearLayout.HORIZONTAL);
        actionsContainer.setGravity(android.view.Gravity.CENTER);

        // Edit button allows the user to update an existing entry
        ImageButton editButton = new ImageButton(this);
        LinearLayout.LayoutParams editBtnParams =
                new LinearLayout.LayoutParams(80, 80);
        editBtnParams.setMarginEnd(8);
        editButton.setLayoutParams(editBtnParams);
        editButton.setImageResource(android.R.drawable.ic_menu_edit);
        editButton.setBackgroundColor(0x00000000);
        editButton.setContentDescription("Edit entry");

        // Edit button click listener - opens a dialog to update the entry
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditEntryDialog(entryId, date, weight);
            }
        });

        // Delete button removes the entry from the database
        ImageButton deleteButton = new ImageButton(this);
        LinearLayout.LayoutParams deleteBtnParams =
                new LinearLayout.LayoutParams(80, 80);
        deleteButton.setLayoutParams(deleteBtnParams);
        deleteButton.setImageResource(android.R.drawable.ic_menu_delete);
        deleteButton.setBackgroundColor(0x00000000);
        deleteButton.setContentDescription("Delete entry");

        // Delete button click listener - confirms and removes the entry
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show confirmation dialog before deleting
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Delete Entry")
                        .setMessage("Are you sure you want to delete this weight entry?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            // Delete the entry from the database
                            if (databaseHelper.deleteWeightEntry(entryId)) {
                                Toast.makeText(MainActivity.this,
                                        "Entry deleted", Toast.LENGTH_SHORT).show();
                                // Refresh the grid to reflect the deletion
                                loadWeightEntries();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        // Add divider line between rows for visual separation
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(0xFFE0E0E0);

        // Assemble the row: add all child views
        actionsContainer.addView(editButton);
        actionsContainer.addView(deleteButton);
        row.addView(dateText);
        row.addView(weightText);
        row.addView(actionsContainer);

        // Add the divider and row to the grid container
        if (gridContainer.getChildCount() > 0) {
            gridContainer.addView(divider);
        }
        gridContainer.addView(row);
    }

    /**
     * UPDATE operation: Shows a dialog that allows the user to edit the date
     * and weight of an existing entry, then updates the database.
     *
     * Enhancement: Uses InputValidator for edit dialog validation too,
     * ensuring consistent rules whether adding or editing entries.
     *
     * @param entryId       The database ID of the entry to edit
     * @param currentDate   The current date value displayed in the row
     * @param currentWeight The current weight value displayed in the row
     */
    private void showEditEntryDialog(int entryId, String currentDate, String currentWeight) {
        // Build a dialog with input fields pre-filled with current values
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Weight Entry");

        // Create a layout to hold the two input fields
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(48, 32, 48, 16);

        // Date input field - Enhancement: hint shows expected format
        EditText dateInput = new EditText(this);
        dateInput.setHint("Date (MM/DD/YYYY)");
        dateInput.setText(currentDate);
        dateInput.setInputType(android.text.InputType.TYPE_CLASS_DATETIME);
        dialogLayout.addView(dateInput);

        // Weight input field
        EditText weightInput = new EditText(this);
        weightInput.setHint("Weight (lbs)");
        weightInput.setText(currentWeight);
        weightInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        dialogLayout.addView(weightInput);

        builder.setView(dialogLayout);

        // Save button validates and updates the entry in the database
        builder.setPositiveButton("Save", (dialog, which) -> {
            String newDate = dateInput.getText().toString().trim();
            String newWeightStr = weightInput.getText().toString().trim();

            // Enhancement: Validate date format using centralized validator
            InputValidator.ValidationResult dateResult =
                    InputValidator.validateDate(newDate);
            if (!dateResult.isValid()) {
                Toast.makeText(this, dateResult.getErrorMessage(),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // Enhancement: Validate weight range using centralized validator
            InputValidator.ValidationResult weightResult =
                    InputValidator.validateWeight(newWeightStr);
            if (!weightResult.isValid()) {
                Toast.makeText(this, weightResult.getErrorMessage(),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            double newWeight = Double.parseDouble(newWeightStr);

            // Update the entry in the database
            if (databaseHelper.updateWeightEntry(entryId, newDate, newWeight)) {
                Toast.makeText(this, "Entry updated",
                        Toast.LENGTH_SHORT).show();
                // Refresh the grid to show the updated values
                loadWeightEntries();

                // Check if updated weight meets the goal
                checkGoalWeight(newWeight);
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Shows a dialog for the user to set or update their goal weight.
     * The goal weight is saved to the database and displayed on the main screen.
     *
     * Enhancement: Uses InputValidator.validateGoalWeight() instead of
     * inline number parsing, ensuring consistent validation.
     */
    private void showGoalWeightDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Goal Weight");

        // Create input field for the goal weight
        EditText goalInput = new EditText(this);
        goalInput.setHint("Enter goal weight (lbs)");
        goalInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        goalInput.setPadding(48, 32, 48, 16);

        // Pre-fill with current goal weight if one exists
        double currentGoal = databaseHelper.getGoalWeight(currentUserId);
        if (currentGoal > 0) {
            goalInput.setText(String.format("%.1f", currentGoal));
        }

        builder.setView(goalInput);

        // Save button validates and updates the goal weight in the database
        builder.setPositiveButton("Save", (dialog, which) -> {
            String goalStr = goalInput.getText().toString().trim();

            // Enhancement: Validate goal weight using centralized validator
            InputValidator.ValidationResult goalResult =
                    InputValidator.validateGoalWeight(goalStr);
            if (!goalResult.isValid()) {
                Toast.makeText(this, goalResult.getErrorMessage(),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            double goalWeight = Double.parseDouble(goalStr);

            // Save the goal weight to the database
            databaseHelper.updateGoalWeight(currentUserId, goalWeight);

            // Update the display with the new goal weight
            textGoalWeight.setText(String.format("%.1f lbs", goalWeight));
            Toast.makeText(this, "Goal weight updated",
                    Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Checks if the entered weight has reached the user's goal weight.
     * If the goal is reached and SMS permission is granted with notifications enabled,
     * sends a congratulatory SMS message to the user's saved phone number.
     *
     * @param currentWeight The weight value just entered or updated
     */
    private void checkGoalWeight(double currentWeight) {
        double goalWeight = databaseHelper.getGoalWeight(currentUserId);

        // Only check if a goal weight has been set
        if (goalWeight <= 0) {
            return;
        }

        // Check if the current weight is at or below the goal weight
        if (currentWeight <= goalWeight) {
            Toast.makeText(this,
                    "Congratulations! You've reached your goal weight!",
                    Toast.LENGTH_LONG).show();

            // Attempt to send an SMS notification if permission is granted
            sendGoalReachedSms(currentWeight, goalWeight);
        }
    }

    /**
     * Sends an SMS notification when the user reaches their goal weight.
     * Only sends if SMS permission is granted, the user has enabled goal notifications,
     * and a phone number is saved in the database.
     *
     * Enhancement: Added try-finally for cursor cleanup in notification settings check.
     *
     * @param currentWeight The weight that triggered the notification
     * @param goalWeight    The user's goal weight
     */
    private void sendGoalReachedSms(double currentWeight, double goalWeight) {
        // Check if SMS permission has been granted by the user
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            return; // App continues to function without SMS feature
        }

        // Check if the user has enabled goal reached notifications
        // Enhancement: try-finally ensures cursor is always closed
        Cursor settingsCursor = null;
        try {
            settingsCursor = databaseHelper.getNotificationSettings(currentUserId);
            if (settingsCursor != null && settingsCursor.moveToFirst()) {
                int notifyGoal = settingsCursor.getInt(
                        settingsCursor.getColumnIndexOrThrow(
                                DatabaseHelper.COLUMN_NOTIFY_GOAL_REACHED));

                // Only send if goal notifications are enabled
                if (notifyGoal != 1) {
                    return;
                }
            } else {
                return; // No settings found, skip SMS
            }
        } finally {
            if (settingsCursor != null) {
                settingsCursor.close();
            }
        }

        // Retrieve the phone number from the database
        String phoneNumber = databaseHelper.getPhoneNumber(currentUserId);
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return; // No phone number saved, skip SMS
        }

        // Compose and send the SMS notification
        try {
            SmsManager smsManager = SmsManager.getDefault();
            String message = "Weight Tracker: Congratulations! You've reached your goal weight of "
                    + String.format("%.1f", goalWeight) + " lbs! Current weight: "
                    + String.format("%.1f", currentWeight) + " lbs.";
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Toast.makeText(this, "Goal reached notification sent!",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            // SMS sending failed - app continues to function normally
            Toast.makeText(this, "Could not send SMS notification",
                    Toast.LENGTH_SHORT).show();
        }
    }
}
