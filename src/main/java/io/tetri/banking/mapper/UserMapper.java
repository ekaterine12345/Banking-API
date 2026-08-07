package io.tetri.banking.mapper;

import io.tetri.banking.dto.response.UserResponse;
import io.tetri.banking.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getUsername(),
                user.getCreatedAt()
        );
    }
}
