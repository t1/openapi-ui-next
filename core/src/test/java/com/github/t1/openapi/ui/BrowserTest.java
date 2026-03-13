package com.github.t1.openapi.ui;

import com.microsoft.playwright.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BrowserTest {
    static Playwright playwright;
    static Browser browser;
    BrowserContext context;
    Page page;
    HttpServer server;
    @TempDir Path outputDir;

    @BeforeAll static void setupAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll static void teardownAll() {
        browser.close();
        playwright.close();
    }

    @BeforeEach void setup() {
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterEach void teardown() {
        context.close();
        if (server != null) server.stop(0);
    }

    /** Starts a static file server for the output directory. Returns the base URL. */
    String serve() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            var uriPath = exchange.getRequestURI().getPath();
            if (uriPath.equals("/")) uriPath = "/index.html";
            var file = outputDir.resolve(uriPath.substring(1));
            if (Files.exists(file) && Files.isRegularFile(file)) {
                var bytes = Files.readAllBytes(file);
                var contentType = uriPath.endsWith(".js") ? "application/javascript"
                        : uriPath.endsWith(".css") ? "text/css" : "text/html";
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } else {
                exchange.sendResponseHeaders(404, 0);
            }
            exchange.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    void generate(String fixture) throws Exception {
        var specPath = Path.of(getClass().getResource("/" + fixture).toURI());
        new OpenApiUiGenerator(specPath, outputDir).generate();
    }

    @Test
    void arrowKeysNavigateTree() throws Exception {
        generate("nested-paths.yaml");
        var baseUrl = serve();

        page.navigate(baseUrl);
        page.locator("[role='tree']").focus();

        var focused = page.locator("[role='treeitem'][aria-selected='true']");
        assertTrue(focused.textContent().contains("pets"));

        page.keyboard().press("ArrowDown");
        focused = page.locator("[role='treeitem'][aria-selected='true']");
        assertNotNull(focused.textContent());
    }

    @Test
    void enterKeyLoadsFragment() throws Exception {
        generate("one-get.yaml");
        var baseUrl = serve();

        page.navigate(baseUrl);
        page.locator("[role='tree']").focus();
        page.keyboard().press("Enter");
        page.waitForSelector("#detail :text('List pets')");

        assertTrue(page.locator("#detail").textContent().contains("List pets"));
    }

    @Test
    void arrowRightExpandsAndLeftCollapsesNode() throws Exception {
        generate("nested-paths.yaml");
        var baseUrl = serve();

        page.navigate(baseUrl);
        page.locator("[role='tree']").focus();

        assertFalse(page.locator(":text('{petId}')").isVisible());

        page.keyboard().press("ArrowRight");
        assertTrue(page.locator(":text('{petId}')").isVisible());

        page.keyboard().press("ArrowLeft");
        assertFalse(page.locator(":text('{petId}')").isVisible());
    }

    @Test
    void tabAndEscapeMoveFocus() throws Exception {
        generate("one-get.yaml");
        var baseUrl = serve();

        page.navigate(baseUrl);
        page.locator("[role='tree']").focus();
        page.keyboard().press("Enter");
        page.waitForSelector("#detail :text('List pets')");

        page.keyboard().press("Tab");
        var focusInDetail = (Boolean) page.evaluate(
                "() => document.activeElement.closest('#detail') !== null");
        assertTrue(focusInDetail);

        page.keyboard().press("Escape");
        var treeHasFocus = (Boolean) page.evaluate(
                "() => document.activeElement.closest('[role=\"tree\"]') !== null"
                + " || document.activeElement === document.querySelector('[role=\"tree\"]')");
        assertTrue(treeHasFocus);
    }

    @Test
    void desktopLayoutIsSideBySide() throws Exception {
        generate("one-get.yaml");
        var baseUrl = serve();

        page.setViewportSize(1280, 720);
        page.navigate(baseUrl);

        var treeBox = page.locator("[role='tree']").boundingBox();
        var detailBox = page.locator("#detail").boundingBox();
        assertTrue(treeBox.x < detailBox.x,
                "Tree should be left of detail pane on desktop");
    }

    @Test
    void mobileLayoutIsStacked() throws Exception {
        generate("one-get.yaml");
        var baseUrl = serve();

        page.setViewportSize(375, 667);
        page.navigate(baseUrl);

        var treeBox = page.locator("[role='tree']").boundingBox();
        var detailBox = page.locator("#detail").boundingBox();
        assertTrue(treeBox.y < detailBox.y,
                "Tree should be above detail pane on mobile");
    }

    @Test
    void clickingTreeNodeLoadsFragment() throws Exception {
        generate("one-get.yaml");
        var baseUrl = serve();

        page.navigate(baseUrl);
        page.locator("[hx-get='pets/GET.html']").click();
        page.waitForSelector("#detail :text('List pets')");

        assertTrue(page.locator("#detail").textContent().contains("List pets"));
    }

    @Test
    void modeToggleHasThreeOptionsAndSwitches() throws Exception {
        generate("one-get.yaml");
        var baseUrl = serve();

        page.navigate(baseUrl);

        assertTrue(page.locator("button:text('Try')").isVisible());
        assertTrue(page.locator("button:text('httpie')").isVisible());
        assertTrue(page.locator("button:text('curl')").isVisible());

        page.locator("button:text('curl')").click();
        var mode = (String) page.evaluate(
                "() => document.querySelector('[data-mode]').getAttribute('data-mode')");
        assertEquals("curl", mode);
    }

    @Test
    void curlModeCopiesCommand() throws Exception {
        generate("params.yaml");

        context.close();
        context = browser.newContext(new Browser.NewContextOptions()
                .setPermissions(List.of("clipboard-read", "clipboard-write")));
        page = context.newPage();

        var baseUrl = serve();
        page.navigate(baseUrl);

        page.locator("button:text('curl')").click();
        page.locator("[role='tree']").focus();
        page.keyboard().press("ArrowRight"); // expand pets
        page.locator("[hx-get='pets/{petId}/GET.html']").click();
        page.waitForSelector("#detail input[name='petId']");
        page.locator("#detail input[name='petId']").fill("42");
        page.locator("#detail button:text('Send')").click();

        var clipboard = (String) page.evaluate("() => navigator.clipboard.readText()");
        assertTrue(clipboard.contains("curl"), "Expected curl command, got: " + clipboard);
        assertTrue(clipboard.contains("https://api.example.com/pets/42"),
                "Expected URL with petId=42, got: " + clipboard);
    }

    @Test
    void httpieModeCopiesCommand() throws Exception {
        generate("params.yaml");

        context.close();
        context = browser.newContext(new Browser.NewContextOptions()
                .setPermissions(List.of("clipboard-read", "clipboard-write")));
        page = context.newPage();

        var baseUrl = serve();
        page.navigate(baseUrl);

        page.locator("button:text('httpie')").click();
        page.locator("[role='tree']").focus();
        page.keyboard().press("ArrowRight"); // expand pets
        page.locator("[hx-get='pets/{petId}/GET.html']").click();
        page.waitForSelector("#detail input[name='petId']");
        page.locator("#detail input[name='petId']").fill("42");
        page.locator("#detail button:text('Send')").click();

        var clipboard = (String) page.evaluate("() => navigator.clipboard.readText()");
        assertTrue(clipboard.contains("http GET"), "Expected httpie command, got: " + clipboard);
        assertTrue(clipboard.contains("https://api.example.com/pets/42"),
                "Expected URL with petId=42, got: " + clipboard);
    }

    void mockEndpoint(String path, String contentType, String body) {
        server.createContext("/api" + path, exchange -> {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            var bytes = body.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    void overrideBaseUrl(String baseUrl) throws Exception {
        var indexPath = outputDir.resolve("index.html");
        var html = Files.readString(indexPath);
        html = html.replace("https://api.example.com", baseUrl + "/api");
        Files.writeString(indexPath, html);
    }

    @Test
    void tryModeSendsRequestAndShowsPrettifiedJson() throws Exception {
        generate("one-get.yaml");
        var baseUrl = serve();
        mockEndpoint("/pets", "application/json", "{\"id\":\"1\",\"name\":\"Fido\"}");
        overrideBaseUrl(baseUrl);

        page.navigate(baseUrl);
        page.locator("[role='tree']").focus();
        page.keyboard().press("Enter");
        page.waitForSelector("#detail :text('List pets')");
        page.locator("#detail button:text('Send')").click();

        page.waitForSelector("#detail pre.response");
        var responseText = page.locator("#detail pre.response").textContent();
        assertTrue(responseText.contains("\"name\""), "Expected JSON with name field");
        assertTrue(responseText.contains("Fido"), "Expected JSON with value Fido");
        assertTrue(responseText.contains("\n"), "Expected prettified JSON with newlines");
    }

    @Test
    void tryModeShowsHtmlResponseAsIs() throws Exception {
        generate("one-get.yaml");
        var baseUrl = serve();
        mockEndpoint("/pets", "text/html", "<h1>Hello</h1><p>World</p>");
        overrideBaseUrl(baseUrl);

        page.navigate(baseUrl);
        page.locator("[role='tree']").focus();
        page.keyboard().press("Enter");
        page.waitForSelector("#detail :text('List pets')");
        page.locator("#detail button:text('Send')").click();

        page.waitForSelector("#detail pre.response");
        var responseText = page.locator("#detail pre.response").textContent();
        assertTrue(responseText.contains("<h1>Hello</h1>"), "HTML should be shown as raw text");
    }

    @Test
    void tryModeShowsXmlResponseAsIs() throws Exception {
        generate("one-get.yaml");
        var baseUrl = serve();
        mockEndpoint("/pets", "application/xml", "<pets><pet><name>Fido</name></pet></pets>");
        overrideBaseUrl(baseUrl);

        page.navigate(baseUrl);
        page.locator("[role='tree']").focus();
        page.keyboard().press("Enter");
        page.waitForSelector("#detail :text('List pets')");
        page.locator("#detail button:text('Send')").click();

        page.waitForSelector("#detail pre.response");
        var responseText = page.locator("#detail pre.response").textContent();
        assertTrue(responseText.contains("<pets>"), "XML should be shown as raw text");
    }

    @Test
    void tryModeShowsYamlResponseAsIs() throws Exception {
        generate("one-get.yaml");
        var baseUrl = serve();
        mockEndpoint("/pets", "application/yaml", "pets:\n  - name: Fido\n    id: 1");
        overrideBaseUrl(baseUrl);

        page.navigate(baseUrl);
        page.locator("[role='tree']").focus();
        page.keyboard().press("Enter");
        page.waitForSelector("#detail :text('List pets')");
        page.locator("#detail button:text('Send')").click();

        page.waitForSelector("#detail pre.response");
        var responseText = page.locator("#detail pre.response").textContent();
        assertTrue(responseText.contains("pets:"), "YAML should be shown as raw text");
        assertTrue(responseText.contains("Fido"), "YAML should contain data");
    }
}
