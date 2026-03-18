# SAPL Spring Security Tutorial

Companion code for the [Spring Security with SAPL](https://sapl.io/scenarios/spring/) scenario on sapl.io.

This project demonstrates how to secure a Spring Boot application with attribute-based access control (ABAC) using SAPL. It covers method-level enforcement with `@PreEnforce` and `@PostEnforce`, policy-based data filtering, and integration with the embedded PDP.

## Prerequisites

- JDK 17+
- Maven 3.9+

## Build and Run

```
mvn spring-boot:run
```

The application starts on `http://localhost:8080`.

## What's Inside

- Spring Boot web application with in-memory user store
- SAPL policies in `src/main/resources/policies/`
- Method-level enforcement using `@PreEnforce` and `@PostEnforce` annotations
- Embedded PDP (no external server required)

## Learn More

- [Full tutorial walkthrough](https://sapl.io/scenarios/spring/)
- [SAPL Documentation](https://sapl.io/docs/latest/)
- [SAPL Playground](https://playground.sapl.io/)
- [All scenarios](https://sapl.io/scenarios/)
