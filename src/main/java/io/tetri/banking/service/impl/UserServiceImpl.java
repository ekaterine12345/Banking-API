package io.tetri.banking.service.impl;

import io.tetri.banking.dto.request.CreateUserRequest;
import io.tetri.banking.dto.response.UserResponse;
import io.tetri.banking.entity.User;
import io.tetri.banking.mapper.UserMapper;
import io.tetri.banking.repository.UserRepository;
import io.tetri.banking.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserResponse register(CreateUserRequest request) {
        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setUsername(request.username());

        User saved = userRepository.save(user);
        log.info("Registered new user id={} username={}", saved.getId(), saved.getUsername());

        return userMapper.toResponse(saved);
    }
}
