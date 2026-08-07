package io.tetri.banking.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(

        @Size(min = 2, max = 100, message = "firstName must be between 2 and 100 characters")
        String firstName,

        @Size(min = 2, max = 100, message = "lastName must be between 2 and 100 characters")
        String lastName,

        @Email(message = "email must be a valid email address")
        String email
) {
}
