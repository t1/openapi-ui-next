# Problem Details Error Handling Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add RFC 9457 problem details error responses to all demo app resources.

**Architecture:** Two exception mappers — one for business exceptions (custom hierarchy, always 400), one for Bean Validation constraint violations (400 with violations array). All existing `NotFoundException` usages replaced with custom exceptions. Type URN derived from exception class name.

**Tech Stack:** Quarkus 3.18, Jakarta REST, Hibernate Validator, Jackson

**Spec:** `docs/superpowers/specs/2026-03-20-problem-details-design.md`

**Skills:** @tdder:tdd, @tdder:java, @tdder:maven

---

## Chunk 1: Exception Hierarchy and Business Exception Mapper

### Task 1: Add `quarkus-hibernate-validator` dependency

**Files:**
- Modify: `demo/pom.xml`

- [x] **Step 1: Add the dependency**

Add to the `<dependencies>` section of `demo/pom.xml`, after the `quarkus-rest-jackson` dependency:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-hibernate-validator</artifactId>
</dependency>
```

- [x] **Step 2: Verify it compiles**

Run: `mvn compile -pl demo`
Expected: BUILD SUCCESS

- [x] **Step 3: Commit**

```bash
git add demo/pom.xml
git commit -m "add quarkus-hibernate-validator dependency"
```

### Task 2: BusinessException base class and BusinessExceptionMapper

**Files:**
- Create: `demo/src/main/java/com/github/t1/openapi/ui/demo/BusinessException.java`
- Create: `demo/src/main/java/com/github/t1/openapi/ui/demo/BusinessExceptionMapper.java`
- Create: `demo/src/main/java/com/github/t1/openapi/ui/demo/PetNotFoundException.java`
- Modify: `demo/src/test/java/com/github/t1/openapi/ui/demo/PetResourceTest.java`
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java`

#### TDD: Business exception mapper returns problem details for pet-not-found

- [ ] **Step 1: Write the failing test**

Add to `PetResourceTest`:

```java
@Test void shouldReturnProblemDetailsForUnknownPet() {
    given()
            .when().get("/pets/999")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", is("urn:problem-type:pet-not-found"))
            .body("title", is("Bad Request"))
            .body("status", is(400))
            .body("detail", is("Pet with ID 999 not found"));
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -pl demo -Dtest='PetResourceTest#shouldReturnProblemDetailsForUnknownPet'`
Expected: FAIL — currently returns 404 without problem details body

- [ ] **Step 3: Implement BusinessException, PetNotFoundException, and BusinessExceptionMapper**

Create `BusinessException.java`:

```java
package com.github.t1.openapi.ui.demo;

public abstract class BusinessException extends RuntimeException {
    BusinessException(String message) { super(message); }
}
```

Create `PetNotFoundException.java`:

```java
package com.github.t1.openapi.ui.demo;

public class PetNotFoundException extends BusinessException {
    PetNotFoundException(long id) { super("Pet with ID " + id + " not found"); }
}
```

Create `BusinessExceptionMapper.java`:

```java
package com.github.t1.openapi.ui.demo;

import jakarta.json.Json;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Provider
class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {
    @Override public Response toResponse(BusinessException exception) {
        return Response.status(400)
                .type("application/problem+json")
                .entity(Json.createObjectBuilder()
                        .add("type", typeUrn(exception))
                        .add("title", "Bad Request")
                        .add("status", 400)
                        .add("detail", exception.getMessage())
                        .build()
                        .toString())
                .build();
    }

    static String typeUrn(BusinessException exception) {
        var name = exception.getClass().getSimpleName()
                .replaceAll("Exception$", "")
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .toLowerCase();
        return "urn:problem-type:" + name;
    }
}
```

Replace `NotFoundException` with `PetNotFoundException` in `PetResource.get()`:

```java
// Change from:
.orElseThrow(NotFoundException::new);
// To:
.orElseThrow(() -> new PetNotFoundException(id));
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -pl demo -Dtest='PetResourceTest#shouldReturnProblemDetailsForUnknownPet'`
Expected: PASS

- [ ] **Step 5: Update remaining 404 tests to expect 400 with problem details (test-first)**

Remove `shouldReturn404ForUnknownPet` (superseded by the new test).

Update `shouldReturn404ForPutUnknownPet`:

```java
@Test void shouldReturnProblemDetailsForPutUnknownPet() {
    given()
            .contentType(APPLICATION_JSON)
            .body("{\"name\":\"X\",\"status\":\"available\",\"ownerId\":1}")
            .when().put("/pets/999")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", is("urn:problem-type:pet-not-found"));
}
```

Update `shouldReturn404ForPatchUnknownPet`:

```java
@Test void shouldReturnProblemDetailsForPatchUnknownPet() {
    given()
            .contentType(APPLICATION_JSON)
            .body("{\"name\":\"X\"}")
            .when().patch("/pets/999")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", is("urn:problem-type:pet-not-found"));
}
```

Add new test for delete not-found:

```java
@Test void shouldReturnProblemDetailsForDeleteUnknownPet() {
    given()
            .when().delete("/pets/999")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", is("urn:problem-type:pet-not-found"));
}
```

- [ ] **Step 6: Run tests to verify they fail**

Run: `mvn test -pl demo -Dtest='PetResourceTest'`
Expected: FAIL — `update()`, `patch()`, `delete()` still throw `NotFoundException` (404)

- [ ] **Step 7: Replace remaining NotFoundException usages in PetResource**

Replace all remaining `NotFoundException` in `PetResource`:
- `update()`: `throw new PetNotFoundException(id);`
- `patch()`: `throw new PetNotFoundException(id);`
- `delete()`: `throw new PetNotFoundException(id);`

Remove the `NotFoundException` import from `PetResource`.

- [ ] **Step 8: Run all PetResource tests**

Run: `mvn test -pl demo -Dtest='PetResourceTest'`
Expected: all pass

- [ ] **Step 9: Commit**

```bash
git add demo/src/main/java/com/github/t1/openapi/ui/demo/BusinessException.java \
        demo/src/main/java/com/github/t1/openapi/ui/demo/BusinessExceptionMapper.java \
        demo/src/main/java/com/github/t1/openapi/ui/demo/PetNotFoundException.java \
        demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java \
        demo/src/test/java/com/github/t1/openapi/ui/demo/PetResourceTest.java
git commit -m "add problem details for pet not-found errors"
```

### Task 3: OwnerNotFoundException and VisitNotFoundException

**Files:**
- Create: `demo/src/main/java/com/github/t1/openapi/ui/demo/OwnerNotFoundException.java`
- Create: `demo/src/main/java/com/github/t1/openapi/ui/demo/VisitNotFoundException.java`
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/OwnerResource.java`
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/VisitResource.java`
- Modify: `demo/src/test/java/com/github/t1/openapi/ui/demo/OwnerResourceTest.java`
- Modify: `demo/src/test/java/com/github/t1/openapi/ui/demo/VisitResourceTest.java`

#### TDD: Owner not-found returns problem details

- [ ] **Step 1: Write the failing test**

Replace `shouldReturn404ForUnknownOwner` in `OwnerResourceTest`:

```java
@Test void shouldReturnProblemDetailsForUnknownOwner() {
    given()
            .when().get("/owners/999")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", is("urn:problem-type:owner-not-found"))
            .body("detail", is("Owner with ID 999 not found"));
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -pl demo -Dtest='OwnerResourceTest#shouldReturnProblemDetailsForUnknownOwner'`
Expected: FAIL

- [ ] **Step 3: Implement OwnerNotFoundException and wire it**

Create `OwnerNotFoundException.java`:

```java
package com.github.t1.openapi.ui.demo;

public class OwnerNotFoundException extends BusinessException {
    OwnerNotFoundException(long id) { super("Owner with ID " + id + " not found"); }
}
```

In `OwnerResource.get()`, replace:
```java
.orElseThrow(NotFoundException::new);
```
with:
```java
.orElseThrow(() -> new OwnerNotFoundException(id));
```

Remove the `NotFoundException` import from `OwnerResource`.

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -pl demo -Dtest='OwnerResourceTest'`
Expected: all pass

- [ ] **Step 5: Commit**

```bash
git add demo/src/main/java/com/github/t1/openapi/ui/demo/OwnerNotFoundException.java \
        demo/src/main/java/com/github/t1/openapi/ui/demo/OwnerResource.java \
        demo/src/test/java/com/github/t1/openapi/ui/demo/OwnerResourceTest.java
git commit -m "add problem details for owner not-found errors"
```

#### TDD: Visit not-found returns problem details

- [ ] **Step 6: Write the failing test**

Replace `shouldReturn404ForUnknownVisit` in `VisitResourceTest`:

```java
@Test void shouldReturnProblemDetailsForUnknownVisit() {
    given()
            .when().get("/pets/1/visits/999")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", is("urn:problem-type:visit-not-found"))
            .body("detail", is("Visit with ID 999 not found"));
}
```

- [ ] **Step 7: Run to verify it fails**

Run: `mvn test -pl demo -Dtest='VisitResourceTest#shouldReturnProblemDetailsForUnknownVisit'`
Expected: FAIL

- [ ] **Step 8: Implement VisitNotFoundException and wire it**

Create `VisitNotFoundException.java`:

```java
package com.github.t1.openapi.ui.demo;

public class VisitNotFoundException extends BusinessException {
    VisitNotFoundException(long id) { super("Visit with ID " + id + " not found"); }
}
```

In `VisitResource.get()`, replace:
```java
.orElseThrow(NotFoundException::new);
```
with:
```java
.orElseThrow(() -> new VisitNotFoundException(visitId));
```

Remove the `NotFoundException` import from `VisitResource`.

- [ ] **Step 9: Run to verify it passes**

Run: `mvn test -pl demo -Dtest='VisitResourceTest'`
Expected: all pass

- [ ] **Step 10: Commit**

```bash
git add demo/src/main/java/com/github/t1/openapi/ui/demo/VisitNotFoundException.java \
        demo/src/main/java/com/github/t1/openapi/ui/demo/VisitResource.java \
        demo/src/test/java/com/github/t1/openapi/ui/demo/VisitResourceTest.java
git commit -m "add problem details for visit not-found errors"
```

## Chunk 2: Bean Validation and Constraint Violation Mapper

### Task 4: ConstraintViolationExceptionMapper and Pet validation

**Files:**
- Create: `demo/src/main/java/com/github/t1/openapi/ui/demo/ConstraintViolationExceptionMapper.java`
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/Pet.java`
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java`
- Modify: `demo/src/test/java/com/github/t1/openapi/ui/demo/PetResourceTest.java`

#### TDD: Creating a pet with blank name returns constraint violation

- [ ] **Step 1: Write the failing test**

Add to `PetResourceTest`:

```java
@Test void shouldReturnConstraintViolationForBlankPetName() {
    given()
            .contentType(APPLICATION_JSON)
            .body("{\"name\":\"\",\"status\":\"available\",\"ownerId\":1}")
            .when().post("/pets")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", is("urn:problem-type:constraint-violation"))
            .body("title", is("Bad Request"))
            .body("status", is(400))
            .body("violations.size()", is(1))
            .body("violations[0].field", is("name"))
            .body("violations[0].message", is("must not be blank"));
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -pl demo -Dtest='PetResourceTest#shouldReturnConstraintViolationForBlankPetName'`
Expected: FAIL — no validation is active yet

- [ ] **Step 3: Implement ConstraintViolationExceptionMapper, add annotations to Pet, add @Valid to PetResource.create()**

Note: In Quarkus REST, a custom `@Provider` `ExceptionMapper<ConstraintViolationException>` should take priority over the built-in one. If the test at Step 5 fails with an unexpected response format, add `@jakarta.annotation.Priority(1)` to the mapper class.

Create `ConstraintViolationExceptionMapper.java`:

```java
package com.github.t1.openapi.ui.demo;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    @Override public Response toResponse(ConstraintViolationException exception) {
        var violations = Json.createArrayBuilder();
        exception.getConstraintViolations().stream()
                .sorted(java.util.Comparator.comparing(v -> fieldName(v.getPropertyPath())))
                .forEach(v -> violations.add(Json.createObjectBuilder()
                        .add("field", fieldName(v.getPropertyPath()))
                        .add("message", v.getMessage())));
        return Response.status(400)
                .type("application/problem+json")
                .entity(Json.createObjectBuilder()
                        .add("type", "urn:problem-type:constraint-violation")
                        .add("title", "Bad Request")
                        .add("status", 400)
                        .add("violations", violations)
                        .build()
                        .toString())
                .build();
    }

    private static String fieldName(jakarta.validation.Path path) {
        var name = "";
        for (var node : path) name = node.getName();
        return name;
    }
}
```

Update `Pet.java` — add validation annotations:

```java
package com.github.t1.openapi.ui.demo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record Pet(
        @Schema(examples = "1") long id,
        @NotBlank @Schema(examples = "Max") String name,
        @NotNull @Schema(examples = "available") PetStatus status,
        @Schema(examples = "42") long ownerId) {}
```

Add `@Valid` to `PetResource.create()`:

```java
@POST @Operation(summary = "Add a new pet")
public Response create(@RequestBody @Valid Pet pet) {
```

Add `@Valid` to `PetResource.update()`:

```java
@PUT @Path("/{id}") @Operation(summary = "Update a pet", deprecated = true)
@Deprecated
public Pet update(@PathParam("id") long id, @RequestBody @Valid Pet pet) {
```

Add `import jakarta.validation.Valid;` to `PetResource`.

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -pl demo -Dtest='PetResourceTest#shouldReturnConstraintViolationForBlankPetName'`
Expected: PASS

- [ ] **Step 5: Run all PetResource tests**

Run: `mvn test -pl demo -Dtest='PetResourceTest'`
Expected: all pass

- [ ] **Step 6: Commit**

```bash
git add demo/src/main/java/com/github/t1/openapi/ui/demo/ConstraintViolationExceptionMapper.java \
        demo/src/main/java/com/github/t1/openapi/ui/demo/Pet.java \
        demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java \
        demo/src/test/java/com/github/t1/openapi/ui/demo/PetResourceTest.java
git commit -m "add constraint violation mapper and pet validation"
```

### Task 5: Owner and Visit validation

**Files:**
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/Owner.java`
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/Visit.java`
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/VisitResource.java`
- Modify: `demo/src/test/java/com/github/t1/openapi/ui/demo/VisitResourceTest.java`

Note: `OwnerResource` has no POST/PUT endpoints, so `@Valid` is not needed there. But adding annotations to `Owner` is still useful for documentation and future-proofing.

#### TDD: Creating a visit with blank reason returns constraint violation

- [ ] **Step 1: Write the failing test**

Add to `VisitResourceTest`:

```java
@Test void shouldReturnConstraintViolationForBlankVisitReason() {
    given()
            .contentType(APPLICATION_JSON)
            .body("{\"date\":\"2024-01-01\",\"reason\":\"\"}")
            .when().post("/pets/1/visits")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", is("urn:problem-type:constraint-violation"))
            .body("violations.size()", is(1))
            .body("violations[0].field", is("reason"));
}
```

Add required imports to `VisitResourceTest`:

```java
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.is;
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -pl demo -Dtest='VisitResourceTest#shouldReturnConstraintViolationForBlankVisitReason'`
Expected: FAIL

- [ ] **Step 3: Add validation annotations to Visit and Owner, add @Valid to VisitResource.create()**

Update `Visit.java`:

```java
package com.github.t1.openapi.ui.demo;

import jakarta.validation.constraints.NotBlank;

public record Visit(long id, long petId, @NotBlank String date, @NotBlank String reason) {}
```

Update `Owner.java`:

```java
package com.github.t1.openapi.ui.demo;

import jakarta.validation.constraints.NotBlank;

public record Owner(long id, @NotBlank String name, @NotBlank String email) {}
```

Add `@Valid` to `VisitResource.create()`:

```java
@POST @Operation(summary = "Record a visit")
public Visit create(@PathParam("petId") long petId, @Valid Visit visit) {
```

Add `import jakarta.validation.Valid;` to `VisitResource`.

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -pl demo -Dtest='VisitResourceTest#shouldReturnConstraintViolationForBlankVisitReason'`
Expected: PASS

- [ ] **Step 5: Run all demo tests**

Run: `mvn test -pl demo`
Expected: all pass

- [ ] **Step 6: Commit**

```bash
git add demo/src/main/java/com/github/t1/openapi/ui/demo/Owner.java \
        demo/src/main/java/com/github/t1/openapi/ui/demo/Visit.java \
        demo/src/main/java/com/github/t1/openapi/ui/demo/VisitResource.java \
        demo/src/test/java/com/github/t1/openapi/ui/demo/VisitResourceTest.java
git commit -m "add validation annotations to Owner and Visit"
```

## Chunk 3: Business Validation (Invalid Owner/Pet)

### Task 6: InvalidOwnerIdException for pet create/update

**Files:**
- Create: `demo/src/main/java/com/github/t1/openapi/ui/demo/InvalidOwnerIdException.java`
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java`
- Modify: `demo/src/test/java/com/github/t1/openapi/ui/demo/PetResourceTest.java`

#### TDD: Creating a pet with nonexistent owner returns problem details

- [ ] **Step 1: Write the failing test**

Add to `PetResourceTest`:

```java
@Test void shouldReturnProblemDetailsForInvalidOwnerId() {
    given()
            .contentType(APPLICATION_JSON)
            .body("{\"name\":\"Rex\",\"status\":\"available\",\"ownerId\":999}")
            .when().post("/pets")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", is("urn:problem-type:invalid-owner-id"))
            .body("detail", is("Owner with ID 999 does not exist"));
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -pl demo -Dtest='PetResourceTest#shouldReturnProblemDetailsForInvalidOwnerId'`
Expected: FAIL — pet is created with status 201

- [ ] **Step 3: Implement InvalidOwnerIdException and add owner validation to PetResource**

Create `InvalidOwnerIdException.java`:

```java
package com.github.t1.openapi.ui.demo;

public class InvalidOwnerIdException extends BusinessException {
    InvalidOwnerIdException(long id) { super("Owner with ID " + id + " does not exist"); }
}
```

Add a helper method and owner checks to `PetResource`:

```java
private static void validateOwner(long ownerId) {
    var ownerExists = OwnerResource.OWNERS.stream().anyMatch(o -> o.id() == ownerId);
    if (!ownerExists) throw new InvalidOwnerIdException(ownerId);
}
```

Call `validateOwner(pet.ownerId())` in `create()`, `update()`, and in `patch()` when `ownerId` is present:

In `create()`, add before creating the pet:
```java
validateOwner(pet.ownerId());
```

In `update()`, add before the loop:
```java
validateOwner(pet.ownerId());
```

In `patch()`, add inside the `if (existing.id() == id)` block, before creating `updated`:
```java
if (patch.containsKey("ownerId")) validateOwner(patch.getJsonNumber("ownerId").longValue());
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -pl demo -Dtest='PetResourceTest#shouldReturnProblemDetailsForInvalidOwnerId'`
Expected: PASS

- [ ] **Step 5: Run all demo tests**

Run: `mvn test -pl demo`
Expected: all pass

- [ ] **Step 6: Commit**

```bash
git add demo/src/main/java/com/github/t1/openapi/ui/demo/InvalidOwnerIdException.java \
        demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java \
        demo/src/test/java/com/github/t1/openapi/ui/demo/PetResourceTest.java
git commit -m "add owner ID validation to pet create/update"
```

### Task 7: Pet existence check on visit create

**Files:**
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/VisitResource.java`
- Modify: `demo/src/test/java/com/github/t1/openapi/ui/demo/VisitResourceTest.java`

#### TDD: Creating a visit for nonexistent pet returns problem details

- [ ] **Step 1: Write the failing test**

Add to `VisitResourceTest`:

```java
@Test void shouldReturnProblemDetailsForVisitOnNonexistentPet() {
    given()
            .contentType(APPLICATION_JSON)
            .body("{\"date\":\"2024-01-01\",\"reason\":\"Checkup\"}")
            .when().post("/pets/999/visits")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", is("urn:problem-type:pet-not-found"))
            .body("detail", is("Pet with ID 999 not found"));
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -pl demo -Dtest='VisitResourceTest#shouldReturnProblemDetailsForVisitOnNonexistentPet'`
Expected: FAIL — visit is created with 200

- [ ] **Step 3: Add pet existence check to VisitResource.create()**

In `VisitResource.create()`, add before creating the visit:

```java
var petExists = PetResource.PETS.stream().anyMatch(p -> p.id() == petId);
if (!petExists) throw new PetNotFoundException(petId);
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -pl demo -Dtest='VisitResourceTest#shouldReturnProblemDetailsForVisitOnNonexistentPet'`
Expected: PASS

- [ ] **Step 5: Run all demo tests**

Run: `mvn test -pl demo`
Expected: all pass

- [ ] **Step 6: Commit**

```bash
git add demo/src/main/java/com/github/t1/openapi/ui/demo/VisitResource.java \
        demo/src/test/java/com/github/t1/openapi/ui/demo/VisitResourceTest.java
git commit -m "add pet existence check on visit create"
```

### Task 8: Final verification

- [ ] **Step 1: Run all demo tests**

Run: `mvn test -pl demo`
Expected: all pass

- [ ] **Step 2: Run all project tests**

Run: `mvn test`
Expected: all pass

- [ ] **Step 3: Squash commits into one**

Squash all commits from this plan into a single commit:

```bash
git reset --soft HEAD~7
git commit -m "add problem details error handling to demo app"
```
