# Problem Details Error Handling

## Goal

All error responses from the demo app return RFC 9457 problem details bodies with content type `application/problem+json`. Business errors return **400** to distinguish them from technical 404s (wrong URL). Validation errors include a `violations` array.

## Exception Hierarchy

```
BusinessException (abstract, always 400)
├── PetNotFoundException
├── OwnerNotFoundException
├── VisitNotFoundException
└── InvalidOwnerIdException
```

`BusinessException` carries a message. **Every** existing `jakarta.ws.rs.NotFoundException` usage across all resources is replaced by the corresponding custom exception:

| Location | Current | Replacement |
|---|---|---|
| `PetResource.get()` | `NotFoundException` | `PetNotFoundException` |
| `PetResource.update()` | `NotFoundException` | `PetNotFoundException` |
| `PetResource.patch()` | `NotFoundException` | `PetNotFoundException` |
| `PetResource.delete()` | `NotFoundException` | `PetNotFoundException` |
| `OwnerResource.get()` | `NotFoundException` | `OwnerNotFoundException` |
| `VisitResource.get()` | `NotFoundException` | `VisitNotFoundException` |

The mapper derives the type URN from the exception class name: `PetNotFoundException` → `urn:problem-type:pet-not-found` (kebab-case, strip `Exception` suffix).

## Response Formats

### Business error (400)

Content-Type: `application/problem+json`

```json
{
  "type": "urn:problem-type:pet-not-found",
  "title": "Bad Request",
  "status": 400,
  "detail": "Pet with ID 42 not found"
}
```

### Validation error (400)

Content-Type: `application/problem+json`

The `detail` field is omitted for validation errors; the `violations` array provides per-field detail.

```json
{
  "type": "urn:problem-type:constraint-violation",
  "title": "Bad Request",
  "status": 400,
  "violations": [
    { "field": "name", "message": "must not be blank" },
    { "field": "status", "message": "must not be null" }
  ]
}
```

## Two Mappers

1. **`BusinessExceptionMapper`** — catches `BusinessException`, returns 400 with content type `application/problem+json`, type URN derived from class name, detail from `getMessage()`.
2. **`ConstraintViolationExceptionMapper`** — catches `ConstraintViolationException`, returns 400 with content type `application/problem+json` and `violations` array.

## Bean Validation

Add `quarkus-hibernate-validator` dependency. Add Bean Validation annotations to record fields:

- **Pet**: `@NotBlank name`, `@NotNull status`
- **Owner**: `@NotBlank name`, `@NotBlank email`
- **Visit**: `@NotBlank date`, `@NotBlank reason`

Add `@Valid` on request body parameters for POST and PUT endpoints.

**PATCH is excluded** from Bean Validation: `PetResource.patch()` accepts a raw `JsonObject` for partial updates, so `@Valid` does not apply. Partial updates may omit fields by design.

## Business Validation

- **Pet create/update**: check that `ownerId` references an existing owner; throw `InvalidOwnerIdException` if not. This applies to `create()`, `update()` (deprecated but still accepts requests), and `patch()` (when `ownerId` is present in the patch). Since `ownerId` is a primitive `long`, a missing value defaults to 0 and fails the owner existence check with an "invalid owner ID" error rather than a "required field" error — this is acceptable.
- **Visit create**: check that `petId` (from the path) references an existing pet; throw `PetNotFoundException` if not.

## List Endpoints for Nonexistent Parents

List endpoints that filter by a parent ID (`OwnerResource.listPets()`, `VisitResource.list()`) **silently return empty lists** when the parent does not exist. No existence check is added — this is intentionally lenient.

## Delete Not-Found Behavior

- `PetResource.delete()`: replace `NotFoundException` with `PetNotFoundException`.
- `VisitResource.delete()`: keep as-is (silently succeeds, idempotent deletes are fine).

## Out of Scope

- OpenAPI `@APIResponse` annotations for error responses
- Date format validation on `Visit.date` (only `@NotBlank`)

## Scope

All three resources: pets, owners, visits.
