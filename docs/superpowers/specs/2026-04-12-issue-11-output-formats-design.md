# Output Format Code Snippets — Design Spec (#11)

## Summary

Add copyable client code snippets to the "Try it out" panel. Today only `curl` and `HTTPie`
are supported as copy formats. This feature adds 8 more: JS fetch, JAX-RS Client, MP Rest
Client, Java HttpClient, Spring WebClient, Spring RestTemplate, Python requests, and Go
net/http. The existing `curl`/`HTTPie` generators get refactored into the same pluggable
architecture.

## Format List

| Key              | Label             | Description                                      |
|------------------|-------------------|--------------------------------------------------|
| (special)        | Try               | Sends request via browser `fetch()` — not a generator |
| `HTTPie`         | HTTPie            | HTTPie CLI command                               |
| `curl`           | curl              | curl CLI command                                 |
| `JS fetch`       | JS fetch          | Browser/Node `fetch()` API                       |
| `JAX-RS`         | JAX-RS            | JAX-RS Client API (`ClientBuilder`)              |
| `MP Rest Client` | MP Rest Client    | MicroProfile type-safe client with record DTOs   |
| `Java HttpClient`| Java HttpClient   | `java.net.http.HttpClient` (Java 11+)            |
| `Spring WebClient`| Spring WebClient | Spring reactive WebClient                        |
| `Spring RestTemplate`| Spring RestTemplate | Spring classic RestTemplate                  |
| `Python`         | Python            | Python `requests` library                        |
| `Go`             | Go                | Go `net/http` standard library                   |

## Architecture

### Generator Registry

A flat `Map<String, Function>` in JavaScript. Each key is the display label, each value is a
generator function with the signature:

```js
function generate({ method, url, headers, body, contentType, schema }) → string
```

All copy-format modes call `generators[label](params)` and copy the returned string to the
clipboard. The `Try` mode remains special (sends the actual request).

The `schema` parameter contains operation metadata extracted from the embedded JSON blob (see
below). Generators that don't need it ignore it.

### Schema Embedding

At build time, `OperationFragmentGenerator` embeds a JSON blob in each operation fragment:

```html
<script type="application/json" class="operation-schema">
{
    "operationId": "createPet",
    "requestBody": {
        "type": "object",
        "properties": { "name": { "type": "string" }, "species": { "type": "string" } }
    },
    "responses": {
        "200": {
            "type": "object",
            "properties": { "id": { "type": "integer" }, "name": { "type": "string" } }
        }
    }
}
</script>
```

The form submit handler parses this and passes it to generators. MP Rest Client uses it to
generate record types; other generators can use it for typed code generation in the future.

### Mode Selector UX

The current 3-segment toggle (`Try | HTTPie | curl`) is replaced with:

1. **Try** — permanent first segment, always visible
2. **Most recently used format** — second segment
3. **Second most recently used format** — third segment
4. **Overflow dropdown (`▾`)** — fourth position, opens a menu listing all formats

Initial state (no history): `Try | HTTPie | curl | ▾` — matches today's defaults.

**Keyboard shortcuts:**
- `Ctrl+1` → Try (always)
- `Ctrl+2` → Most recent format
- `Ctrl+3` → Second most recent format
- `Ctrl+4` → Open overflow dropdown

Recently used formats are persisted in `localStorage`.

### Snippet Behavior

All copy-format modes behave like today's `curl`/`HTTPie`: clicking the button copies the
generated snippet to the clipboard and shows a brief "Copied!" toast. No preview pane.

## Generator Output Examples

### curl
```
curl -X POST -H 'Authorization: Bearer token' -H 'Content-Type: application/json' \
  -d '{"name":"Fido"}' http://localhost:8080/pets
```

### HTTPie
```
echo '{"name":"Fido"}' | https POST localhost:8080/pets Authorization:'Bearer token'
```

### JS fetch
```js
const response = await fetch('http://localhost:8080/pets', {
    method: 'POST',
    headers: { 'Authorization': 'Bearer token', 'Content-Type': 'application/json' },
    body: JSON.stringify({"name":"Fido"})
});
const data = await response.json();
```

### JAX-RS Client
```java
Response response = ClientBuilder.newClient()
    .target("http://localhost:8080")
    .path("/pets")
    .request(MediaType.APPLICATION_JSON)
    .header("Authorization", "Bearer token")
    .post(Entity.json("{\"name\":\"Fido\"}"));
```

### MP Rest Client
```java
@RegisterRestClient(baseUri = "http://localhost:8080")
public interface PetsClient {
    @POST @Path("/pets")
    @Consumes(MediaType.APPLICATION_JSON)
    PetResponse createPet(PetRequest body);
}

record PetRequest(String name) {}
record PetResponse(long id, String name) {}

// Usage: PetResponse response = petsClient.createPet(new PetRequest("Fido"));
```

### Java HttpClient
```java
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("http://localhost:8080/pets"))
    .header("Authorization", "Bearer token")
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Fido\"}"))
    .build();
HttpResponse<String> response = HttpClient.newHttpClient()
    .send(request, HttpResponse.BodyHandlers.ofString());
```

### Spring WebClient
```java
String result = WebClient.create("http://localhost:8080")
    .post()
    .uri("/pets")
    .header("Authorization", "Bearer token")
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue("{\"name\":\"Fido\"}")
    .retrieve()
    .bodyToMono(String.class)
    .block();
```

### Spring RestTemplate
```java
HttpHeaders headers = new HttpHeaders();
headers.set("Authorization", "Bearer token");
headers.setContentType(MediaType.APPLICATION_JSON);
HttpEntity<String> entity = new HttpEntity<>("{\"name\":\"Fido\"}", headers);
ResponseEntity<String> response = new RestTemplate()
    .exchange("http://localhost:8080/pets", HttpMethod.POST, entity, String.class);
```

### Python
```python
import requests
response = requests.post('http://localhost:8080/pets',
    headers={'Authorization': 'Bearer token'},
    json={"name": "Fido"})
```

### Go
```go
body := strings.NewReader(`{"name":"Fido"}`)
req, _ := http.NewRequest("POST", "http://localhost:8080/pets", body)
req.Header.Set("Authorization", "Bearer token")
req.Header.Set("Content-Type", "application/json")
resp, _ := http.DefaultClient.Do(req)
```

## Testing Strategy

**Unit tests (Java):**
- Toggle component tests for overflow dropdown rendering
- Schema JSON blob generation in operation fragments

**Browser tests (Playwright):**
- Each generator: select format → click Copy → read clipboard → verify snippet structure
- Overflow dropdown: open, select, verify format changes
- Keyboard shortcuts: Ctrl+1-4
- Recently-used persistence across page reloads

## Sub-Issues (Implementation Phases)

1. **Refactor existing modes into generator registry** — Extract curl/HTTPie from app.js into
   generator functions. Introduce the registry pattern. No new formats, no UX changes.

2. **Embed operation schema JSON** — Generate schema JSON blobs in operation fragments from
   Java. No consumer yet.

3. **Mode selector UX overhaul** — Replace 3-segment toggle with "Try + 2 recent + dropdown".
   No new generators.

4. **Add generators: JS fetch, Java HttpClient** — Simple generators needing only
   method/url/headers/body.

5. **Add generators: JAX-RS, Python, Go** — More generators, no schema info needed.

6. **Add generators: MP Rest Client, Spring WebClient, Spring RestTemplate** — Generators
   that use schema info for record/DTO generation.
