package com.project.curve.support;

import com.project.curve.envelope.EventActor;

public class EmptyActorProvider implements ActorContextProvider {
    @Override
    public EventActor getActor() {
        return new EventActor("anonymous", "none", "0.0.0.0");
    }
}
