package com.project.curve.port;

import com.project.curve.envelope.EventId;

public interface IdGenerator {
    EventId generate();
}
