package com.example.weighttracker;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * InputValidator provides centralized input validation for the Weight Tracker app.
 * This class was created during the CS 499 Capstone enhancement to separate validation
 * logic from the Activity classes, following the Single Responsibility Principle (SRP).
 *
 * Enhancement: Software Engineering & Design
 * - Centralizes all validation in one testable class
 * - Enforces date format (MM/dd/yyyy), weight range, and password strength rules
 * - Reduces code duplication across activities
 */
public class InputValidator {

    // Date format constant enforced across the application
    public static final String DATE_FORMAT = "MM/dd/yyyy";

    // Weight boundaries based on reasonable real-world values
    private static final double MIN_WEIGHT = 1.0;    // Minimum weight in lbs
    private static final double MAX_WEIGHT = 1500.0;  // Maximum weight in lbs

    // Password requirements
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$"
    );

    // Phone number pattern: digits, spaces, dashes, parentheses, optional leading +
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?[\\d\\s\\-()]{7,15}$"
    );

    /**
     * Validates a date string against the required format (MM/dd/yyyy).
     * Uses strict parsing so invalid dates like 02/30/2025 are rejected.
     *
     * @param dateStr The date string to validate
     * @return A ValidationResult indicating success or describing the error
     */
    public static ValidationResult validateDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return ValidationResult.error("Date is required");
        }

        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        sdf.setLenient(false); // Strict parsing rejects invalid dates

        try {
            Date parsedDate = sdf.parse(dateStr.trim());
            Date today = new Date();

            // Reject future dates - users should not log weights in the future
            if (parsedDate.after(today)) {
                return ValidationResult.error("Date cannot be in the future");
            }

            return ValidationResult.success();
        } catch (ParseException e) {
            return ValidationResult.error("Invalid date format. Please use MM/DD/YYYY");
        }
    }

    /**
     * Validates a weight value string. Checks that the value is a valid number
     * within a reasonable range for human body weight.
     *
     * @param weightStr The weight string to validate
     * @return A ValidationResult indicating success or describing the error
     */
    public static ValidationResult validateWeight(String weightStr) {
        if (weightStr == null || weightStr.trim().isEmpty()) {
            return ValidationResult.error("Weight is required");
        }

        try {
            double weight = Double.parseDouble(weightStr.trim());

            if (weight < MIN_WEIGHT) {
                return ValidationResult.error(
                        "Weight must be at least " + MIN_WEIGHT + " lbs");
            }
            if (weight > MAX_WEIGHT) {
                return ValidationResult.error(
                        "Weight must be less than " + MAX_WEIGHT + " lbs");
            }

            return ValidationResult.success();
        } catch (NumberFormatException e) {
            return ValidationResult.error("Please enter a valid number for weight");
        }
    }

    /**
     * Validates a goal weight value. Uses the same range as regular weight
     * validation since a goal weight must also be realistic.
     *
     * @param goalStr The goal weight string to validate
     * @return A ValidationResult indicating success or describing the error
     */
    public static ValidationResult validateGoalWeight(String goalStr) {
        return validateWeight(goalStr);
    }

    /**
     * Validates a username. Ensures the username is not empty and meets
     * minimum length requirements.
     *
     * @param username The username to validate
     * @return A ValidationResult indicating success or describing the error
     */
    public static ValidationResult validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return ValidationResult.error("Username is required");
        }
        if (username.trim().length() < 3) {
            return ValidationResult.error("Username must be at least 3 characters");
        }
        if (username.trim().length() > 50) {
            return ValidationResult.error("Username must be less than 50 characters");
        }
        return ValidationResult.success();
    }

    /**
     * Validates a password against strength requirements.
     * Requires at least 8 characters with uppercase, lowercase, and a digit.
     *
     * Enhancement: Security mindset - enforces strong password policies
     * instead of accepting any string as a password.
     *
     * @param password The password to validate
     * @return A ValidationResult indicating success or describing the error
     */
    public static ValidationResult validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return ValidationResult.error("Password is required");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return ValidationResult.error(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            return ValidationResult.error(
                    "Password must contain at least one uppercase letter, "
                            + "one lowercase letter, and one digit");
        }
        return ValidationResult.success();
    }

    /**
     * Validates a phone number format.
     *
     * @param phoneNumber The phone number to validate
     * @return A ValidationResult indicating success or describing the error
     */
    public static ValidationResult validatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return ValidationResult.error("Phone number is required");
        }
        if (!PHONE_PATTERN.matcher(phoneNumber.trim()).matches()) {
            return ValidationResult.error("Please enter a valid phone number");
        }
        return ValidationResult.success();
    }

    /**
     * Inner class representing the result of a validation check.
     * Encapsulates both success/failure status and an error message.
     * This pattern avoids returning null or using exceptions for validation flow.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
