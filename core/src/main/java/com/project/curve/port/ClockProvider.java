package com.project.curve.port;

import java.time.Instant;

public interface ClockProvider {
    Instant now();
}