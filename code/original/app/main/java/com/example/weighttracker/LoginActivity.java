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
                String username = editUsername.getText().toString().trim();
                String password = editPassword.getText().toString().trim();

                // Check that both fields have been filled in
                if (username.isEmpty() || password.isEmpty()) {
                    textError.setText("Please enter both username and password");
                    textError.setVisibility(View.VISIBLE);
                    return;
                }

                // Validate the username and password against the database
                if (databaseHelper.validateUser(username, password)) {
                    // Credentials are valid - navigate to the main screen
                    textError.setVisibility(View.GONE);
                    int userId = databaseHelper.getUserId(username);
                    navigateToMain(userId, username);
                } else {
                    // Credentials do not match any record in the database
                    textError.setText("Invalid username or password");
                    textError.setVisibility(View.VISIBLE);
                }
            }
        });

        // Create account button click listener - registers a new user in the database
        buttonCreateAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = editUsername.getText().toString().trim();
                String password = editPassword.getText().toString().trim();

                // Check that both fields have been filled in
                if (username.isEmpty() || password.isEmpty()) {
                    textError.setText("Please enter a username and password to create an account");
                    textError.setVisibility(View.VISIBLE);
                    return;
                }

                // Attempt to add the new user to the database
                if (databaseHelper.addUser(username, password)) {
                    // Account created successfully - navigate to the main screen
                    textError.setVisibility(View.GONE);
                    Toast.makeText(LoginActivity.this,
                            "Account created successfully!", Toast.LENGTH_SHORT).show();
                    int userId = databaseHelper.getUserId(username);
                    navigateToMain(userId, username);
                } else {
                    // Username already exists in the database
                    textError.setText("Username already exists. Please choose a different one.");
                    textError.setVisibility(View.VISIBLE);
                }
            }
        });
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
