package com.ocee.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI api() {
        return new OpenAPI().info(new Info()
                .title("OCEE API")
                .version("0.1")
                .description("Online code execution engine — submissions API"));
    }
}
