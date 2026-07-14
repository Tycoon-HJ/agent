---
name: code-review
description: 代码审查技能，检查代码质量、安全性、性能和最佳实践。
---

## Code Review Checklist

### 1. Correctness (正确性)
- Logic errors
- Off-by-one errors
- Null pointer handling
- Exception handling
- Edge cases

### 2. Security (安全性)
- SQL injection
- XSS vulnerabilities
- Input validation
- Authentication/Authorization
- Sensitive data exposure

### 3. Performance (性能)
- N+1 queries
- Unnecessary object creation
- Memory leaks
- Inefficient algorithms
- Missing indexes

### 4. Maintainability (可维护性)
- Code readability
- Naming conventions
- Method length (max 20 lines)
- Class responsibility (SRP)
- DRY principle

### 5. Testing (测试)
- Test coverage
- Test quality
- Mock usage
- Edge case coverage

### Review Output Format
```
【严重问题】- 必须修复
- Issue 1: description
  File: xxx.java:line
  Fix: suggestion

【建议优化】- 可以改进
- Issue 2: description
  File: xxx.java:line
  Suggestion: recommendation
```
