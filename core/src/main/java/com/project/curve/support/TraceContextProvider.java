package com.project.curve.support;

import com.project.curve.envelope.EventTrace;

public interface TraceContextProvider {
    EventTrace getTrace();
}
