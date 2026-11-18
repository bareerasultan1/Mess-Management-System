package com.mess.repository;

import com.mess.domain.User;

import java.util.Optional;

public interface IUserRepository {

    Optional<User> findByUsername(String username);

    Optional<User> findByCredentialId(String credentialId);

    /** Inserts the user and returns a copy that carries the generated id. */
    User save(User user);
}
