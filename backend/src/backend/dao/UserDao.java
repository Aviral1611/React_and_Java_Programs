package backend.dao;

import backend.db.Database;
import backend.model.UserAccount;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class UserDao {
    private final Database database;

    public UserDao(Database database) {
        this.database = database;
    }

    public UserAccount findByUsername(String username) throws Exception {
        String sql = "SELECT username, password, role FROM users WHERE username = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new UserAccount(
                    result.getString("username"),
                    result.getString("password"),
                    result.getString("role")
                );
            }
        }
    }
}
