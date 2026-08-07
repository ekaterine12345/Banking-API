package io.tetri.banking.service;

import io.tetri.banking.dto.request.CreateUserRequest;
import io.tetri.banking.dto.request.UpdateUserRequest;
import io.tetri.banking.dto.response.UserResponse;

import java.util.UUID;

public interface UserService {

    UserResponse register(CreateUserRequest request);

    UserResponse update(UUID userId, UpdateUserRequest request);
}
