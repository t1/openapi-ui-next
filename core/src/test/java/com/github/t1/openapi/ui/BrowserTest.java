package com.github.t1.openapi.ui;

import com.microsoft.playwright.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.*;

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
            if (Files.exists(file)) {
                var bytes = Files.readAllBytes(file);
                var contentType = uriPath.endsWith(".js") ? "application/javascript" : "text/html";
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
    void clickingTreeNodeLoadsFragment() throws Exception {
        generate("one-get.yaml");
        var baseUrl = serve();

        page.navigate(baseUrl);
        page.locator("[hx-get='pets/GET.html']").click();
        page.waitForSelector("#detail :text('List pets')");

        assertTrue(page.locator("#detail").textContent().contains("List pets"));
    }
}
