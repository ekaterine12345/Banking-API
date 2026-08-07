package io.tetri.banking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bankingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mini Banking API")
                        .description("Manages bank accounts and money transfers between them.")
                        .version("v1")
                        .contact(new Contact().name("Ekaterine Gurgenidze")));
    }
}
