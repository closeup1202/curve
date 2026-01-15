package com.project.curve.autoconfigure.context;

import com.project.curve.core.context.*;
import com.project.curve.spring.context.SpringEventContextProvider;
import com.project.curve.spring.context.actor.SpringSecurityActorContextProvider;
import com.project.curve.spring.context.schema.AnnotationBasedSchemaContextProvider;
import com.project.curve.spring.context.source.SpringSourceContextProvider;
import com.project.curve.spring.context.trace.MdcTraceContextProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

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
        return new SpringSecurityActorContextProvider();
    }

    @Bean
    @ConditionalOnMissingBean(SchemaContextProvider.class)
    public SchemaContextProvider schemaContextProvider() {
        return new AnnotationBasedSchemaContextProvider();
    }

    @Bean
    @ConditionalOnMissingBean(SourceContextProvider.class)
    public SourceContextProvider sourceContextProvider(
            @Value("${spring.application.name:unknown-service}") String service,
            Environment env,
            @Value("${curve.source.version:1.0.0}") String version
    ) {
        return new SpringSourceContextProvider(service, env, version);
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
