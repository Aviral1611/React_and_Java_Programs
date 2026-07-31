package backend.service;

import backend.dao.UserDao;
import backend.model.UserAccount;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class AuthService {
    private final UserDao userDao;

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    public UserAccount authenticate(String username, String password) throws Exception {
        if (username == null || password == null) {
            return null;
        }
        UserAccount account = userDao.findByUsername(username);
        if (account == null) {
            return null;
        }

        byte[] expected = account.getPassword().getBytes(StandardCharsets.UTF_8);
        byte[] actual = password.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual) ? account : null;
    }
}
