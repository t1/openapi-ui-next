package com.github.t1.openapi.ui;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.BDDAssertions.then;

class BrowserTest {
    @RegisterExtension static TestContext context = new TestContext();

    @Nested class GivenAppWithOneGet {
        @RegisterExtension static AppFixture app = context.launch("one-get.yaml");

        @Test void shouldShowMethodBadge() {
            then(app.hasMethodBadge("GET")).isTrue();
        }

        @Test void methodAddonsAreRightAligned() {
            then(app.areMethodAddonsRightAligned()).isTrue();
        }

        @Test void treeIsInBoxAndDetailIsNot() {
            then(app.isTreeInBox()).isTrue();
            then(app.isDetailInBox()).isFalse();
        }

        @Test void shouldIncludeCustomStylesheet() {
            then(app.hasStylesheet("openapi-ui.css")).isTrue();
        }

        @Test void enterKeyLoadsFragment() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");

            then(app.detailText()).contains("List pets");
            app.screenshot("fragment-loaded");
        }

        @Test void focusedTreeScreenshot() {
            app.focusTree();
            app.screenshot("focus-tree");
        }

        @Test void tabAndEscapeMoveFocus() {
            app.focusTree();

            app.pressKey("Tab");
            then(app.isTreeFocused()).isFalse();

            app.pressKey("Escape");
            then(app.isTreeFocused()).isTrue();
        }

        @Test void clickingTreeNodeLoadsFragment() {
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("List pets");

            then(app.detailText()).contains("List pets");
        }

        @Test void modeToggleIsSegmentedControl() {
            then(app.hasSegmentedControl()).isTrue();
            then(app.isSegmentActive("try")).isTrue();
        }

        @Test void modeToggleHasThreeOptionsAndSwitches() {
            then(app.isModeButtonVisible("try")).isTrue();
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

            @Test void tryModeSendButtonRecoversAfterResponse() {
                app.mockEndpoint("/pets", "application/json", "{\"id\":\"1\"}");
                navigateToListPetsAndSend();

                then(app.sendButtonText()).isEqualTo("Send");
                then(app.isSendButtonEnabled()).isTrue();
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

    @Nested class GivenMultiMethodApp {
        @RegisterExtension static AppFixture app = context.launch("multi-method.yaml");

        @Test void shouldSwitchMethodTabOnClick() {
            app.waitForDetailContent("List pets");
            then(app.detailText()).contains("List pets");

            app.clickMethodTab(2);
            app.waitForDetailContent("Create a pet");

            then(app.detailText()).contains("Create a pet");
        }

        @Test void shouldEnterTabsOnArrowRight() {
            app.focusTree();
            app.pressKey("ArrowRight"); // expanded node → enter tabs
            then(app.isTabFocused()).isTrue();
            app.screenshot("focus-tab");
        }

        @Test void shouldNavigateFromChevronWithArrowKeys() {
            app.waitForDetailContent("List pets");
            app.focusDescriptionToggle();
            then(app.activeElementSelector()).contains("desc-toggle");

            app.pressKey("ArrowDown");
            then(app.activeElementSelector()).contains("button").contains("data-path");

            app.focusDescriptionToggle();
            app.pressKey("ArrowUp");
            then(app.isTabFocused()).isTrue();
        }

        @Test void shouldExpandAndCollapseDescription() {
            app.waitForDetailContent("List pets");
            then(app.isDescriptionClamped()).isTrue();

            app.clickDescriptionToggle();
            then(app.isDescriptionExpanded()).isTrue();

            app.clickDescriptionToggle();
            then(app.isDescriptionClamped()).isTrue();
        }

        @Test void shouldSwitchTabsWithArrowKeys() {
            app.focusTree();
            app.pressKey("ArrowRight"); // enter tabs on first tab (GET)
            app.pressKey("ArrowRight"); // switch to POST tab
            app.waitForDetailContent("Create a pet");
            then(app.isTabActive(2)).isTrue();

            app.pressKey("ArrowLeft"); // back to GET
            app.waitForDetailContent("List pets");
            then(app.isTabActive(1)).isTrue();
        }

        @Test void shouldReturnToTreeOnArrowLeftFromFirstTab() {
            app.focusTree();
            app.pressKey("ArrowRight"); // enter tabs
            app.pressKey("ArrowLeft"); // first tab → back to tree
            then(app.isTreeFocused()).isTrue();
        }

        @Test void shouldEnterCurrentTabOnEnter() {
            app.focusTree();
            app.pressKey("ArrowRight"); // enter tabs, first tab (GET)
            app.pressKey("ArrowRight"); // switch to POST
            app.pressKey("Escape"); // back to tree
            app.pressKey("Enter"); // should re-enter on POST (current tab)
            then(app.isTabFocused()).isTrue();
            then(app.isTabActive(2)).isTrue();
        }

        @Test void shouldNavigateFromTabsToFields() {
            app.focusTree();
            app.pressKey("ArrowDown"); // select {petId}
            app.waitForInput("petId");
            app.pressKey("ArrowRight"); // enter tabs
            app.pressKey("ArrowDown"); // enter fields
            then(app.activeElementTag()).isEqualTo("INPUT");
            app.screenshot("focus-field");
        }

        @Test void shouldReturnToTabsOnArrowUpFromFirstField() {
            app.focusTree();
            app.pressKey("ArrowDown"); // select {petId}
            app.waitForInput("petId");
            app.pressKey("ArrowRight"); // enter tabs
            app.pressKey("ArrowDown"); // enter fields
            app.pressKey("ArrowUp"); // back to tabs
            then(app.isTabFocused()).isTrue();
        }

        @Test void shouldBumpOnTreeBoundary() {
            app.focusTree();
            app.pressKey("ArrowUp"); // already first item → bump
            then(app.selectedItemHasBumpClass()).isTrue();
        }

        @Test void shouldEscapeFromFieldsToTree() {
            app.focusTree();
            app.pressKey("ArrowDown"); // select {petId}
            app.waitForDetailContent("Get pet by ID");
            app.pressKey("ArrowRight"); // enter tabs
            app.pressKey("ArrowDown"); // enter fields
            app.pressKey("Escape"); // back to tree
            then(app.isTreeFocused()).isTrue();
        }

        @Test void shouldMoveActiveClassOnTabSwitch() {
            app.waitForDetailContent("List pets");
            then(app.isTabActive(1)).isTrue();
            then(app.isTabActive(2)).isFalse();

            app.clickMethodTab(2);
            app.waitForDetailContent("Create a pet");

            then(app.isTabActive(1)).isFalse();
            then(app.isTabActive(2)).isTrue();
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

        @Test void tryModeSendsRequestWithPathParam() {
            app.mockRootEndpoint("/items/42", "application/json", "{\"id\":\"42\",\"name\":\"Widget\"}");
            app.clickTreeNode("items/{itemId}/index.html");
            app.waitForInput("itemId");
            app.fillInput("itemId", "42");
            app.clickSend();
            app.waitForResponse();

            then(app.responseText()).contains("Widget");
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

        @Test void shouldNavigateArrowDownFromChevronToField() {
            app.waitForDetailContent("Get a pet");
            app.focusDescriptionToggle();
            then(app.activeElementSelector()).contains("desc-toggle");

            app.pressKey("ArrowDown");
            then(app.activeElementSelector()).contains("input")
                    .doesNotContain("data-path");
        }

        @Nested class InTryMode {
            @RegisterExtension static AppFixture app =
                    context.launch("params.yaml").withBaseUrlOverride();

            @Test void tryModeSendsRequestWithPathParam() {
                app.mockEndpoint("/pets/42", "application/json", "{\"id\":\"42\",\"name\":\"Fido\"}");
                app.clickTreeNode("pets/{petId}/index.html");
                app.waitForInput("petId");
                app.fillInput("petId", "42");
                app.clickSend();
                app.waitForResponse();

                then(app.responseText()).contains("Fido");
                app.screenshot("try-mode-path-param");
            }
        }

        @Test void curlModeCopiesCommand() {
            app.clickModeButton("curl");
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForInput("petId");
            app.fillInput("petId", "42");
            app.screenshot("params-filled");
            app.clickSend();

            then(app.readClipboard())
                    .contains("curl -X GET")
                    .contains("https://api.example.com/pets/42");
        }

        @Test void curlModeShowsCopyButtonLabel() {
            app.clickModeButton("curl");
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForInput("petId");

            then(app.sendButtonText()).isEqualTo("Copy");
        }

        @Test void curlModeShowsCopiedFeedback() {
            app.clickModeButton("curl");
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForInput("petId");
            app.fillInput("petId", "42");
            app.clickSend();

            then(app.sendButtonText()).isEqualTo("Copied!");
        }

        @Test void curlModeIncludesMethod() {
            app.clickModeButton("curl");
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForInput("petId");
            app.fillInput("petId", "42");
            app.clickSend();

            then(app.readClipboard()).startsWith("curl -X GET");
        }

        @Test void httpieModeCopiesCommand() {
            app.clickModeButton("httpie");
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForInput("petId");
            app.fillInput("petId", "42");
            app.clickSend();

            then(app.readClipboard())
                    .contains("http GET")
                    .contains("https://api.example.com/pets/42");
        }
    }

    @Nested class GivenAppWithPostEndpoint {
        @RegisterExtension static AppFixture app =
                context.launch("post-endpoint.yaml").withBaseUrlOverride();

        @Test void tryModeSendsPostRequest() {
            app.mockEndpoint("/pets", "POST", "application/json", "{\"id\":1}");
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.clickSend();
            app.waitForResponse();

            then(app.responseText()).contains("\"id\"");
        }
    }

    @Nested class GivenAppWithRequestBody {
        @RegisterExtension static AppFixture app =
                context.launch("request-body.yaml").withBaseUrlOverride();

        @Test void tryModeSendsRequestBody() {
            app.mockEndpointWithBodyEcho("/pets", "POST");
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.fillRequestBody("{\"name\": \"Fido\", \"age\": 3}");
            app.clickSend();
            app.waitForResponse();

            then(app.responseText()).contains("Fido");
        }

        @Test void curlModeIncludesRequestBody() {
            app.clickModeButton("curl");
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.fillRequestBody("{\"name\": \"Fido\"}");
            app.clickSend();

            then(app.readClipboard())
                    .contains("curl -X POST")
                    .contains("-H 'Content-Type: application/json'")
                    .contains("-d '{\"name\": \"Fido\"}'");
        }

        @Test void httpieModeIncludesRequestBody() {
            app.clickModeButton("httpie");
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.fillRequestBody("{\"name\": \"Fido\"}");
            app.clickSend();

            then(app.readClipboard())
                    .contains("http POST")
                    .contains("echo '{\"name\": \"Fido\"}'");
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
            then(app.isTextVisible("{petId}")).isTrue();

            app.pressKey("ArrowLeft");
            then(app.isTextVisible("{petId}")).isFalse();

            app.pressKey("ArrowRight");
            then(app.isTextVisible("{petId}")).isTrue();
            app.screenshot("tree-expanded");
        }
    }
}
