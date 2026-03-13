package com.github.t1.openapi.ui;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.sun.net.httpserver.HttpServer;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

@SuppressWarnings("SameParameterValue")
class AppFixture implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback {
    private final Browser browser;
    private final String specFilename;
    private final boolean overrideBaseUrl;
    private Path outputDir;
    private HttpServer server;
    private BrowserContext context;
    private Page page;

    AppFixture(Browser browser, String specFilename) {this(browser, specFilename, false);}

    private AppFixture(Browser browser, String specFilename, boolean overrideBaseUrl) {
        this.browser = browser;
        this.specFilename = specFilename;
        this.overrideBaseUrl = overrideBaseUrl;
    }

    AppFixture withBaseUrlOverride() {return new AppFixture(browser, specFilename, true);}

    @Override public void beforeAll(ExtensionContext extensionContext) {
        extensionContext.getStore(GLOBAL).computeIfAbsent(this, key -> {
            try {
                outputDir = Files.createTempDirectory("openapi-ui-test");
                var specPath = Optional.ofNullable(getClass().getResource("/" + specFilename))
                        .map(URL::toString).map(URI::create).map(Path::of)
                        .orElseThrow(() -> new RuntimeException("spec not found: " + specFilename));
                new OpenApiUiGenerator(specPath, outputDir).generate();

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

                if (overrideBaseUrl) {
                    var indexPath = outputDir.resolve("index.html");
                    var html = Files.readString(indexPath);
                    html = html.replace("https://api.example.com", baseUrl() + "/api");
                    Files.writeString(indexPath, html);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return (AutoCloseable) () -> {
                server.stop(0);
                deleteRecursively(outputDir);
            };
        });
    }

    @Override public void beforeEach(@NonNull ExtensionContext extensionContext) {
        context = browser.newContext(new Browser.NewContextOptions()
                .setPermissions(List.of("clipboard-read", "clipboard-write")));
        page = context.newPage();
        page.navigate(baseUrl());
    }

    @Override public void afterEach(@NonNull ExtensionContext extensionContext) {
        context.close();
    }

    String baseUrl() {return "http://localhost:" + server.getAddress().getPort();}

    void mockEndpoint(String path, String contentType, String body) {
        try {server.removeContext("/api" + path);} catch (IllegalArgumentException ignored) {}
        server.createContext("/api" + path, exchange -> {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            var bytes = body.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    // Page interactions

    void focusTree() {page.locator("[role='tree']").focus();}

    void pressKey(String key) {page.keyboard().press(key);}

    String selectedTreeItemText() {
        return page.locator("[role='treeitem'][aria-selected='true']").textContent();
    }

    boolean isTextVisible(String text) {return page.locator(":text('" + text + "')").isVisible();}

    void clickTreeNode(String hxGetPath) {page.locator("[hx-get='" + hxGetPath + "']").click();}

    void waitForDetailContent(String text) {page.waitForSelector("#detail :text('" + text + "')");}

    String detailText() {return page.locator("#detail").textContent();}

    double[] treeBoundingBox() {
        var box = page.locator("[role='tree']").boundingBox();
        return new double[]{box.x, box.y, box.width, box.height};
    }

    double[] detailBoundingBox() {
        var box = page.locator("#detail").boundingBox();
        return new double[]{box.x, box.y, box.width, box.height};
    }

    boolean isModeButtonVisible(String mode) {return page.locator("button:text('" + mode + "')").isVisible();}

    void clickModeButton(String mode) {page.locator("button:text('" + mode + "')").click();}

    String currentMode() {
        return (String) page.evaluate("() => document.querySelector('[data-mode]').getAttribute('data-mode')");
    }

    void fillInput(String name, String value) {page.locator("#detail input[name='" + name + "']").fill(value);}

    void waitForInput(String name) {page.waitForSelector("#detail input[name='" + name + "']");}

    void clickSend() {page.locator("#detail button:text('Send')").click();}

    void waitForResponse() {page.waitForSelector("#detail pre.response");}

    String responseText() {return page.locator("#detail pre.response").textContent();}

    boolean isFocusInDetail() {
        return (Boolean) page.evaluate("() => document.activeElement.closest('#detail') !== null");
    }

    boolean isTreeFocused() {
        return (Boolean) page.evaluate(
                "() => document.activeElement.closest('[role=\"tree\"]') !== null"
                + " || document.activeElement === document.querySelector('[role=\"tree\"]')");
    }

    String readClipboard() {return (String) page.evaluate("() => navigator.clipboard.readText()");}

    void setViewportSize(int width, int height) {page.setViewportSize(width, height);}

    void navigate(String url) {page.navigate(url);}

    private static void deleteRecursively(Path path) {
        try (var walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {Files.delete(p);} catch (Exception e) {throw new RuntimeException(e);}
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
