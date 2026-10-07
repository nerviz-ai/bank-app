package dev.nerviz.bankapp.infrastructure.rest.idempotent;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class IdempotencyWebConfig implements WebMvcConfigurer {

    private final IdempotencyKeyInterceptor interceptor;

    IdempotencyWebConfig(IdempotencyKeyInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**");
    }
}
