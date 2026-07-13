I want to build a microservice. The purpose is to learn microservices architecture and gain hands on experience with building and deploying microservices.
To achieve this goal, I will start by selecting a programming language and framework that I am comfortable with, then design a simple microservice that performs a specific function/service.

This microservice needs to be covers the following aspects:

- It should have a well-defined API that allows other services to interact with it.
- It should be able to handle requests and responses in a scalable manner.
- It should be containerized using Docker to ensure consistency across different environments.
- It should include proper logging and monitoring to track its performance and health.
- It should be use Redis for caching and data storage to improve performance and reduce latency.
- It should be use message queues (like Kafka) for asynchronous communication between services.
- It should be use a database (like PostgreSQL) for persistent data storage.
- It should be use a CI/CD pipeline for automated testing and deployment to ensure that changes can be made quickly and safely.

## Technology Stack:

- Programming Language: Java (version 25)
- Framework: Spring Boot (version 4.1.0) with spring Web for building RESTful APIs
- Build Tool: Maven
- Logging: SLF4J with Logback
- Lombok for reducing boilerplate code
- Spring Security, Spring OAuth2 client, spring session for authentication and authorization
- Spring Data JPA for database access with PostgreSQL driver
- Spring validation for input validation
- Containerization: Docker with Docker Compose (only for Redis, Kafka, and PostgreSQL - not for the microservice itself)
- Caching: Redis (Spring Data Redis for integration)
- Message Queue: Apache Kafka (Spring Kafka for integration)
- Database: PostgreSQL (Spring Data JPA for integration)
- CI/CD: GitHub Actions for automated testing and deployment

## First Step:

- first we needs to decide what type of microservice we want to build. I needs your help. (I want to focus on a simple microservice that can be built and deployed quickly, but also has enough complexity to demonstrate the key concepts of microservices architecture. and cover the aspects mentioned above.)

## Second Step:

- Once we have decided, I needs to flow a same patten across all microservices that I will build in the future. This pattern should include the following:

- Folder Structure: (for every microservice)

```markdown
- src/main/java/com/example/microservice
    - controller
    - service
    - repository
    - entity
    - config
    - exception
    - dto
    - mapper
    - util
- src/main/resources
    - application.yml
- src/test/java/com/example/microservice
```

## exception Handling: (for every microservice, need a global exception handler to handle exceptions and return appropriate responses)

- ErrorResponse.java (DTO for error response)

```java
package com.kavinda.example.exceptions;

import java.time.LocalDateTime;

public record ErrorResponse(
        int status,
        String error,
        String message,
        LocalDateTime timestamp
) {
}
```

- GlobalExceptionHandler.java (Global exception handler)

```java
package com.kavinda.example.exceptions;


import com.kavinda.example.exceptions.types.ResourceConflictException;
import com.kavinda.example.exceptions.types.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // --------------------------------- custom exception handlers -------------------------------

    // Handles ResourceNotFoundException
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    // Handles ResourceConflictException
    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ErrorResponse> handleResourceConflict(ResourceConflictException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(RuntimeException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    // ------------------------------- default exception handlers -------------------------------

    // Handles Validation errors (@Valid annotation use by Validation library)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {

        // Grab the first validation error message or combine them
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                errorMessage,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // Fallback handler for unexpected generic runtime errors
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(RuntimeException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Fallback handler for unIllegalArgumentException errors
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}
```

- for the custom exceptions, we can create a package called `exceptions.types` and create the following classes:
- and for the custom exceptions, we needs to create general exception classes for common scenarios like `ResourceNotFoundException`, `ResourceConflictException`, etc. so we can use them across any scenarios.

e.g. `ResourceNotFoundException.java`

```java
public User findUserById(Long id) {
    return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
}
```

- in a different scenario,

```java
public Order findOrderById(Long id) {
    return orderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
}
```

## Consistence message format: (for every microservice, we needs to use a consistent message format for all responses)

- ApiResponse.java (DTO for API response)

```java

import java.time.LocalDateTime;

public record ApiResponse<T>(
        boolean success,
        String message,
        LocalDateTime timestamp,
        T data
) {
    // Convenience factory method for quick instantiation
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, LocalDateTime.now(), data);
    }
}
```

- ApiResponse.java can be used to wrap all successful responses from the microservice, ensuring a consistent structure for all API responses. For example:

```java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable Long id) {
    User user = userService.findUserById(id);
    return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", user));
}
```

# Must have microservices:

## Authentication and Authorization Microservice

- Must have microservice is Authentication and Authorization microservice. This microservice will handle user authentication, authorization, and token management. It will provide endpoints for user registration, login, token generation, and validation. It will also manage user roles and permissions to ensure secure access to other microservices.
- This service will have both form-based (using username, email, and password) and OAuth2.0 based authentication (using Google) to provide flexibility in user authentication methods.
- The authenticated session is managed by Redis, which will store session tokens and their expiration times. This allows for quick validation of user sessions and helps in managing user state across different microservices. no JWT is used. in Redis we hold the user info like userId, roles, and permissions associated with the session token. This information is used to authorize user actions in other microservices. and we set the session token in user's browser cookies for subsequent requests to other microservices. The session token is validated against the Redis store to ensure that the user is authenticated and authorized to access the requested resources.
- for form-based authentication, we use `email` as `username` and `password` for user login. The password is securely hashed before storing it in the database to ensure that user credentials are protected. For OAuth2.0 based authentication, we integrate with Google's OAuth2.0 service to allow users to log in using their Google accounts. This provides a convenient and secure way for users to authenticate without needing to create a new account specifically for our application. (we use email instead of username is because we give user OAuth2.0 based authentication and in OAuth2.0 we use email as username, so to keep consistency we use email as username for form-based authentication as well.)

## A Load Balancer Microservice (router service) (Main job is acting as a reverse proxy and routing requests to the appropriate microservices and handle rate limiting. load balancing is optional but can be implemented if spring has built-in support for it.)

- The Load Balancer microservice will act as a reverse proxy and distribute incoming requests to the appropriate microservices based on the request path and load balancing strategy. It will handle routing, load balancing, and failover to ensure high availability and scalability of the system. This service will also provide health checks for the microservices it manages, allowing it to detect and route around any unhealthy instances.
- This service handle the rate limiting for the incoming requests to prevent abuse and ensure fair usage of the system resources.

---

with this foundation, we can start building the microservices.

1. first we needs to decide the what type of microservice we want to build. I needs your help. (I want to focus on a simple microservice that can be built and deployed quickly, but also has enough complexity to demonstrate the key concepts of microservices architecture. and cover the aspects mentioned above.)
2. give it a name and a brief description of its functionality.
3. create a simple `project-overview.md` file that outlines the purpose, functionality, and key features of the microservice. (this file will help AI agents to understand the microservice and its functionality, and will be used during generate code for the microservice.)
