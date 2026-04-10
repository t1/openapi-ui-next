package com.github.t1.openapi.ui.generator;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator.FilterOptions;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.ScreenshotOptions;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ColorScheme;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.microsoft.playwright.options.WaitForSelectorState.HIDDEN;
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
            var browserType = System.getProperty("playwright.browser", "chromium");
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
        var options = new Browser.NewContextOptions();
        if (!browser.browserType().name().equals("webkit")) {
            options.setPermissions(List.of("clipboard-read", "clipboard-write"));
        }
        context = browser.newContext(options);
        page = context.newPage();
        navigateHome();
    }

    @Override public void afterEach(@NonNull ExtensionContext extensionContext) {
        testServer.resetMocks();
        context.close();
    }

    void navigateHome() {page.navigate(testServer.baseUrl());}

    void mockEndpoint(String path, String contentType, String body) {testServer.mockEndpoint(path, contentType, body);}

    void mockEndpoint(String path, String expectedMethod, String contentType, String body) {testServer.mockEndpoint(path, expectedMethod, contentType, body);}

    void mockEndpoint(String path, String contentType, String body, int statusCode) {testServer.mockEndpoint(path, contentType, body, statusCode);}

    void mockEndpointWithHeaders(String path, String contentType, String body, Map<String, String> responseHeaders) {testServer.mockEndpointWithHeaders(path, contentType, body, responseHeaders);}

    void mockEndpointWithBodyEcho(String path, String expectedMethod) {testServer.mockEndpointWithBodyEcho(path, expectedMethod);}

    void mockEndpointWithContentNegotiation(String path, Map<String, String> responsesByAccept) {testServer.mockEndpointWithContentNegotiation(path, responsesByAccept);}

    void mockRootEndpoint(String path, String contentType, String body) {testServer.mockRootEndpoint(path, contentType, body);}

    void blockHtmxRequests() {testServer.blockHtmxRequests();}

    void unblockHtmxRequests() {testServer.unblockHtmxRequests();}

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

    void clickMethodBadge(String hxGetPath, String method) {
        page.locator("[hx-get='" + hxGetPath + "'] .tag:text('" + method + "')").click();
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

    boolean isModeButtonVisible(String mode) {return page.locator("[data-toggle-value='" + mode.toLowerCase() + "']").isVisible();}

    String modeButtonTooltip(String mode) {return page.locator("[data-toggle-value='" + mode.toLowerCase() + "']").getAttribute("title");}

    void focusModeToggle() {page.locator("[data-toggle='mode']").focus();}

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

    void fillInput(String name, String value) {page.locator("#detail input[name='" + name + "']").fill(value);}

    boolean isInputDisabled(String name) {return page.locator("#detail input[name='" + name + "']").isDisabled();}

    void checkCheckbox(String name) {page.locator("#detail input[type='checkbox'][name='" + name + "']").check();}

    boolean isCheckboxChecked(String name) {return page.locator("#detail input[type='checkbox'][name='" + name + "']").isChecked();}

    void focusInput(String name) {page.locator("#detail input[name='" + name + "']").focus();}

    void focusSelect(String name) {page.locator("#detail select[name='" + name + "']").focus();}

    void selectOption(String name, String value) {page.locator("#detail select[name='" + name + "']").selectOption(value);}

    String selectValue(String name) {return page.locator("#detail select[name='" + name + "']").inputValue();}

    int pinIconRotationAfterTransition(String name) {
        // wait for transition to complete (150ms CSS transition + margin)
        page.waitForTimeout(200);
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

    void fillRequestBody(String body) {page.locator("#detail textarea[data-request-body]").fill(body);}

    double requestBodyHeight() {return page.locator("#detail textarea[data-request-body]").boundingBox().height;}

    void focusRequestBody() {page.locator("#detail textarea[data-request-body]").focus();}

    void setCursorAtStart() {
        page.evaluate("() => { var el = document.activeElement; el.setSelectionRange(0, 0); }");
    }

    void setCursorAtEnd() {
        page.evaluate("() => { var el = document.activeElement; el.setSelectionRange(el.value.length, el.value.length); }");
    }

    void waitForInput(String name) {page.waitForSelector("#detail input[name='" + name + "']");}

    void waitForFocusedInput(String name) {
        page.waitForFunction("name => document.activeElement && document.activeElement.getAttribute('name') === name", name);
    }

    void focusSendButton() {page.locator("#detail button[type=submit]").focus();}

    void clickSend() {page.locator("#detail button[type=submit]").click();}

    String sendButtonText() {return page.locator("#detail button[type=submit]").textContent();}

    void waitForResponse() {page.waitForSelector("#detail .response-status");}

    /// Clicks Send and waits for the request cycle to complete.
    /// Robust against missing the intermediate "Sending..." label.
    void resend() {
        clickSend();
        waitForResponse();
        // And ensure the button is back to its idle state
        page.waitForFunction("() => document.querySelector('#detail button[type=submit]')?.textContent === 'Send'");
    }

    /// Clicks Send, waits for headers toggle to disappear and reappear (avoids "Sending..." race condition).
    void resendAndWaitForHeaders() {
        // Remove existing toggle so we can wait for a fresh one
        page.evaluate("document.querySelector('#detail .response-headers-toggle')?.remove()");
        clickSend();
        page.waitForSelector("#detail .response-headers-toggle");
    }

    boolean hasResponseStatus() {return page.locator("#detail .response-status").count() > 0;}

    String statusBadgeText() {return page.locator("#detail .response-status").textContent();}

    String noBodyMessageText() {return page.locator("#detail .response-area .response-empty.box").textContent();}

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
                "() => !document.querySelector('#detail button[type=submit]').disabled");
    }

    boolean isSendButtonFocused() {
        return (Boolean) page.evaluate(
                "() => document.activeElement === document.querySelector('#detail button[type=submit]')");
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

    void focusStatusCodeTab(String code) {
        page.locator(".schema-box[data-box='response'] .schema-status-tab:text('" + code + "')").focus();
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

    void clearClipboard() {page.evaluate("() => navigator.clipboard.writeText('')");}

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
        page.waitForSelector("#tree-container :text('" + text + "')");
    }

    void setViewportSize(int width, int height) {page.setViewportSize(width, height);}

    void waitMs(double ms) {page.waitForTimeout(ms);}

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

    String locationHash() {return (String) page.evaluate("() => location.hash");}

    void goBack() {page.goBack();}

    void navigateTo(String hash) {page.navigate(testServer.baseUrl() + "/index.html" + hash);}

    boolean isGlobalHeadersCollapsed() {
        return page.locator("#global-headers.is-collapsed").count() > 0;
    }

    void clickGlobalHeadersToggle() {
        page.locator(".global-headers-toggle").click();
    }

    void clickGlobalHeaderButton(String text) {
        page.locator("#global-headers button:text('" + text + "')").click();
    }

    void fillGlobalHeader(int index, String name, String value) {
        page.locator("#global-headers .custom-header-row").nth(index).locator(".custom-header-name").fill(name);
        page.locator("#global-headers .custom-header-row").nth(index).locator(".custom-header-value").fill(value);
    }

    void fillGlobalHeaderName(int index, String name) {
        page.locator("#global-headers .custom-header-row").nth(index).locator(".custom-header-name").fill(name);
    }

    double globalHeaderNameWidth(int index) {
        var result = page.locator("#global-headers .custom-header-row").nth(index).locator(".custom-header-name")
                .evaluate("el => el.getBoundingClientRect().width");
        return ((Number) result).doubleValue();
    }

    int globalHeaderRowCount() {
        return page.locator("#global-headers .custom-header-row").count();
    }

    void toggleGlobalHeaderPersist(int index) {
        page.locator("#global-headers .custom-header-row").nth(index).locator(".persist-toggle").click();
    }

    boolean isGlobalHeaderPersisted(int index) {
        return "true".equals(page.locator("#global-headers .custom-header-row").nth(index)
                .locator(".persist-toggle").getAttribute("aria-pressed"));
    }

    boolean isGlobalHeaderPinInsideControl(int index) {
        return page.locator("#global-headers .custom-header-row").nth(index)
                       .locator(".control.has-icons-right .icon.is-right").count() > 0;
    }

    void focusGlobalHeaderValue(int index) {
        page.locator("#global-headers .custom-header-row").nth(index).locator(".custom-header-value").focus();
    }

    void removeGlobalHeader(int index) {
        page.locator("#global-headers .custom-header-row").nth(index).locator(".custom-header-remove").click();
    }

    String globalHeaderName(int index) {
        return page.locator("#global-headers .custom-header-row").nth(index).locator(".custom-header-name").inputValue();
    }

    String globalHeaderValue(int index) {
        return page.locator("#global-headers .custom-header-row").nth(index).locator(".custom-header-value").inputValue();
    }

    void toggleParamPersist(String paramName) {
        page.locator("#detail .field:has([name='" + paramName + "']) .persist-toggle").click();
    }

    boolean isParamPersisted(String paramName) {
        return "true".equals(page.locator("#detail .field:has([name='" + paramName + "']) .persist-toggle").getAttribute("aria-pressed"));
    }

    void navigateToHash(String hash) {
        // wait for the tree to be fully rendered before navigating
        page.waitForSelector("[hx-get]");
        page.evaluate("location.hash = '#" + hash + "'");
        // trigger popstate since programmatic hash change doesn't fire it
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

    double customHeaderNameWidth(int index) {
        var result = page.locator("#detail .custom-header-row").nth(index).locator(".custom-header-name")
                .evaluate("el => el.getBoundingClientRect().width");
        return ((Number) result).doubleValue();
    }

    boolean isErrorBannerVisible() {
        return page.locator("#error-banner").isVisible();
    }

    void waitForErrorBanner() {
        page.waitForSelector("#error-banner:not([style*='display: none'])");
    }

    void waitForErrorBannerGone() {
        page.waitForSelector("#error-banner", new Page.WaitForSelectorOptions()
                .setState(HIDDEN));
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
}
