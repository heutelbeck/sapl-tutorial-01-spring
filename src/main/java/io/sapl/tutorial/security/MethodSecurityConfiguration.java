package io.sapl.tutorial.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import io.sapl.spring.config.EnableSaplMethodSecurity;

@Configuration
@EnableWebSecurity
@EnableSaplMethodSecurity
public class MethodSecurityConfiguration {

}
