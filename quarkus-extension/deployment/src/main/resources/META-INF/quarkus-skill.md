---
name: quarkus-openapi-ui
description: Using com.github.t1/openapi-ui-quarkus to generate static, keyboard-navigable HTML API documentation
categories: [web, documentation]
---

# OpenAPI UI with Quarkus

Generate static, keyboard-navigable HTML from OpenAPI specs using `openapi-ui-quarkus`.


## Dependencies

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-openapi</artifactId>
</dependency>
<dependency>
    <groupId>com.github.t1</groupId>
    <artifactId>openapi-ui-quarkus</artifactId>
</dependency>
```

**Note**: The artifact is `openapi-ui-quarkus`, NOT `openapi-ui`. The parent `openapi-ui` is a POM with no JAR - you need the Quarkus extension module.


## Configuration with Quinoa

If using Quinoa (Vue/React/Angular), **you must** ignore OpenAPI UI paths:

```properties
quarkus.quinoa.ignored-path-prefixes=/openapi-ui,/q
```

Without this, Quinoa forwards `/openapi-ui/` to Vite → 404s.


## Common Pitfalls

### 404 on /openapi-ui/

**Symptom**: Files listed in error page but return 404

**Cause**: Quinoa intercepts the path

**Fix**: Add `quarkus.quinoa.ignored-path-prefixes=/openapi-ui,/q`


## OpenAPI UI vs Swagger UI

Both UIs are included by default with `quarkus-smallrye-openapi`:

- **OpenAPI UI** (`/openapi-ui/`): Build-time static HTML, keyboard nav, try-it-out, works fully offline
- **Swagger UI** (`/q/swagger-ui/`): Runtime JS SPA, interactive testing

Both provide API testing. Use one or both.

**To disable Swagger UI** (since OpenAPI UI has try-it-out):

```properties
quarkus.swagger-ui.enabled=false
```

This completely removes Swagger UI from the Dev UI and disables `/q/swagger-ui/`. The OpenAPI spec at `/q/openapi` and OpenAPI UI continue to work.

**Note**: Use `enabled` not `enable` - the latter is deprecated.
