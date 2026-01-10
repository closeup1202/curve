package com.project.curve.context;

import com.project.curve.envelope.EventTrace;

public interface TraceContextProvider {
    EventTrace getTrace();
}
