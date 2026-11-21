package com.mess.support;

import com.mess.domain.User;
import com.mess.domain.UserFactory;
import com.mess.repository.IUserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Test double for IUserRepository. */
public class InMemoryUserRepository implements IUserRepository {

    private final List<User> users = new ArrayList<>();
    private long nextId = 1;

    @Override
    public synchronized Optional<User> findByUsername(String username) {
        return users.stream().filter(u -> u.getUsername().equals(username)).findFirst();
    }

    @Override
    public synchronized Optional<User> findByCredentialId(String credentialId) {
        return users.stream().filter(u -> credentialId.equals(u.getCredentialId())).findFirst();
    }

    @Override
    public synchronized User save(User user) {
        User saved = UserFactory.create(user.getRole(), nextId++, user.getUsername(), user.getFullName(),
                user.getPasswordHash(), user.getCredentialId(), user.isActive());
        users.add(saved);
        return saved;
    }
}
