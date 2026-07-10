-- Create Database
CREATE DATABASE IF NOT EXISTS react_java_auth;
USE react_java_auth;

-- Create Users Table
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert Test Data (Passwords should be hashed in production, using plaintext or simple hash here for demonstration)
-- Let's assume the Java backend will check plaintext for now or we will implement simple hashing later.
-- We will insert an admin and a regular user.
INSERT IGNORE INTO users (username, password, role) VALUES ('admin', 'admin123', 'ADMIN');
INSERT IGNORE INTO users (username, password, role) VALUES ('user', 'user123', 'USER');
