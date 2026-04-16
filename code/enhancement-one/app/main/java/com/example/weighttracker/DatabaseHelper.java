package com.example.weighttracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * DatabaseHelper manages the SQLite database for the Weight Tracker app.
 * It provides CRUD operations for user accounts, weight entries, and user settings.
 * The database is persistent so no user data is lost when the app is closed.
 *
 * Enhancement: Software Engineering & Design (CS 499 Capstone)
 * Changes from original:
 * - Passwords are now stored as salted SHA-256 hashes via PasswordUtils (security mindset)
 * - Added try-catch blocks around all database operations for robust error handling
 * - Added logging via Log.e for debugging database errors
 * - Database version incremented to 2 to trigger migration for existing users
 * - All methods follow consistent patterns for readability and maintainability
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    // Tag for log messages from this class
    private static final String TAG = "DatabaseHelper";

    // Database configuration constants
    private static final String DATABASE_NAME = "WeightTracker.db";
    private static final int DATABASE_VERSION = 2; // Incremented for password hashing migration

    // ==================== Users Table ====================
    // Stores login credentials for user authentication
    public static final String TABLE_USERS = "users";
    public static final String COLUMN_USER_ID = "user_id";
    public static final String COLUMN_USERNAME = "username";
    public static final String COLUMN_PASSWORD = "password"; // Now stores hashed passwords

    // ==================== Weight Entries Table ====================
    // Stores daily weight records entered by the user
    public static final String TABLE_WEIGHT_ENTRIES = "weight_entries";
    public static final String COLUMN_ENTRY_ID = "entry_id";
    public static final String COLUMN_ENTRY_USER_ID = "user_id";
    public static final String COLUMN_DATE = "date";
    public static final String COLUMN_WEIGHT = "weight";

    // ==================== User Settings Table ====================
    // Stores user preferences such as goal weight and SMS notification settings
    public static final String TABLE_USER_SETTINGS = "user_settings";
    public static final String COLUMN_SETTINGS_USER_ID = "user_id";
    public static final String COLUMN_GOAL_WEIGHT = "goal_weight";
    public static final String COLUMN_PHONE_NUMBER = "phone_number";
    public static final String COLUMN_NOTIFY_GOAL_REACHED = "notify_goal_reached";
    public static final String COLUMN_NOTIFY_WEEKLY = "notify_weekly";

    // SQL statement to create the users table
    private static final String CREATE_TABLE_USERS =
            "CREATE TABLE " + TABLE_USERS + " (" +
                    COLUMN_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_USERNAME + " TEXT UNIQUE NOT NULL, " +
                    COLUMN_PASSWORD + " TEXT NOT NULL)";

    // SQL statement to create the weight entries table
    private static final String CREATE_TABLE_WEIGHT_ENTRIES =
            "CREATE TABLE " + TABLE_WEIGHT_ENTRIES + " (" +
                    COLUMN_ENTRY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_ENTRY_USER_ID + " INTEGER NOT NULL, " +
                    COLUMN_DATE + " TEXT NOT NULL, " +
                    COLUMN_WEIGHT + " REAL NOT NULL, " +
                    "FOREIGN KEY (" + COLUMN_ENTRY_USER_ID + ") REFERENCES " +
                    TABLE_USERS + "(" + COLUMN_USER_ID + "))";

    // SQL statement to create the user settings table
    private static final String CREATE_TABLE_USER_SETTINGS =
            "CREATE TABLE " + TABLE_USER_SETTINGS + " (" +
                    COLUMN_SETTINGS_USER_ID + " INTEGER PRIMARY KEY, " +
                    COLUMN_GOAL_WEIGHT + " REAL DEFAULT 0, " +
                    COLUMN_PHONE_NUMBER + " TEXT DEFAULT '', " +
                    COLUMN_NOTIFY_GOAL_REACHED + " INTEGER DEFAULT 1, " +
                    COLUMN_NOTIFY_WEEKLY + " INTEGER DEFAULT 0, " +
                    "FOREIGN KEY (" + COLUMN_SETTINGS_USER_ID + ") REFERENCES " +
                    TABLE_USERS + "(" + COLUMN_USER_ID + "))";

    /**
     * Constructor initializes the database helper with the app context.
     *
     * @param context The application context used to create or open the database
     */
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Called when the database is created for the first time.
     * Creates all required tables for the application.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_USERS);
        db.execSQL(CREATE_TABLE_WEIGHT_ENTRIES);
        db.execSQL(CREATE_TABLE_USER_SETTINGS);
    }

    /**
     * Called when the database needs to be upgraded to a new version.
     * Enhancement: Version 2 drops and recreates tables to support hashed passwords.
     * In a production app, a migration strategy would preserve existing data.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USER_SETTINGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_WEIGHT_ENTRIES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    // ==================== User Account Methods ====================

    /**
     * Adds a new user account to the database with a hashed password.
     *
     * Enhancement: Password is now hashed using PasswordUtils before storage.
     * The plaintext password is never written to the database.
     *
     * @param username The username for the new account
     * @param password The plaintext password (will be hashed before storage)
     * @return true if the account was created successfully, false if the username already exists
     */
    public boolean addUser(String username, String password) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();

            // Hash the password before storing it in the database
            String hashedPassword = PasswordUtils.hashPassword(password);

            ContentValues values = new ContentValues();
            values.put(COLUMN_USERNAME, username);
            values.put(COLUMN_PASSWORD, hashedPassword);

            // Insert returns -1 if the operation fails (e.g., duplicate username)
            long result = db.insert(TABLE_USERS, null, values);

            if (result != -1) {
                // Create default settings entry for the new user
                ContentValues settingsValues = new ContentValues();
                settingsValues.put(COLUMN_SETTINGS_USER_ID, result);
                db.insert(TABLE_USER_SETTINGS, null, settingsValues);
            }

            return result != -1;
        } catch (Exception e) {
            Log.e(TAG, "Error adding user: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validates login credentials against the database using secure hash comparison.
     *
     * Enhancement: Instead of comparing plaintext passwords, this method retrieves
     * the stored hash and uses PasswordUtils.verifyPassword() for comparison.
     * This uses constant-time comparison to prevent timing attacks.
     *
     * @param username The username to check
     * @param password The plaintext password to verify
     * @return true if the credentials match a record in the database
     */
    public boolean validateUser(String username, String password) {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            cursor = db.query(
                    TABLE_USERS,
                    new String[]{COLUMN_PASSWORD},
                    COLUMN_USERNAME + " = ?",
                    new String[]{username},
                    null, null, null
            );

            if (cursor.moveToFirst()) {
                String storedHash = cursor.getString(
                        cursor.getColumnIndexOrThrow(COLUMN_PASSWORD));
                // Verify the plaintext password against the stored hash
                return PasswordUtils.verifyPassword(password, storedHash);
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error validating user: " + e.getMessage());
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Retrieves the user ID for a given username.
     *
     * Enhancement: Added try-finally block to ensure cursor is always closed,
     * preventing potential memory leaks from unclosed cursors.
     *
     * @param username The username to look up
     * @return The user ID, or -1 if the user is not found
     */
    public int getUserId(String username) {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            cursor = db.query(
                    TABLE_USERS,
                    new String[]{COLUMN_USER_ID},
                    COLUMN_USERNAME + " = ?",
                    new String[]{username},
                    null, null, null
            );

            if (cursor.moveToFirst()) {
                return cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_USER_ID));
            }
            return -1;
        } catch (Exception e) {
            Log.e(TAG, "Error getting user ID: " + e.getMessage());
            return -1;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    // ==================== Weight Entry CRUD Methods ====================

    /**
     * CREATE: Adds a new weight entry to the database.
     *
     * Enhancement: Added try-catch for robust error handling with logging.
     *
     * @param userId The ID of the user adding the entry
     * @param date   The date of the weight measurement (format: MM/dd/yyyy)
     * @param weight The weight value in pounds
     * @return true if the entry was added successfully
     */
    public boolean addWeightEntry(int userId, String date, double weight) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COLUMN_ENTRY_USER_ID, userId);
            values.put(COLUMN_DATE, date);
            values.put(COLUMN_WEIGHT, weight);

            long result = db.insert(TABLE_WEIGHT_ENTRIES, null, values);
            return result != -1;
        } catch (Exception e) {
            Log.e(TAG, "Error adding weight entry: " + e.getMessage());
            return false;
        }
    }

    /**
     * READ: Retrieves all weight entries for a specific user, ordered by date.
     *
     * Note: The caller is responsible for closing the returned Cursor.
     *
     * @param userId The ID of the user whose entries to retrieve
     * @return A Cursor containing all weight entries (entry_id, date, weight)
     */
    public Cursor getAllWeightEntries(int userId) {
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            return db.query(
                    TABLE_WEIGHT_ENTRIES,
                    new String[]{COLUMN_ENTRY_ID, COLUMN_DATE, COLUMN_WEIGHT},
                    COLUMN_ENTRY_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)},
                    null, null,
                    COLUMN_DATE + " ASC"
            );
        } catch (Exception e) {
            Log.e(TAG, "Error getting weight entries: " + e.getMessage());
            return null;
        }
    }

    /**
     * UPDATE: Modifies the date and weight of an existing entry.
     *
     * Enhancement: Added try-catch for robust error handling with logging.
     *
     * @param entryId   The ID of the entry to update
     * @param newDate   The new date value
     * @param newWeight The new weight value
     * @return true if the entry was updated successfully
     */
    public boolean updateWeightEntry(int entryId, String newDate, double newWeight) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COLUMN_DATE, newDate);
            values.put(COLUMN_WEIGHT, newWeight);

            // Update returns the number of rows affected
            int rowsAffected = db.update(
                    TABLE_WEIGHT_ENTRIES,
                    values,
                    COLUMN_ENTRY_ID + " = ?",
                    new String[]{String.valueOf(entryId)}
            );
            return rowsAffected > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error updating weight entry: " + e.getMessage());
            return false;
        }
    }

    /**
     * DELETE: Removes a weight entry from the database.
     *
     * Enhancement: Added try-catch for robust error handling with logging.
     *
     * @param entryId The ID of the entry to delete
     * @return true if the entry was deleted successfully
     */
    public boolean deleteWeightEntry(int entryId) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();

            // Delete returns the number of rows affected
            int rowsAffected = db.delete(
                    TABLE_WEIGHT_ENTRIES,
                    COLUMN_ENTRY_ID + " = ?",
                    new String[]{String.valueOf(entryId)}
            );
            return rowsAffected > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting weight entry: " + e.getMessage());
            return false;
        }
    }

    // ==================== User Settings Methods ====================

    /**
     * Retrieves the goal weight for a specific user.
     *
     * Enhancement: Added try-finally block for guaranteed cursor cleanup.
     *
     * @param userId The ID of the user
     * @return The goal weight value, or 0 if not set
     */
    public double getGoalWeight(int userId) {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            cursor = db.query(
                    TABLE_USER_SETTINGS,
                    new String[]{COLUMN_GOAL_WEIGHT},
                    COLUMN_SETTINGS_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)},
                    null, null, null
            );

            if (cursor.moveToFirst()) {
                return cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_GOAL_WEIGHT));
            }
            return 0;
        } catch (Exception e) {
            Log.e(TAG, "Error getting goal weight: " + e.getMessage());
            return 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Updates the goal weight for a specific user.
     *
     * @param userId     The ID of the user
     * @param goalWeight The new goal weight value
     * @return true if the goal weight was updated successfully
     */
    public boolean updateGoalWeight(int userId, double goalWeight) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COLUMN_GOAL_WEIGHT, goalWeight);

            int rowsAffected = db.update(
                    TABLE_USER_SETTINGS,
                    values,
                    COLUMN_SETTINGS_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)}
            );
            return rowsAffected > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error updating goal weight: " + e.getMessage());
            return false;
        }
    }

    /**
     * Saves the phone number for SMS notifications.
     *
     * @param userId      The ID of the user
     * @param phoneNumber The phone number to save
     * @return true if the phone number was saved successfully
     */
    public boolean savePhoneNumber(int userId, String phoneNumber) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COLUMN_PHONE_NUMBER, phoneNumber);

            int rowsAffected = db.update(
                    TABLE_USER_SETTINGS,
                    values,
                    COLUMN_SETTINGS_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)}
            );
            return rowsAffected > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error saving phone number: " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves the phone number for a specific user.
     *
     * Enhancement: Added try-finally block for guaranteed cursor cleanup.
     *
     * @param userId The ID of the user
     * @return The saved phone number, or an empty string if not set
     */
    public String getPhoneNumber(int userId) {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            cursor = db.query(
                    TABLE_USER_SETTINGS,
                    new String[]{COLUMN_PHONE_NUMBER},
                    COLUMN_SETTINGS_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)},
                    null, null, null
            );

            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PHONE_NUMBER));
            }
            return "";
        } catch (Exception e) {
            Log.e(TAG, "Error getting phone number: " + e.getMessage());
            return "";
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Updates the notification preference settings for a user.
     *
     * @param userId           The ID of the user
     * @param notifyGoalReached Whether to notify when goal weight is reached
     * @param notifyWeekly     Whether to send weekly progress notifications
     * @return true if the settings were updated successfully
     */
    public boolean updateNotificationSettings(int userId, boolean notifyGoalReached,
                                              boolean notifyWeekly) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COLUMN_NOTIFY_GOAL_REACHED, notifyGoalReached ? 1 : 0);
            values.put(COLUMN_NOTIFY_WEEKLY, notifyWeekly ? 1 : 0);

            int rowsAffected = db.update(
                    TABLE_USER_SETTINGS,
                    values,
                    COLUMN_SETTINGS_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)}
            );
            return rowsAffected > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error updating notification settings: " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves the notification settings for a specific user.
     *
     * Note: The caller is responsible for closing the returned Cursor.
     *
     * @param userId The ID of the user
     * @return A Cursor containing notify_goal_reached and notify_weekly columns
     */
    public Cursor getNotificationSettings(int userId) {
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            return db.query(
                    TABLE_USER_SETTINGS,
                    new String[]{COLUMN_NOTIFY_GOAL_REACHED, COLUMN_NOTIFY_WEEKLY},
                    COLUMN_SETTINGS_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)},
                    null, null, null
            );
        } catch (Exception e) {
            Log.e(TAG, "Error getting notification settings: " + e.getMessage());
            return null;
        }
    }
}
