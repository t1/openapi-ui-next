package com.github.t1.openapi.ui;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.BDDAssertions.then;

class BrowserTest {
    @RegisterExtension static TestContext context = new TestContext();

    @Nested class GivenAppWithOneGet {
        @RegisterExtension static AppFixture app = context.launch("one-get.yaml");

        @Test void enterKeyLoadsFragment() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");

            then(app.detailText()).contains("List pets");
            app.screenshot("fragment-loaded");
        }

        @Test void tabAndEscapeMoveFocus() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");

            app.pressKey("Tab");
            then(app.isFocusInDetail()).isTrue();

            app.pressKey("Escape");
            then(app.isTreeFocused()).isTrue();
        }

        @Test void clickingTreeNodeLoadsFragment() {
            app.clickTreeNode("pets/GET.html");
            app.waitForDetailContent("List pets");

            then(app.detailText()).contains("List pets");
        }

        @Test void modeToggleHasThreeOptionsAndSwitches() {
            then(app.isModeButtonVisible("Try")).isTrue();
            then(app.isModeButtonVisible("httpie")).isTrue();
            then(app.isModeButtonVisible("curl")).isTrue();

            app.clickModeButton("curl");
            then(app.currentMode()).isEqualTo("curl");
        }

        @Test void desktopLayoutIsSideBySide() {
            app.setViewportSize(1280, 720);
            app.navigate(app.baseUrl());

            then(app.treeBoundingBox()[0]).as("Tree should be left of detail pane on desktop")
                    .isLessThan(app.detailBoundingBox()[0]);
            app.screenshot("layout-desktop");
        }

        @Test void mobileLayoutIsStacked() {
            app.setViewportSize(375, 667);
            app.navigate(app.baseUrl());

            then(app.treeBoundingBox()[1]).as("Tree should be above detail pane on mobile")
                    .isLessThan(app.detailBoundingBox()[1]);
            app.screenshot("layout-mobile");
        }

        @Nested class InTryMode {
            @RegisterExtension static AppFixture app =
                    context.launch("one-get.yaml").withBaseUrlOverride();

            private void navigateToListPetsAndSend() {
                app.focusTree();
                app.pressKey("Enter");
                app.waitForDetailContent("List pets");
                app.clickSend();
                app.waitForResponse();
            }

            @Test void tryModeSendsRequestAndShowsPrettifiedJson() {
                app.mockEndpoint("/pets", "application/json", "{\"id\":\"1\",\"name\":\"Fido\"}");
                navigateToListPetsAndSend();

                then(app.responseText())
                        .contains("\"name\"")
                        .contains("Fido")
                        .contains("\n");
                app.screenshot("try-mode-json-response");
            }

            @Test void tryModeShowsHtmlResponseAsIs() {
                app.mockEndpoint("/pets", "text/html", "<h1>Hello</h1><p>World</p>");
                navigateToListPetsAndSend();

                then(app.responseText()).contains("<h1>Hello</h1>");
            }

            @Test void tryModeShowsXmlResponseAsIs() {
                app.mockEndpoint("/pets", "application/xml", "<pets><pet><name>Fido</name></pet></pets>");
                navigateToListPetsAndSend();

                then(app.responseText()).contains("<pets>");
            }

            @Test void tryModeShowsErrorForFailedFetch() {
                app.mockEndpoint("/pets", "text/plain", "not found", 404);
                navigateToListPetsAndSend();

                then(app.responseText()).contains("404");
            }

            @Test void tryModeShowsYamlResponseAsIs() {
                app.mockEndpoint("/pets", "application/yaml", "pets:\n  - name: Fido\n    id: 1");
                navigateToListPetsAndSend();

                then(app.responseText())
                        .contains("pets:")
                        .contains("Fido");
            }
        }
    }

    @Nested class GivenAppWithRelativeBase {
        @RegisterExtension static AppFixture app = context.launch("relative-base.yaml");

        @Test void tryModeShouldResolveRelativeBaseUrl() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List items");
            app.mockRootEndpoint("/items", "application/json", "{\"id\":\"1\"}");
            app.clickSend();
            app.waitForResponse();

            then(app.responseText()).contains("\"id\"");
        }
    }

    @Nested class GivenAppWithNoSummary {
        @RegisterExtension static AppFixture app = context.launch("no-summary.yaml");

        @Test void shouldNotShowEmDashWithoutSummary() {
            then(app.selectedTreeItemText()).doesNotContain("—");
        }
    }

    @Nested class GivenAppWithParams {
        @RegisterExtension static AppFixture app = context.launch("params.yaml");

        @Test void curlModeCopiesCommand() {
            app.clickModeButton("curl");
            app.focusTree();
            app.pressKey("ArrowRight");
            app.clickTreeNode("pets/{petId}/GET.html");
            app.waitForInput("petId");
            app.fillInput("petId", "42");
            app.screenshot("params-filled");
            app.clickSend();

            then(app.readClipboard())
                    .contains("curl")
                    .contains("https://api.example.com/pets/42");
        }

        @Test void curlModeShowsCopyButtonLabel() {
            app.clickModeButton("curl");
            app.focusTree();
            app.pressKey("ArrowRight");
            app.clickTreeNode("pets/{petId}/GET.html");
            app.waitForInput("petId");

            then(app.sendButtonText()).isEqualTo("Copy");
        }

        @Test void curlModeShowsCopiedFeedback() {
            app.clickModeButton("curl");
            app.focusTree();
            app.pressKey("ArrowRight");
            app.clickTreeNode("pets/{petId}/GET.html");
            app.waitForInput("petId");
            app.fillInput("petId", "42");
            app.clickSend();

            then(app.sendButtonText()).isEqualTo("Copied!");
        }

        @Test void httpieModeCopiesCommand() {
            app.clickModeButton("httpie");
            app.focusTree();
            app.pressKey("ArrowRight");
            app.clickTreeNode("pets/{petId}/GET.html");
            app.waitForInput("petId");
            app.fillInput("petId", "42");
            app.clickSend();

            then(app.readClipboard())
                    .contains("http GET")
                    .contains("https://api.example.com/pets/42");
        }
    }

    @Nested class GivenAppWithNestedPaths {
        @RegisterExtension static AppFixture app = context.launch("nested-paths.yaml");

        @Test void arrowKeysNavigateTree() {
            app.focusTree();
            then(app.selectedTreeItemText()).contains("pets");

            app.pressKey("ArrowDown");
            then(app.selectedTreeItemText()).isNotNull();
        }

        @Test void arrowRightExpandsAndLeftCollapsesNode() {
            app.focusTree();
            then(app.isTextVisible("{petId}")).isFalse();

            app.pressKey("ArrowRight");
            then(app.isTextVisible("{petId}")).isTrue();
            app.screenshot("tree-expanded");

            app.pressKey("ArrowLeft");
            then(app.isTextVisible("{petId}")).isFalse();
        }
    }
}
