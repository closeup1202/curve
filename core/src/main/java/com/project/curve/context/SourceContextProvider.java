package com.project.curve.context;

import com.project.curve.envelope.EventSource;

public interface SourceContextProvider {
    EventSource getSource();
}
