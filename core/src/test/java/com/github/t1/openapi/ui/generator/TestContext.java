package com.github.t1.openapi.ui.generator;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

class TestContext implements BeforeAllCallback {
    private Playwright playwright;
    private Browser browser;

    @Override public void beforeAll(ExtensionContext context) {
        context.getRoot().getStore(GLOBAL).computeIfAbsent(this, key -> {
            playwright = Playwright.create();
            var browserType = System.getProperty("playwright.browser", "chromium");
            browser = switch (browserType) {
                case "webkit" -> playwright.webkit().launch();
                case "firefox" -> playwright.firefox().launch();
                default -> playwright.chromium().launch();
            };
            return (AutoCloseable) () -> {
                browser.close();
                playwright.close();
            };
        });
    }

    AppFixture launch(String specFilename) {return new AppFixture(browser, specFilename);}
}
