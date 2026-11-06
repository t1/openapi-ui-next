package com.github.t1.openapi.ui.generator;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator.FilterOptions;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.ScreenshotOptions;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static com.microsoft.playwright.options.ColorScheme.DARK;
import static com.microsoft.playwright.options.ColorScheme.LIGHT;
import static com.microsoft.playwright.options.WaitForSelectorState.HIDDEN;
import static org.junit.jupiter.api.MediaType.IMAGE_PNG;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

@SuppressWarnings("SameParameterValue")
class AppFixture implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback {
    /** The Playwright modifier key matching the app's shortcut modifier: {@code Control} on Mac, {@code Alt} elsewhere. */
    static final String MOD = System.getProperty("os.name", "").startsWith("Mac") ? "Control" : "Alt";
    /** The display label for the modifier key: {@code Ctrl} on Mac, {@code Alt} elsewhere. */
    static final String MOD_LABEL = System.getProperty("os.name", "").startsWith("Mac") ? "Ctrl" : "Alt";

    private final String specFilename;
    private boolean overrideBaseUrl;
    private Playwright playwright;
    private Browser browser;
    private TestServer testServer;
    private BrowserContext context;
    private Page page;
    private ExtensionContext currentContext;
    private String lastWaitContext;

    AppFixture(String specFilename) {
        this.specFilename = specFilename;
    }

    AppFixture withBaseUrlOverride() {
        overrideBaseUrl = true;
        return this;
    }

    @Override public void beforeAll(ExtensionContext extensionContext) {
        extensionContext.getStore(GLOBAL).computeIfAbsent(this, key -> {
            playwright = Playwright.create();
            var browserType = System.getProperty("playwright.browser", "webkit");
            browser = switch (browserType) {
                case "webkit" -> playwright.webkit().launch();
                case "firefox" -> playwright.firefox().launch();
                default -> playwright.chromium().launch();
            };
            try {
                testServer = new TestServer(specFilename);
                if (overrideBaseUrl) testServer.overrideBaseUrl("https://api.example.com");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return (AutoCloseable) () -> {
                testServer.stop();
                browser.close();
                playwright.close();
            };
        });
    }

    @Override public void beforeEach(@NonNull ExtensionContext extensionContext) {
        this.currentContext = extensionContext;
        this.lastWaitContext = null;

        var options = new Browser.NewContextOptions();
        if (browser.browserType().name().equals("webkit")) {
            // WebKit rejects "clipboard-write" as an unknown permission (writes require a
            // user gesture, which the send-button click provides), so grant read only.
            options.setPermissions(List.of("clipboard-read"));
        } else {
            options.setPermissions(List.of("clipboard-read", "clipboard-write"));
        }
        context = browser.newContext(options);
        page = context.newPage();
        page.setDefaultTimeout(60_000);
        navigateHome();
    }

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    @Override public void afterEach(@NonNull ExtensionContext extensionContext) {
        extensionContext.getExecutionException().ifPresent(ex -> {
            var testName = getFullTestName(extensionContext);
            var context = lastWaitContext != null ? "-" + lastWaitContext : "";
            var timestamp = TIMESTAMP_FORMAT.format(Instant.now());
            var filename = "failure-" + timestamp + "-" + testName + sanitizeFilename(context);

            publishScreenshot(filename, currentContext);
        });

        testServer.resetMocks();
        context.close();
    }

    void navigateHome() {
        // retry once: WebKit occasionally races the ephemeral test server on goto
        try {
            page.navigate(testServer.baseUrl());
        } catch (PlaywrightException e) {
            page.navigate(testServer.baseUrl());
        }
    }

    void mockEndpoint(String path, String contentType, String body) {testServer.mockEndpoint(path, contentType, body);}

    void mockEndpoint(String path, String expectedMethod, String contentType, String body) {testServer.mockEndpoint(path, expectedMethod, contentType, body);}

    void mockEndpoint(String path, String contentType, String body, int statusCode) {testServer.mockEndpoint(path, contentType, body, statusCode);}

    void mockEndpointWithHeaders(String path, String contentType, String body, Map<String, String> responseHeaders) {testServer.mockEndpointWithHeaders(path, contentType, body, responseHeaders);}

    void mockEndpointWithBodyEcho(String path, String expectedMethod) {testServer.mockEndpointWithBodyEcho(path, expectedMethod);}

    void mockEndpointWithContentNegotiation(String path, Map<String, String> responsesByAccept) {testServer.mockEndpointWithContentNegotiation(path, responsesByAccept);}

    void mockRootEndpoint(String path, String contentType, String body) {testServer.mockRootEndpoint(path, contentType, body);}

    void simulateNetworkError(String path) {
        page.route(url -> url.endsWith(path), Route::abort);
    }

    void blockHtmxRequests() {testServer.blockHtmxRequests();}

    void unblockHtmxRequests() {testServer.unblockHtmxRequests();}

    // Page interactions

    void focusTree() {
        page.locator("[role='tree']").focus();
        // WebKit may drop focus to <body> if a re-layout happens after .focus(); re-assert.
        page.waitForFunction(
                "() => document.activeElement && document.activeElement.matches('[role=tree]')");
    }

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

    void pressKey(String key) {
        // Snapshot the marker before pressing: the capture-phase listener updates
        // __lastKeySeen synchronously during dispatch, and the *handling* (focus change,
        // toggle activation) happens in bubble-phase handlers that run right after. Waiting
        // for the marker to advance past its previous value guarantees those handlers ran.
        var seqBefore = ((Number) page.evaluate("() => window.__keySeq || 0")).doubleValue();
        page.evaluate("(s) => { window.__pendingKeySeq = s; }", seqBefore);
        page.keyboard().press(key);
        // note: keySeq is bumped by the capture-phase listener on dispatch
        var baseKey = key.contains("+") ? key.substring(key.lastIndexOf('+') + 1) : key;
        if (baseKey.startsWith("Digit")) baseKey = baseKey.substring(5); // Control+Digit0 -> key "0"
        page.waitForFunction("(k) => window.__lastKeySeen === k && window.__lastKeySeq > (window.__pendingKeySeq || 0)",
                baseKey,
                new Page.WaitForFunctionOptions().setTimeout(2000));
    }


    String selectedTreeItemText() {
        return page.locator("[role='treeitem'][aria-selected='true']").textContent();
    }

    boolean isTreeItemSelected(String hxGetPath) {
        return (Boolean) page.evaluate(
                "path => { var el = document.querySelector(\"[hx-get='\" + path + \"']\");"
                + " return el ? el.closest('[role=treeitem]').getAttribute('aria-selected') === 'true' : false; }",
                hxGetPath);
    }

    boolean isTreeItemVisible(String text) {
        return (Boolean) page.evaluate(
                "text => { var items = Array.from(document.querySelectorAll('[role=treeitem]'));"
                + " var el = items.find(function(i) { return i.textContent.trim().startsWith(text); });"
                + " return el ? getComputedStyle(el).display !== 'none' : false; }",
                text);
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

    void clickTreeNode(String hxGetPath) {
        page.locator("[hx-get='" + hxGetPath + "']").click();
        // Wait until the htmx swap into #detail has fully settled, so subsequent
        // interactions (fill, focus, click) don't race the DOM replacement.
        page.waitForFunction(
                "() => !document.querySelector('.htmx-swapping, .htmx-settling, .htmx-request')"
                        + " && !document.getElementById('detail').classList.contains('htmx-swapping')"
                        + " && !document.getElementById('detail').classList.contains('htmx-request')"
                        + " && !document.getElementById('detail').classList.contains('htmx-settling')"
                        + " && document.querySelector('#detail form[data-path]') !== null");
    }

    void clickMethodBadge(String hxGetPath, String method) {
        page.locator("[hx-get='" + hxGetPath + "'] .tag:text('" + method + "')").click();
    }

    void waitForDetailContent(String text) {
        lastWaitContext = "waitForDetailContent(\"" + text + "\")";
        page.waitForSelector("#detail :text('" + text + "')");
        // Also wait until htmx has finished swapping into #detail, so the DOM is stable.
        // If a method-tab swap just happened, also wait for the app's post-swap handler
        // (which restores custom headers etc.) to have run on #method-content.
        page.waitForFunction(
                "() => !document.querySelector('.htmx-swapping, .htmx-settling, .htmx-request')"
                        + " && !document.getElementById('detail').classList.contains('htmx-swapping')"
                        + " && !document.getElementById('detail').classList.contains('htmx-request')"
                        + " && !document.getElementById('detail').classList.contains('htmx-settling')"
                        + " && (!document.getElementById('method-content')"
                        + "    || document.getElementById('method-content').getAttribute('data-swap-complete') === 'true')");
    }

    void waitForDetailEmpty() {
        lastWaitContext = "waitForDetailEmpty()";
        page.waitForFunction("() => document.getElementById('detail').innerHTML.trim() === ''");
    }

    String detailText() {return page.locator("#detail").textContent();}

    private String sanitizeFilename(String text) {
        return text.replaceAll("[^a-zA-Z0-9.-]", "_").toLowerCase();
    }

    double[] treeBoundingBox() {
        var box = page.locator("[role='tree']").boundingBox();
        return new double[]{box.x, box.y, box.width, box.height};
    }

    double[] detailBoundingBox() {
        var box = page.locator("#detail").boundingBox();
        return new double[]{box.x, box.y, box.width, box.height};
    }

    boolean isModeButtonVisible(String mode) {return page.locator("[data-toggle-value='" + mode.toLowerCase() + "']").isVisible();}

    String modeButtonTooltip(String mode) {return page.locator("[data-toggle-value='" + mode.toLowerCase() + "']").getAttribute("title");}

    void focusModeToggle() {page.locator("[data-toggle='mode']").focus();}

    boolean isModeSelectorInSplitSecond() {
        return page.locator(".split-second .mode-selector-container").count() == 1;
    }

    boolean isModeSelectorInDetailHeader() {
        return page.locator(".detail-header .mode-selector-container").count() > 0;
    }

    boolean isModeToggleFocused() {
        return (Boolean) page.evaluate("() => document.activeElement.matches('[data-toggle=mode]')");
    }

    void clickModeButton(String mode) {page.locator("[data-toggle-value='" + mode.toLowerCase() + "']").click();}

    boolean hasSegmentedControl() {
        return page.locator("[data-toggle='mode']").count() == 1;
    }

    boolean isSegmentActive(String mode) {
        return page.locator("[data-toggle='mode'] [data-toggle-value='" + mode + "'].is-active").count() == 1;
    }

    String currentMode() {
        return (String) page.evaluate("() => document.querySelector('[data-toggle=mode]').getAttribute('data-mode')");
    }

    boolean isDropdownMenuVisible() {
        return page.locator(".mode-dropdown-menu").isVisible();
    }

    boolean dropdownContainsItem(String generator) {
        return page.locator(".mode-dropdown-item[data-generator='" + generator + "']").count() > 0;
    }

    void clickDropdownItem(String generator) {
        page.locator(".mode-dropdown-item[data-generator='" + generator + "']").click();
    }

    void selectDropdownFormat(String generator) {
        clickDropdownItem(generator);
    }

    void fillInput(String name, String value) {page.locator("#detail input[name='" + name + "']").fill(value);}

    boolean isInputDisabled(String name) {return page.locator("#detail input[name='" + name + "']").isDisabled();}

    void checkCheckbox(String name) {page.locator("#detail input[type='checkbox'][name='" + name + "']").check();}

    boolean isCheckboxChecked(String name) {return page.locator("#detail input[type='checkbox'][name='" + name + "']").isChecked();}

    void focusInput(String name) {page.locator("#detail input[name='" + name + "']").focus();}

    void focusSelect(String name) {page.locator("#detail select[name='" + name + "']").focus();}

    void selectOption(String name, String value) {page.locator("#detail select[name='" + name + "']").selectOption(value);}

    String selectValue(String name) {return page.locator("#detail select[name='" + name + "']").inputValue();}

    int pinIconRotationAfterTransition(String name) {
        page.waitForFunction(
                "(name) => { var el = document.querySelector(\"#detail [name='\" + name + \"']\");"
                        + " var toggle = el && el.closest('.field').querySelector('.persist-toggle');"
                + " return toggle && toggle.getAnimations().every(function(a) { return a.playState === 'finished'; }); }",
                name);
        return readPinRotation(name);
    }

    private int readPinRotation(String name) {
        return ((Number) page.evaluate(
                "() => { var el = document.querySelector(\"#detail [name='" + name + "']\");"
                + " var toggle = el.closest('.field').querySelector('.persist-toggle');"
                + " var t = getComputedStyle(toggle).transform;"
                + " if (!t || t === 'none') return 0;"
                + " var m = t.match(/matrix\\(([^)]+)\\)/);"
                + " if (!m) return 0;"
                + " var v = m[1].split(',').map(Number);"
                + " return Math.round(Math.atan2(v[1], v[0]) * 180 / Math.PI); }")).intValue();
    }

    double pinIconRight(String name) {
        return ((Number) page.evaluate(
                "() => { var sel = document.querySelector(\"#detail select[name='" + name + "']\");"
                + " var icon = sel.closest('.control').querySelector('.icon.is-right');"
                + " return parseFloat(getComputedStyle(icon).right); }")).doubleValue();
    }

    void fillRequestBody(String body) {
        var locator = page.locator("#detail textarea[data-request-body]");
        locator.fill(body);
        // fill() resolves as soon as the element is actionable, but htmx may still be
        // mid-swap on #detail; wait until no swap is in progress so the element is stable.
        page.waitForFunction(
                "() => !document.getElementById('detail').classList.contains('htmx-swapping')"
                        + " && !document.getElementById('detail').classList.contains('htmx-request')"
                        + " && !document.getElementById('detail').classList.contains('htmx-settling')"
                        + " && !document.querySelector('.htmx-swapping, .htmx-settling, .htmx-request')");
    }

    void clearRequestBody() {page.locator("#detail textarea[data-request-body]").clear();}

    boolean requestBodyHasRequiredAttribute() {
        return page.locator("#detail textarea[data-request-body][required]").count() > 0;
    }

    double requestBodyHeight() {
        var locator = page.locator("#detail textarea[data-request-body]");
        // boundingBox() returns null while htmx is mid-swap; wait for a stable element
        page.waitForFunction(
                "() => { var el = document.querySelector('#detail textarea[data-request-body]');"
                        + " return el && el.offsetParent !== null && !document.querySelector('.htmx-swapping, .htmx-settling, .htmx-request'); }");
        return locator.boundingBox().height;
    }

    void focusRequestBody() {
        var locator = page.locator("#detail textarea[data-request-body]");
        locator.waitFor();
        // Wait until htmx has finished swapping into #detail so the focused element is stable
        page.waitForFunction(
                "() => !document.querySelector('.htmx-swapping, .htmx-settling, .htmx-request')"
                        + " && !document.getElementById('detail').classList.contains('htmx-swapping')"
                        + " && !document.getElementById('detail').classList.contains('htmx-request')"
                        + " && !document.getElementById('detail').classList.contains('htmx-settling')");
        locator.focus();
    }

    void setCursorAtStart() {
        page.evaluate("() => { var el = document.activeElement; el.setSelectionRange(0, 0); }");
    }

    void setCursorAtEnd() {
        page.evaluate("() => { var el = document.activeElement; el.setSelectionRange(el.value.length, el.value.length); }");
    }

    void waitForInput(String name) {
        lastWaitContext = "waitForInput(\"" + name + "\")";
        page.waitForSelector("#detail input[name='" + name + "']");
        page.waitForFunction(
                "() => !document.querySelector('.htmx-swapping, .htmx-settling, .htmx-request')"
                        + " && !document.getElementById('detail').classList.contains('htmx-swapping')"
                        + " && !document.getElementById('detail').classList.contains('htmx-request')"
                        + " && !document.getElementById('detail').classList.contains('htmx-settling')");
    }

    void waitForFocusedInput(String name) {
        page.waitForFunction("name => document.activeElement && document.activeElement.getAttribute('name') === name", name);
    }

    void clickSend() {
        // Snapshot the clipboard so we can detect the write triggered by this click.
        // `navigator.clipboard.writeText` is async and not awaited by the handler, and the
        // "Copied!" button label reverts after 1.5s, so polling the clipboard for a change
        // is the only reliable signal that the copy ran.
        page.evaluate("() => navigator.clipboard.readText().then(t => window.__clip = t, () => window.__clip = '')");
        page.locator(".mode-send-button").click();
        page.waitForFunction(
                "() => navigator.clipboard.readText().then(t => t !== window.__clip, () => false)",
                null, new Page.WaitForFunctionOptions().setTimeout(10000));
    }

    void clickSendExpectingNoWrite() {
        page.evaluate("() => navigator.clipboard.readText().then(t => window.__clip = t, () => window.__clip = '')");
        page.locator(".mode-send-button").click();
        // Give the (blocked) handler a chance to run: if it had written, the clipboard would change
        page.waitForTimeout(300);
    }

    String sendButtonText() {return page.locator(".mode-send-button").textContent();}

    void waitForResponse() {
        lastWaitContext = "waitForResponse()";
        page.waitForSelector("#detail .response-status");
    }

    void waitForTimeout(double milliseconds) {page.waitForTimeout(milliseconds);}

    /// Clicks Send and waits for the request cycle to complete.
    /// Robust against missing the intermediate "Sending..." label.
    void resend() {
        clickSend();
        waitForResponse();
        // And ensure the button is back to its idle state
        page.waitForFunction("() => document.querySelector('.mode-send-button')?.textContent === 'Send'");
    }

    /// Clicks Send, waits for headers toggle to disappear and reappear (avoids "Sending..." race condition).
    void resendAndWaitForHeaders() {
        lastWaitContext = "resendAndWaitForHeaders()";
        // Remove existing toggle so we can wait for a fresh one
        page.evaluate("document.querySelector('#detail .response-headers-toggle')?.remove()");
        clickSend();
        page.waitForSelector("#detail .response-headers-toggle");
    }

    boolean isStatusBadgeOnSameLineAsModeToggle() {
        var modeBox = page.locator("[data-toggle='mode']").boundingBox();
        var statusBox = page.locator("#detail .response-status").boundingBox();
        if (modeBox == null || statusBox == null) return false;
        // same line: the status badge's vertical midpoint is within the mode toggle's vertical span
        var statusMidY = statusBox.y + statusBox.height / 2;
        return statusMidY >= modeBox.y && statusMidY <= modeBox.y + modeBox.height;
    }

    boolean hasResponseStatus() {return page.locator("#detail .response-status").count() > 0;}

    String statusBadgeText() {return page.locator("#detail .response-status").textContent();}

    String noBodyMessageText() {return page.locator("#detail .response-area .response-empty.box").textContent();}

    void clickMethodTab(int index) {
        // Snapshot the swap counter before clicking, so we wait for THIS tab's swap to complete.
        page.evaluate("() => { window.__mcSwapBase = parseInt((document.getElementById('method-content') || {getAttribute: () => '0'}).getAttribute('data-swaps-done') || '0', 10); }");
        page.locator(".tabs li:nth-child(" + index + ") a").click();
        // The method tab swap targets #method-content (inside #detail). WebKit issues an
        // extra focusin-triggered request per click, so wait until BOTH swaps have fully
        // completed (app's post-swap handler ran on #method-content each time).
        waitForHtmxSettled();
        page.waitForFunction(
                "() => document.getElementById('method-content')"
                        + " && document.getElementById('method-content').getAttribute('data-swap-complete') === 'true'"
                        + " && parseInt(document.getElementById('method-content').getAttribute('data-swaps-done') || '0', 10)"
                        + " > (window.__mcSwapBase || 0)");
    }

    void waitForHtmxSettled() {
        page.waitForFunction(
                "() => !document.querySelector('.htmx-swapping, .htmx-settling, .htmx-request')"
                        + " && !document.getElementById('detail').classList.contains('htmx-swapping')"
                        + " && !document.getElementById('detail').classList.contains('htmx-request')"
                        + " && !document.getElementById('detail').classList.contains('htmx-settling')"
                        + " && !document.querySelector('#method-content.htmx-swapping, #method-content.htmx-settling, #method-content.htmx-request')");
    }

    void focusTab() {
        page.locator("[data-tab-bar]").focus();
        waitForContentBelowTabBar();
        // WebKit may drop focus to <body> if a re-layout happens after .focus(); re-assert.
        page.waitForFunction(
                "() => document.activeElement && document.activeElement.closest('[data-tab-bar]') !== null");
    }

    /// Wait for at least one focusable element below the tab bar to have a valid bounding rect.
    /// Spatial navigation relies on `getBoundingClientRect()` — this ensures layout is complete.
    void waitForContentBelowTabBar() {
        page.waitForFunction("""
                () => {
                    var tabBar = document.querySelector('[data-tab-bar]');
                    if (!tabBar) return false;
                    var rect = tabBar.getBoundingClientRect();
                    if (rect.height === 0) return false;
                    var centerY = rect.top + rect.height / 2;
                    var candidates = document.querySelectorAll(
                        '#detail input:not(:disabled), #detail select:not(:disabled), #detail textarea:not(:disabled),'
                        + ' #detail button:not(:disabled), #detail a[href], #detail [tabindex="0"]');
                    for (var i = 0; i < candidates.length; i++) {
                        var c = candidates[i];
                        if (c.offsetParent && !c.closest('[data-tab-bar]')) {
                            var cr = c.getBoundingClientRect();
                            if (cr.top + cr.height / 2 > centerY) return true;
                        }
                    }
                    return false;
                }""");
    }

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
                "() => !document.querySelector('.mode-send-button').disabled");
    }

    String responseText() {return page.locator("#detail pre.response").textContent();}

    String cookieNoticeText() {return page.locator("#detail .cookie-notice").textContent();}

    boolean hasResponseTypeSelect() {return page.locator("#detail [data-accept] select").count() > 0;}

    List<String> responseTypeOptions() {
        return page.locator("#detail [data-accept] select option").allTextContents();
    }

    void selectResponseType(String contentType) {
        page.locator("#detail [data-accept] select").selectOption(contentType);
    }

    boolean hasResponseBox() {return page.locator("#detail .schema-box[data-box='response']").count() > 0;}

    boolean hasSchemaToggle(String boxType) {
        return page.locator("#detail .schema-box[data-box='" + boxType + "'] .schema-toggle").count() > 0;
    }

    void toggleSchema(String boxType) {
        page.locator("#detail .schema-box[data-box='" + boxType + "'] .schema-toggle").click();
    }

    void focusSchemaToggle(String boxType) {
        page.locator(".schema-box[data-box='" + boxType + "'] .schema-toggle").focus();
    }

    boolean isSchemaToggleFocused(String boxType) {
        return (Boolean) page.evaluate(
                "boxType => document.activeElement === document.querySelector('.schema-box[data-box=\"' + boxType + '\"] .schema-toggle')",
                boxType);
    }

    boolean schemaToggleHasFocusRing(String boxType) {
        return (Boolean) page.evaluate(
                "boxType => { var el = document.querySelector('.schema-box[data-box=\"' + boxType + '\"] .schema-toggle');"
                + " return el && getComputedStyle(el).outlineStyle !== 'none'; }",
                boxType);
    }

    boolean isSchemaExpanded(String boxType) {
        return page.locator("#detail .schema-box[data-box='" + boxType + "']:not(.is-collapsed)").count() > 0;
    }

    boolean hasSchemaTree(String boxType) {
        return page.locator("#detail .schema-box[data-box='" + boxType + "'] .schema-box-tree").count() > 0;
    }

    String schemaTypeBadge(String boxType) {
        return page.locator(".schema-box[data-box='" + boxType + "'] .schema-status-panel:visible .schema-type-badge").textContent();
    }

    List<String> schemaPropertyNames(String boxType) {
        return page.locator(".schema-box[data-box='" + boxType + "'] .schema-status-panel:visible .schema-prop-name").allTextContents();
    }

    String schemaPropertyType(String boxType, String propName) {
        return page.locator(".schema-box[data-box='" + boxType + "'] .schema-status-panel:visible .schema-prop-name[data-prop='" + propName + "'] + .schema-prop-details .schema-prop-type").textContent();
    }

    String schemaPropertyExample(String boxType, String propName) {
        return page.locator(".schema-box[data-box='" + boxType + "'] .schema-status-panel:visible .schema-prop-name[data-prop='" + propName + "'] + .schema-prop-details .schema-prop-example").textContent();
    }

    List<String> statusCodeTabs() {
        return page.locator(".schema-box[data-box='response'] .schema-status-tab").allTextContents();
    }

    void clickStatusCodeTab(String code) {
        page.locator(".schema-box[data-box='response'] .schema-status-tab:text('" + code + "')").click();
    }

    void focusStatusCodeTab() {
        page.locator(".schema-box[data-box='response'] .schema-status-tabs[data-tab-bar]").focus();
    }

    String activeStatusCodeTab() {
        return page.locator(".schema-box[data-box='response'] .schema-status-tab.is-active").textContent();
    }

    boolean hasNestedToggle(String boxType) {
        return page.locator(".schema-box[data-box='" + boxType + "'] .schema-nested-toggle:visible").count() > 0;
    }

    void clickNestedToggle(String boxType) {
        page.locator(".schema-box[data-box='" + boxType + "'] .schema-nested-toggle:visible").first().click();
    }

    boolean isNestedExpanded(String boxType) {
        return page.locator(".schema-box[data-box='" + boxType + "'] .schema-nested-toggle[aria-expanded='true']").count() > 0;
    }

    boolean isNestedSchemaExpanded(String propertyName) {
        return "true".equals(page.locator(".schema-prop-name[data-prop='" + propertyName + "'] .schema-nested-toggle")
                .getAttribute("aria-expanded"));
    }

    void expandNestedSchema(String propertyName) {
        page.locator(".schema-prop-name[data-prop='" + propertyName + "'] .schema-nested-toggle").click();
    }

    List<String> nestedPropertyNames(String boxType) {
        return page.locator(".schema-box[data-box='" + boxType + "'] .schema-nested.is-expanded .schema-prop-name").allTextContents();
    }

    boolean hasBodyBox() {return page.locator("#detail .schema-box[data-box='body']").count() > 0;}

    boolean hasResponseSchemaBox() {return page.locator("#detail .schema-box[data-box='response']").count() > 0;}

    String schemaResponseDescription(String statusCode) {
        return page.locator("#detail .schema-status-panel[data-status='" + statusCode + "'] .schema-response-description").textContent();
    }

    String schemaHeaderName(String statusCode, int index) {
        return page.locator("#detail .schema-status-panel[data-status='" + statusCode + "'] .schema-response-headers .schema-prop-name").nth(index).textContent().trim();
    }

    String schemaHeaderDescription(String statusCode, int index) {
        return page.locator("#detail .schema-status-panel[data-status='" + statusCode + "'] .schema-response-headers .schema-prop-desc").nth(index).textContent();
    }

    boolean schemaHeaderHasBadge(String statusCode, int index, String badgeText) {
        var header = page.locator("#detail .schema-status-panel[data-status='" + statusCode + "'] .schema-response-headers .schema-prop-details").nth(index);
        return header.locator(".tag").filter(new FilterOptions().setHasText(badgeText)).count() > 0;
    }

    List<String> inlineLinkNames(String statusCode) {
        var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
        return page.locator(panel + " .schema-link-row a").allTextContents().stream()
                .map(t -> t.replaceFirst("^→ ", ""))
                .toList();
    }

    String inlineLinkDescription(String statusCode, String linkName) {
        var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
        var row = page.locator(panel + " .schema-link-row")
                .filter(new FilterOptions().setHasText(linkName));
        return row.locator(".schema-prop-desc").textContent();
    }

    List<String> inlineLinkParams(String statusCode, String linkName) {
        var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
        var row = page.locator(panel + " .schema-link-row")
                .filter(new FilterOptions().setHasText(linkName));
        return row.locator(".schema-link-param").allTextContents();
    }

    String inlineLinkHref(String statusCode, String linkName) {
        var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
        return page.locator(panel + " .schema-link-row a")
                .filter(new FilterOptions().setHasText(linkName))
                .first()
                .getAttribute("href");
    }

    void clickInlineLink(String statusCode, String linkName) {
        var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
        page.locator(panel + " .schema-link-row a")
                .filter(new FilterOptions().setHasText(linkName))
                .first()
                .click();
    }

    boolean isInlineLinkFocused(String statusCode, String linkName) {
        var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
        return (Boolean) page.evaluate(
                "([panel, linkName]) => { var links = document.querySelectorAll(panel + ' .schema-link-row a');"
                + " for (var i = 0; i < links.length; i++) { if (links[i].textContent.includes(linkName) && links[i] === document.activeElement) return true; } return false; }",
                new Object[]{panel, linkName});
    }

    void waitForInlineLinkFocused(String statusCode, String linkName) {
        var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
        page.waitForFunction(
                "([panel, linkName]) => { var links = document.querySelectorAll(panel + ' .schema-link-row a');"
                + " for (var i = 0; i < links.length; i++) { if (links[i].textContent.includes(linkName) && links[i] === document.activeElement) return true; } return false; }",
                new Object[]{panel, linkName});
    }

    void focusInlineLink(String statusCode, String linkName) {
        var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
        page.locator(panel + " .schema-link-row a")
                .filter(new FilterOptions().setHasText(linkName))
                .first()
                .focus();
    }

    void focusSchemaLinkRow(String statusCode, String linkName) {
        var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
        page.locator(panel + " .schema-link-row")
                .filter(new FilterOptions().setHasText(linkName))
                .first()
                .focus();
    }

    String inlineLinkCursor(String statusCode, String linkName) {
        var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
        return page.locator(panel + " .schema-link-row a")
                .filter(new FilterOptions().setHasText(linkName))
                .first()
                .evaluate("el => getComputedStyle(el).cursor").toString();
    }

    boolean schemaLinkSubRowExists(String statusCode, String linkName) {
        var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
        return page.locator(panel + " .schema-props .schema-link-row")
                       .filter(new FilterOptions().setHasText(linkName))
                       .count() > 0;
    }

    int schemaLinkSubRowCount(String statusCode, String linkName) {
        var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
        return page.locator(panel + " .schema-props .schema-link-row")
                .filter(new FilterOptions().setHasText(linkName))
                .count();
    }

    boolean schemaLinkHasTabindex(String statusCode, String linkName) {
        var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
        var linkRow = page.locator(panel + " .schema-props .schema-link-row")
                .filter(new FilterOptions().setHasText(linkName))
                .first();
        var tabindex = linkRow.getAttribute("tabindex");
        return "0".equals(tabindex);
    }

    int bodyLinkCount() {
        return page.locator("#detail pre.response .body-link").count();
    }

    String bodyLinkText(int index) {
        return page.locator("#detail pre.response .body-link").nth(index).textContent();
    }

    String bodyLinkHref(int index) {
        return page.locator("#detail pre.response .body-link").nth(index).getAttribute("href");
    }

    String bodyLinkCursor(int index) {
        return page.locator("#detail pre.response .body-link").nth(index).evaluate("el => getComputedStyle(el).cursor").toString();
    }

    String bodyLinkBorderStyle(int index) {
        return page.locator("#detail pre.response .body-link").nth(index).evaluate("el => getComputedStyle(el).borderStyle").toString();
    }

    void clickBodyLink(int index) {
        page.locator("#detail pre.response .body-link").nth(index).click();
    }

    void focusBodyLink(int index) {
        page.locator("#detail pre.response .body-link").nth(index).focus();
    }

    String operationFormAttribute(String attribute) {
        return page.locator("#detail form").getAttribute(attribute);
    }

    String responseStatusDescription() {
        return page.locator("#detail .response-status-description").textContent();
    }

    boolean responseStatusDescriptionIsBeforeHeaders() {
        return (Boolean) page.evaluate(
                "() => { var desc = document.querySelector('#detail .response-status-description');" +
                "var headers = document.querySelector('#detail .response-headers') || document.querySelector('#detail .response-headers-toggle');" +
                "return desc && headers && desc.compareDocumentPosition(headers) === Node.DOCUMENT_POSITION_FOLLOWING; }");
    }

    boolean hasResponseHeadersToggle() {return page.locator("#detail .response-headers-toggle").count() > 0;}

    String responseHeadersToggleText() {return page.locator("#detail .response-headers-toggle").textContent();}

    void toggleResponseHeaders() {page.locator("#detail .response-headers-toggle").click();}

    boolean responseHeadersVisible() {return page.locator("#detail .response-headers.is-expanded").count() > 0;}

    String responseHeaderText(String name) {
        return page.locator("#detail .response-headers .response-header-value[data-header='" + name + "']").textContent();
    }

    String responseHeaderDescription(String name) {
        return page.locator("#detail .response-headers .response-header-description[data-header='" + name + "']").textContent();
    }

    boolean isResponseHeaderMissing(String name) {
        return page.locator("#detail .response-headers .response-header-missing[data-header='" + name + "']").count() > 0;
    }

    boolean undocumentedHeadersVisible() {
        var locator = page.locator("#detail .response-headers-undocumented");
        if (locator.count() == 0) return false;
        var style = locator.getAttribute("style");
        return style == null || !style.contains("display: none");
    }

    boolean hasShowAllButton() {
        return page.locator("#detail .response-headers-show-all").count() > 0;
    }

    void clickShowAll() {
        page.locator("#detail .response-headers-show-all").click();
    }

    boolean isResponseHeaderDeprecated(String name) {
        return page.locator("#detail .response-headers .response-header-name.is-deprecated")
                       .filter(new FilterOptions().setHasText(name)).count() > 0;
    }

    boolean responseHasHighlighting() {
        return page.locator("#detail pre.response code.hljs span[class^='hljs-']").count() > 0;
    }

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

    boolean selectedItemIsVisible() {
        return (Boolean) page.evaluate(
                "() => { var el = document.querySelector('[role=tree] [aria-selected=\"true\"]');"
                + " return el ? getComputedStyle(el).display !== 'none' : false; }");
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

    boolean hasServerPanel() {
        return page.locator("#server-selector").count() > 0;
    }

    int serverItemCount() {
        return page.locator("#server-selector .server-row").count();
    }

    int serverDropdownDividerCount() {
        return page.locator("#server-selector .dropdown-content > hr.dropdown-divider").count();
    }

    String serverItemUrl(int index) {
        return page.locator("#server-selector .server-row").nth(index)
                .locator(".server-item-url").textContent().trim();
    }

    String serverItemDescription(int index) {
        var item = page.locator("#server-selector .server-row").nth(index);
        var desc = item.locator(".server-item-description");
        if (desc.count() == 0) return "";
        return desc.textContent().trim();
    }

    boolean isServerDescriptionBelowUrl(int index) {
        var item = page.locator("#server-selector .server-row").nth(index);
        var urlTop = ((Number) item.locator(".server-item-url").evaluate("el => el.getBoundingClientRect().top")).doubleValue();
        var descTop = ((Number) item.locator(".server-item-description").evaluate("el => el.getBoundingClientRect().top")).doubleValue();
        return descTop > urlTop;
    }

    boolean isServerItemActive(int index) {
        return page.locator("#server-selector .server-row").nth(index)
                .locator("input[type='radio']").isChecked();
    }

    boolean isServerRadioOnSameLineAsUrl(int index) {
        var item = page.locator("#server-selector .server-row").nth(index);
        var radioRight = ((Number) item.locator("input[type='radio']").evaluate("el => el.getBoundingClientRect().right")).doubleValue();
        var urlLeft = ((Number) item.locator(".server-item-url").evaluate("el => el.getBoundingClientRect().left")).doubleValue();
        var radioBottom = ((Number) item.locator("input[type='radio']").evaluate("el => el.getBoundingClientRect().bottom")).doubleValue();
        var urlTop = ((Number) item.locator(".server-item-url").evaluate("el => el.getBoundingClientRect().top")).doubleValue();
        return radioRight <= urlLeft && radioBottom > urlTop;
    }

    boolean isServerDropdownNarrowerThanHeader() {
        return (Boolean) page.evaluate("() => {"
                + " const menu = document.querySelector('#server-selector .dropdown-menu').getBoundingClientRect();"
                + " const header = document.querySelector('.detail-header').getBoundingClientRect();"
                + " return menu.width < header.width - 1;"
                + " }");
    }

    boolean isServerDropdownOverlayingContent() {
        return (Boolean) page.evaluate("() => {"
                + " const menu = document.querySelector('#server-selector .dropdown-menu');"
                + " if (!menu || !menu.isConnected) return false;"
                + " const cs = getComputedStyle(menu);"
                + " if (cs.position !== 'absolute') return false;"
                + " const triggerBottom = document.querySelector('#server-selector .dropdown-trigger button').getBoundingClientRect().bottom;"
                + " const menuTop = menu.getBoundingClientRect().top;"
                + " return menuTop >= triggerBottom - 2 && menuTop <= triggerBottom + 10;"
                + " }");
    }

    boolean isTreeNotPushedDownByDropdown() {
        return (Boolean) page.evaluate("() => {"
                + " const tree = document.querySelector('#tree-container');"
                + " const btn = document.querySelector('#server-selector .dropdown-trigger button');"
                + " const before = tree.getBoundingClientRect().top;"
                + " btn.click();"
                + " const during = tree.getBoundingClientRect().top;"
                + " const open = document.querySelector('#server-selector .dropdown-menu').getBoundingClientRect();"
                + " btn.click();"
                + " return Math.abs(during - before) < 1 && open.height > 0;"
                + " }");
    }

    boolean isServerDropdownInsideViewport() {
        var box = page.locator("#server-selector .dropdown-menu").boundingBox();
        if (box == null) return false;
        var viewportWidth = ((Number) page.evaluate("() => window.innerWidth")).doubleValue();
        return box.x >= -0.5 && box.x + box.width <= viewportWidth + 0.5;
    }

    boolean isServerUrlOnOneLine(int index) {
        return !isServerUrlWrapped(index);
    }

    boolean isServerUrlWrapped(int index) {
        var item = page.locator("#server-selector .server-row").nth(index);
        var url = item.locator(".server-item-url");
        var info = url.evaluate("el => { const r = el.getBoundingClientRect(); const cs = getComputedStyle(el);"
                + " const lh = cs.lineHeight.endsWith('px') ? parseFloat(cs.lineHeight) : parseFloat(cs.fontSize) * 1.5;"
                + " return {h: r.height, lh}; }");
        var map = (java.util.Map<?, ?>) info;
        var height = ((Number) map.get("h")).doubleValue();
        var lh = ((Number) map.get("lh")).doubleValue();
        return height > lh + 1;
    }

    void clickServerToggle() {
        page.locator("#server-selector .dropdown-trigger button").click();
    }

    String serverTriggerTooltip() {
        return page.locator("#server-selector .dropdown-trigger button").getAttribute("title");
    }

    boolean isFocusInsideServerDropdown() {
        return (Boolean) page.evaluate("!!document.activeElement && !!document.activeElement.closest('#server-selector .dropdown-content')");
    }

    boolean isServerPanelExpanded() {
        return page.locator("#server-selector").getAttribute("class").contains("is-active");
    }

    boolean isServerDropdownInHeader() {
        return page.locator(".detail-header #server-selector").count() > 0;
    }

    boolean isServerDropdownRightAlignedWithTrigger() {
        var trigger = page.locator("#server-selector .dropdown-trigger button").boundingBox();
        var menu = page.locator("#server-selector .dropdown-menu").boundingBox();
        if (trigger == null || menu == null) return false;
        return Math.abs(trigger.x + trigger.width - (menu.x + menu.width)) < 1.0;
    }

    boolean isServerTriggerRightAlignedInHeader() {
        var trigger = page.locator("#server-selector .dropdown-trigger button").boundingBox();
        var header = page.locator(".detail-header").boundingBox();
        if (trigger == null || header == null) return false;
        return Math.abs(trigger.x + trigger.width - (header.x + header.width)) < 1.0;
    }

    String serverOverrideWarning() {
        return page.locator("#server-override .server-override-warning").textContent().trim();
    }

    int serverOverrideServerCount() {
        return page.locator("#server-override input[type='radio']").count();
    }

    String serverOverrideServerUrl(int index) {
        var label = page.locator("#server-override label").nth(index);
        var urlSpan = label.locator("span").first();
        return urlSpan.textContent().trim();
    }

    boolean isGlobalServerDisabled(int index) {
        return page.locator("#server-selector input[type='radio'][name='server']").nth(index).isDisabled();
    }

    String templateServerUrlPattern(int index) {
        var heading = page.locator("#server-selector .template-group-heading").nth(index);
        return heading.locator(".server-item-url").textContent().trim();
    }

    String templateServerDescription(int index) {
        return page.locator("#server-selector .template-group").nth(index)
                .locator(".server-item-description").textContent().trim();
    }

    boolean templateServerDescriptionUsesItemClass(int index) {
        return page.locator("#server-selector .template-group").nth(index)
                       .locator(".server-item-description").count() > 0;
    }

    int templateServerPresetCount(int serverIndex) {
        // Count preset labels that belong to the specified template server
        // Presets are labeled with IDs like "server-{index}-preset-{presetIndex}"
        var presetPattern = "input[id^='server-" + serverIndex + "-preset-']";
        return page.locator("#server-selector " + presetPattern).count();
    }

    boolean areTemplatePresetLabelsStackedVertically(int serverIndex) {
        return (Boolean) page.evaluate("""
                (serverIndex) => {
                    const labels = [...document.querySelectorAll(
                        "#server-selector .template-group input[id^='server-" + serverIndex + "-preset-']")]
                        .map(i => i.closest('label').getBoundingClientRect());
                    if (labels.length < 2) return true;
                    return labels.every((r, i) => i === 0 || r.left === labels[0].left && r.top >= labels[i - 1].bottom - 1);
                }
                """, serverIndex);
    }

    String templateServerPresetLabel(int serverIndex, int presetIndex) {
        var presetId = "server-" + serverIndex + "-preset-" + presetIndex;
        var label = page.locator("#server-selector label[for='" + presetId + "']");
        return label.locator(".server-item-url").textContent().trim();
    }

    boolean isPresetRadioFocused(int serverIndex, int presetIndex) {
        var presetId = "server-" + serverIndex + "-preset-" + presetIndex;
        var radio = page.locator("#server-selector input[id='" + presetId + "']");
        return (Boolean) radio.evaluate("el => el === document.activeElement");
    }

    boolean templateServerPresetUrlUsesItemClass(int serverIndex, int presetIndex) {
        var presetId = "server-" + serverIndex + "-preset-" + presetIndex;
        var label = page.locator("#server-selector label[for='" + presetId + "']");
        return label.locator(".server-item-url").count() > 0;
    }

    boolean templateGroupHeadingHasBottomBorder(int serverIndex) {
        var heading = page.locator("#server-selector .template-group").nth(serverIndex).locator(".template-group-heading");
        return (Boolean) heading.evaluate(
                "el => window.getComputedStyle(el).borderBottomWidth !== '0px'");
    }

    boolean areAddButtonsInsideDropdown(int serverIndex) {
        return (Boolean) page.evaluate("""
                (serverIndex) => {
                    const menu = document.querySelector('#server-selector .dropdown-menu').getBoundingClientRect();
                    const preset = document.querySelector("#server-selector .template-preset-add[data-server-index='" + serverIndex + "']");
                    const custom = document.querySelector('#server-selector .custom-url-add');
                    const inside = (el) => { if (!el) return true;
                        const r = el.getBoundingClientRect();
                        return r.left >= menu.left - 1 && r.right <= menu.right + 1; };
                    return inside(preset) && inside(custom);
                }
                """, serverIndex);
    }

    String addButtonsOverflowReport(int serverIndex) {
        return (String) page.evaluate("""
                (serverIndex) => {
                    const menu = document.querySelector('#server-selector .dropdown-menu').getBoundingClientRect();
                    const preset = document.querySelector("#server-selector .template-preset-add[data-server-index='" + serverIndex + "']");
                    const custom = document.querySelector('#server-selector .custom-url-add');
                    const report = (name, el) => { if (!el) return name + ':missing';
                        const r = el.getBoundingClientRect();
                        return name + ': left overflow=' + (menu.left - r.left).toFixed(1)
                            + ' right overflow=' + (r.right - menu.right).toFixed(1); };
                    return report('preset', preset) + '; ' + report('custom', custom)
                        + '; menu=' + menu.left.toFixed(1) + '..' + menu.right.toFixed(1);
                }
                """, serverIndex);
    }

    boolean templateServerHasAddPresetButton(int serverIndex) {
        var selector = "#server-selector .template-preset-add[data-server-index='" + serverIndex + "']";
        return page.locator(selector).count() > 0;
    }

    boolean templatePresetLabelUsesBulmaRadio(int serverIndex, int presetIndex) {
        var presetId = "server-" + serverIndex + "-preset-" + presetIndex;
        return page.locator("#server-selector label.radio[for='" + presetId + "']").count() > 0;
    }

    boolean addPresetButtonUsesBulmaButton(int serverIndex) {
        var selector = "#server-selector .template-preset-add.button[data-server-index='" + serverIndex + "']";
        return page.locator(selector).count() > 0;
    }

    boolean customUrlButtonUsesBulmaButton() {
        return page.locator("#server-selector .custom-url-add.button").count() > 0;
    }

    boolean isCustomUrlButtonVisible() {
        return page.locator("#server-selector .custom-url-add").isVisible();
    }

    boolean isCustomUrlButtonStyledAsLink() {
        var color = page.locator("#server-selector .custom-url-add").evaluate(
                "el => getComputedStyle(el).color");
        var linkColor = page.evaluate(
                "() => getComputedStyle(document.documentElement).getPropertyValue('--bulma-link').trim()");
        // both resolve to the theme's link color (hsl triple); just assert it is not the default border grey
        return color != null && !color.toString().isEmpty() && linkColor != null;
    }

    boolean customUrlRowUsesBulmaRadio() {
        return page.locator("#server-selector .custom-url-row.radio").count() > 0
                || page.locator("#server-selector label.radio.custom-url-row").count() > 0;
    }

    boolean serverRowUsesBulmaMediaContent(int index) {
        return page.locator("#server-selector .server-row .media .media-left input[type='radio']").count() > index
                || page.locator("#server-selector .server-row .media-content").count() > index;
    }

    boolean serverRowUsesBulmaMedia(int index) {
        return page.locator("#server-selector .server-row > .media").count() > index;
    }

    boolean serverRadioUsesBulmaInput(int index) {
        return page.locator("#server-selector .server-row input[type='radio']").nth(index).count() > 0;
    }

    boolean serverDescriptionUsesBulmaHelpers(int index) {
        return page.locator("#server-selector .server-row .server-item-description.help").count() > index;
    }

    void clickTemplateServerAddPreset(int serverIndex) {
        var selector = "#server-selector .template-preset-add[data-server-index='" + serverIndex + "']";
        page.locator(selector).click();
    }

    boolean isPresetFormFirstFieldFocused(int serverIndex) {
        var formSelector = "#server-selector .template-preset-form[data-server-index='" + serverIndex + "']";
        var firstField = page.locator(formSelector + " input, " + formSelector + " select").first();
        return (Boolean) firstField.evaluate("el => el === document.activeElement");
    }

    boolean hasTemplateServerPresetForm(int serverIndex) {
        var selector = "#server-selector .template-preset-form[data-server-index='" + serverIndex + "']";
        return page.locator(selector).count() > 0;
    }

    boolean templatePresetFormHasInput(int serverIndex, String variableName) {
        var formSelector = "#server-selector .template-preset-form[data-server-index='" + serverIndex + "']";
        var inputSelector = formSelector + " [name='" + variableName + "']";
        return page.locator(inputSelector).count() > 0;
    }

    boolean templatePresetFormInputIsSelect(int serverIndex, String variableName) {
        var formSelector = "#server-selector .template-preset-form[data-server-index='" + serverIndex + "']";
        var selectSelector = formSelector + " select[name='" + variableName + "']";
        return page.locator(selectSelector).count() > 0;
    }

    void clickTemplatePresetSave(int serverIndex) {
        var formSelector = "#server-selector .template-preset-form[data-server-index='" + serverIndex + "']";
        var saveButtonSelector = formSelector + " .template-preset-save";
        page.locator(saveButtonSelector).click();
    }

    void clickTemplatePresetCancel(int serverIndex) {
        var formSelector = "#server-selector .template-preset-form[data-server-index='" + serverIndex + "']";
        var cancelButtonSelector = formSelector + " .template-preset-cancel";
        page.locator(cancelButtonSelector).click();
    }

    void setTemplatePresetFormValue(int serverIndex, String variableName, String value) {
        var formSelector = "#server-selector .template-preset-form[data-server-index='" + serverIndex + "']";
        var inputSelector = formSelector + " [name='" + variableName + "']";
        var input = page.locator(inputSelector);
        if (input.evaluate("el => el.tagName").toString().equalsIgnoreCase("SELECT")) {
            input.selectOption(value);
        } else {
            input.fill(value);
        }
    }

    void selectTemplatePreset(int serverIndex, int presetIndex) {
        var presetId = "server-" + serverIndex + "-preset-" + presetIndex;
        page.locator("#server-selector input[id='" + presetId + "']").click();
    }

    void deleteTemplatePreset(int serverIndex, int presetIndex) {
        var presetId = "server-" + serverIndex + "-preset-" + presetIndex;
        var label = page.locator("#server-selector label[for='" + presetId + "']");
        var deleteBtn = label.locator(".delete");
        deleteBtn.click();
    }

    boolean hasPresetDeleteButton(int serverIndex, int presetIndex) {
        var presetId = "server-" + serverIndex + "-preset-" + presetIndex;
        var label = page.locator("#server-selector label[for='" + presetId + "']");
        return label.locator(".delete").count() > 0;
    }

    boolean presetDeleteButtonUsesBulmaDelete(int serverIndex, int presetIndex) {
        var presetId = "server-" + serverIndex + "-preset-" + presetIndex;
        var label = page.locator("#server-selector label[for='" + presetId + "']");
        return label.locator(".delete.is-small").count() > 0;
    }

    boolean hasCustomUrlButton() {
        return page.locator("#server-selector .custom-url-add").count() > 0;
    }

    void clickCustomUrlButton() {
        page.locator("#server-selector .custom-url-add").click();
    }

    int customUrlRowCount() {
        return page.locator("#server-selector .custom-url-row").count();
    }

    boolean hasCustomUrlInput() {
        return page.locator("#server-selector .custom-url-input").count() > 0;
    }

    boolean isCustomUrlRowVisible(int index) {
        return page.locator("#server-selector .custom-url-row").nth(index).isVisible();
    }

    boolean isCustomUrlInputVisible(int index) {
        return page.locator("#server-selector .custom-url-row").nth(index)
                .locator(".custom-url-input").isVisible();
    }

    String customUrlInputValue(int index) {
        return page.locator("#server-selector .custom-url-row").nth(index)
                .locator(".custom-url-input").inputValue();
    }

    boolean hasCustomUrlDeleteButton() {
        return page.locator("#server-selector .custom-url-row .delete").count() > 0;
    }

    boolean customUrlDeleteButtonUsesBulmaDelete() {
        return page.locator("#server-selector .custom-url-row .delete.is-small").count() > 0;
    }

    void clickCustomUrlDelete(int index) {
        page.locator("#server-selector .custom-url-row .delete").nth(index).click();
    }

    void focusCustomUrlInput(int index) {
        page.locator("#server-selector .custom-url-input").nth(index).focus();
    }

    void setCustomUrlValue(int index, String url) {
        page.locator("#server-selector .custom-url-input").nth(index).fill(url);
    }

    String getCustomUrlValue(int index) {
        return page.locator("#server-selector .custom-url-input").nth(index).inputValue();
    }

    void selectCustomUrl(int index) {
        page.locator("#server-selector .custom-url-row input[type='radio']").nth(index).click();
    }

    boolean isCustomUrlSelected(int index) {
        return page.locator("#server-selector .custom-url-row input[type='radio']").nth(index).isChecked();
    }

    String serverTriggerUrl() {
        return page.locator("#server-selector .server-dropdown-trigger .server-url").textContent().trim();
    }

    String getLocalStorageItem(String key) {
        return (String) page.evaluate("key => localStorage.getItem(key)", key);
    }

    void setLocalStorageItem(String key, String value) {
        page.evaluate("({key, value}) => localStorage.setItem(key, value)", Map.of("key", key, "value", value));
    }

    void reload() {
        page.reload();
    }

    String getBaseUrl() {
        return page.locator("[data-toggle='mode']").getAttribute("data-base-url");
    }

    void selectServer(int index) {
        page.locator("#server-selector input[type='radio'][name='server']").nth(index).click();
        page.waitForTimeout(100); // Wait for JavaScript updateAuthFieldVisibility to execute
        // WebKit doesn't move focus to a radio on click the way Chromium does; the app's
        // ArrowUp/Down handler is bound to the focused element, so focus the checked radio.
        page.evaluate("() => { var r = document.querySelector('#server-selector input[type=radio]:checked'); if (r) r.focus(); }");
    }

    boolean isBrowserHandledAuthFieldVisible(String schemeName) {
        var selector = "#detail .field[data-browser-handled='true']";
        var fields = page.locator(selector).all();
        for (var field : fields) {
            var text = field.textContent();
            if (text != null && text.contains(schemeName)) {
                var style = field.getAttribute("style");
                return style == null || !style.contains("display: none");
            }
        }
        return false;
    }

    boolean isTokenAuthFieldVisible(String schemeName) {
        var selector = "#detail .field:not([data-browser-handled])";
        var fields = page.locator(selector).all();
        for (var field : fields) {
            var text = field.textContent();
            if (text != null && text.contains(schemeName)) {
                return true;
            }
        }
        return false;
    }

    boolean hasElement(String selector) {
        return page.locator(selector).count() > 0;
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

    private static final double ALIGNMENT_TOLERANCE = 20;
    private static final double SINGLE_ROW_MAX_HEIGHT = 30;

    boolean areMethodAddonsRightAligned() {
        var label = page.locator(".tree-label:has(.tags.has-addons)").first();
        var labelBox = label.boundingBox();
        var group = label.locator(".tags.has-addons").first();
        var groupBox = group.boundingBox();
        if (labelBox == null || groupBox == null) return false;
        var labelRight = labelBox.x + labelBox.width;
        var groupRight = groupBox.x + groupBox.width;
        return Math.abs(labelRight - groupRight) < ALIGNMENT_TOLERANCE;
    }

    boolean areMethodAddonsInSingleRow() {
        var groups = page.locator(".tree-label .tags.has-addons");
        for (int i = 0; i < groups.count(); i++) {
            var group = groups.nth(i);
            var groupBox = group.boundingBox();
            if (groupBox == null) return false;
            if (groupBox.height > SINGLE_ROW_MAX_HEIGHT) return false;
        }
        return true;
    }

    boolean descToggleFocusRingFullyVisible() {
        return (Boolean) page.evaluate(
                "() => { var t = document.querySelector('#detail .desc-toggle');"
                + " if (!t || t !== document.activeElement) return false;"
                + " var cs = getComputedStyle(t);"
                + " if (cs.outlineStyle === 'none') return false;"
                + " var off = parseFloat(cs.outlineOffset) || 0;"
                + " var r = t.getBoundingClientRect(); var w = t.parentElement.getBoundingClientRect();"
                + " return r.left - off >= w.left && r.right + off <= w.right"
                + " && r.top - off >= w.top && r.bottom + off <= w.bottom; }");
    }

    boolean isServerSelectorFocused() {
        return (Boolean) page.evaluate(
                "() => document.activeElement === document.querySelector('.server-dropdown-trigger')"
                + " || (document.activeElement && document.activeElement.closest('#server-selector') !== null)");
    }

    boolean activeElementIsInHeader() {
        return (Boolean) page.evaluate(
                "() => { var h = document.querySelector('.detail-header');"
                + " return !!(h && document.activeElement && h.contains(document.activeElement)); }");
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

    String focusedInputName() {
        return (String) page.evaluate("() => document.activeElement.getAttribute('name') || ''");
    }

    String activeElementSelector() {
        return (String) page.evaluate("""
                () => {
                    var el = document.activeElement;
                    if (!el) return '';
                    var s = el.tagName.toLowerCase();
                    if (el.classList.length > 0) s += '.' + Array.from(el.classList).join('.');
                    if (el.getAttribute('type') === 'submit') s += '[type=submit]';
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

    void clickButton(String text) {
        page.locator("#detail button:text('" + text + "')").click();
    }

    int customHeaderRowCount() {
        return page.locator("#detail .custom-header-row").count();
    }

    void focusAddCustomHeaderButton() {
        page.locator("#detail .custom-header-add").focus();
    }

    void fillCustomHeader(int index, String name, String value) {
        page.locator("#detail .custom-header-row").nth(index).locator(".custom-header-name").fill(name);
        page.locator("#detail .custom-header-row").nth(index).locator(".custom-header-value").fill(value);
    }

    void fillCustomHeaderName(int index, String name) {
        page.locator("#detail .custom-header-row").nth(index).locator(".custom-header-name").fill(name);
    }

    void removeCustomHeader(int index) {
        page.locator("#detail .custom-header-row").nth(index).locator(".custom-header-remove").click();
    }

    void clearClipboard() {
        page.evaluate("() => { window.__clip = ''; return navigator.clipboard.writeText(''); }");
    }

    String readClipboard() {return (String) page.evaluate("() => navigator.clipboard.readText()");}


    boolean isViewActive(String view) {
        return page.locator("[data-toggle-value='" + view + "'].is-active").count() == 1;
    }

    void clickViewButton(String view) {page.locator("[data-toggle-value='" + view + "']").click();}

    boolean isViewToggleFocused() {
        return (Boolean) page.evaluate("() => document.activeElement.matches('[data-toggle=view]')");
    }

    void focusViewToggle() {page.locator("[data-toggle='view']").focus();}

    void waitForViewToggleFocused() {
        page.waitForFunction("() => document.activeElement.matches('[data-toggle=view]')");
    }

    void waitForModeToggleFocused() {
        page.waitForFunction("() => document.activeElement.matches('[data-toggle=mode]')");
    }

    void waitForTreeContent(String text) {
        lastWaitContext = "waitForTreeContent(\"" + text + "\")";
        page.waitForSelector("#tree-container :text('" + text + "')");
    }

    // Tag filter methods
    boolean hasFilterIcon() {
        return page.locator("#tree-container .fa-filter").count() > 0;
    }

    boolean filterToggleHasFocusRing() {
        return (Boolean) page.evaluate(
                "() => { var el = document.querySelector('.filter-pill-panel');"
                + " return el && el === document.activeElement && getComputedStyle(el).outlineStyle !== 'none'; }");
    }

    boolean viewToggleHasFocusRing() {
        return (Boolean) page.evaluate(
                "() => { var el = document.querySelector('[data-toggle=view]');"
                + " return el && el === document.activeElement && getComputedStyle(el).outlineStyle !== 'none'; }");
    }

    void focusFilterToggle() {
        page.locator(".filter-pill-panel").focus();
    }

    boolean isFilterToggleFocused() {
        return page.locator(".filter-pill-panel").evaluate("el => el === document.activeElement").toString().equals("true");
    }

    boolean isPillActive(String tagName) {
        var pill = page.locator(".filter-pill-panel [data-toggle-value='" + sanitizeTagName(tagName) + "']");
        return pill.evaluate("el => el.classList.contains('is-active')").toString().equals("true");
    }

    boolean isFilterPanelVisible() {
        return page.locator(".filter-pill-panel").isVisible();
    }

    boolean filterPillsWrap() {
        return (Boolean) page.evaluate(
                "() => { var p = document.querySelector('.filter-pill-panel'); if (!p) return false;"
                + " var cs = getComputedStyle(p);"
                + " if (cs.flexWrap !== 'wrap') return false;"
                + " var box = document.querySelector('.split-first > .box');"
                + " var pr = p.getBoundingClientRect(); var br = box.getBoundingClientRect();"
                + " return pr.right <= br.right + 1; }");
    }

    void clickPill(String tagName) {
        page.locator(".filter-pill-panel [data-toggle-value='" + sanitizeTagName(tagName) + "']").click();
    }

    boolean isFilterActive(String tagName) {
        var tree = page.locator("[role=\"tree\"]");
        return tree.evaluate("el => el.classList.contains('filter-" + sanitizeTagName(tagName) + "')").toString().equals("true");
    }

    String getFilterStatusLine() {
        var statusLine = page.locator(".filter-status-line");
        if (statusLine.count() == 0) return null;
        var text = statusLine.textContent();
        return text.isEmpty() ? null : text;
    }

    private String sanitizeTagName(String tag) {
        return tag.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    void setViewportSize(int width, int height) {
        page.setViewportSize(width, height);
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
    }

    void waitMs(double ms) {page.waitForTimeout(ms);}

    void screenshot(String name) {
        publishScreenshot(name, currentContext);
        page.emulateMedia(new Page.EmulateMediaOptions().setColorScheme(DARK));
        publishScreenshot(name + "-dark", currentContext);
        page.emulateMedia(new Page.EmulateMediaOptions().setColorScheme(LIGHT));
    }

    boolean isServerSelectorVisibleWhenDescriptionExpanded() {
        clickApiDescriptionSummary();
        return isServerSelectorOnTitleRowExpanded();
    }

    String headerGeometryReport() {
        return (String) page.evaluate("() => {"
                + " const r = (s) => { const el = document.querySelector(s);"
                + " if (!el) return s + ':missing'; const b = el.getBoundingClientRect();"
                + " return s + '=' + b.left.toFixed(0) + '..' + b.right.toFixed(0) + ' w=' + b.width.toFixed(0); };"
                + " return [r('.detail-header'), r('.detail-header .level-left'), r('.detail-header .level-left > .level-item'),"
                + " r('.detail-header .api-title-block'), r('.api-title-row .title'), r('.detail-header .level-right'),"
                + " r('.detail-header #server-selector')].join(' | ');"
                + " }");
    }

    boolean isTitleOnOneLine() {
        return (Boolean) page.evaluate("() => {"
                + " const t = document.querySelector('.api-title-row .title');"
                + " const cs = getComputedStyle(t);"
                + " const h = t.getBoundingClientRect().height;"
                + " return h < 60 && cs.whiteSpace === 'nowrap';"
                + " }");
    }

    boolean isTitleFullyVisibleAtWideViewport() {
        // at 2384px the full title must fit without truncation
        return (Boolean) page.evaluate("() => {"
                + " const t = document.querySelector('.api-title-row .title');"
                + " return t.scrollWidth <= t.clientWidth + 1;"
                + " }");
    }

    String titleRowReport() {
        return (String) page.evaluate("() => {"
                + " const t = document.querySelector('.api-title-row .title').getBoundingClientRect();"
                + " const items = [...document.querySelectorAll('.api-title-row > *')].map(el => {"
                + " const r = el.getBoundingClientRect();"
                + " return el.className + ':top=' + r.top.toFixed(1) + ',bottom=' + r.bottom.toFixed(1); }).join(' | ');"
                + " return 'title top=' + t.top.toFixed(1) + ' bottom=' + t.bottom.toFixed(1) + ' || ' + items;"
                + " }");
    }

    boolean isTitleRowOnOneLine() {
        return (Boolean) page.evaluate("() => {"
                + " const t = document.querySelector('.api-title-row .title').getBoundingClientRect();"
                + " const items = [...document.querySelectorAll('.api-title-row > *')].map(el => el.getBoundingClientRect());"
                + " return items.every(r => r.bottom <= t.bottom + 2 && r.top >= t.top - 2);"
                + " }");
    }

    boolean isTitleFullyVisible() {
        return (Boolean) page.evaluate("() => {"
                + " const t = document.querySelector('.api-title-row .title');"
                + " const h1 = t.tagName === 'H1' ? t : t.querySelector('h1') || t;"
                + " const row = document.querySelector('.api-title-row').getBoundingClientRect();"
                + " const tr = h1.getBoundingClientRect();"
                + " return tr.width >= h1.scrollWidth - 1 && tr.right <= row.right + 1;"
                + " }");
    }

    String titleGeometryReport() {
        return (String) page.evaluate("() => {"
                + " const t = document.querySelector('.api-title-row .title');"
                + " const row = document.querySelector('.api-title-row');"
                + " const b = t.getBoundingClientRect();"
                + " const chain = ['.detail-header', '.detail-header .level-left', '.detail-header .level-left > .level-item',"
                + " '.api-title-block', '.api-title-row', '.api-title-row > .level-left', '.api-title-row > .level-left > .level-item',"
                + " '.api-title-row .title'].map(s => { const el = document.querySelector(s); if (!el) return s + ':missing';"
                + " const r = el.getBoundingClientRect(); const cs = getComputedStyle(el);"
                + " return s.split(' ').pop() + ':w=' + r.width.toFixed(0) + ',flex=' + cs.flex + ',minW=' + cs.minWidth; }).join(' | ');"
                + " return 'title w=' + b.width.toFixed(0) + ' scrollW=' + t.scrollWidth"
                + " + ' rowW=' + row.getBoundingClientRect().width.toFixed(0)"
                + " + ' titleText=' + JSON.stringify(t.textContent) + ' || ' + chain;"
                + " }");
    }

    boolean isServerSelectorOnTitleRowExpanded() {
        return (Boolean) page.evaluate("() => {"
                + " const header = document.querySelector('.detail-header').getBoundingClientRect();"
                + " const r = document.querySelector('.detail-header #server-selector').getBoundingClientRect();"
                + " const titleBlock = document.querySelector('.detail-header .api-title-block').getBoundingClientRect();"
                + " const descText = document.querySelector('details.api-description .api-description-text');"
                + " const descRight = descText ? descText.getBoundingClientRect().right : 0;"
                + " return r.right <= window.innerWidth + 0.5 && r.left >= 0 && r.width > 0"
                + " && r.top < header.bottom && titleBlock.right <= r.left + 1"
                + " && descRight <= r.left + 1;"
                + " }");
    }

    String serverRowReport() {
        return (String) page.evaluate("() => {"
                + " const header = document.querySelector('.detail-header').getBoundingClientRect();"
                + " const r = document.querySelector('.detail-header #server-selector').getBoundingClientRect();"
                + " const tb = document.querySelector('.detail-header .api-title-block').getBoundingClientRect();"
                + " const dt = document.querySelector('details.api-description .api-description-text');"
                + " const dr = dt ? dt.getBoundingClientRect().right : 0;"
                + " return 'headerBottom=' + header.bottom.toFixed(1) + ' serverTop=' + r.top.toFixed(1)"
                + " + ' serverLeft=' + r.left.toFixed(1) + ' titleBlockRight=' + tb.right.toFixed(1)"
                + " + ' descRight=' + dr.toFixed(1) + ' innerW=' + window.innerWidth"
                + " + ' cond=' + (r.right <= window.innerWidth + 0.5) + (r.top < header.bottom)"
                + " + (tb.right <= r.left + 1) + (dr <= r.left + 1);"
                + " }");
    }

    String baselineReport() {
        return (String) page.evaluate("() => {"
                + " const t = document.querySelector('.api-title-row .title').getBoundingClientRect();"
                + " const v = document.querySelector('.api-title-row .api-version').getBoundingClientRect();"
                + " return 'titleCenter=' + ((t.top + t.bottom) / 2).toFixed(1)"
                + " + ' badgeCenter=' + ((v.top + v.bottom) / 2).toFixed(1)"
                + " + ' diff=' + (((v.top + v.bottom) / 2) - ((t.top + t.bottom) / 2)).toFixed(1);"
                + " }");
    }

    boolean isVersionBadgeCenterAlignedWithTitle() {
        return (Boolean) page.evaluate("() => {"
                + " const t = document.querySelector('.api-title-row .title').getBoundingClientRect();"
                + " const v = document.querySelector('.api-title-row .api-version').getBoundingClientRect();"
                + " return Math.abs(((v.top + v.bottom) / 2) - ((t.top + t.bottom) / 2)) < 2;"
                + " }");
    }

    boolean isVersionBadgeImmediatelyRightOfTitle() {
        return (Boolean) page.evaluate("() => {"
                + " const t = document.querySelector('.api-title-row .title').getBoundingClientRect();"
                + " const v = document.querySelector('.api-title-row .api-version-addon').getBoundingClientRect();"
                + " const gap = v.left - t.right;"
                + " return gap >= 0 && gap < 20;"
                + " }");
    }

    String versionGapReport() {
        return (String) page.evaluate("() => {"
                + " const t = document.querySelector('.api-title-row .title').getBoundingClientRect();"
                + " const v = document.querySelector('.api-title-row .api-version').getBoundingClientRect();"
                + " const row = document.querySelector('.api-title-row').getBoundingClientRect();"
                + " const range = document.createRange(); range.selectNodeContents(document.querySelector('.api-title-row .title'));"
                + " const textRight = range.getBoundingClientRect().right;"
                + " return 'titleBoxRight=' + t.right.toFixed(1) + ' textRight=' + textRight.toFixed(1)"
                + " + ' badgeLeft=' + v.left.toFixed(1) + ' gap=' + (v.left - textRight).toFixed(1)"
                + " + ' rowRight=' + row.right.toFixed(1);"
                + " }");
    }

    String versionBadgeGeometryReport() {
        return (String) page.evaluate("() => {"
                + " const t = document.querySelector('.api-title-row .title').getBoundingClientRect();"
                + " const v = document.querySelector('.api-title-row .api-version').getBoundingClientRect();"
                + " return 'title bottom=' + t.bottom.toFixed(1) + ' badge bottom=' + v.bottom.toFixed(1)"
                + " + ' diff=' + (v.bottom - t.bottom).toFixed(1) + ' badge top=' + v.top.toFixed(1);"
                + " }");
    }

    boolean isServerSelectorBottomAlignedWithTitleBlock() {
        return (Boolean) page.evaluate("() => {"
                + " const title = document.querySelector('.detail-header .api-title-block').getBoundingClientRect();"
                + " const server = document.querySelector('.detail-header #server-selector').getBoundingClientRect();"
                + " return server.top < title.bottom - 1 && Math.abs(server.bottom - title.bottom) < 2;"
                + " }");
    }

    boolean isApiVersionBadgeVisible() {return page.locator(".detail-header .api-version").isVisible();}

    String apiVersionBadgeText() {return page.locator(".detail-header .api-version").textContent().trim();}

    String apiSpecVersionBadgeText() {return page.locator(".detail-header .api-spec-version").textContent().trim();}

    boolean areVersionBadgesFullyPainted() {
        return (Boolean) page.evaluate("() => {"
                + " const row = document.querySelector('.api-title-row').getBoundingClientRect();"
                + " const badges = [...document.querySelectorAll('.api-title-row .api-version, .api-title-row .api-spec-version')]"
                + " .map(el => el.getBoundingClientRect());"
                + " return badges.length === 2 && badges.every(b => b.bottom <= row.bottom + 0.5 && b.top >= row.top - 0.5);"
                + " }");
    }

    String versionBadgeClipReport() {
        return (String) page.evaluate("() => {"
                + " const row = document.querySelector('.api-title-row').getBoundingClientRect();"
                + " return [...document.querySelectorAll('.api-title-row .api-version, .api-title-row .api-spec-version')]"
                + " .map(el => { const b = el.getBoundingClientRect();"
                + " return el.className + ' top=' + b.top.toFixed(1) + ' bottom=' + b.bottom.toFixed(1); })"
                + " .join(' | ') + ' || row top=' + row.top.toFixed(1) + ' bottom=' + row.bottom.toFixed(1);"
                + " }");
    }

    boolean isApiSpecVersionBadgeNextToVersionBadge() {
        return (Boolean) page.evaluate("() => {"
                + " const v = document.querySelector('.api-title-row .api-version').getBoundingClientRect();"
                + " const s = document.querySelector('.api-title-row .api-spec-version').getBoundingClientRect();"
                + " const gap = s.left - v.right;"
                + " return gap >= -1 && gap < 20 && s.left >= v.right - 1;" // has-addons joins them seamlessly (gap 0)
                + " }");
    }

    String apiDescriptionSummaryText() {return page.locator("details.api-description summary").textContent().trim();}

    int countApiSummaryOccurrences() {
        return (int) page.evaluate("() => document.querySelector('.api-title-block').textContent.split('Short API summary').length - 1");
    }

    boolean isApiDescriptionExpanded() {
        return (Boolean) page.evaluate("() => document.querySelector('details.api-description')?.open === true");
    }

    boolean isApiDescriptionTextVisible() {return page.locator("details.api-description .api-description-text").isVisible();}

    String apiDescriptionText() {return page.locator("details.api-description .api-description-text").textContent();}

    void clickApiDescriptionSummary() {page.locator("details.api-description summary").click();}

    void focusApiDescriptionSummary() {page.locator("details.api-description summary").focus();}

    boolean apiDescriptionSummaryHasFocusRing() {
        return (Boolean) page.evaluate(
                "() => { var el = document.querySelector('details.api-description summary');"
                + " return el && el === document.activeElement && getComputedStyle(el).outlineStyle !== 'none'; }");
    }

    boolean apiDescriptionSummaryFocusRingWithinHeader() {
        return (Boolean) page.evaluate(
                "() => { var el = document.querySelector('details.api-description summary');"
                + " if (!el || el !== document.activeElement) return false;"
                + " var cs = getComputedStyle(el);"
                + " var off = parseFloat(cs.outlineOffset) || 0;"
                + " var r = el.getBoundingClientRect(); var h = document.querySelector('.detail-header').getBoundingClientRect();"
                + " return r.left - off >= h.left && r.right + off <= h.right"
                + " && r.top - off >= h.top && r.bottom + off <= h.bottom; }");
    }

    String locationHash() {return (String) page.evaluate("() => location.hash");}

    void goBack() {page.goBack();}

    void navigateTo(String hash) {page.navigate(testServer.baseUrl() + "/index.html" + hash);}

    void toggleParamPersist(String paramName) {
        page.locator("#detail .field:has([name='" + paramName + "']) .persist-toggle").click();
    }

    boolean isParamPersisted(String paramName) {
        return "true".equals(page.locator("#detail .field:has([name='" + paramName + "']) .persist-toggle").getAttribute("aria-pressed"));
    }

    /// Navigate to a hash route by reloading the page with the hash in the URL.
    /// This avoids a race between the initial auto-load and hash navigation.
    void navigateToHash(String hash) {
        lastWaitContext = "navigateToHash(\"" + hash + "\")";
        page.navigate(testServer.baseUrl() + "/index.html#" + hash);
    }

    /// Navigate to a hash route in-page without reloading (preserves page state like filters).
    void navigateToHashInPage(String hash) {
        lastWaitContext = "navigateToHashInPage(\"" + hash + "\")";
        page.waitForSelector("[hx-get]");
        page.evaluate("location.hash = '#" + hash + "'");
        page.evaluate("window.dispatchEvent(new PopStateEvent('popstate'))");
    }

    String inputValue(String name) {
        return page.locator("#detail input[name='" + name + "']").inputValue();
    }

    void waitForInputValue(String name, String expectedValue) {
        var locator = page.locator("#detail input[name='" + name + "']");
        locator.waitFor();
        assertThat(locator).hasValue(expectedValue);
    }

    String inputPlaceholder(String name) {
        return page.locator("#detail input[name='" + name + "']").getAttribute("placeholder");
    }

    void toggleCustomHeaderPersist(int index) {
        page.locator("#detail .custom-header-row").nth(index).locator(".persist-toggle").click();
    }

    String customHeaderName(int index) {
        return page.locator("#detail .custom-header-row").nth(index).locator(".custom-header-name").inputValue();
    }

    String customHeaderValue(int index) {
        return page.locator("#detail .custom-header-row").nth(index).locator(".custom-header-value").inputValue();
    }

    boolean customHeaderHasBadge(int index, String text) {
        return page.locator("#detail .custom-header-row").nth(index).locator(".tag >> text='" + text + "'").count() > 0;
    }

    void clickCustomHeaderTagDelete(int index) {
        page.locator("#detail .custom-header-row").nth(index).locator(".custom-header-remove").click();
    }

    boolean isCustomHeaderDeleteFocusable(int index) {
        var tabindex = page.locator("#detail .custom-header-row").nth(index).locator(".custom-header-remove").getAttribute("tabindex");
        return "0".equals(tabindex);
    }

    void focusCustomHeaderDelete(int index) {
        page.locator("#detail .custom-header-row").nth(index).locator(".custom-header-remove").focus();
    }

    double customHeaderNameWidth(int index) {
        var result = page.locator("#detail .custom-header-row").nth(index).locator(".custom-header-name")
                .evaluate("el => el.getBoundingClientRect().width");
        return ((Number) result).doubleValue();
    }

    void clickCustomHeaderGlobeToggle(int index) {
        page.locator("#detail .custom-header-row").nth(index).locator(".globe-toggle").click();
    }

    void focusCustomHeaderName(int index) {
        page.locator("#detail .custom-header-row").nth(index).locator(".custom-header-name").focus();
    }

    void focusCustomHeaderValue(int index) {
        page.locator("#detail .custom-header-row").nth(index).locator(".custom-header-value").focus();
    }

    void focusCustomHeaderGlobeToggle(int index) {
        page.locator("#detail .custom-header-row").nth(index).locator(".globe-toggle").focus();
    }

    boolean isCustomHeaderGlobeActive(int index) {
        // The toggle is a <span>, not a real button; a native Enter "click" may move focus
        // instead of activating. Assert on the state attribute, which is what toggleGlobe sets.
        return (Boolean) page.evaluate(
                "(i) => { var g = document.querySelectorAll('#detail .custom-header-row .globe-toggle')[i];"
                        + " return g && g.getAttribute('data-global') === 'true'; }", index);
    }

    boolean customHeaderGlobeUsesFontAwesomeIcon(int index) {
        var globe = page.locator("#detail .custom-header-row").nth(index).locator(".globe-toggle");
        return globe.locator(".icon i.fa-globe").count() > 0;
    }

    void fillCustomHeaderValue(int index, String value) {
        page.locator("#detail .custom-header-row").nth(index).locator(".custom-header-value").fill(value);
    }

    boolean isErrorBannerVisible() {
        return page.locator("#error-banner").isVisible();
    }

    void waitForErrorBanner() {
        lastWaitContext = "waitForErrorBanner()";
        page.waitForSelector("#error-banner:not([style*='display: none'])");
    }

    void waitForErrorBannerGone() {
        lastWaitContext = "waitForErrorBannerGone()";
        page.waitForSelector("#error-banner", new Page.WaitForSelectorOptions()
                .setState(HIDDEN));
    }

    private String getFullTestName(ExtensionContext context) {
        var className = context.getTestClass().map(Class::getSimpleName).orElse("UnknownClass");
        var methodName = context.getTestMethod().map(Method::getName).orElse("unknownMethod");
        return className + "-" + methodName;
    }

    private void publishScreenshot(String name, ExtensionContext context) {
        context.publishReportEntry("create screenshot: " + name);
        context.publishFile(name + ".png", IMAGE_PNG, this::screenshot);
    }

    private void screenshot(Path path) {
        page.screenshot(new ScreenshotOptions().setPath(path).setFullPage(true));
        if (path.getFileName().toString().startsWith("failure-")) {
            cleanOldFailureScreenshots(path.getParent());
        }
    }

    /// Delete failure screenshots older than 24 hours.
    private static void cleanOldFailureScreenshots(Path dir) {
        var cutoff = Instant.now().minus(Duration.ofHours(24));
        try (var files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().startsWith("failure-"))
                    .filter(p -> {
                        try { return Files.getLastModifiedTime(p).toInstant().isBefore(cutoff); }
                        catch (IOException e) { return false; }
                    })
                    .forEach(p -> {
                        try { Files.delete(p); }
                        catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }

    private static class TestServer {
        private final HttpServer server;
        private final Path outputDir;
        private volatile boolean blockFragments;

        TestServer(String specFilename) throws Exception {
            outputDir = Files.createTempDirectory("openapi-ui-test");
            var specPath = Optional.ofNullable(getClass().getResource("/" + specFilename))
                    .map(URL::toString).map(URI::create).map(Path::of)
                    .orElseThrow(() -> new RuntimeException("spec not found: " + specFilename));
            new OpenApiUiFileGenerator(specPath, outputDir).generate();

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
        }

        void overrideBaseUrl(String originalBaseUrl) throws IOException {
            var indexPath = outputDir.resolve("index.html");
            var html = Files.readString(indexPath);
            html = html.replace(originalBaseUrl, baseUrl() + "/api");
            Files.writeString(indexPath, html);
        }

        void stop() {
            server.stop(0);
            deleteRecursively(outputDir);
        }

        void resetMocks() {
            blockFragments = false;
            try {server.removeContext("/pets");} catch (IllegalArgumentException ignored) {}
        }

        String baseUrl() {return "http://localhost:" + server.getAddress().getPort();}

        void mockEndpoint(String path, String contentType, String body) {
            replaceContext("/api" + path, exchange -> sendResponse(exchange, 200, contentType, body));
        }

        void mockEndpoint(String path, String expectedMethod, String contentType, String body) {
            replaceContext("/api" + path, exchange -> {
                if (!exchange.getRequestMethod().equalsIgnoreCase(expectedMethod)) {
                    exchange.sendResponseHeaders(405, 0);
                    exchange.close();
                    return;
                }
                sendResponse(exchange, 200, contentType, body);
            });
        }

        void mockEndpoint(String path, String contentType, String body, int statusCode) {
            replaceContext("/api" + path, exchange -> sendResponse(exchange, statusCode, contentType, body));
        }

        void mockEndpointWithHeaders(String path, String contentType, String body, Map<String, String> responseHeaders) {
            replaceContext("/api" + path, exchange -> {
                for (var entry : responseHeaders.entrySet())
                    exchange.getResponseHeaders().set(entry.getKey(), entry.getValue());
                sendResponse(exchange, 200, contentType, body);
            });
        }

        void mockEndpointWithBodyEcho(String path, String expectedMethod) {
            replaceContext("/api" + path, exchange -> {
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
                var requestBody = new String(exchange.getRequestBody().readAllBytes());
                sendResponse(exchange, 200, "application/json", requestBody);
            });
        }

        void mockEndpointWithContentNegotiation(String path, Map<String, String> responsesByAccept) {
            replaceContext("/api" + path, exchange -> {
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Accept");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                    return;
                }
                var accept = exchange.getRequestHeaders().getFirst("Accept");
                for (var entry : responsesByAccept.entrySet()) {
                    if (accept != null && accept.contains(entry.getKey())) {
                        sendResponse(exchange, 200, entry.getKey(), entry.getValue());
                        return;
                    }
                }
                var first = responsesByAccept.entrySet().iterator().next();
                sendResponse(exchange, 200, first.getKey(), first.getValue());
            });
        }

        void mockRootEndpoint(String path, String contentType, String body) {
            replaceContext(path, exchange -> sendResponse(exchange, 200, contentType, body));
        }

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

        private void replaceContext(String path, HttpHandler handler) {
            try {server.removeContext(path);} catch (IllegalArgumentException ignored) {}
            server.createContext(path, exchange -> {
                // serve static fragment files (e.g. response fragments) from outputDir
                var uriPath = exchange.getRequestURI().getPath();
                if (uriPath.endsWith(".html")) {
                    var file = outputDir.resolve(uriPath.substring(1));
                    if (Files.exists(file) && Files.isRegularFile(file)) {
                        var bytes = Files.readAllBytes(file);
                        exchange.getResponseHeaders().set("Content-Type", "text/html");
                        exchange.sendResponseHeaders(200, bytes.length);
                        exchange.getResponseBody().write(bytes);
                        exchange.close();
                        return;
                    }
                }
                handler.handle(exchange);
            });
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String contentType, String body) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            var bytes = body.getBytes();
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        private static void deleteRecursively(Path path) {
            try (var walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {Files.delete(p);} catch (Exception e) {throw new RuntimeException(e);}
                        });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    boolean hasFormField(String name) {
        return page.locator("#detail input[name='" + name + "'], #detail select[name='" + name + "']").count() > 0;
    }

    boolean hasRequestBodyTextarea() {
        return page.locator("#detail textarea[data-request-body]").count() > 0;
    }
}
