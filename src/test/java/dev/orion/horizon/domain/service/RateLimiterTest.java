/*
 * Copyright 2026 Orion Services.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.orion.horizon.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Testes do {@link RateLimiter}.
 */
final class RateLimiterTest {

    @Test
    void twoAcquiresRespectMinimumDelay() throws Exception {
        final int delayMs = 50;
        final RateLimiter limiter =
                new RateLimiter(
                        delayMs,
                        List.of(30, 60, 120),
                        3,
                        MillisSleeper.threadDefault()
                );
        final long t0 = System.nanoTime();
        limiter.acquire();
        limiter.acquire();
        final long elapsedMs =
                (System.nanoTime() - t0) / 1_000_000L;
        assertTrue(elapsedMs >= delayMs);
    }

    @Test
    void on429FirstSleepsAbout30Seconds() throws Exception {
        final List<Long> sleeps = new ArrayList<>();
        final RateLimiter limiter =
                new RateLimiter(
                        0,
                        List.of(30, 60, 120),
                        3,
                        sleeps::add
                );
        limiter.on429();
        assertEquals(List.of(30_000L), sleeps);
    }

    @Test
    void on429SecondSleepsAbout60Seconds() throws Exception {
        final List<Long> sleeps = new ArrayList<>();
        final RateLimiter limiter =
                new RateLimiter(
                        0,
                        List.of(30, 60, 120),
                        3,
                        sleeps::add
                );
        limiter.on429();
        limiter.on429();
        assertEquals(List.of(30_000L, 60_000L), sleeps);
    }

    @Test
    void on429ThirdSleepsAbout120Seconds() throws Exception {
        final List<Long> sleeps = new ArrayList<>();
        final RateLimiter limiter =
                new RateLimiter(
                        0,
                        List.of(30, 60, 120),
                        3,
                        sleeps::add
                );
        limiter.on429();
        limiter.on429();
        limiter.on429();
        assertEquals(
                List.of(30_000L, 60_000L, 120_000L),
                sleeps
        );
    }

    @Test
    void on429FourthSetsAborted() throws Exception {
        final RateLimiter limiter =
                new RateLimiter(
                        0,
                        List.of(30, 60, 120),
                        3,
                        ms -> { }
                );
        assertFalse(limiter.isAborted());
        limiter.on429();
        limiter.on429();
        limiter.on429();
        assertFalse(limiter.isAborted());
        limiter.on429();
        assertTrue(limiter.isAborted());
    }

    @Test
    void onSuccessAfter429ResetsCounter() throws Exception {
        final List<Long> sleeps = new ArrayList<>();
        final RateLimiter limiter =
                new RateLimiter(
                        0,
                        List.of(30, 60, 120),
                        3,
                        sleeps::add
                );
        limiter.on429();
        limiter.onSuccess();
        limiter.on429();
        assertEquals(List.of(30_000L, 30_000L), sleeps);
    }
}
