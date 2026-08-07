package io.tetri.banking.dto.response;

import java.util.UUID;

public record AccountOwnerResponse(
        UUID id,
        String fullName,
        String email
) {
}
