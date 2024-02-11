---
layout: post
author: Dominic Heutelbeck
title: "Implementing Attribute-based Access Control (ABAC) with Spring and SAPL"
date: 2022-08-04
tags: abac asbac sapl spring spring-boot tutorial
categories: tutorials
excerpt_separator: <!--more-->
---

# Implementing Attribute-based Access Control (ABAC) with Spring and SAPL

## What is Attribute-based Access Control?

Attribute-based Access Control (ABAC) is an expressive access control model. A system can use ABAC to determine if a subject is permitted to perform a specific action on a resource. In this tutorial, you will learn how to secure services and APIs of a Spring Boot application using the SAPL Engine to implement ABAC. The tutorial assumes a basic familiarity with the Spring application development process.

<!--more-->

![abac.png](.attachments.594717/abac.png)

ABAC decides whether to grant access by checking the attributes of the subject, the resource, the action and the environment.

The subject may be a user, a machine, another application, or a service requesting access to a resource. Attributes may include information such as the user's department in an organisation, a security clearance level, schedules, location, or qualifications in the form of certifications.

The action is how the subject attempts to access the resource. An action may be one of the typical CRUD operations or something more domain-specific, such as "assign new operator," and attributes may include parameters of the operation.

Resources are the entities that the subject directs the action to. Resource attributes may include owners, security classifications, categories, or other arbitrary domain-specific data.

In some cases, it is also helpful to consider the authorization environment. Environment attributes include data like the system and infrastructure context or time.

An application performing authorizing of an action formulates an authorization question by collecting attributes of the subject, action, resource, and environment as required by the domain and poses it to a decision component, which then makes a decision based on domain-specific rules which the application then has to enforce.

ABAC allows the implementation of fine-grained access control rules and flexible access control models, such as Role-based access control (RBAC), Mandatory Access Control (MAC), Bell-LaPadula, Clark-Wilson, Biba, Brewer-Nash, or very domain-specific models for an application.

### The SAPL Attribute-Based Access Control (ABAC) Architecture

SAPL implements its interpretation of ABAC called Attribute Stream-Based Access Control (ASBAC). It uses publish-subscribe as the primary mode of interaction between the individual components. This tutorial explains the basic ideas. The [SAPL Documentation](https://sapl.io/docs/latest/sapl-reference.html) provides a more complete discussion of the architecture.

![sapl-architecture.png](.attachments.594717/sapl-architecture.png)

In your application, there will be multiple code paths where a subject attempts to perform some action on a resource, and based on the requirements of the domain, the action must be authorized. For example, all actions triggered by users or other components must be explicitly authorized in a zero-trust system.

A *Policy Enforcement Point (PEP)* is the logic in your application that:

* Provides access to the *Resource Access Point (RAP)*, i.e., which is the component that performs the action and potentially retrieves data.
* Formulates the authorization request as an authorization subscription, a JSON object containing values for the subject, action, resource and possibly the environment. The PEP determines the values based on the domain and context of the current attempt to perform the action.
* Delegates the decision on the authorization question to the *Policy Decision Point (PDP)* by subscribing to it using the authorization subscription.
* Enforces any decisions made by the PDP.

This tutorial will not examine the subscription nature of SAPL authorization.  Instead, it will only look at PEPs that require a single decision. Later tutorials will teach you how to use authorization subscriptions to handle reactive data types, such as `Flux<>`, Axon subscription queries, or interactive web applications using Vaadin.

In SAPL, decisions can include additional requirements for the PEP to enforce beyond simply granting or denying access. SAPL decisions can include constraints, which are additional actions the PEP must perform to grant access. If a constraint is optional, it is called an *advice*. If the constraint is mandatory, it is called an *obligation*.

SAPL also denotes a policy language that is used to express the rules that describe the overall policies governing access control in the organisation. For each authorization subscription, the PDP monitors the *Policy Retrieval Point (PRP)* for policies that are responsible, i.e., *applicable*, to the subscription. Individual policies may refer to attributes that are not stored within the authorization subscription. The PDP can subscribe to these attributes using domain-specific *Policy Information Points (PIPs)*. The PDP continuously evaluates the policies as the PIP attributes change and the policy documents are updated. It notifies the PEP when the implicit *authorization decision* changes.

When developing an application using SAPL or ABAC in general, the PDP and the systems used by the PDP are usually well-developed and only require the integration of domain-specific PIPs. A significant part of the effort in adopting the ABAC pattern lies in the implementation of the PEPs. Developing a PEP capable of flexibly handling of decisions with constraints can become very complex. SAPL provides several libraries that make this process as unobtrusive as possible and integrate deeply into the supported frameworks. This tutorial will show you how to implement PEPs in a Spring Boot application, using a JPA repository as an example.

## Project Setup

First, you will implement a simple Spring Boot application. Go to [Spring Initializr](https://start.spring.io/) and add the following dependencies to a project:

* **Spring Web** (to provide a REST API for testing your application)
* **Spring Data JPA** (to develop the domain model for your application)
* **H2 Database** (as a simple in-memory database to support the application)
* **Lombok** (to eliminate some boilerplate code)
* **Spring Boot DevTools** (to improve the development process)

For this tutorial, we will use Maven as our build tool and Java as our language.

For this tutorial, we will use Maven as our build tool and Java as our language. Name your project template as you like. SAPL is compatible with Java 11 and higher. So feel free to choose your preferred version. Depending on your operating system, there are different downloads for Java. This must be followed according to the [instructions](https://www.java.com/de/download/manual.jsp).

 Your Initializr settings should now look something like this:

![Spring Initializr.png](.attachments.594717/Spring%20Initializr%20%282%29.png)

Now click "GENERATE." Your browser will download the project template as a ".zip" file.

Now unzip the project and import it into your preferred IDE.

### Adding SAPL Dependencies

This tutorial uses the `3.0.0-SNAPSHOT` version of SAPL. To enable Maven to download the respective libraries, add the central snapshot repository to your `pom.xml` file:

```xml
    <repositories>
        <repository>
            <id>ossrh</id>
            <url>https://s01.oss.sonatype.org/content/repositories/snapshots</url>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
        </repository>
    </repositories>
```

SAPL provides a bill of materials module to help you to use compatible versions of SAPL modules. By adding the following to your `pom.xml`, you do not need to declare the `<version>` of each SAPL dependency:

```xml
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.sapl</groupId>
                <artifactId>sapl-bom</artifactId>
                <version>3.0.0-SNAPSHOT</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```

To develop an application using SAPL, you need two components. First, you need a component to make authorization decisions, the so-called Policy Decision Point (PDP). You can embed the PDP in your application or use a dedicated server application and delegate the decision-making to that remote service. This tutorial uses an embedded PDP that makes decisions locally based on policies stored in the application resources. Add the following dependency to your project:

```xml
    <dependency>
        <groupId>io.sapl</groupId>
        <artifactId>sapl-spring-pdp-embedded</artifactId>
    </dependency>
```

SAPL offers deep integration with Spring Security. This integration allows you to deploy Policy Enforcement Points easily in your Spring application using a declarative aspect-oriented programming style. Add the following dependency to your project:

```xml
    <dependency>
        <groupId>io.sapl</groupId>
        <artifactId>sapl-spring-security</artifactId>
    </dependency>
```

To use the Argon2 Password Encoder, add the following dependency:

```xml
    <dependency>
        <groupId>org.bouncycastle</groupId>
        <artifactId>bcpkix-jdk15on</artifactId>
        <version>1.70</version>
    </dependency>
```

Finally, create a new folder in the resources folder `src/main/resources` called `policies` and create a file called `pdp.json`:

```json
{
    "algorithm": "DENY_UNLESS_PERMIT",
    "variables": {}
}
```

The `algorithm` property selects an algorithm for resolving conflicting policy evaluation results. In this case, the algorithm ensures that the PDP always returns a `deny` decision when no policy evaluation returns an explicit `permit` decision.

**Note**: The algorithm in the `pdp.json` file must be written in uppercase and with `_`, unlike the same algorithm in later sections. 

You can use the `variables` property to define environment variables, such as the configuration of Policy Information Points (PIPs). All policies can access the contents of these variables.

This file completes the basic setup of the Maven project. Now, we can start implementing the application.

## The Project Domain

This tutorial will be applied to a library where books can only be viewed and checked out if the user meets the minimum age specified for the book. If you are already familiar with Spring, JPA, and Spring Security basics, you can skip this section and go directly to  [Securing a Service Method with SAPL](#securing-a-service-method-with-sapl).

### Define the Book Entity and Repository

First, define a book entity that contains an ID, a name, and a suitable age rating. You can use project Lombok annotations to automatically create getters, setters, and constructors as follows:

```java
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Book {
    
	@Id
    Long id;
    String name;
    Integer ageRating;
}
```

Now, define a matching repository interface. For now, only include a `findAll`, `findById`, and `save` method:

```java
public interface BookRepository {

    Iterable<Book> findAll();

    Optional<Book> findById(Long id);

    Book save(Book entity);
}
```

Also, define a matching repository bean to have Spring Data automatically instantiate a repository implementing your interface:

```java
@Repository
// Attention: here order of interface matters for detecting SAPL annotations. 
public interface JpaBookRepository extends BookRepository, CrudRepository<Book, Long>  { }
```

### Expose the Books using a REST Controller

To expose the books to the users, implement a simple REST controller. We use the Lombok annotation to create a constructor that takes the required beans as parameters for the dependency injection of the repository implementation:

```java
@RestController
@RequiredArgsConstructor
public class BookController {

    private final BookRepository repository;

    @GetMapping("/api/books")
    Iterable<Book> findAll() {
        return repository.findAll();
    }

    @GetMapping("/api/books/{id}")
    Optional<Book> findById(@PathVariable Long id) {
        return repository.findById(id);
    }

}
```

### Create a Custom `LibraryUser` Implementation

Now extend the `User` class from the package `org.springframework.security.core.userdetails` to create a custom `LibraryUser` implementation that contains the library user's birthdate.

```java
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class LibraryUser extends User {

    @Getter
    private LocalDate birthday;

    public LibraryUser(String username, LocalDate birthday, String password) {
        super(username, password, true, true, true, true, List.of());
        this.birthday=birthday;
    }

}
```

To make sure the custom `LibraryUser` class will end up in the security context, implement a custom `LibraryUserDetailsService` which implements the `UserDetailsService`. This class loads the various `LibraryUsers` so that they can be used for authentication. So, for the tutorial, implement a simple custom in-memory `UserDetailsService`: 

```java
public class LibraryUserDetailsService implements UserDetailsService {

    Map<String, LibraryUser> users = new HashMap<>();

    public LibraryUserDetailsService(Collection<LibraryUser> users) {
        users.forEach(this::load);
    }

    public void load(LibraryUser user) {
        users.put(user.getUsername(), user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = users.get(username);
        System.out.println("->"+user);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        return new LibraryUser(user.getUsername(), user.getBirthday(), user.getPassword());
    }

}
```

### Create a Configurations class

Create a `SecurityConfiguration` class with the Lombok annotations `@Configuration` and `@EnableWebSecurity`. This class provides methods that are automatically processed in the context of Spring Security.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        var clearSiteData = new HeaderWriterLogoutHandler(new ClearSiteDataHeaderWriter(Directive.ALL));
        // @formatter:off
        return http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                   .formLogin(login -> login.defaultSuccessUrl("/api/books", true))
                   .logout(logout -> logout.permitAll()
                           .logoutSuccessUrl("/login")
                           .addLogoutHandler(clearSiteData))
                   .build();
        // @formatter:on
    }

    @Bean
    static PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        return new LibraryUserDetailsService(DemoData.users(passwordEncoder));
    }
}
```

### Generate Test-Data on Application Startup

The default configuration with H2 and JPA will create a volatile in-memory database. Therefore, we want the system to contain some books each time the application starts. For this, create a `CommandLineRunner`. This class executes once the application context is loaded successfully:

```java
@Component
@RequiredArgsConstructor
public class DemoData implements CommandLineRunner {

    public static final String DEFAULT_RAW_PASSWORD = "password";

    private final BookRepository bookRepository;

    @Override
    public void run(String... args) {
        // @formatter:off
		bookRepository.save(new Book(1L, "Clifford: It's Pool Time!",                                  0));
		bookRepository.save(new Book(2L, "The Rescue Mission: (Pokemon: Kalos Reader #1)",             4));
		bookRepository.save(new Book(3L, "Dragonlance Chronicles Vol. 1: Dragons of Autumn Twilight",  9));
		bookRepository.save(new Book(4L, "The Three-Body Problem",                                    14));
		// @formatter:on
    }

    private static LocalDate birthdayForAgeInYears(int age) {
        return LocalDate.now().minusYears(age).minusDays(20);
    }

    public static Collection<LibraryUser> users(PasswordEncoder encoder) {
        var users = new LinkedList<LibraryUser>();
        // @formatter:off
        users.add(new LibraryUser("zoe",   birthdayForAgeInYears(17), encoder.encode(DEFAULT_RAW_PASSWORD)));
        users.add(new LibraryUser("bob",   birthdayForAgeInYears(10), encoder.encode(DEFAULT_RAW_PASSWORD)));
        users.add(new LibraryUser("alice", birthdayForAgeInYears(3),  encoder.encode(DEFAULT_RAW_PASSWORD)));
        // @formatter:on
        return users;
    }

}
```

The application domain is complete, and you can test the application. Build it with `mvn clean install` and then run it by executing `mvn spring-boot:run` on the command line or use the matching tools in your IDE.

After the application starts, go to <http://localhost:8080/api/books>. The browser will forward you to the login page. Use one of the users above to log in. You should see a list of all books:

```json
[
    {
        "id"       : 1,
        "name"     : "Clifford: It's Pool Time!",
        "ageRating": 0
    },
    {
        "id"       : 2,
        "name"     : "The Rescue Mission: (Pokemon: Kalos Reader #1)",
        "ageRating": 4
    },
    {
        "id"       : 3,
        "name"     : "Dragonlance Chronicles Vol. 1: Dragons of Autumn Twilight",
        "ageRating": 9
    },
    {
        "id"       : 4,
        "name"     : "The Three-Body Problem",
        "ageRating": 14
    }
]
```

So far, this tutorial has not used any features of SAPL, and you just created a basic Spring Boot application. Note that we did not explicitly add any dependency on Spring Security. The SAPL Spring integration has a transitive dependency on Spring Security, which activated it for the application.

## Securing Repository Methods with SAPL

### Setting Up Method Security

SAPL extends the Spring Security framework's method security features. To activate SAPL's method security for single decisions, add the `@EnableSaplMethodSecurity` Lombok annotation to your `SecurityConfiguration` class.

```java
@Configuration
@EnableWebSecurity
@EnableSaplMethodSecurity
public class SecurityConfiguration {
    ...
}
```

### Adding the first PEP

The SAPL Spring Boot integration uses annotations to add PEPs to methods and classes. The scope of this tutorial covers the two variants `@PreEnforce` and `@PostEnforce`. Depending on which annotation is selected, the PEP is placed before or after the method execution. As a first example, add the `@PreEnforce` annotation to the `findById`method of the `BookRepository` interface:

```java
public interface BookRepository {

    Iterable<Book> findAll();

    @PreEnforce
    Optional<Book> findById(Long id);

    Book save(Book entity);
}
```

### Enable console output

Add  `io.sapl.pdp.embedded.print-text-report=true` to your `application.properties` file. This property provides interesting insights into the decision-making of the PDP during policy evaluation, in a human-friendly way. You can also select the properties `...print-json-report` or `...print-trace`. `print-trace` is the most fine-grained explanation and is only recommended as a last resort for troubleshooting.

For Debug and to obtain more information in general, e.g., which policy documents are loaded at startup, you can use the properties `logging.level.io.sapl=TRACE` and `logging.level.org.springframework=WARN`.

Restart the application, log in, and navigate to <http://localhost:8080/api/books/1>. You should now see an error page including the statement: `There was an unexpected error (type=Forbidden, status=403).`

Inspect the console, and you will find out what happened behind the scenes. The logs should contain some statements similar to the following:

```
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : --- The PDP made a decision ---
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Subscription: {"subject":{"authorities":[],"details":{"remoteAddress":"0:0:0:0:0:0:0:1","sessionId":"EF114D1F3433826A178E7A97F6DFA7D2"},"authenticated":true,"principal":{"username":"zoe","authorities":[],"accountNonExpired":true,"accountNonLocked":true,"credentialsNonExpired":true,"enabled":true,"birthday":"2006-12-26"},"name":"zoe"},"action":{"http":{"characterEncoding":"UTF-8","protocol":"HTTP/1.1","scheme":"http","serverName":"localhost","serverPort":8080,"remoteAddress":"0:0:0:0:0:0:0:1","remoteHost":"0:0:0:0:0:0:0:1","remotePort":55905,"isSecure":false,"localName":"0:0:0:0:0:0:0:1","localAddress":"0:0:0:0:0:0:0:1","localPort":8080,"method":"GET","contextPath":"","requestedSessionId":"5456E2F43FFFBD37B4FFBDE9FB67E661","requestedURI":"/api/books/1","requestURL":"http://localhost:8080/api/books/1","servletPath":"/api/books/1","headers":{"host":["localhost:8080"],"connection":["keep-alive"],"sec-ch-ua":["\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\""],"sec-ch-ua-mobile":["?0"],"sec-ch-ua-platform":["\"Windows\""],"dnt":["1"],"upgrade-insecure-requests":["1"],"user-agent":["Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"],"sec-purpose":["prefetch;prerender"],"purpose":["prefetch"],"accept":["text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"],"sec-fetch-site":["none"],"sec-fetch-mode":["navigate"],"sec-fetch-user":["?1"],"sec-fetch-dest":["document"],"accept-encoding":["gzip, deflate, br"],"accept-language":["de-DE,de;q=0.9"]},"cookies":[{"name":"JSESSIONID","value":"5456E2F43FFFBD37B4FFBDE9FB67E661"}],"locale":"de_DE","locales":["de_DE","de"]},"java":{"name":"findById","declaringTypeName":"io.sapl.springtutorial.domain.BookRepository","modifiers":["public"],"instanceof":[{"name":"jdk.proxy4.$Proxy118","simpleName":"$Proxy118"},{"name":"io.sapl.springtutorial.domain.JpaBookRepository","simpleName":"JpaBookRepository"},{"name":"io.sapl.springtutorial.domain.BookRepository","simpleName":"BookRepository"},{"name":"org.springframework.data.repository.CrudRepository","simpleName":"CrudRepository"},{"name":"org.springframework.data.repository.Repository","simpleName":"Repository"},{"name":"org.springframework.data.repository.Repository","simpleName":"Repository"},{"name":"org.springframework.transaction.interceptor.TransactionalProxy","simpleName":"TransactionalProxy"},{"name":"org.springframework.aop.SpringProxy","simpleName":"SpringProxy"},{"name":"org.springframework.aop.framework.Advised","simpleName":"Advised"},{"name":"org.springframework.aop.TargetClassAware","simpleName":"TargetClassAware"},{"name":"org.springframework.core.DecoratingProxy","simpleName":"DecoratingProxy"},{"name":"java.lang.reflect.Proxy","simpleName":"Proxy"},{"name":"java.io.Serializable","simpleName":"Serializable"},{"name":"java.lang.Object","simpleName":"Object"}],"arguments":[1]}},"resource":{"http":{"characterEncoding":"UTF-8","protocol":"HTTP/1.1","scheme":"http","serverName":"localhost","serverPort":8080,"remoteAddress":"0:0:0:0:0:0:0:1","remoteHost":"0:0:0:0:0:0:0:1","remotePort":55905,"isSecure":false,"localName":"0:0:0:0:0:0:0:1","localAddress":"0:0:0:0:0:0:0:1","localPort":8080,"method":"GET","contextPath":"","requestedSessionId":"5456E2F43FFFBD37B4FFBDE9FB67E661","requestedURI":"/api/books/1","requestURL":"http://localhost:8080/api/books/1","servletPath":"/api/books/1","headers":{"host":["localhost:8080"],"connection":["keep-alive"],"sec-ch-ua":["\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\""],"sec-ch-ua-mobile":["?0"],"sec-ch-ua-platform":["\"Windows\""],"dnt":["1"],"upgrade-insecure-requests":["1"],"user-agent":["Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"],"sec-purpose":["prefetch;prerender"],"purpose":["prefetch"],"accept":["text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"],"sec-fetch-site":["none"],"sec-fetch-mode":["navigate"],"sec-fetch-user":["?1"],"sec-fetch-dest":["document"],"accept-encoding":["gzip, deflate, br"],"accept-language":["de-DE,de;q=0.9"]},"cookies":[{"name":"JSESSIONID","value":"5456E2F43FFFBD37B4FFBDE9FB67E661"}],"locale":"de_DE","locales":["de_DE","de"]},"java":{"name":"findById","declaringTypeName":"io.sapl.springtutorial.domain.BookRepository","modifiers":["public"],"instanceof":[{"name":"jdk.proxy4.$Proxy118","simpleName":"$Proxy118"},{"name":"io.sapl.springtutorial.domain.JpaBookRepository","simpleName":"JpaBookRepository"},{"name":"io.sapl.springtutorial.domain.BookRepository","simpleName":"BookRepository"},{"name":"org.springframework.data.repository.CrudRepository","simpleName":"CrudRepository"},{"name":"org.springframework.data.repository.Repository","simpleName":"Repository"},{"name":"org.springframework.data.repository.Repository","simpleName":"Repository"},{"name":"org.springframework.transaction.interceptor.TransactionalProxy","simpleName":"TransactionalProxy"},{"name":"org.springframework.aop.SpringProxy","simpleName":"SpringProxy"},{"name":"org.springframework.aop.framework.Advised","simpleName":"Advised"},{"name":"org.springframework.aop.TargetClassAware","simpleName":"TargetClassAware"},{"name":"org.springframework.core.DecoratingProxy","simpleName":"DecoratingProxy"},{"name":"java.lang.reflect.Proxy","simpleName":"Proxy"},{"name":"java.io.Serializable","simpleName":"Serializable"},{"name":"java.lang.Object","simpleName":"Object"}]}},"environment":null}
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"DENY"}
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Timestamp   : 2024-01-15T19:02:09.643312400Z
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Algorithm   : "DENY_UNLESS_PERMIT"
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Matches     : NONE (i.e.,no policies/policy sets were set, or all target expressions evaluated to false or error.)
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : No policy or policy sets have been evaluated
```

The first log entry contains the authorization subscription for which a decision is made. The second log entry contains the decision made by the PDP, followed by a timestamp and the algorithm used to resolve conflicting results.

The fifth log entry will contain a list of all matching policies that are being evaluated, followed by more detailed information about each policy.

The subscription is not very readable this way. Let us apply some formatting to the JSON data to unpack the subscription object:

```json
{
    "subject":{
        "authorities":[],
        "details":{
            "remoteAddress":"0:0:0:0:0:0:0:1",
            "sessionId":"EF114D1F3433826A178E7A97F6DFA7D2"
        },
        "authenticated":true,
        "principal":{
            "username":"zoe",
            "authorities":[],
            "accountNonExpired":true,
            "accountNonLocked":true,
            "credentialsNonExpired":true,
            "enabled":true,
            "birthday":"2006-12-26"
        },
        "name":"zoe"
    },
    "action":{
        "http":{
            "characterEncoding":"UTF-8",
            "protocol":"HTTP/1.1",
            "scheme":"http",
            "serverName":"localhost",
            "serverPort":8080,
            "remoteAddress":"0:0:0:0:0:0:0:1",
            "remoteHost":"0:0:0:0:0:0:0:1",
            "remotePort":55905,
            "isSecure":false,
            "localName":"0:0:0:0:0:0:0:1",
            "localAddress":"0:0:0:0:0:0:0:1",
            "localPort":8080,
            "method":"GET",
            "contextPath":"",
            "requestedSessionId":"5456E2F43FFFBD37B4FFBDE9FB67E661",
            "requestedURI":"/api/books/1",
            "requestURL":"http://localhost:8080/api/books/1",
            "servletPath":"/api/books/1",
            "headers":{
                "host":["localhost:8080"],
                "connection":["keep-alive"],
                "sec-ch-ua":["\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\""],
                "sec-ch-ua-mobile":["?0"],
                "sec-ch-ua-platform":["\"Windows\""],
                "dnt":["1"],
                "upgrade-insecure-requests":["1"],
                "user-agent":["Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"],
                "sec-purpose":["prefetch;prerender"],
                "purpose":["prefetch"],
                "accept":["text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"],
                "sec-fetch-site":["none"],
                "sec-fetch-mode":["navigate"],
                "sec-fetch-user":["?1"],
                "sec-fetch-dest":["document"],
                "accept-encoding":["gzip, deflate, br"],
                "accept-language":["de-DE,de;q=0.9"]
            },
            "cookies":[{"name":"JSESSIONID","value":"5456E2F43FFFBD37B4FFBDE9FB67E661"}],
            "locale":"de_DE",
            "locales":["de_DE","de"]
        },
        "java":{
            "name":"findById",
            "declaringTypeName":"io.sapl.springtutorial.domain.BookRepository",
            "modifiers":["public"],
            "instanceof":[
                {"name":"jdk.proxy4.$Proxy118","simpleName":"$Proxy118"},
                {"name":"io.sapl.springtutorial.domain.JpaBookRepository","simpleName":"JpaBookRepository"},
                {"name":"io.sapl.springtutorial.domain.BookRepository","simpleName":"BookRepository"},
                {"name":"org.springframework.data.repository.CrudRepository","simpleName":"CrudRepository"},
                {"name":"org.springframework.data.repository.Repository","simpleName":"Repository"},
                {"name":"org.springframework.data.repository.Repository","simpleName":"Repository"},
                {"name":"org.springframework.transaction.interceptor.TransactionalProxy","simpleName":"TransactionalProxy"},
                {"name":"org.springframework.aop.SpringProxy","simpleName":"SpringProxy"},
                {"name":"org.springframework.aop.framework.Advised","simpleName":"Advised"},
                {"name":"org.springframework.aop.TargetClassAware","simpleName":"TargetClassAware"},
                {"name":"org.springframework.core.DecoratingProxy","simpleName":"DecoratingProxy"},
                {"name":"java.lang.reflect.Proxy","simpleName":"Proxy"},
                {"name":"java.io.Serializable","simpleName":"Serializable"},
                {"name":"java.lang.Object","simpleName":"Object"}
            ],
            "arguments":[1]
        }
    },
    "resource":{
        "http":{
            [ ... ]
        },
        "java":{
            [ ... ]
        }
    },
    "environment":null
}
```

As you can see, without any specific configuration, the subscription is a massive object with significant redundancies. This is because the SAPL Engine and Spring integration do not have any domain knowledge regarding the application. Thus, the PEP gathers any information it can find that could reasonably describe the three required objects (subject, action, resource) for an authorization subscription.

By default, the PEP attempts to marshal the `Authentication` object from Spring's `SecurityContext` directly into a JSON object for the `subject`. This is a reasonable approach in most cases, and as you can see, `subject.principal.birthday` contains the data you previously defined for the custom `LibraryUser`class and is made available to the PDP.

The `action` and `resource` objects are almost identical. Consider where one can find information from the application context to describe these objects. Without any domain knowledge, the PEP can only gather technical information.

Let's begin with the action and its associated Java information. The PEP can consider the name and types of the protected classes and methods to describe the action. For example, the method name `findById` can be considered a  verb that describes the action, while the argument `1` is an attribute of this action.

At the same time, the argument `1` can also be considered as the resource's ID. What information about the PEP's Java context is actually relevant is unknown to the PEP. Therefore, it adds all information it can gather to the action and resource.

Second, if the action happens in the context of a web application, often the application context contains an HTTP request. Again, this HTTP request can describe the action, e.g., the HTTP method GET, or the resource, e.g., the URL naturally identifies a resource.

This kind of subscription object is wasteful. Later, you will learn how to customize the subscription to be more compact and match your application domain. For now, we stick with the default configuration.

## Storing SAPL Policies for an Embedded PDP

As you can see in the sixth line in the console log, the PDP did not find any policy document matching the authorization subscription, as we have not yet defined any policy for the application. With an embedded PDP, policies can be stored alongside the application's resources folder or somewhere on the host's filesystem. The difference between these options is that with policies in the resources, once you have built and started the application, the policies are static at runtime. When using the filesystem, the PDP will actively monitor the folder and update its behavior accordingly when policies change at runtime.

The default configuration of an embedded PDP is the first option, so the application's policies are currently embedded in the resources.

If you want to configure this behavior, you have to add `io.sapl.pdp.embedded.pdp-config-type = FILESYSTEM` to the application.properties file.

The `pdp.json` file and the policies can be stored in different folders. This is regulated with the properties `io.sapl.pdp.embedded.config-path` for the `pdp.json` file and `io.sapl.pdp.embedded.policies-path` for the policies. Both require a valid file system path for the folder where the files are located.

**Note:** `\` within the path must be replaced by `/`, e.g., `C:\Users` by `C:/Users`.

## Creating SAPL Policies

### Basic Information

The stored policy documents must adhere to some rules:

- The SAPL PDP will only load documents that have the suffix `.sapl` .
- Each document contains exactly one policy or one policy set.
- The top-level policies or policy sets in all documents must have pairwise different names.
- All `.sapl` documents must be syntactically correct, or the PDP may fall back to a default decision determined by the algorithm given in the `pdp.json` configuration.

A SAPL policy document contains the following minimum elements:

* The *keyword* `policy`, declaring that the document contains a policy (as opposed to a policy set; You will learn about policy sets later)
* A unique policy *name* so that the PDP can distinguish them
* The *entitlement*, which is the decision result the PDP should return if the policy is successfully evaluated, i.e., `permit` or `deny`

Other optional elements will be explained later.

### First SAPL Policies - Permit All or Deny All

The most basic policies are the policies to either permit or deny all actions without further inspection of any attributes.

Let us start with a "permit all" policy. Add a file `permit_all.sapl` to the `resources/policies` folder of the maven project with the following contents:

```
policy "permit all" permit
```

As described above, we start with the keyword `policy`, which indicates that it is a policy.
This keyword is always followed by the *name* of the SAPL policy as a string. In this case `"permit all"`. The policy name must always be followed by the *entitlement*, in this case `permit`.

In this scenario, we haven't described any rules in the policy. Therefore, all of its rules are satisfied, and the policy tells the PDP to return a `permit` decision, regardless of any details of the attributes contained in the authorization subscription or any external attributes from PIPs. This type of policy is dangerous and not very practical for production systems. However, it is helpful during development to be able to perform quick tests without authorization getting in the way.

Now restart the application, authenticate with any user and access <http://localhost:8080/api/books/1> again.

Now you should get the data for book 1:

```json
{
    "id"        : 1,
    "name"      : "Clifford: It's Pool Time!",
    "ageRating" : 0
}
```

And your log should look like this:

```
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : --- The PDP made a decision ---
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Subscription: { ... }
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"PERMIT"}
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Timestamp   : 2024-01-18T14:41:39.298519100Z
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Algorithm   : "DENY_UNLESS_PERMIT"
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Matches     : ["permit all"]
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Policy Evaluation Result ===================
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Name        : "permit all"
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Entitlement : "PERMIT"
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"PERMIT"}
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Target      : true
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Where       : true
```

In this log, you can see on the sixth line that the PRP has identified exactly one matching policy document for the authorization subscription. Let's take a look at the evaluation result for the policy document with the name `"permit all"`.

Let's start with lines 11 and 12, where you can see that the PDP came to the conclusion that both the *target expression* and the *where* block, which will be explained later, were evaluated as `true`. This is because the `"permit all"` policy does not contain any rules. Since both values are true, the decision of the evaluation of the individual policy document corresponds to the entitlement defined in the policy, i.e., `permit`.

Finally, as this is the only matching document with a decision, the `DENY_UNLESS_PERMIT` combining algorithm also concludes to return a `permit`. Therefore, the PEP allows access to the repository method.

Now, create a "deny all" policy. Add a file `deny_all.sapl` to the `resources/policies` folder of the maven project with the following contents:

```
policy "deny all" deny
```

Now restart the application, authenticate with any user and access <http://localhost:8080/api/books/1> again.

The PDP will grant access, and the log will look similar to this:

```
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : --- The PDP made a decision ---
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Subscription: { ... }
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"PERMIT"}
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Timestamp   : 2024-01-18T18:55:20.363441300Z
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Algorithm   : "DENY_UNLESS_PERMIT"
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Matches     : ["permit all","deny all"]
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Policy Evaluation Result ===================
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Name        : "permit all"
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Entitlement : "PERMIT"
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"PERMIT"}
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Target      : true
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Where       : true
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Policy Evaluation Result ===================
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Name        : "deny all"
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Entitlement : "DENY"
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"DENY"}
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Target      : true
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Where       : true
```

Note that your system's ordering of the log entries may be slightly different. The log indicates that both policies matched the subscription and that the PDP evaluated them. Then, the combining algorithm resolved the two decisions, i.e., one `permit` and one `deny`, to `permit`.

The PDP uses the `DENY_UNLESS_PERMIT` combining algorithm selected in the `pdp.json` configuration file. This algorithm is relatively permissive because it only returns `deny` if no `permit` is present. The SAPL engine implements alternative algorithms to resolve the presence of different, potentially contradicting, decisions (also see [SAPL Documentation - Combining Algorithm](https://sapl.io/docs/latest/sapl-reference.html#combining-algorithm-2)). For the tutorial domain, select a more restrictive algorithm. Replace `DENY_UNLESS_PERMIT` in the `pdp.json` file with `DENY_OVERRIDES`. This algorithm prioritizes `deny` decisions over `permit`.

Now restart the application, authenticate with any user and access <http://localhost:8080/api/books/1> again.

The application should `deny` access and the log will look similar to this (remember, the line order may vary):

```
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : --- The PDP made a decision ---
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Subscription: { ... }
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"DENY"}
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Timestamp   : 2024-01-20T14:45:49.524911400Z
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Algorithm   : "DENY_OVERRIDES"
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Matches     : ["permit all","deny all"]
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Policy Evaluation Result ===================
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Name        : "permit all"
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Entitlement : "PERMIT"
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"PERMIT"}
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Target      : true
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Where       : true
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Policy Evaluation Result ===================
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Name        : "deny all"
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Entitlement : "DENY"
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"DENY"}
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Target      : true
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Where       : true
```

As expected, the combining algorithm gave precedence to the `deny` decision.

Finally, rename `deny_all.sapl` to `deny_all.sapl.off` and `permit_all.sapl` to `permit_all.sapl.off`. Now access to the book should be denied, as the PDP only loads documents with the `.sapl`suffix.

The PDP returns `not applicable` because it did not find a document making a decision explicitly and `DENY_OVERRIDES` does not have a default decision like `DENY_UNLESS_PERMIT`. The PDP may also return `indeterminate` if an error occurred during policy evaluation. In both cases, a PEP must not grant access. Additional information about the different results of a policy evaluation can be found in the [SAPL documentation](https://sapl.io./docs/latest/sapl-reference.html#policy-2).

In this section, you learned how a PEP and PDP interact in SAPL and how the PDP combines outcomes of different policies. In the next step, you will learn how to write more practical policies and when precisely a policy is *applicable*, i.e., matches, for an authorization subscription.

### Create Domain-Specific Policies

First, add a `@PreEnforce` PEP to the `findAll` method of the `BookRepository`:

```java
public interface BookRepository {

    @PreEnforce
    Iterable<Book> findAll();

    @PreEnforce
    Optional<Book> findById(Long id);

    Book save(Book entity);
}
```

Let's write a policy that says, "Only Bob can see individual book entries". Writing such *natural language policies (NLP)* is important to avoid inconsistencies between administrators and other users. Now, create a policy document `permit_bob_for_books.sapl` in the policies folder under resources, and translate the NLP into a SAPL policy document as follows:

```
policy "only bob may see individual book entries"
permit action.java.name == "findById" & action.java.declaringTypeName =~ ".*BookRepository$"
where
  subject.name == "bob";
```

Now restart and log in as Bob. You should see the same error page, including the statement: `There was an unexpected error (type=Forbidden, status=403).` Like at the beginning of the tutorial. Your log should look as follows:

```
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : --- The PDP made a decision ---
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : Subscription: { ... }
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"NOT_APPLICABLE"}
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : Timestamp   : 2024-01-21T19:22:53.850109800Z
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : Algorithm   : "DENY_OVERRIDES"
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : Matches     : NONE (i.e.,no policies/policy sets were set, or all target expressions evaluated to false or error.)
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : No policy or policy sets have been evaluated
```

This happens because we have implemented in our `SecurityConfiguration` class, that we are automatically redirected to `/api/books` after a successful login, which results in the `findAll` method being called. However, we have not created a corresponding policy for accessing this method, which is monitored by a PEP.

Now access an individual book, <http://localhost:8080/api/books/1>. Access will be granted, and the log looks like this:

```
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : --- The PDP made a decision ---
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Subscription: { ... }
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"PERMIT"}
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Timestamp   : 2024-01-21T19:23:00.783253700Z
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Algorithm   : "DENY_OVERRIDES"
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Matches     : ["only bob may see individual book entries"]
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Policy Evaluation Result ===================
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Name        : "only bob may see individual book entries"
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Entitlement : "PERMIT"
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"PERMIT"}
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Target      : true
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Where       : true
```

Now go to <http://localhost:8080/logout> and log out. Then log in as Zoe and try to access <http://localhost:8080/api/books/1>.

The application denies access, and the log looks like this:

```
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : --- The PDP made a decision ---
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Subscription: { ... }
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Timestamp   : 2024-01-21T20:20:50.669216100Z
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Algorithm   : "DENY_OVERRIDES"
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Matches     : ["only bob may see individual book entries"]
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Policy Evaluation Result ===================
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Name        : "only bob may see individual book entries"
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Entitlement : "PERMIT"
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"NOT_APPLICABLE"}
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Target      : true
[nio-8080-exec-1] i.s.p.i.ReportingDecisionInterceptor     : Where       : false
```

As you can see, there are several differences in the decision-making process of the PDP. First, let us examine what leads to the fact that there are no applicable (matching) documents when accessing `/api/books` or after a successful login.

If you look at the policy, there is an expression following the *entitlement* `permit` that states `action.java.name == "findById" & action.java.declaringTypeName =~ ".*BookRepository$"` and ends at the (optional) keyword `where`. An expression at this position in a policy is called the *target expression*. The *target expression* is a rule which determines if the policy is applicable to a given authorization subscription. The PDP only evaluates policy documents if the expression is `true` for the given subscription and the policies are therefore applicable. As seen in the `"permit all"` example, if the *target expression* is missing, the policy is always considered applicable.

In this case, the *target expression* examines two attributes of the action in the subscription. It validates if `action.java.name` is equal to `"findById"` and if `action.java.declaringTypeName` matches the regular expression `".*BookRepository$"`, i.e., the attribute string ends with `BookRepository`, using the regex comparison operator `=~`.

**Note**: The JSON structure with `:{` must be converted to a `.`.

These two expressions explain why the PDP has identified the policy document `"permit_bob_for_books.sapl"` as applicable when accessing individual books, but does not find a matching document when accessing the entire list.

Please note that SAPL distinguishes between lazy Boolean operators, i.e., `&&` and `||` for AND and OR, and eager Boolean operators `&` and `|` respectively. *Target expressions* only allow eager operators, a requirement for efficient indexing of larger sets of policies.

The PDP evaluates the complete policy in the case where the user attempts to access the individual book, i.e., the rules following `where` are evaluated. This section of the policy is called the *where block* or *body*. The *where block* contains an arbitrary number of rules or variable assignments ending with a `;`. Each rule is a Boolean expression. The *where block* as a whole evaluates to `true` when all of its rules evaluate to `true`. Rules are evaluated lazily from top to bottom.

In the situations above, the rule `subject.name == "bob";` is only `true` for the case where Bob is accessing the book.

In this section, you have learned when a SAPL document is applicable, the purpose of the *target expression*, and where to find the *where block* of a policy.

Next, you will learn how to customize the authorization subscription and use temporal functions to only grant access to age-appropriate books.

### Enforce the Age Rating of Individual Books

First, in preparation, deactivate all existing policies in your project by deleting them or appending the `.off` suffix to the filename.

The goal of this section is only to grant access to books appropriate for the user's age. To make this decision, the PDP needs the birthdate of the user (attribute of the subject), the age rating of the book (attribute of the resource), and the current date (attribute of the environment). When you examine the authorization subscription sent in the previous examples, you will notice that only the user's birthdate is currently available in the subscription. How can we make the other attributes available for the PDP in the policies?

Generally, there are two potential sources for attributes: the authorization subscription or Policy Information Points (PIPs).

Consider the age rating of the book. This information is not known to the PEP before executing the query. Therefore, in the `BookRepository`, replace the `@PreEnforce` on `findById` with a `@PostEnforce` annotation as follows:

```java
public interface BookRepository {
    
    @PreEnforce
    Iterable<Book> findAll();

    @PostEnforce(subject  = "authentication.getPrincipal()", 
                 action   = "'read book'", 
                 resource = "returnObject")
    Optional<Book> findById(Long id);

    Book save(Book entity);
}
```

This annotation does a couple of things:

* First, invoke the method.
* Construct a custom authorization subscription with [Spring Expression Language (SpEL)](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#expressions).
* Subscribe to the PDP with the custom authorization subscription.
* Enforce the decision.

When we inspected the original automatically generated authorization subscription, you will remember that the resulting object was relatively large and technical. Here, the parameters of the `@PostEnforce` annotation helps create a more domain-specific precise authorization subscription.

The parameter `subject = "authentication.getPrincipal()"` extracts the principal object from the authentication object and uses it as the subject-object in the subscription.

The parameter `action = "'read book'"` sets the action-object in the subscription to the string constant `read book`.

Finally, the parameter `resource = "returnObject"` sets the resource-object in the subscription to the method invocation result. As this resource is the book entity, it will automatically contain its `ageRating` attribute.

After identifying these objects, the PEP uses the `ObjectMapper` in the Spring application context to serialize the objects to JSON.

The resulting authorization subscription will look similar to this:

```json
{
  "subject": {
    "password": null,
    "username": "zoe",
    "authorities": [],
    "accountNonExpired": true,
    "accountNonLocked": true,
    "credentialsNonExpired": true,
    "enabled": true,
    "birthday": "2007-01-03"
  },
  "action": "read book",
  "resource": {
    "id": 1,
    "name": "Clifford: It's Pool Time!",
    "ageRating": 0
  },
  "environment": null
}
```

This authorization subscription is much more manageable and practical than the automatic guesswork the Spring integration performs without any customization.

The policy we will write to enforce the book age restriction will introduce a number of new concepts:

* Definition of local attribute variables
* Usage of Policy Information Points
* Function libraries
* Logging for debugging policy information

Create a policy document `check_age_logging.sapl` as follows:

```
policy "check age" 
permit action == "read book"
where 
   var birthday    = log.infoSpy("birthday     : ", subject.birthday);
   var today       = log.infoSpy("today        : ", time.dateOf(|<time.now>));
   var age         = log.infoSpy("age          : ", time.timeBetween(birthday, today, "years"));
   var ageRating   = log.infoSpy("age rating   : ", resource.ageRating);
                     log.infoSpy("is older     : ", age >= ageRating );
```

In its *target expression*, the policy `check age` scopes its applicability to all authorization subscriptions with the action `read book`.

Using the keyword in the first line of the block, the policy defines a local attribute variable named `birthday` and assigns it to the `subject.birthday` attribute. While doing so, the expression `subject.birthday` is wrapped in a function call. The function `log.infoSpy` is a utility function, logging its parameter to the console using the log level `INFO`. The function is the identity function with the logging as a side-effect. Similar functions exist for other log levels. The logging function library also contains functions like `log.debug`, without the `Spy` which logs their parameter and always returns `true`. These log functions can be used as single rule lines in a `where` block.

The second line of the `where` block assigns the current date to the variable `today`. In SAPL, angled brackets `<ATTRIBUTE_IDENTIFIER>` always denotes an attribute stream, a subscription to an external attribute source, using a Policy Information Point (PIP). In this case, the identifier `time.now` is used to access the current time in UTC from the system clock.

In this scenario, we do not need the streaming nature of the time, and we are only interested in the first event in the attribute stream. Prepending the pipe symbol to the angled brackets `|<>` only takes the head element, i.e., the first event in the attribute stream, and then unsubscribes from the PIP. The time libraries in SAPL use ISO 8601 strings to represent time. The function `time.dateOF` is then used to extract the date component of the timestamp retrieved from the PIP.

The policy calculates the subject's age in years using the `time.timeBetween` function and the defined variables. The `ageRating` of the book is stored in the matching variable.

Note that the engine evaluates variable assignment rules from top to bottom. And each rule has access to variables defined above it. Also, these assignment rules always evaluate to `true` unless an error occurs during evaluation.

Finally, the `age` is compared with the `ageRating` and the policy returns `true`if the subject's age is above the book's age rating.

For example, if you log in as Zoe and access the first book, the logs will look similar to this:

```
[nio-8080-exec-8] i.sapl.functions.LoggingFunctionLibrary  : [SAPL] birthday     :  "2007-01-04"
[nio-8080-exec-8] i.sapl.functions.LoggingFunctionLibrary  : [SAPL] today        :  "2024-01-24"
[nio-8080-exec-8] i.sapl.functions.LoggingFunctionLibrary  : [SAPL] age          :  17
[nio-8080-exec-8] i.sapl.functions.LoggingFunctionLibrary  : [SAPL] age rating   :  0
[nio-8080-exec-8] i.sapl.functions.LoggingFunctionLibrary  : [SAPL] is older     :  true
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : --- The PDP made a decision ---
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : Subscription: {"subject":{"password":null,"username":"zoe","authorities":[],"accountNonExpired":true,"accountNonLocked":true,"credentialsNonExpired":true,"enabled":true,"birthday":"2007-01-04"},"action":"read book","resource":{"id":1,"name":"Clifford: It's Pool Time!","ageRating":0},"environment":null}
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"PERMIT"}
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : Timestamp   : 2024-01-24T09:49:35.745099400Z
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : Algorithm   : "DENY_OVERRIDES"
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : Matches     : ["check age"]
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : Policy Evaluation Result ===================
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : Name        : "check age"
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : Entitlement : "PERMIT"
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"PERMIT"}
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : Target      : true
[nio-8080-exec-8] i.s.p.i.ReportingDecisionInterceptor     : Where       : true
```

However, if Alice attempts to access book four, access will be denied because the policy is not applicable, i.e., not all rules evaluate to `true`:

```
[nio-8080-exec-3] i.sapl.functions.LoggingFunctionLibrary  : [SAPL] birthday     :  "2021-01-03"
[nio-8080-exec-3] i.sapl.functions.LoggingFunctionLibrary  : [SAPL] today        :  "2024-01-23"
[nio-8080-exec-3] i.sapl.functions.LoggingFunctionLibrary  : [SAPL] age          :  3
[nio-8080-exec-3] i.sapl.functions.LoggingFunctionLibrary  : [SAPL] age rating   :  14
[nio-8080-exec-3] i.sapl.functions.LoggingFunctionLibrary  : [SAPL] is older     :  false
[nio-8080-exec-3] i.s.p.i.ReportingDecisionInterceptor     : --- The PDP made a decision ---
[nio-8080-exec-3] i.s.p.i.ReportingDecisionInterceptor     : Subscription: {"subject":{"password":null,"username":"alice","authorities":[],"accountNonExpired":true,"accountNonLocked":true,"credentialsNonExpired":true,"enabled":true,"birthday":"2021-01-03"},"action":"read book","resource":{"id":4,"name":"The Three-Body Problem","ageRating":14},"environment":null}
[nio-8080-exec-3] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"NOT_APPLICABLE"}
[nio-8080-exec-3] i.s.p.i.ReportingDecisionInterceptor     : Timestamp   : 2024-01-24T09:54:43.202487200Z
[nio-8080-exec-3] i.s.p.i.ReportingDecisionInterceptor     : Algorithm   : "DENY_OVERRIDES"
[nio-8080-exec-3] i.s.p.i.ReportingDecisionInterceptor     : Matches     : ["check age"]
[nio-8080-exec-3] i.s.p.i.ReportingDecisionInterceptor     : Policy Evaluation Result ===================
[nio-8080-exec-3] i.s.p.i.ReportingDecisionInterceptor     : Name        : "check age"
[nio-8080-exec-3] i.s.p.i.ReportingDecisionInterceptor     : Entitlement : "PERMIT"
[nio-8080-exec-3] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"NOT_APPLICABLE"}
[nio-8080-exec-3] i.s.p.i.ReportingDecisionInterceptor     : Target      : true
[nio-8080-exec-3] i.s.p.i.ReportingDecisionInterceptor     : Where       : false
```

The policy can be written more compactly without logging and using an `import` statement:

```
import time.*
policy "check age compact" 
permit action == "read book"
where 
   var age = timeBetween(subject.birthday, dateOf(|<now>), "years");
   age >= resource.ageRating;
```

Imports allow the use of a shorter name instead of the fully qualified name of functions or attribute finders stored in libraries within a SAPL policy document.

For instance, the statement `import time.*` imports all time functions and attribute finders from the time library, making them available under their simple name (e.g., `<now>` instead of `<time.now>`).

It is also possible to import only single functions and use them under their simple names, as well as to choose an alias for a certain library with `'library name' as 'alias'`.

## How to transform and constrain outputs with SAPL Policies?

In this part of the tutorial, you will learn how to use policies to change the outcome of queries and how to trigger side effects using constraints.

SAPL can be used to instruct the PEP to grant access only if other instructions are enforced at the same time. SAPL supports three types of additional instructions and calls them *constraints*. These constraints are as follows:

* *Obligation*, i.e., a mandatory condition that the PEP must fulfil. If this is not possible, access must be denied.
* *Advice*, i.e., an optional condition that the PEP should fulfil. If it fails to do so, access is still granted if the original decision was `permit`.
* *Transformation*, i.e., a special case of an obligation that expresses that the PEP must replace the accessed resource with the resource-object supplied in the authorization decision.

An authorization decision containing a constraint expresses that the PEP must only grant (or deny) access when it can fulfil all obligations.

For example, any doctor may access a patient's medical record in an emergency. However,  the system must log access if the doctor is not the attending doctor of the patient in question, triggering an audit process. Such a set of requirements is a so-called "breaking the glass scenario."

### How to use Transformations in SAPL Policies?

To have some more data to work with, extend the domain model by adding some content to the books:

```java
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Book {
    @Id
    Long id;
    String name;
    Integer ageRating;
    String content;
}
```

Also, extend the `DemoData` class accordingly:

```java
bookRepository.save(new Book(1L, "Clifford: It's Pool Time!", 0, "*Woof*"));
bookRepository.save(new Book(2L, "The Rescue Mission: (Pokemon: Kalos Reader #1)", 4, "Gotta catch 'em all!"));
bookRepository.save(new Book(3L, "Dragonlance Chronicles Vol. 1: Dragons of Autumn Twilight", 9, "Some fantasy story."));
bookRepository.save(new Book(4L, "The Three-Body Problem", 14, "Space is scary."));
```

We want to change the policies of the library in a way that users not meeting the age requirement do not get their access denied. Instead, only the content of the applied book should be blackened. To implement this change, add the following `check_age_transform.sapl` policy document to the application's policies:

```
import time.*
policy "check age transform" 
permit action == "read book"
where 
   var age = timeBetween(subject.birthday, dateOf(|<now>), "years");
   age < resource.ageRating;
transform
   resource |- {
        @.content : filter.blacken(3,0,"\u2588")
   }
```

This policy introduces the `transform` expression for the *transformations*.

If the policy is applicable, i.e., all rules evaluate to `true`, whatever JSON value the `transform` expression evaluates to is added to the authorization decision as the property `resource`. The presence of a `resource` object in the authorization decision instructs the PEP to replace the original `resource` data with the one supplied.

In this case, the so-called filter operator `|-` is applied to the `resource` object. The filter operator enables the selection of individual parts of a JSON value for manipulation, e.g., applying a function to the selected value. In this case, the operator selects the `content` key of the resource and replaces it with a version of its content, only exposing the three leftmost characters and replacing the rest with a black square ("\\u2588" in Unicode). The selection expression is very powerful. Please refer to the [SAPL Documentation](https://sapl.io./docs/latest/sapl-reference.html#filtering) for a full explanation.

Ensure that the original age-checking policy is still in place. Now, restart and log in as Alice.

When accessing <http://localhost:8080/api/books/1>, you will get:

```json
{
    "id"        : 1,
    "name"      : "Clifford: It's Pool Time!",
    "ageRating" : 0,
    "content"   : "*Woof*"
}
```

But of course, because Alice is only three years old, the content of the age-inappropriate book <http://localhost:8080/api/books/4> will be blackened:

```json
{
    "id"        : 4,
    "name"      : "The Three-Body Problem",
    "ageRating" : 14,
    "content"   : "Spa████████████"
}
```

The logs for this access attempt look like this:

```
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : --- The PDP made a decision ---
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Subscription: {"subject":{"password":null,"username":"alice","authorities":[],"accountNonExpired":true,"accountNonLocked":true,"credentialsNonExpired":true,"enabled":true,"birthday":"2021-01-04"},"action":"read book","resource":{"id":4,"name":"The Three-Body Problem","ageRating":14,"content":"Space is scary."},"environment":null}
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"PERMIT","resource":{"id":4,"name":"The Three-Body Problem","ageRating":14,"content":"Spa????????????"}}
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Timestamp   : 2024-01-24T10:36:21.144698500Z
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Algorithm   : "DENY_OVERRIDES"
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Matches     : ["check age compact","check age transform"]
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Policy Evaluation Result ===================
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Name        : "check age compact"
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Entitlement : "PERMIT"
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"NOT_APPLICABLE"}
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Target      : true
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Where       : false
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Policy Evaluation Result ===================
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Name        : "check age transform"
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Entitlement : "PERMIT"
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"PERMIT","resource":{"id":4,"name":"The Three-Body Problem","ageRating":14,"content":"Spa????????????"}}
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Target      : true
[io-8080-exec-10] i.s.p.i.ReportingDecisionInterceptor     : Where       : true
```

The PRP discovered both policies to be matching the subscription. The PDP starts to evaluate both and the `check age compact` policy evaluates to `NOT_APPLICABLE`, because Alice is not old enough to read "The Three-Body Problem". At the same time, the `check age transform` policy evaluates to `permit`. As a result, the authorization decision also contains a resource object, namely the transformed one. The PEP has, therefore, replaced the changed resource object.

### How to use Obligations and Advice in SAPL Policies?

The `check age transform` policy with the `transform` statement was the first example of a policy that instructs the PEP to grant access only if additional statements are enforced at the same time.

Now, we want to add an obligation to this policy. The system should also log attempted access to books that are not age-appropriate. This will allow parents to discuss the book with their children first.

To do so, modify the `check_age_transform.sapl` policy as follows:

```
import time.*
policy "check age transform" 
permit action == "read book"
where 
   var age = timeBetween(subject.birthday, dateOf(|<now>), "years");
   age < resource.ageRating;
obligation {
				"type": "logAccess",
				"message": "Attention, "+subject.username+" accessed the book '"+resource.name+"'."
           }
transform
   resource |- {
        @.content : filter.blacken(3,0,"\u2588")
   }
```

Now log in as Alice and attempt to access <http://localhost:8080/api/books/2>.

Access will be denied, and the logs look as follows:

```
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : --- The PDP made a decision ---
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : Subscription: {"subject":{"password":null,"username":"alice","authorities":[],"accountNonExpired":true,"accountNonLocked":true,"credentialsNonExpired":true,"enabled":true,"birthday":"2021-01-04"},"action":"read book","resource":{"id":2,"name":"The Rescue Mission: (Pokemon: Kalos Reader #1)","ageRating":4,"content":"Gotta catch 'em all!"},"environment":null}
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"PERMIT","resource":{"id":2,"name":"The Rescue Mission: (Pokemon: Kalos Reader #1)","ageRating":4,"content":"Got?????????????????"},"obligations":[{"type":"logAccess","message":"Attention, alice accessed the book 'The Rescue Mission: (Pokemon: Kalos Reader #1)'."}]}
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : Timestamp   : 2024-01-24T13:37:19.333683900Z
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : Algorithm   : "DENY_OVERRIDES"
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : Matches     : ["check age compact","check age transform"]
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : Policy Evaluation Result ===================
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : Name        : "check age compact"
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : Entitlement : "PERMIT"
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"NOT_APPLICABLE"}
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : Target      : true
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : Where       : false
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : Policy Evaluation Result ===================
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : Name        : "check age transform"
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : Entitlement : "PERMIT"
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"PERMIT","resource":{"id":2,"name":"The Rescue Mission: (Pokemon: Kalos Reader #1)","ageRating":4,"content":"Got?????????????????"},"obligations":[{"type":"logAccess","message":"Attention, alice accessed the book 'The Rescue Mission: (Pokemon: Kalos Reader #1)'."}]}
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : Target      : true
[nio-8080-exec-9] i.s.p.i.ReportingDecisionInterceptor     : Where       : true
```

Despite the PDP's decision to permit access, it was still denied due to the obligation to log the access in the authorization decision. This is because SAPL expresses obligations and advice as arbitrary JSON objects and does not know which of them might be relevant in an application domain or how policies decide to describe them. Thus, the PEP was unable to understand and enforce the logging obligation, resulting in the denial of access.

To support the logging obligation, implement a so-called *constraint handler provider*:

```java
@Slf4j
@Service
public class LoggingConstraintHandlerProvider implements RunnableConstraintHandlerProvider {

	@Override
	public Signal getSignal() {
		return Signal.ON_DECISION;
	}

	@Override
	public boolean isResponsible(JsonNode constraint) {
		return constraint != null && constraint.has("type")
				&& "logAccess".equals(constraint.findValue("type").asText());
	}

	@Override
	public Runnable getHandler(JsonNode constraint) {
		return () -> log.info(constraint.findValue("message").asText());

	}
}
```

The SAPL Spring integration offers different hooks in the execution path where applications can add constraint handlers. Depending on the annotation and if the underlying method returns a value synchronously or uses reactive datatypes like `Flux<>` different hooks are available.

For each of these hooks, the constraint handlers can influence the execution differently. E.g., for `@PreEnforce` the constraint handler may attempt to change the arguments handed over to the method. The different hooks map to interfaces a service bean can implement to provide the capability of enforcing different types of constraints. You can find a full list of the potential interfaces in the `sapl-spring-security` module.

In the case of logging, the constraint handler triggers a side effect by logging the message contained in the obligation to the console. Therefore, the `RunnableConstraintHandlerProvider` is the appropriate interface to implement.

This interface requires three methods:

* `isResponsible` returns `true` if the handlers provided can fulfil the constraint.
* `getSignal` returns when the `Runnable` should be executed. Here, the PEP immediately executes the `Runnable`  after it receives the decision from the PDP. Most other signals are primarily relevant for reactive data types and are out of the scope of this tutorial.
* `getHandler` returns the `Runnable` enforcing the constraint.

When logging in as Alice and attempting to access <http://localhost:8080/api/books/2> access will be granted, and the logs now contain the following line:

```
[nio-8080-exec-9] i.s.s.s.LoggingConstraintHandlerProvider : Attention, alice accessed the book 'The Rescue Mission: (Pokemon: Kalos Reader #1)'.
```

Let's try another example of an obligation.

After a successful login, access is still denied. This is because we have not yet implemented a policy for the `findAll` method. Therefore, we need a policy that allows us to list all age-appropriate books. However, we do not replace the resource with the `transform` instruction. In a real library, this would require a significant amount of work for the PEP, potentially involving the processing of several hundred data records. Instead, we instruct the PEP to display only certain books.

First, complete the `@PreEnforce` on `findAll` in the `BookRepository` as follows:

```java
public interface BookRepository {
    
    @PreEnforce(subject = "authentication.getPrincipal()",
		        action="'list books'")
    Iterable<Book> findAll();

    @PostEnforce(subject  = "authentication.getPrincipal()", 
                 action   = "'read book'", 
                 resource = "returnObject")
    Optional<Book> findById(Long id);

    Book save(Book entity);
}
```

The concept is the same as with the `findById` method. The parameter `subject = "authentication.getPrincipal()"` extracts the principal object and uses it as the subject-object in the subscription. The parameter `action = "'list books'"` sets the action object to the string `list books`. However, the resource remains unchanged or is guessed due to `@PreEnforce`.

To see all accessible books write a policy as follows:

```
import time.*
policy "filter content in collection"
permit action == "list books"
obligation
	{
		"type" : "jsonContentFilterPredicate",
		"conditions" : [
						{
							"path" : "$.ageRating",
							"type" : "<=",
							"value" : timeBetween(subject.birthday, dateOf(|<now>), "years")
						}
					   ]
	}
```

We use the `ContentFilterPredicateProvider` class that is already provided in the SAPL engine. This class can be used to filter a JSON object and extract nodes that match the specified conditions.

The class is addressed in the obligation using the assignment `"type" : "jsonContentFilterPredicate"`. This is followed by the `conditions` keyword, where one or more conditions are specified and checked. Here, the array is checked for JSON nodes that contain the `ageRating` element and whether the age rating is lower than the age of the accessing user. Any matching nodes are then displayed.

Instead of using the provided class, we can again implement our own *constraint handler provider*:

```java
@Service
public class FilterByAgeProvider implements FilterPredicateConstraintHandlerProvider {

    @Override
    public boolean isResponsible(JsonNode constraint) {
        return constraint != null && constraint.has("type")
                && "filterBooksByAge".equals(constraint.findValue("type").asText()) && constraint.has("age")
                && constraint.get("age").isInt();
    }

    @Override
    public Predicate<Object> getHandler(JsonNode constraint) {
        return o -> {
            var age = constraint.get("age").asInt();
            if (o instanceof Book book) {
                return age >= book.getAgeRating();
            } else {
                return true;
            }
        };
    }
}
```

Here, we implement the `FilterPredicateConstraintHandlerProvider` interface.

This interface requires two methods:

* `isResponsible` returns `true` if the handlers provided can fulfil the constraint.
* `getHandler` returns a `Predicate` what is a boolean-valued function of one argument for testing.

Now log in as Bob, and you will see the following list of books:

```json
[
  {
    "id": 1,
    "name": "Clifford: It's Pool Time!",
    "ageRating": 0,
    "content": "*Woof*"
  },
  {
    "id": 2,
    "name": "The Rescue Mission: (Pokemon: Kalos Reader #1)",
    "ageRating": 4,
    "content": "Gotta catch 'em all!"
  },
  {
    "id": 3,
    "name": "Dragonlance Chronicles Vol. 1: Dragons of Autumn Twilight",
    "ageRating": 9,
    "content": "Some fantasy story."
  }
]
```

## Create a Policy Set

A SAPL policy set allows a group of policies to be viewed separately and evaluated using a selected combining algorithm. The result is passed to the PDP and evaluated with the remaining policies (sets). The same algorithms are available as for final conflict resolution, including the `first-applicable` algorithm.

**Note**: In contrast to the `pdp.json` file, the algorithms in policy sets must be written in lowercase and with `-`.

A SAPL policy set consists of the following elements:

* the *keyword* `set`, declaring that the document contains a policy set
* a unique policy set *name*, so that the PDP can distinguish them
* a *combining algorithm*
* an optional *target expression*
* optional variable assignments
* two or more policies

As a small example, create a file `check_age_by_id_set.sapl`. Only one of the two policies, `'check age compact'` and `'check age transform'`, from the previous chapter can be applicable at a time. Therefore, let's create a policy set that processes both policies.

```
import time.*
import filter.*

set "check age set"
first-applicable
for action == "read book"
var birthday    = subject.birthday;
var today       = time.dateOf(|<time.now>);
var age         = time.timeBetween(birthday, today, "years");
	
	policy "check age transform set"
	permit
	where
		age < resource.ageRating;
    obligation {
				"type": "logAccess",
				"message": "Attention, "+subject.username+" accessed the book '"+resource.name+"'."
           	   }
	transform
    	resource |- {
        	@.content : blacken(3,0,"\u2588")
        }

	policy "check age compact set"
	permit
		age >= resource.ageRating
```

The rules for policies of a set are the same as for top-level policies. So the `where` keyword is optional, as you can see in the second policy of the set. If you use the optional keyword, the line must also end with a `;` as usual.

Deactivate the two policy documents `'check_age_compact.sapl'` and `'check_age_transform.sapl'` with the extension `.off` and restart the application.

Now, log in as Bob and access <http://localhost:8080/api/books/3>.
Your logs look as follows:

```
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     : --- The PDP made a decision ---
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     : Subscription: {"subject":{"password":null,"username":"bob","authorities":[],"accountNonExpired":true,"accountNonLocked":true,"credentialsNonExpired":true,"enabled":true,"birthday":"2014-01-20"},"action":"read book","resource":{"id":3,"name":"Dragonlance Chronicles Vol. 1: Dragons of Autumn Twilight","ageRating":9,"content":"Some fantasy story."},"environment":null}
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"PERMIT"}
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     : Timestamp   : 2024-02-09T08:14:37.063506800Z
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     : Algorithm   : "DENY_OVERRIDES"
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     : Matches     : ["check age set"]
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     : Policy Set Evaluation Result ===============
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     : Name        : "check age set"
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     : Algorithm   : "FIRST_APPLICABLE"
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"PERMIT"}
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     : Target      : true
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     :    |Policy Evaluation Result ===================
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     :    |Name        : "check age transform set"
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     :    |Entitlement : "PERMIT"
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     :    |Decision    : {"decision":"NOT_APPLICABLE"}
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     :    |Target      : true
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     :    |Where       : false
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     :    |Policy Information Point Data:
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     :    | - {"value":"2024-02-09T08:14:37.061507Z","attributeName":"<time.now>","timestamp":{"value":"2024-02-09T08:14:37.061507Z"}}
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     :    |Policy Evaluation Result ===================
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     :    |Name        : "check age compact set"
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     :    |Entitlement : "PERMIT"
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     :    |Decision    : {"decision":"PERMIT"}
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     :    |Target      : true
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     :    |Where       : true
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     :    |Policy Information Point Data:
[nio-8080-exec-5] i.s.p.i.ReportingDecisionInterceptor     :    | - {"value":"2024-02-09T08:14:37.061507Z","attributeName":"<time.now>","timestamp":{"value":"2024-02-09T08:14:37.061507Z"}}
```

The policies of the set are indented to distinguish them from the rest of the policies. They are ordered directly after the result of the *target expression*. Unlike top-level policies, the policies of a set explicitly follow the order specified in the set or are evaluated in this order. Combining a logical evaluation sequence with a `FIRST_APPLICABLE` algorithm can save time or avoid work for the PEP. In the case of book three, both policies are still evaluated.

Now access <http://localhost:8080/api/books/4>, you will get:

```
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : --- The PDP made a decision ---
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Subscription: {"subject":{"password":null,"username":"bob","authorities":[],"accountNonExpired":true,"accountNonLocked":true,"credentialsNonExpired":true,"enabled":true,"birthday":"2014-01-20"},"action":"read book","resource":{"id":4,"name":"The Three-Body Problem","ageRating":14,"content":"Space is scary."},"environment":null}
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"PERMIT","resource":{"id":4,"name":"The Three-Body Problem","ageRating":14,"content":"Spa????????????"},"obligations":[{"type":"logAccess","message":"Attention, bob accessed the book 'The Three-Body Problem'."}]}
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Timestamp   : 2024-02-09T08:19:12.474766200Z
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Algorithm   : "DENY_OVERRIDES"
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Matches     : ["check age set"]
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Policy Set Evaluation Result ===============
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Name        : "check age set"
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Algorithm   : "FIRST_APPLICABLE"
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Decision    : {"decision":"PERMIT","resource":{"id":4,"name":"The Three-Body Problem","ageRating":14,"content":"Spa????????????"},"obligations":[{"type":"logAccess","message":"Attention, bob accessed the book 'The Three-Body Problem'."}]}
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     : Target      : true
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     :    |Policy Evaluation Result ===================
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     :    |Name        : "check age transform set"
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     :    |Entitlement : "PERMIT"
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     :    |Decision    : {"decision":"PERMIT","resource":{"id":4,"name":"The Three-Body Problem","ageRating":14,"content":"Spa????????????"},"obligations":[{"type":"logAccess","message":"Attention, bob accessed the book 'The Three-Body Problem'."}]}
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     :    |Target      : true
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     :    |Where       : true
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     :    |Policy Information Point Data:
[nio-8080-exec-7] i.s.p.i.ReportingDecisionInterceptor     :    | - {"value":"2024-02-09T08:19:12.4707663Z","attributeName":"<time.now>","timestamp":{"value":"2024-02-09T08:19:12.470766300Z"}}
[nio-8080-exec-7] i.s.s.s.LoggingConstraintHandlerProvider : Attention, bob accessed the book 'The Three-Body Problem'.
```

As you can see, the second policy in the set was not evaluated because the first policy was already applicable.

## Collection of Obligations, Advice and Transformations

Before we end this tutorial, there is one more thing to note:

Finally, all the *obligations* and *advice* from policies that have been evaluated to the same decision are collected in an authorization decision. It is important to note that there may be a difference between policies and policy sets. In the case of sets, not all policies may be evaluated. In this case, only the *obligations* and *advice* from evaluated policies with the same result may be collected and transferred.

Another special case concerns *transformations*. It is not possible to combine multiple transformation statements through multiple policies. Any combining algorithm in SAPL will not return the decision `PERMIT` if there is more than one policy evaluating to `PERMIT` and at least one of them contains a transformation statement (this is called **transformation uncertainty**).

You can download the demo project from the [GitHub repository for this tutorial](https://github.com/heutelbeck/sapl-tutorial-01-spring).

## Conclusions

In this tutorial series, you have learned the basics of attribute-based access control and how to secure a Spring application with SAPL.

You can achieve much more with SAPL, including deploying flexible distributed organization-wide authorization infrastructures. The following tutorials in this series will focus on more complex obligations, testing, reactive data types, data streaming, customizing UIs based on policies, and applications based on the Axon framework.

Feel free to engage with the developers and community on our [Discord Server](https://discord.gg/pRXEVWm3xM).
