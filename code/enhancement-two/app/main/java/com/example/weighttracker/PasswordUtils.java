package com.example.weighttracker;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PasswordUtils provides secure password hashing and verification for the Weight Tracker app.
 * This class was created during the CS 499 Capstone enhancement to replace the original
 * plaintext password storage with salted SHA-256 hashing.
 *
 * Enhancement: Software Engineering & Design / Security Mindset
 * - Passwords are never stored in plaintext
 * - Each password has a unique random salt to prevent rainbow table attacks
 * - Uses SHA-256 hashing which is available on all Android versions
 *
 * Security note: For production applications, bcrypt or Argon2 would be preferred
 * over SHA-256 for password hashing. SHA-256 is used here because it is available
 * natively on Android without additional dependencies.
 */
public class PasswordUtils {

    // Salt length in bytes - 16 bytes provides 128 bits of randomness
    private static final int SALT_LENGTH = 16;

    // Separator used to store salt and hash together as a single string
    private static final String SEPARATOR = ":";

    /**
     * Hashes a password with a randomly generated salt.
     * The salt is prepended to the result string so it can be extracted during verification.
     *
     * Storage format: Base64(salt):Base64(hash)
     *
     * @param password The plaintext password to hash
     * @return A string containing the salt and hash, separated by ':'
     * @throws RuntimeException if SHA-256 is not available (should not occur on Android)
     */
    public static String hashPassword(String password) {
        try {
            // Generate a cryptographically secure random salt
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);

            // Compute the hash of salt + password
            byte[] hash = computeHash(salt, password);

            // Encode both salt and hash as Base64 and combine with separator
            String saltBase64 = Base64.getEncoder().encodeToString(salt);
            String hashBase64 = Base64.getEncoder().encodeToString(hash);

            return saltBase64 + SEPARATOR + hashBase64;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verifies a plaintext password against a stored hash.
     * Extracts the salt from the stored hash, re-hashes the input password
     * with the same salt, and compares the results.
     *
     * @param password   The plaintext password to verify
     * @param storedHash The stored hash string (salt:hash format)
     * @return true if the password matches the stored hash
     */
    public static boolean verifyPassword(String password, String storedHash) {
        try {
            // Split the stored string into salt and hash components
            String[] parts = storedHash.split(SEPARATOR);
            if (parts.length != 2) {
                return false; // Invalid format - reject
            }

            // Decode the salt from Base64
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[1]);

            // Hash the input password with the extracted salt
            byte[] actualHash = computeHash(salt, password);

            // Constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (NoSuchAlgorithmException | IllegalArgumentException e) {
            return false; // Any error means verification fails
        }
    }

    /**
     * Computes a SHA-256 hash of the salt concatenated with the password.
     *
     * @param salt     The salt bytes to prepend
     * @param password The password string to hash
     * @return The resulting hash bytes
     * @throws NoSuchAlgorithmException if SHA-256 is not available
     */
    private static byte[] computeHash(byte[] salt, String password)
            throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        return digest.digest(password.getBytes());
    }
}
