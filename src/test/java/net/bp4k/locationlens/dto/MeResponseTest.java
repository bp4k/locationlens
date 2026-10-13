package net.bp4k.locationlens.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.bp4k.locationlens.entity.User;

class MeResponseTest {

    @Test
    void fromRightSplitsAThreeWordDisplayName() {
        User user = new User(UUID.randomUUID(), "mary@example.com", "Mary Anne Rose");

        MeResponse response = MeResponse.from(user);

        assertEquals("Mary Anne", response.firstName());
        assertEquals("Rose", response.lastName());
        assertEquals("Mary Anne Rose", response.fullName());
        assertEquals("mary@example.com", response.email());
    }

    @Test
    void fromTreatsASingleWordDisplayNameAsFirstNameWithEmptyLastName() {
        User user = new User(UUID.randomUUID(), "john@example.com", "John");

        MeResponse response = MeResponse.from(user);

        assertEquals("John", response.firstName());
        assertEquals("", response.lastName());
        assertEquals("John", response.fullName());
    }

    @Test
    void fromSplitsATwoWordDisplayNameIntoFirstAndLastName() {
        User user = new User(UUID.randomUUID(), "john@example.com", "John Barclays");

        MeResponse response = MeResponse.from(user);

        assertEquals("John", response.firstName());
        assertEquals("Barclays", response.lastName());
        assertEquals("John Barclays", response.fullName());
    }
}
