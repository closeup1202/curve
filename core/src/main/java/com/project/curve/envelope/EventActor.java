package com.project.curve.envelope;

public record EventActor(
        String id,   // userId or systemId
        String role, // user role
        String ip    // client ip
) {
}
