package com.example.weighttracker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * SmsSettingsActivity manages SMS notification preferences for the app.
 * It handles requesting SMS permission from the user, saving the phone number
 * and notification preferences to the database, and ensures the app continues
 * to function normally if the user denies SMS permission.
 *
 * Enhancement: Software Engineering & Design (CS 499 Capstone)
 * Changes from original:
 * - Phone number validation via InputValidator (rejects invalid formats)
 * - Cursor management improved with try-finally blocks
 * - Event handlers extracted to named methods for readability
 */
public class SmsSettingsActivity extends AppCompatActivity {

    // Request code for the SMS permission dialog
    private static final int SMS_PERMISSION_REQUEST_CODE = 101;

    // UI element references
    private Button buttonEnableSms;
    private Button buttonDisableSms;
    private TextView textPermissionStatus;
    private View permissionStatusIndicator;
    private Switch switchGoalReached;
    private Switch switchWeeklyProgress;
    private EditText editPhoneNumber;
    private Button buttonSavePhone;
    private Button buttonNavHome;
    private Button buttonNavNotifications;

    // Database helper for persistent settings storage
    private DatabaseHelper databaseHelper;

    // Current logged-in user's ID, passed from MainActivity
    private int currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_settings);

        // Retrieve the user ID passed from MainActivity
        currentUserId = getIntent().getIntExtra("USER_ID", -1);

        // Initialize the database helper
        databaseHelper = new DatabaseHelper(this);

        // Initialize UI elements by finding views from the layout
        buttonEnableSms = findViewById(R.id.buttonEnableSms);
        buttonDisableSms = findViewById(R.id.buttonDisableSms);
        textPermissionStatus = findViewById(R.id.textPermissionStatus);
        permissionStatusIndicator = findViewById(R.id.permissionStatusIndicator);
        switchGoalReached = findViewById(R.id.switchGoalReached);
        switchWeeklyProgress = findViewById(R.id.switchWeeklyProgress);
        editPhoneNumber = findViewById(R.id.editPhoneNumber);
        buttonSavePhone = findViewById(R.id.buttonSavePhone);
        buttonNavHome = findViewById(R.id.buttonNavHome);
        buttonNavNotifications = findViewById(R.id.buttonNavNotifications);

        // Check and display the current SMS permission status
        checkSmsPermission();

        // Load saved settings from the database
        loadUserSettings();

        // Enable SMS button - requests permission from the system
        buttonEnableSms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestSmsPermission();
            }
        });

        // Disable SMS button - turns off both notification switches
        buttonDisableSms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleDisableSms();
            }
        });

        // Goal reached switch listener - saves preference to database when toggled
        switchGoalReached.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Save the updated notification preferences to the database
                databaseHelper.updateNotificationSettings(
                        currentUserId, isChecked, switchWeeklyProgress.isChecked());
            }
        });

        // Weekly progress switch listener - saves preference to database when toggled
        switchWeeklyProgress.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Save the updated notification preferences to the database
                databaseHelper.updateNotificationSettings(
                        currentUserId, switchGoalReached.isChecked(), isChecked);
            }
        });

        // Save phone number button - validates and stores the number in the database
        buttonSavePhone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleSavePhoneNumber();
            }
        });

        // Bottom navigation - Home button returns to the main weight tracking screen
        buttonNavHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SmsSettingsActivity.this, MainActivity.class);
                intent.putExtra("USER_ID", currentUserId);
                startActivity(intent);
                finish();
            }
        });

        // Notifications button - already on this screen, no action needed
        buttonNavNotifications.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // User is already on the Notifications screen; no navigation required
            }
        });
    }

    /**
     * Handles the Disable SMS button click.
     * Enhancement: Extracted to a named method for readability.
     */
    private void handleDisableSms() {
        // Disable both notification switches
        switchGoalReached.setChecked(false);
        switchWeeklyProgress.setChecked(false);

        // Save the disabled state to the database
        databaseHelper.updateNotificationSettings(currentUserId, false, false);

        Toast.makeText(SmsSettingsActivity.this,
                "SMS notifications disabled", Toast.LENGTH_SHORT).show();
    }

    /**
     * Handles the Save Phone Number button click.
     * Enhancement: Validates phone number format using InputValidator
     * before saving to the database. The original code only checked
     * for an empty string.
     */
    private void handleSavePhoneNumber() {
        String phone = editPhoneNumber.getText().toString().trim();

        // Enhancement: Validate phone number format using centralized validator
        InputValidator.ValidationResult phoneResult =
                InputValidator.validatePhoneNumber(phone);
        if (!phoneResult.isValid()) {
            Toast.makeText(SmsSettingsActivity.this,
                    phoneResult.getErrorMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        // Save the validated phone number to the database
        if (databaseHelper.savePhoneNumber(currentUserId, phone)) {
            Toast.makeText(SmsSettingsActivity.this,
                    "Phone number saved", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(SmsSettingsActivity.this,
                    "Error saving phone number", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Loads the user's saved notification settings from the database
     * and populates the UI fields with the stored values.
     *
     * Enhancement: Added try-finally for guaranteed cursor cleanup.
     */
    private void loadUserSettings() {
        // Load saved phone number from the database
        String savedPhone = databaseHelper.getPhoneNumber(currentUserId);
        if (savedPhone != null && !savedPhone.isEmpty()) {
            editPhoneNumber.setText(savedPhone);
        }

        // Load notification toggle states from the database
        // Enhancement: try-finally ensures cursor is always closed
        Cursor cursor = null;
        try {
            cursor = databaseHelper.getNotificationSettings(currentUserId);
            if (cursor != null && cursor.moveToFirst()) {
                boolean goalReached = cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                                DatabaseHelper.COLUMN_NOTIFY_GOAL_REACHED)) == 1;
                boolean weekly = cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                                DatabaseHelper.COLUMN_NOTIFY_WEEKLY)) == 1;

                switchGoalReached.setChecked(goalReached);
                switchWeeklyProgress.setChecked(weekly);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Checks if SMS permission has already been granted and updates the UI.
     * This is called when the activity is first created to show the current state.
     */
    private void checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            updatePermissionUI(true);
        } else {
            updatePermissionUI(false);
        }
    }

    /**
     * Requests SMS permission from the user via the system permission dialog.
     * The result is handled in onRequestPermissionsResult().
     */
    private void requestSmsPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.SEND_SMS},
                SMS_PERMISSION_REQUEST_CODE);
    }

    /**
     * Handles the result of the SMS permission request.
     * Updates the UI based on whether the user granted or denied the permission.
     * If denied, the rest of the app continues to function without SMS notifications.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted - enable SMS notification features
                updatePermissionUI(true);
                Toast.makeText(this, "SMS notifications enabled!",
                        Toast.LENGTH_SHORT).show();
            } else {
                // Permission denied - app continues without SMS notifications
                updatePermissionUI(false);
                Toast.makeText(this,
                        "SMS permission denied. App will continue without notifications.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Updates the UI elements to reflect the current SMS permission status.
     * When permission is granted, shows notification preferences and the disable button.
     * When permission is denied, shows the enable button and disables the switches.
     *
     * @param isGranted true if SMS permission has been granted, false otherwise
     */
    private void updatePermissionUI(boolean isGranted) {
        if (isGranted) {
            // Permission granted - show green status and enable notification controls
            textPermissionStatus.setText("SMS Permission: Granted");
            textPermissionStatus.setTextColor(0xFF388E3C); // Green
            permissionStatusIndicator.setBackgroundColor(0xFF388E3C); // Green
            buttonEnableSms.setVisibility(View.GONE);
            buttonDisableSms.setVisibility(View.VISIBLE);
            switchGoalReached.setEnabled(true);
            switchWeeklyProgress.setEnabled(true);
        } else {
            // Permission denied - show red status and disable notification controls
            textPermissionStatus.setText("SMS Permission: Not Granted");
            textPermissionStatus.setTextColor(0xFFD32F2F); // Red
            permissionStatusIndicator.setBackgroundColor(0xFFD32F2F); // Red
            buttonEnableSms.setVisibility(View.VISIBLE);
            buttonDisableSms.setVisibility(View.GONE);
            switchGoalReached.setEnabled(false);
            switchWeeklyProgress.setEnabled(false);
        }
    }
}
