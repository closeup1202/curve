package com.project.curve.autoconfigure.context;

import com.project.curve.core.context.*;
import com.project.curve.core.port.ClockProvider;
import com.project.curve.core.port.IdGenerator;
import com.project.curve.core.port.SnowflakeIdGenerator;
import com.project.curve.core.port.UtcClockProvider;
import com.project.curve.spring.context.*;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CurveContextAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TraceContextProvider.class)
    public TraceContextProvider traceContextProvider() {
        return new MdcTraceContextProvider();
    }

    @Bean
    @ConditionalOnMissingBean(ActorContextProvider.class)
    public ActorContextProvider actorContextProvider() {
        return new SpringSecurityActorProvider();
    }

    @Bean
    @ConditionalOnMissingBean(SchemaContextProvider.class)
    public SchemaContextProvider schemaContextProvider() {
        return new SpringSchemaContextProvider();
    }

    @Bean
    @ConditionalOnMissingBean(SourceContextProvider.class)
    public SourceContextProvider sourceContextProvider() {
        return new SpringSourceContextProvider();
    }

    @Bean
    @ConditionalOnMissingBean(EventContextProvider.class)
    public EventContextProvider eventContextProvider(
            ActorContextProvider actorContextProvider,
            TraceContextProvider traceContextProvider,
            SourceContextProvider sourceContextProvider,
            SchemaContextProvider schemaContextProvider
    ) {
        return new SpringEventContextProvider(
                actorContextProvider, traceContextProvider, sourceContextProvider, schemaContextProvider);
    }

}
