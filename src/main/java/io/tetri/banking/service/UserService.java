package io.tetri.banking.service;

import io.tetri.banking.dto.request.CreateUserRequest;
import io.tetri.banking.dto.response.UserResponse;

public interface UserService {

    UserResponse register(CreateUserRequest request);
}
