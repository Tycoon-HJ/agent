---
name: unit-test-generator
description: 单元测试生成技能，支持 JUnit 5 + Mockito，遵循 AAA 模式。
requiredTools:
  - execute_command
  - file_read
  - file_write
---

## Unit Test Generation Rules

### Framework
- Use JUnit 5 (Jupiter) for test framework
- Use Mockito for mocking dependencies
- Use @SpringBootTest for integration tests
- Use @WebMvcTest for controller tests

### Test Structure (AAA Pattern)
```java
@Test
void shouldDoSomething() {
    // Arrange - 准备测试数据和 mock
    given(mockService.getData()).willReturn(testData);

    // Act - 执行被测试的方法
    Result result = target.methodUnderTest(input);

    // Assert - 验证结果
    assertThat(result).isEqualTo(expected);
}
```

### Naming Convention
- Test class: `{ClassName}Test`
- Test method: `should{ExpectedBehavior}When{Condition}`
- Example: `shouldReturnUserWhenIdExists`

### Coverage Requirements
- Cover happy path (正常路径)
- Cover edge cases: null, empty, boundary values
- Cover exception cases
- Use @ParameterizedTest for data-driven tests

### Mock Guidelines
- Mock all external dependencies
- Verify interactions when behavior matters
- Use @InjectMocks for the class under test
- Use @Mock for dependencies
