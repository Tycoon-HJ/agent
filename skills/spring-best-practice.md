---
name: spring-best-practice
description: Spring Boot 开发最佳实践，包含架构设计、编码规范、性能优化等知识。
requiredTools:
  - execute_command
  - file_read
  - file_write
---

## Spring Boot Best Practices

### Architecture
- Use layered architecture: Controller → Service → Repository
- Use DTO for API request/response, never expose entities directly
- Use constructor injection, not @Autowired on fields
- Use interfaces for service layer to support testing and mocking

### Coding Standards
- Use Lombok to reduce boilerplate (@Data, @Builder, @Slf4j)
- Use proper exception handling with @ControllerAdvice
- Use validation annotations (@Valid, @NotNull, @Size)
- Use records for immutable DTOs

### Testing
- Use JUnit 5 + Mockito for unit tests
- Use @SpringBootTest for integration tests
- Follow AAA pattern (Arrange, Act, Assert)
- Mock external dependencies

### Performance
- Use pagination for list queries
- Use @Cacheable for frequently accessed data
- Use async processing for long-running tasks
- Use connection pooling for database connections
