package com.example.weighttracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * DatabaseHelper manages the SQLite database for the Weight Tracker app.
 * It provides CRUD operations for user accounts, weight entries, and user settings.
 * The database is persistent so no user data is lost when the app is closed.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    // Database configuration constants
    private static final String DATABASE_NAME = "WeightTracker.db";
    private static final int DATABASE_VERSION = 1;

    // ==================== Users Table ====================
    // Stores login credentials for user authentication
    public static final String TABLE_USERS = "users";
    public static final String COLUMN_USER_ID = "user_id";
    public static final String COLUMN_USERNAME = "username";
    public static final String COLUMN_PASSWORD = "password";

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
     * Drops existing tables and recreates them.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USER_SETTINGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_WEIGHT_ENTRIES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    // ==================== User Account Methods ====================

    /**
     * Adds a new user account to the database.
     *
     * @param username The username for the new account
     * @param password The password for the new account
     * @return true if the account was created successfully, false if the username already exists
     */
    public boolean addUser(String username, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_USERNAME, username);
        values.put(COLUMN_PASSWORD, password);

        // Insert returns -1 if the operation fails (e.g., duplicate username)
        long result = db.insert(TABLE_USERS, null, values);

        if (result != -1) {
            // Create default settings entry for the new user
            ContentValues settingsValues = new ContentValues();
            settingsValues.put(COLUMN_SETTINGS_USER_ID, result);
            db.insert(TABLE_USER_SETTINGS, null, settingsValues);
        }

        return result != -1;
    }

    /**
     * Validates login credentials against the database.
     *
     * @param username The username to check
     * @param password The password to check
     * @return true if the credentials match a record in the database
     */
    public boolean validateUser(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_USERS,
                new String[]{COLUMN_USER_ID},
                COLUMN_USERNAME + " = ? AND " + COLUMN_PASSWORD + " = ?",
                new String[]{username, password},
                null, null, null
        );

        boolean isValid = cursor.getCount() > 0;
        cursor.close();
        return isValid;
    }

    /**
     * Retrieves the user ID for a given username.
     *
     * @param username The username to look up
     * @return The user ID, or -1 if the user is not found
     */
    public int getUserId(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_USERS,
                new String[]{COLUMN_USER_ID},
                COLUMN_USERNAME + " = ?",
                new String[]{username},
                null, null, null
        );

        int userId = -1;
        if (cursor.moveToFirst()) {
            userId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_USER_ID));
        }
        cursor.close();
        return userId;
    }

    // ==================== Weight Entry CRUD Methods ====================

    /**
     * CREATE: Adds a new weight entry to the database.
     *
     * @param userId The ID of the user adding the entry
     * @param date   The date of the weight measurement
     * @param weight The weight value in pounds
     * @return true if the entry was added successfully
     */
    public boolean addWeightEntry(int userId, String date, double weight) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ENTRY_USER_ID, userId);
        values.put(COLUMN_DATE, date);
        values.put(COLUMN_WEIGHT, weight);

        long result = db.insert(TABLE_WEIGHT_ENTRIES, null, values);
        return result != -1;
    }

    /**
     * READ: Retrieves all weight entries for a specific user, ordered by date.
     *
     * @param userId The ID of the user whose entries to retrieve
     * @return A Cursor containing all weight entries (entry_id, date, weight)
     */
    public Cursor getAllWeightEntries(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(
                TABLE_WEIGHT_ENTRIES,
                new String[]{COLUMN_ENTRY_ID, COLUMN_DATE, COLUMN_WEIGHT},
                COLUMN_ENTRY_USER_ID + " = ?",
                new String[]{String.valueOf(userId)},
                null, null,
                COLUMN_DATE + " ASC"
        );
    }

    /**
     * UPDATE: Modifies the date and weight of an existing entry.
     *
     * @param entryId  The ID of the entry to update
     * @param newDate  The new date value
     * @param newWeight The new weight value
     * @return true if the entry was updated successfully
     */
    public boolean updateWeightEntry(int entryId, String newDate, double newWeight) {
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
    }

    /**
     * DELETE: Removes a weight entry from the database.
     *
     * @param entryId The ID of the entry to delete
     * @return true if the entry was deleted successfully
     */
    public boolean deleteWeightEntry(int entryId) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Delete returns the number of rows affected
        int rowsAffected = db.delete(
                TABLE_WEIGHT_ENTRIES,
                COLUMN_ENTRY_ID + " = ?",
                new String[]{String.valueOf(entryId)}
        );
        return rowsAffected > 0;
    }

    // ==================== User Settings Methods ====================

    /**
     * Retrieves the goal weight for a specific user.
     *
     * @param userId The ID of the user
     * @return The goal weight value, or 0 if not set
     */
    public double getGoalWeight(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_USER_SETTINGS,
                new String[]{COLUMN_GOAL_WEIGHT},
                COLUMN_SETTINGS_USER_ID + " = ?",
                new String[]{String.valueOf(userId)},
                null, null, null
        );

        double goalWeight = 0;
        if (cursor.moveToFirst()) {
            goalWeight = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_GOAL_WEIGHT));
        }
        cursor.close();
        return goalWeight;
    }

    /**
     * Updates the goal weight for a specific user.
     *
     * @param userId     The ID of the user
     * @param goalWeight The new goal weight value
     * @return true if the goal weight was updated successfully
     */
    public boolean updateGoalWeight(int userId, double goalWeight) {
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
    }

    /**
     * Saves the phone number for SMS notifications.
     *
     * @param userId      The ID of the user
     * @param phoneNumber The phone number to save
     * @return true if the phone number was saved successfully
     */
    public boolean savePhoneNumber(int userId, String phoneNumber) {
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
    }

    /**
     * Retrieves the phone number for a specific user.
     *
     * @param userId The ID of the user
     * @return The saved phone number, or an empty string if not set
     */
    public String getPhoneNumber(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_USER_SETTINGS,
                new String[]{COLUMN_PHONE_NUMBER},
                COLUMN_SETTINGS_USER_ID + " = ?",
                new String[]{String.valueOf(userId)},
                null, null, null
        );

        String phoneNumber = "";
        if (cursor.moveToFirst()) {
            phoneNumber = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PHONE_NUMBER));
        }
        cursor.close();
        return phoneNumber;
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
    }

    /**
     * Retrieves the notification settings for a specific user.
     *
     * @param userId The ID of the user
     * @return A Cursor containing notify_goal_reached and notify_weekly columns
     */
    public Cursor getNotificationSettings(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(
                TABLE_USER_SETTINGS,
                new String[]{COLUMN_NOTIFY_GOAL_REACHED, COLUMN_NOTIFY_WEEKLY},
                COLUMN_SETTINGS_USER_ID + " = ?",
                new String[]{String.valueOf(userId)},
                null, null, null
        );
    }
}
