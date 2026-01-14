package com.project.curve.autoconfigure.context;

import com.project.curve.core.context.*;
import com.project.curve.spring.context.*;
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
        return new SpringSecurityActorProvider();
    }

    @Bean
    @ConditionalOnMissingBean(SchemaContextProvider.class)
    public SchemaContextProvider schemaContextProvider() {
        return new SpringSchemaContextProvider();
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
