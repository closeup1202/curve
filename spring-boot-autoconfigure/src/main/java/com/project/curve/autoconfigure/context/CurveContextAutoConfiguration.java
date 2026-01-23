package com.project.curve.autoconfigure.context;

import com.project.curve.core.context.*;
import com.project.curve.spring.context.ContextAwareTaskDecorator;
import com.project.curve.spring.context.SpringEventContextProvider;
import com.project.curve.spring.context.actor.DefaultActorContextProvider;
import com.project.curve.spring.context.actor.SpringSecurityActorContextProvider;
import com.project.curve.spring.context.correlation.MdcCorrelationContextProvider;
import com.project.curve.spring.context.schema.AnnotationBasedSchemaContextProvider;
import com.project.curve.spring.context.source.SpringSourceContextProvider;
import com.project.curve.spring.context.tag.MdcTagsContextProvider;
import com.project.curve.spring.context.trace.MdcTraceContextProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskDecorator;

@Configuration
public class CurveContextAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CorrelationContextProvider.class)
    public CorrelationContextProvider correlationContextProvider() {
        return new MdcCorrelationContextProvider();
    }

    @Bean
    @ConditionalOnMissingBean(TraceContextProvider.class)
    public TraceContextProvider traceContextProvider() {
        return new MdcTraceContextProvider();
    }

    /**
     * Spring Security가 있는 경우 사용되는 Actor Context Provider
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
    @ConditionalOnMissingBean(ActorContextProvider.class)
    public ActorContextProvider springSecurityActorContextProvider() {
        return new SpringSecurityActorContextProvider();
    }

    /**
     * Spring Security가 없는 경우 사용되는 기본 Actor Context Provider
     */
    @Bean
    @ConditionalOnMissingClass("org.springframework.security.core.context.SecurityContextHolder")
    @ConditionalOnMissingBean(ActorContextProvider.class)
    public ActorContextProvider defaultActorContextProvider() {
        return new DefaultActorContextProvider();
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
            @Value("${curve.source.version:1.0.0}") String version,
            CorrelationContextProvider correlationContextProvider
    ) {
        return new SpringSourceContextProvider(service, env, version, correlationContextProvider);
    }

    @Bean
    @ConditionalOnMissingBean(TagsContextProvider.class)
    public TagsContextProvider tagContextProvider() {
        return new MdcTagsContextProvider();
    }

    @Bean
    @ConditionalOnMissingBean(EventContextProvider.class)
    public EventContextProvider eventContextProvider(
            ActorContextProvider actorContextProvider,
            TraceContextProvider traceContextProvider,
            SourceContextProvider sourceContextProvider,
            SchemaContextProvider schemaContextProvider,
            TagsContextProvider tagContextProvider
    ) {
        return new SpringEventContextProvider(
                actorContextProvider,
                traceContextProvider,
                sourceContextProvider,
                schemaContextProvider,
                tagContextProvider
        );
    }

    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public TaskDecorator contextAwareTaskDecorator() {
        return new ContextAwareTaskDecorator();
    }

}
