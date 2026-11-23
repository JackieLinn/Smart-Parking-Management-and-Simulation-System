package tiei.ajp.project;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MainTest {
    @Test
    void mainRunsWithoutThrowing() {
        assertDoesNotThrow(Main::main);
    }
}
