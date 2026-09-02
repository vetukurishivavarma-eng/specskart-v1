package com.specskart.shared;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenGeneratorTest {

    private final TokenGenerator gen = new TokenGenerator();

    @Test
    void tokensAreOpaqueUniqueAndUrlSafe() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String t = gen.newToken();
            assertThat(t).matches("[A-Za-z0-9_-]{20,}");
            assertThat(seen.add(t)).isTrue();
        }
    }

    @Test
    void hashIsStableAndNotThePlaintext() {
        String t = gen.newToken();
        assertThat(gen.hash(t)).isEqualTo(gen.hash(t)).isNotEqualTo(t).hasSize(64);
    }
}
