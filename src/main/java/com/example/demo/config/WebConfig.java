package com.example.demo.config;

import com.example.demo.security.CurrentTenantResolver;
import com.example.demo.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserResolver currentUserResolver;
    private final CurrentTenantResolver currentTenantResolver;

    @Override
    public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentTenantResolver);
        resolvers.add(currentUserResolver);
    }
}
