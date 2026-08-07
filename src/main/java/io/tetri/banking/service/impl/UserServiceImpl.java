package io.tetri.banking.service.impl;

import io.tetri.banking.dto.request.CreateUserRequest;
import io.tetri.banking.dto.request.UpdateUserRequest;
import io.tetri.banking.dto.response.UserResponse;
import io.tetri.banking.entity.User;
import io.tetri.banking.exception.ConflictException;
import io.tetri.banking.exception.ResourceNotFoundException;
import io.tetri.banking.mapper.UserMapper;
import io.tetri.banking.repository.UserRepository;
import io.tetri.banking.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserResponse register(CreateUserRequest request) {
        validateEmailAvailable(request.email());

        User user = userMapper.toEntity(request);
        User saved = userRepository.save(user);
        log.info("Registered new user id={} username={}", saved.getId(), saved.getUsername());

        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse update(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User update failed, no user with id={}", userId);
                    return ResourceNotFoundException.user(userId);
                });

        validateEmailAvailable(request.email(), user.getId());

        userMapper.updateEntityFromRequest(request, user);

        log.info("Updated user details id={}", userId);

        return userMapper.toResponse(user);
    }

    private void validateEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already exists");
        }
    }

    private void validateEmailAvailable(String email, UUID excludeUserId) {
        if (email != null && userRepository.existsByEmailAndIdNot(email, excludeUserId)) {
            throw new ConflictException("Email already exists");
        }
    }
}
