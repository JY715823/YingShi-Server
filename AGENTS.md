# Yingshi Server Instructions

## First read
Before any task, read:
- docs/implementation/current-task.md
- docs/product/backend-business-rules.md
- docs/contracts/api-overview.md

If the task touches a specific module, also read the matching contract doc under docs/contracts.

## Project
This is the Spring Boot backend for Yingshi.

The Android client is in a separate repository: yingshi-android.
This backend must follow the API contracts in docs/contracts.

## Tech stack
- Java
- Spring Boot
- Maven
- Spring Web
- Spring Data JPA
- Validation
- PostgreSQL for production-like profile
- H2 may be used for local dev bootstrap

## Architecture rules
Use layered structure:
- controller
- service
- repository
- domain/entity
- dto
- mapper
- config
- common

Do not put business logic in controllers.
Do not return entities directly from controllers.
Use DTOs for API input/output.

## Business rules
- Post comments and media comments must stay separate.
- Directory delete only removes post-media relation.
- System delete affects global media visibility.
- System media is not app content until imported into a post.
- Trash has three categories: deleted posts, removed media, system-deleted media.

## Completion checklist
Before finishing:
1. run a build if possible
2. fix compile errors
3. summarize changed files
4. state what is done
5. state risks / TODOs