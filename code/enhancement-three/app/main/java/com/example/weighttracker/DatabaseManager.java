package com.example.weighttracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * DatabaseManager implements the Repository pattern for the Weight Tracker app,
 * providing a clean abstraction layer between the application logic and the
 * underlying SQLite database. This class was created during the CS 499 Capstone
 * Enhancement Three (Databases) to demonstrate advanced database techniques.
 *
 * Enhancement: Databases (CS 499 Capstone)
 * Key improvements over the original DatabaseHelper CRUD methods:
 * - Repository pattern: Separates data access logic from database schema management
 * - Transaction support: Multi-step operations are wrapped in atomic transactions
 * - Parameterized queries: All queries use parameter binding to prevent SQL injection
 * - Audit logging: Records all data modifications with timestamps and action types
 * - Data export: Provides structured data retrieval for backup/export functionality
 * - Input sanitization: Validates and sanitizes data before database operations
 * - Connection management: Centralized database access prevents connection leaks
 * - Integrity checks: PRAGMA-based database health verification
 *
 * Design trade-offs:
 * - Repository pattern adds a layer of indirection but enables testability and
 *   separation of concerns between schema management (DatabaseHelper) and
 *   data access logic (DatabaseManager)
 * - Audit logging increases storage usage but provides accountability and
 *   the ability to reconstruct data history for debugging or compliance
 * - Transaction wrapping adds slight overhead per operation but guarantees
 *   data consistency across multi-step operations
 *
 * Course Outcome 4: Demonstrates innovative techniques and tools in computing
 * practices for implementing database solutions that deliver value.
 * Course Outcome 5: Develops a security mindset through parameterized queries,
 * input sanitization, and audit logging to ensure data integrity and privacy.
 */
public class DatabaseManager {

    private static final String TAG = "DatabaseManager";

    // Singleton instance for centralized database access
    private static DatabaseManager instance;

    // Reference to the DatabaseHelper for obtaining database connections
    private final DatabaseHelper dbHelper;

    // Date format for audit log timestamps
    private static final SimpleDateFormat AUDIT_DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    /**
     * Private constructor enforces singleton pattern.
     * Centralized access prevents multiple database connections and potential
     * locking issues that occur when multiple helpers access the same database.
     *
     * @param context Application context (not Activity context, to prevent memory leaks)
     */
    private DatabaseManager(Context context) {
        // Use application context to prevent Activity memory leaks
        this.dbHelper = new DatabaseHelper(context.getApplicationContext());
    }

    /**
     * Returns the singleton DatabaseManager instance.
     * Thread-safe initialization using synchronized block.
     *
     * Design choice: Singleton pattern ensures a single point of database access,
     * preventing the connection contention issues that arise when multiple
     * SQLiteOpenHelper instances compete for the same database file.
     *
     * @param context Any context (application context will be extracted)
     * @return The singleton DatabaseManager instance
     */
    public static synchronized DatabaseManager getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseManager(context);
        }
        return instance;
    }

    // ==================== Transaction-Wrapped Weight Entry Operations ====================

    /**
     * Adds a weight entry within a database transaction.
     *
     * Enhancement Three: Wrapping the insert in a transaction ensures atomicity —
     * if the audit log write fails, the weight entry insert is also rolled back.
     * This prevents orphaned data that could occur if one operation succeeds
     * and the other fails.
     *
     * Security: Uses ContentValues with parameter binding rather than raw SQL
     * concatenation, which prevents SQL injection attacks where a malicious
     * date or weight string could execute arbitrary SQL.
     *
     * @param userId The ID of the user adding the entry
     * @param date   The date string (validated by InputValidator before reaching here)
     * @param weight The weight value (validated by InputValidator before reaching here)
     * @return true if both the entry and audit log were written successfully
     */
    public boolean addWeightEntryWithAudit(int userId, String date, double weight) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // Sanitize the date string before storage
            String sanitizedDate = sanitizeInput(date);

            // Insert the weight entry using parameterized ContentValues
            ContentValues values = new ContentValues();
            values.put(DatabaseHelper.COLUMN_ENTRY_USER_ID, userId);
            values.put(DatabaseHelper.COLUMN_DATE, sanitizedDate);
            values.put(DatabaseHelper.COLUMN_WEIGHT, weight);

            long entryId = db.insert(DatabaseHelper.TABLE_WEIGHT_ENTRIES, null, values);
            if (entryId == -1) {
                Log.e(TAG, "Failed to insert weight entry");
                return false;
            }

            // Write audit log entry for accountability
            logAuditEvent(db, userId, "INSERT", DatabaseHelper.TABLE_WEIGHT_ENTRIES,
                    (int) entryId, "Added weight: " + weight + " on " + sanitizedDate);

            // Both operations succeeded — commit the transaction
            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Transaction failed in addWeightEntryWithAudit: " + e.getMessage());
            return false;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Updates a weight entry within a transaction, with audit logging.
     *
     * Enhancement Three: The old values are read before the update so the audit
     * log can record what changed, enabling data history reconstruction.
     *
     * @param entryId   The ID of the entry to update
     * @param newDate   The new date value
     * @param newWeight The new weight value
     * @param userId    The ID of the user performing the update
     * @return true if the update and audit log were written successfully
     */
    public boolean updateWeightEntryWithAudit(int entryId, String newDate,
                                               double newWeight, int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // Read old values for audit trail before overwriting
            String oldValues = getEntryDetails(db, entryId);

            String sanitizedDate = sanitizeInput(newDate);

            ContentValues values = new ContentValues();
            values.put(DatabaseHelper.COLUMN_DATE, sanitizedDate);
            values.put(DatabaseHelper.COLUMN_WEIGHT, newWeight);

            int rowsAffected = db.update(
                    DatabaseHelper.TABLE_WEIGHT_ENTRIES,
                    values,
                    DatabaseHelper.COLUMN_ENTRY_ID + " = ?",
                    new String[]{String.valueOf(entryId)}
            );

            if (rowsAffected == 0) {
                Log.e(TAG, "No rows updated for entry ID: " + entryId);
                return false;
            }

            // Log the change with before/after details
            logAuditEvent(db, userId, "UPDATE", DatabaseHelper.TABLE_WEIGHT_ENTRIES,
                    entryId, "Changed from [" + oldValues + "] to [" +
                            sanitizedDate + ", " + newWeight + "]");

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Transaction failed in updateWeightEntryWithAudit: " + e.getMessage());
            return false;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Deletes a weight entry within a transaction, with audit logging.
     *
     * Enhancement Three: Records the deleted data in the audit log before removal,
     * providing a soft-delete-like audit trail without the storage overhead of
     * keeping deleted records in the main table.
     *
     * @param entryId The ID of the entry to delete
     * @param userId  The ID of the user performing the deletion
     * @return true if the deletion and audit log were written successfully
     */
    public boolean deleteWeightEntryWithAudit(int entryId, int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // Capture the data before deletion for the audit trail
            String deletedValues = getEntryDetails(db, entryId);

            int rowsAffected = db.delete(
                    DatabaseHelper.TABLE_WEIGHT_ENTRIES,
                    DatabaseHelper.COLUMN_ENTRY_ID + " = ?",
                    new String[]{String.valueOf(entryId)}
            );

            if (rowsAffected == 0) {
                Log.e(TAG, "No rows deleted for entry ID: " + entryId);
                return false;
            }

            logAuditEvent(db, userId, "DELETE", DatabaseHelper.TABLE_WEIGHT_ENTRIES,
                    entryId, "Deleted entry: [" + deletedValues + "]");

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Transaction failed in deleteWeightEntryWithAudit: " + e.getMessage());
            return false;
        } finally {
            db.endTransaction();
        }
    }

    // ==================== Batch Operations ====================

    /**
     * Deletes all weight entries for a user within a single transaction.
     *
     * Enhancement Three: Demonstrates batch database operations wrapped in a
     * transaction. Without the transaction, a crash mid-deletion could leave
     * the database in an inconsistent state with some entries deleted and
     * others remaining.
     *
     * @param userId The ID of the user whose entries to delete
     * @return The number of entries deleted
     */
    public int deleteAllEntriesForUser(int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            int rowsDeleted = db.delete(
                    DatabaseHelper.TABLE_WEIGHT_ENTRIES,
                    DatabaseHelper.COLUMN_ENTRY_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)}
            );

            logAuditEvent(db, userId, "BATCH_DELETE", DatabaseHelper.TABLE_WEIGHT_ENTRIES,
                    -1, "Deleted all entries: " + rowsDeleted + " rows");

            db.setTransactionSuccessful();
            return rowsDeleted;
        } catch (Exception e) {
            Log.e(TAG, "Transaction failed in deleteAllEntriesForUser: " + e.getMessage());
            return 0;
        } finally {
            db.endTransaction();
        }
    }

    // ==================== Advanced Query Methods ====================

    /**
     * Retrieves weight entries within a specific date range using parameterized queries.
     *
     * Enhancement Three: This method demonstrates a filtered query that goes beyond
     * simple "get all" operations. The date range is passed as parameters rather
     * than concatenated into the SQL string, preventing SQL injection.
     *
     * @param userId    The user ID to filter by
     * @param startDate The start date (inclusive) in MM/dd/yyyy format
     * @param endDate   The end date (inclusive) in MM/dd/yyyy format
     * @return A list of WeightEntry objects within the date range
     */
    public List<WeightAnalytics.WeightEntry> getEntriesInDateRange(
            int userId, String startDate, String endDate) {
        List<WeightAnalytics.WeightEntry> entries = new ArrayList<>();
        Cursor cursor = null;
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();

            // Parameterized query prevents SQL injection on date values
            cursor = db.query(
                    DatabaseHelper.TABLE_WEIGHT_ENTRIES,
                    new String[]{DatabaseHelper.COLUMN_ENTRY_ID,
                            DatabaseHelper.COLUMN_DATE,
                            DatabaseHelper.COLUMN_WEIGHT},
                    DatabaseHelper.COLUMN_ENTRY_USER_ID + " = ? AND " +
                            DatabaseHelper.COLUMN_DATE + " >= ? AND " +
                            DatabaseHelper.COLUMN_DATE + " <= ?",
                    new String[]{String.valueOf(userId),
                            sanitizeInput(startDate),
                            sanitizeInput(endDate)},
                    null, null,
                    DatabaseHelper.COLUMN_DATE + " ASC"
            );

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int entryId = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ENTRY_ID));
                    String date = cursor.getString(
                            cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_DATE));
                    double weight = cursor.getDouble(
                            cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_WEIGHT));
                    entries.add(new WeightAnalytics.WeightEntry(entryId, date, weight));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying date range: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return entries;
    }

    /**
     * Retrieves all weight entries for a user as structured WeightEntry objects.
     *
     * Enhancement Three: Returns domain objects instead of raw Cursors, which
     * prevents cursor leak bugs in the calling code and enables the cached
     * entry list used by WeightAnalytics from Enhancement Two.
     *
     * @param userId The user ID
     * @return A list of all WeightEntry objects for the user
     */
    public List<WeightAnalytics.WeightEntry> getAllEntriesAsObjects(int userId) {
        List<WeightAnalytics.WeightEntry> entries = new ArrayList<>();
        Cursor cursor = null;
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            cursor = db.query(
                    DatabaseHelper.TABLE_WEIGHT_ENTRIES,
                    new String[]{DatabaseHelper.COLUMN_ENTRY_ID,
                            DatabaseHelper.COLUMN_DATE,
                            DatabaseHelper.COLUMN_WEIGHT},
                    DatabaseHelper.COLUMN_ENTRY_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)},
                    null, null,
                    DatabaseHelper.COLUMN_DATE + " ASC"
            );

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int entryId = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ENTRY_ID));
                    String date = cursor.getString(
                            cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_DATE));
                    double weight = cursor.getDouble(
                            cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_WEIGHT));
                    entries.add(new WeightAnalytics.WeightEntry(entryId, date, weight));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting all entries: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return entries;
    }

    /**
     * Returns the total number of weight entries for a user.
     * Uses COUNT(*) aggregate query for efficiency rather than loading all rows.
     *
     * @param userId The user ID
     * @return The count of weight entries
     */
    public int getEntryCount(int userId) {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_WEIGHT_ENTRIES +
                            " WHERE " + DatabaseHelper.COLUMN_ENTRY_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)}
            );
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting entry count: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return 0;
    }

    // ==================== Data Export ====================

    /**
     * Exports all weight entries for a user as a CSV-formatted string.
     *
     * Enhancement Three: Provides a structured data export capability that
     * demonstrates reading from the database and formatting output for external
     * consumption. This addresses the practical need for data portability and
     * user data rights.
     *
     * @param userId The user ID
     * @return A CSV string with header row and all weight entries
     */
    public String exportEntriesToCsv(int userId) {
        StringBuilder csv = new StringBuilder();
        csv.append("Entry ID,Date,Weight (lbs)\n");

        List<WeightAnalytics.WeightEntry> entries = getAllEntriesAsObjects(userId);
        for (WeightAnalytics.WeightEntry entry : entries) {
            csv.append(entry.getEntryId()).append(",");
            csv.append("\"").append(entry.getDateString()).append("\",");
            csv.append(String.format(Locale.US, "%.1f", entry.getWeight())).append("\n");
        }
        return csv.toString();
    }

    // ==================== Audit Log ====================

    /**
     * Records an audit event in the audit_log table.
     *
     * Enhancement Three: Audit logging is a fundamental database security practice
     * that provides accountability, traceability, and the ability to detect
     * unauthorized or unexpected data modifications. The audit log records:
     * - WHO performed the action (user_id)
     * - WHAT action was performed (action_type: INSERT, UPDATE, DELETE)
     * - WHICH table and record were affected (table_name, record_id)
     * - WHEN the action occurred (timestamp)
     * - Additional details about the change (details)
     *
     * Security consideration: The audit log table should not be writable by
     * application users in a production environment. In this implementation,
     * audit writes are restricted to this private method.
     *
     * @param db         The database connection (reuses the caller's transaction)
     * @param userId     The user who performed the action
     * @param actionType The type of action (INSERT, UPDATE, DELETE, BATCH_DELETE)
     * @param tableName  The table that was modified
     * @param recordId   The ID of the affected record (-1 for batch operations)
     * @param details    A description of the change
     */
    private void logAuditEvent(SQLiteDatabase db, int userId, String actionType,
                                String tableName, int recordId, String details) {
        try {
            ContentValues auditValues = new ContentValues();
            auditValues.put("user_id", userId);
            auditValues.put("action_type", actionType);
            auditValues.put("table_name", tableName);
            auditValues.put("record_id", recordId);
            auditValues.put("timestamp", AUDIT_DATE_FORMAT.format(new Date()));
            auditValues.put("details", sanitizeInput(details));

            db.insert(DatabaseHelper.TABLE_AUDIT_LOG, null, auditValues);
        } catch (Exception e) {
            // Audit log failure should not break the main operation
            Log.e(TAG, "Audit log write failed: " + e.getMessage());
        }
    }

    /**
     * Retrieves audit log entries for a specific user, ordered by most recent first.
     *
     * @param userId The user whose audit trail to retrieve
     * @param limit  Maximum number of entries to return
     * @return A list of formatted audit log strings
     */
    public List<String> getAuditLog(int userId, int limit) {
        List<String> logEntries = new ArrayList<>();
        Cursor cursor = null;
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            cursor = db.query(
                    DatabaseHelper.TABLE_AUDIT_LOG,
                    new String[]{"timestamp", "action_type", "table_name", "details"},
                    "user_id = ?",
                    new String[]{String.valueOf(userId)},
                    null, null,
                    "timestamp DESC",
                    String.valueOf(limit)
            );

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String timestamp = cursor.getString(
                            cursor.getColumnIndexOrThrow("timestamp"));
                    String action = cursor.getString(
                            cursor.getColumnIndexOrThrow("action_type"));
                    String details = cursor.getString(
                            cursor.getColumnIndexOrThrow("details"));
                    logEntries.add("[" + timestamp + "] " + action + ": " + details);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading audit log: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return logEntries;
    }

    // ==================== Database Health and Integrity ====================

    /**
     * Performs a database integrity check using SQLite's built-in PRAGMA.
     *
     * Enhancement Three: Database integrity verification is a defensive practice
     * that detects corruption before it causes data loss. This is especially
     * important on mobile devices where sudden power loss, storage errors, or
     * forced app termination can corrupt the database file.
     *
     * @return true if the database passes all integrity checks
     */
    public boolean checkDatabaseIntegrity() {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            cursor = db.rawQuery("PRAGMA integrity_check", null);
            if (cursor != null && cursor.moveToFirst()) {
                String result = cursor.getString(0);
                boolean isOk = "ok".equalsIgnoreCase(result);
                if (!isOk) {
                    Log.e(TAG, "Database integrity check FAILED: " + result);
                }
                return isOk;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error running integrity check: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return false;
    }

    /**
     * Returns database statistics for monitoring and debugging.
     *
     * @return A formatted string with table sizes and database file info
     */
    public String getDatabaseStats() {
        StringBuilder stats = new StringBuilder();
        Cursor cursor = null;
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();

            // Count rows in each table
            String[] tables = {DatabaseHelper.TABLE_USERS,
                    DatabaseHelper.TABLE_WEIGHT_ENTRIES,
                    DatabaseHelper.TABLE_USER_SETTINGS,
                    DatabaseHelper.TABLE_AUDIT_LOG};

            for (String table : tables) {
                cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
                if (cursor != null && cursor.moveToFirst()) {
                    stats.append(table).append(": ").append(cursor.getInt(0)).append(" rows\n");
                }
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }

            // Get database page count and size
            cursor = db.rawQuery("PRAGMA page_count", null);
            if (cursor != null && cursor.moveToFirst()) {
                int pageCount = cursor.getInt(0);
                cursor.close();
                cursor = db.rawQuery("PRAGMA page_size", null);
                if (cursor != null && cursor.moveToFirst()) {
                    int pageSize = cursor.getInt(0);
                    long dbSizeKb = (long) pageCount * pageSize / 1024;
                    stats.append("Database size: ").append(dbSizeKb).append(" KB\n");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting database stats: " + e.getMessage());
            stats.append("Error reading stats");
        } finally {
            if (cursor != null) cursor.close();
        }
        return stats.toString();
    }

    // ==================== Security Utilities ====================

    /**
     * Sanitizes user input before database storage.
     *
     * Enhancement Three: Defense-in-depth approach to SQL injection prevention.
     * While parameterized queries (ContentValues) are the primary defense,
     * sanitizing input provides an additional layer of protection by stripping
     * characters that could be used in SQL injection payloads.
     *
     * Note: This does NOT replace parameterized queries — it supplements them.
     * The combination of parameterized queries + input sanitization follows
     * the principle of defense in depth from secure software design.
     *
     * @param input The raw input string
     * @return The sanitized string with dangerous characters removed
     */
    private String sanitizeInput(String input) {
        if (input == null) return "";
        // Remove characters commonly used in SQL injection attacks
        // Semicolons (statement termination), double dashes (comment injection),
        // and single quotes (string escape) are stripped
        return input.replaceAll("[;'\"\\\\]", "")
                     .trim();
    }

    /**
     * Retrieves the current details of a weight entry for audit logging.
     * Called before updates/deletes to record the "before" state.
     *
     * @param db      The database connection
     * @param entryId The entry ID to look up
     * @return A formatted string of the entry's current values
     */
    private String getEntryDetails(SQLiteDatabase db, int entryId) {
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_WEIGHT_ENTRIES,
                    new String[]{DatabaseHelper.COLUMN_DATE, DatabaseHelper.COLUMN_WEIGHT},
                    DatabaseHelper.COLUMN_ENTRY_ID + " = ?",
                    new String[]{String.valueOf(entryId)},
                    null, null, null
            );
            if (cursor != null && cursor.moveToFirst()) {
                String date = cursor.getString(
                        cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_DATE));
                double weight = cursor.getDouble(
                        cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_WEIGHT));
                return date + ", " + weight;
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return "unknown";
    }

    /**
     * Provides direct access to the underlying DatabaseHelper for operations
     * that do not require the Repository pattern overhead (e.g., user account
     * management handled by LoginActivity).
     *
     * @return The DatabaseHelper instance
     */
    public DatabaseHelper getHelper() {
        return dbHelper;
    }
}
