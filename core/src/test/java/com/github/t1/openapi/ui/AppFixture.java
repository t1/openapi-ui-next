package com.github.t1.openapi.ui;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.ScreenshotOptions;
import com.microsoft.playwright.options.ColorScheme;
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
        blockFragments = false;
        try { server.removeContext("/pets"); } catch (IllegalArgumentException ignored) {}
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

    void mockEndpoint(String path, String expectedMethod, String contentType, String body) {
        try {server.removeContext("/api" + path);} catch (IllegalArgumentException ignored) {}
        server.createContext("/api" + path, exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase(expectedMethod)) {
                exchange.sendResponseHeaders(405, 0);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            var bytes = body.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    void mockEndpoint(String path, String contentType, String body, int statusCode) {
        try {server.removeContext("/api" + path);} catch (IllegalArgumentException ignored) {}
        server.createContext("/api" + path, exchange -> {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            var bytes = body.getBytes();
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    void mockEndpointWithBodyEcho(String path, String expectedMethod) {
        try {server.removeContext("/api" + path);} catch (IllegalArgumentException ignored) {}
        server.createContext("/api" + path, exchange -> {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase(expectedMethod)) {
                exchange.sendResponseHeaders(405, 0);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            var requestBody = new String(exchange.getRequestBody().readAllBytes());
            var bytes = requestBody.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    void mockRootEndpoint(String path, String contentType, String body) {
        try {server.removeContext(path);} catch (IllegalArgumentException ignored) {}
        server.createContext(path, exchange -> {
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

    void expandFirstNode() {
        focusTree();
        page.keyboard().press("ArrowRight");
    }

    void expandAllNodes() {
        page.evaluate("""
                () => document.querySelectorAll('[role="treeitem"][aria-expanded="false"]').forEach(item => {
                    item.setAttribute('aria-expanded', 'true');
                    var group = item.querySelector('[role="group"]');
                    if (group) group.style.display = '';
                })""");
    }

    void pressKey(String key) {page.keyboard().press(key);}

    String selectedTreeItemText() {
        return page.locator("[role='treeitem'][aria-selected='true']").textContent();
    }

    boolean isTreeItemSelected(String hxGetPath) {
        return (Boolean) page.evaluate(
                "path => { var el = document.querySelector(\"[hx-get='\" + path + \"']\");"
                + " return el ? el.closest('[role=treeitem]').getAttribute('aria-selected') === 'true' : false; }",
                hxGetPath);
    }

    boolean isTextVisible(String text) {return page.locator(":text('" + text + "')").isVisible();}

    double treeToggleRotation() {
        var result = page.evaluate("""
                () => new Promise(resolve => {
                    var toggle = document.querySelector('[aria-selected="true"] .tree-toggle');
                    if (!toggle) { resolve(0); return; }
                    function read() {
                        var transform = getComputedStyle(toggle).transform;
                        if (!transform || transform === 'none') return 0;
                        var m = transform.match(/matrix\\(([^)]+)\\)/);
                        if (!m) return 0;
                        var vals = m[1].split(',').map(Number);
                        return Math.round(Math.atan2(vals[1], vals[0]) * 180 / Math.PI);
                    }
                    toggle.addEventListener('transitionend', () => resolve(read()), {once: true});
                    setTimeout(() => resolve(read()), 300);
                })""");
        return ((Number) result).doubleValue();
    }

    void clickTreeNode(String hxGetPath) {page.locator("[hx-get='" + hxGetPath + "']").click();}

    void clickTreeNodeWithMethod(String hxGetPath, String method) {
        page.locator("[hx-get='" + hxGetPath + "'][data-method='" + method + "']").click();
    }

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

    boolean isModeButtonVisible(String mode) {return page.locator("[data-mode-btn='" + mode.toLowerCase() + "']").isVisible();}

    String modeButtonTooltip(String mode) {return page.locator("[data-mode-btn='" + mode.toLowerCase() + "']").getAttribute("title");}

    void focusModeToggle() {page.locator("[data-mode].segmented-control").focus();}

    boolean isModeToggleFocused() {
        return (Boolean) page.evaluate("() => document.activeElement.matches('[data-mode].segmented-control')");
    }

    void clickModeButton(String mode) {page.locator("[data-mode-btn='" + mode.toLowerCase() + "']").click();}

    boolean hasSegmentedControl() {
        return page.locator("[data-mode].segmented-control").count() == 1;
    }

    boolean isSegmentActive(String mode) {
        return page.locator("[data-mode] [data-mode-btn='" + mode + "'].is-active").count() == 1;
    }

    String currentMode() {
        return (String) page.evaluate("() => document.querySelector('[data-mode]').getAttribute('data-mode')");
    }

    void fillInput(String name, String value) {page.locator("#detail input[name='" + name + "']").fill(value);}

    void focusInput(String name) {page.locator("#detail input[name='" + name + "']").focus();}

    void fillRequestBody(String body) {page.locator("#detail textarea[data-request-body]").fill(body);}

    void focusRequestBody() {page.locator("#detail textarea[data-request-body]").focus();}

    void setCursorAtStart() {
        page.evaluate("() => { var el = document.activeElement; el.setSelectionRange(0, 0); }");
    }

    void setCursorAtEnd() {
        page.evaluate("() => { var el = document.activeElement; el.setSelectionRange(el.value.length, el.value.length); }");
    }

    void waitForInput(String name) {page.waitForSelector("#detail input[name='" + name + "']");}

    void clickSend() {page.locator("#detail button[data-path]").click();}

    String sendButtonText() {return page.locator("#detail button[data-path]").textContent();}

    void waitForResponse() {page.waitForSelector("#detail .response-status");}

    String statusBadgeText() {return page.locator("#detail .response-status").textContent();}

    String noBodyMessageText() {return page.locator("#detail .response-no-body").textContent();}

    void clickMethodTab(int index) {page.locator(".tabs li:nth-child(" + index + ") a").click();}

    void focusTab(int index) {page.locator(".tabs li:nth-child(" + index + ") a").focus();}

    boolean isTabActive(int index) {
        var cls = page.locator(".tabs li:nth-child(" + index + ")").getAttribute("class");
        return cls != null && cls.contains("is-active");
    }

    boolean hasMethodBadge(String method) {
        return page.locator("[role='tree'] .tag:text('" + method + "')").isVisible();
    }

    boolean hasStylesheet(String name) {
        return (Boolean) page.evaluate(
                "name => !!document.querySelector('link[rel=stylesheet][href=\"' + name + '\"]')", name);
    }

    boolean isSendButtonEnabled() {
        return (Boolean) page.evaluate(
                "() => !document.querySelector('#detail button[data-path]').disabled");
    }

    String responseText() {return page.locator("#detail pre.response").textContent();}

    boolean isTreeFocused() {
        return (Boolean) page.evaluate(
                "() => document.activeElement.closest('[role=\"tree\"]') !== null"
                + " || document.activeElement === document.querySelector('[role=\"tree\"]')");
    }

    boolean selectedItemHasFocusRing() {
        return (Boolean) page.evaluate(
                "() => { var label = document.querySelector('[aria-selected=\"true\"] > .tree-label');"
                + " return label && getComputedStyle(label).boxShadow !== 'none'; }");
    }

    boolean selectedItemHasBumpClass() {
        var cls = page.locator("[aria-selected='true']").getAttribute("class");
        return cls != null && cls.contains("bump");
    }

    boolean isTreeInBox() {
        return page.locator(".box [role='tree']").count() == 1;
    }

    boolean isDetailInBox() {
        return page.locator(".box#detail").count() == 1;
    }

    boolean hasSplitLayout() {
        return page.locator(".split-layout").count() == 1;
    }

    double treeWidth() {
        return page.locator(".split-first").boundingBox().width;
    }

    void dragSplitHandle(int deltaX) {
        var handle = page.locator(".split-handle");
        var box = handle.boundingBox();
        var x = box.x + box.width / 2;
        var y = box.y + box.height / 2;
        page.mouse().move(x, y);
        page.mouse().down();
        page.mouse().move(x + deltaX, y);
        page.mouse().up();
    }

    boolean areMethodAddonsRightAligned() {
        var label = page.locator(".tree-label:has(.tags.has-addons)").first();
        var labelBox = label.boundingBox();
        var group = label.locator(".tags.has-addons").first();
        var groupBox = group.boundingBox();
        if (labelBox == null || groupBox == null) return false;
        var labelRight = labelBox.x + labelBox.width;
        var groupRight = groupBox.x + groupBox.width;
        return Math.abs(labelRight - groupRight) < 20;
    }

    boolean areMethodAddonsInSingleRow() {
        var groups = page.locator(".tree-label .tags.has-addons");
        for (int i = 0; i < groups.count(); i++) {
            var group = groups.nth(i);
            var groupBox = group.boundingBox();
            if (groupBox == null) return false;
            if (groupBox.height > 30) return false;
        }
        return true;
    }

    boolean isTabFocused() {
        return (Boolean) page.evaluate("() => document.activeElement.closest('.tabs') !== null");
    }

    boolean focusedTabHasFocusRing() {
        return (Boolean) page.evaluate(
                "() => { var el = document.activeElement;"
                + " return el && el.closest('.tabs') && getComputedStyle(el).boxShadow !== 'none'; }");
    }

    String activeElementTag() {return (String) page.evaluate("() => document.activeElement.tagName");}

    String activeElementSelector() {
        return (String) page.evaluate("""
                () => {
                    var el = document.activeElement;
                    if (!el) return '';
                    var s = el.tagName.toLowerCase();
                    if (el.classList.length > 0) s += '.' + Array.from(el.classList).join('.');
                    if (el.getAttribute('data-path')) s += '[data-path]';
                    return s;
                }""");
    }

    void focusDescriptionToggle() {page.locator(".desc-toggle").focus();}

    boolean isDescriptionClamped() {
        return (Boolean) page.evaluate("() => document.querySelector('.op-description-wrapper.is-clamped') !== null");
    }

    boolean isDescriptionExpanded() {
        return (Boolean) page.evaluate("() => document.querySelector('.op-description-wrapper.is-expanded') !== null");
    }

    void clickDescriptionToggle() {page.locator(".desc-toggle").click();}

String readClipboard() {return (String) page.evaluate("() => navigator.clipboard.readText()");}

    boolean hasViewToggle() {return page.locator("[data-view-toggle]").count() == 1;}

    boolean isViewActive(String view) {
        return page.locator("[data-view-btn='" + view + "'].is-active").count() == 1;
    }

    void clickViewButton(String view) {page.locator("[data-view-btn='" + view + "']").click();}

    boolean isViewToggleFocused() {
        return (Boolean) page.evaluate("() => document.activeElement.hasAttribute('data-view-toggle')");
    }

    String focusedElementInfo() {
        return (String) page.evaluate("() => { var el = document.activeElement; return el.tagName + '.' + el.className + '#' + el.id; }");
    }

    void focusViewToggle() {page.locator("[data-view-toggle]").focus();}

    void waitForViewToggleFocused() {
        page.waitForFunction("() => document.activeElement.hasAttribute('data-view-toggle')");
    }

    void waitForTreeContent(String text) {
        page.waitForSelector("#tree-container :text('" + text + "')");
    }

    void setViewportSize(int width, int height) {page.setViewportSize(width, height);}

    void navigate(String url) {page.navigate(url);}

    void screenshot(String name) {
        var dir = Path.of("target/screenshots");
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new RuntimeException("could not create screenshots directory", e);
        }
        page.screenshot(new ScreenshotOptions()
                .setPath(dir.resolve(name + ".png"))
                .setFullPage(true));
        page.emulateMedia(new Page.EmulateMediaOptions().setColorScheme(ColorScheme.DARK));
        page.screenshot(new ScreenshotOptions()
                .setPath(dir.resolve(name + "-dark.png"))
                .setFullPage(true));
        page.emulateMedia(new Page.EmulateMediaOptions().setColorScheme(ColorScheme.LIGHT));
    }

    private volatile boolean blockFragments = false;

    void blockHtmxRequests() {
        blockFragments = true;
        server.createContext("/pets", exchange -> {
            if (blockFragments) {
                exchange.sendResponseHeaders(503, 0);
            } else {
                var uriPath = exchange.getRequestURI().getPath();
                var file = outputDir.resolve(uriPath.substring(1));
                if (Files.exists(file) && Files.isRegularFile(file)) {
                    var bytes = Files.readAllBytes(file);
                    exchange.getResponseHeaders().set("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } else {
                    exchange.sendResponseHeaders(404, 0);
                }
            }
            exchange.close();
        });
    }

    void unblockHtmxRequests() {
        blockFragments = false;
    }

    boolean isErrorBannerVisible() {
        return page.locator("#error-banner").isVisible();
    }

    void waitForErrorBanner() {
        page.waitForSelector("#error-banner:not([style*='display: none'])");
    }

    void waitForErrorBannerGone() {
        page.waitForSelector("#error-banner", new Page.WaitForSelectorOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
    }

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
