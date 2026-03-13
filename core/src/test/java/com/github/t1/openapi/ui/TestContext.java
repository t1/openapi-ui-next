package com.github.t1.openapi.ui;

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
            browser = playwright.chromium().launch();
            return (AutoCloseable) () -> {
                browser.close();
                playwright.close();
            };
        });
    }

    AppFixture launch(String specFilename) {return new AppFixture(browser, specFilename);}
}
