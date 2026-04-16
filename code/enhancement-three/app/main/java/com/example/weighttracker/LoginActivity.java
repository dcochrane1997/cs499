package com.example.weighttracker;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.textfield.TextInputEditText;

/**
 * LoginActivity handles user authentication and new account creation.
 * It validates credentials against the SQLite database and allows
 * first-time users to create a new login and password.
 *
 * Enhancement: Software Engineering & Design (CS 499 Capstone)
 * Changes from original:
 * - Input validation delegated to InputValidator class (separation of concerns)
 * - Password strength enforcement on account creation (security mindset)
 * - Passwords verified via secure hash comparison, not plaintext (PasswordUtils)
 * - Generic error message on login failure to avoid revealing valid usernames
 * - Consistent error display pattern across all validation paths
 */
public class LoginActivity extends AppCompatActivity {

    // UI element references
    private TextInputEditText editUsername;
    private TextInputEditText editPassword;
    private Button buttonLogin;
    private Button buttonCreateAccount;
    private TextView textError;

    // Database helper for user authentication
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize the database helper
        databaseHelper = new DatabaseHelper(this);

        // Initialize UI elements by finding views from the layout
        editUsername = findViewById(R.id.editUsername);
        editPassword = findViewById(R.id.editPassword);
        buttonLogin = findViewById(R.id.buttonLogin);
        buttonCreateAccount = findViewById(R.id.buttonCreateAccount);
        textError = findViewById(R.id.textError);

        // Login button click listener - validates credentials against the database
        buttonLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleLogin();
            }
        });

        // Create account button click listener - registers a new user in the database
        buttonCreateAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleCreateAccount();
            }
        });
    }

    /**
     * Handles the login flow: validates input, checks credentials, navigates on success.
     *
     * Enhancement: Uses InputValidator for input checks. Uses a generic error message
     * ("Invalid username or password") to avoid revealing whether a username exists.
     * This is a security best practice that prevents user enumeration attacks.
     */
    private void handleLogin() {
        String username = editUsername.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        // Validate that both fields have content (basic check for login)
        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password");
            return;
        }

        // Validate the username and password against the database
        // Enhancement: validateUser now uses secure hash comparison
        if (databaseHelper.validateUser(username, password)) {
            // Credentials are valid - navigate to the main screen
            hideError();
            int userId = databaseHelper.getUserId(username);
            navigateToMain(userId, username);
        } else {
            // Generic error message prevents username enumeration
            showError("Invalid username or password");
        }
    }

    /**
     * Handles the account creation flow: validates input strength, creates account.
     *
     * Enhancement: Enforces password strength requirements via InputValidator.
     * The original code accepted any string as a password. Now passwords must
     * be at least 8 characters with uppercase, lowercase, and a digit.
     * Username validation also enforces minimum length requirements.
     */
    private void handleCreateAccount() {
        String username = editUsername.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        // Validate username meets requirements
        InputValidator.ValidationResult usernameResult =
                InputValidator.validateUsername(username);
        if (!usernameResult.isValid()) {
            showError(usernameResult.getErrorMessage());
            return;
        }

        // Validate password meets strength requirements
        InputValidator.ValidationResult passwordResult =
                InputValidator.validatePassword(password);
        if (!passwordResult.isValid()) {
            showError(passwordResult.getErrorMessage());
            return;
        }

        // Attempt to add the new user to the database
        // Enhancement: addUser now hashes the password before storage
        if (databaseHelper.addUser(username, password)) {
            // Account created successfully - navigate to the main screen
            hideError();
            Toast.makeText(LoginActivity.this,
                    "Account created successfully!", Toast.LENGTH_SHORT).show();
            int userId = databaseHelper.getUserId(username);
            navigateToMain(userId, username);
        } else {
            // Username already exists in the database
            showError("Username already exists. Please choose a different one.");
        }
    }

    /**
     * Displays an error message to the user.
     * Enhancement: Extracted to a helper method to reduce code duplication.
     *
     * @param message The error message to display
     */
    private void showError(String message) {
        textError.setText(message);
        textError.setVisibility(View.VISIBLE);
    }

    /**
     * Hides the error message.
     * Enhancement: Extracted to a helper method for consistent UI state management.
     */
    private void hideError() {
        textError.setVisibility(View.GONE);
    }

    /**
     * Navigates to the MainActivity and passes the user ID and username.
     * Finishes the LoginActivity so the user cannot go back to it with the back button.
     *
     * @param userId   The authenticated user's database ID
     * @param username The authenticated user's username
     */
    private void navigateToMain(int userId, String username) {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.putExtra("USER_ID", userId);
        intent.putExtra("USERNAME", username);
        startActivity(intent);
        finish();
    }
}
