package com.yingshi.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI yingshiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Yingshi Server API")
                        .version("v0")
                        .description("Initial backend scaffold for Yingshi.")
                        .contact(new Contact().name("Yingshi Backend")));
    }
}
