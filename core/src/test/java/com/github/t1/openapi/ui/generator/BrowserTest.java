package com.github.t1.openapi.ui.generator;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.Map;
import java.util.stream.IntStream;

import static com.github.t1.openapi.ui.generator.AppFixture.MOD;
import static com.github.t1.openapi.ui.generator.AppFixture.MOD_LABEL;
import static org.assertj.core.api.BDDAssertions.then;

class BrowserTest {
    private static final double MINIMUM_DRAG_DELTA = 50;

    private static AppFixture launch(String specFilename) {return new AppFixture(specFilename);}

    @ResourceLock("one-get") @Nested class GivenAppWithOneGet {
        @RegisterExtension static AppFixture app = launch("one-get.yaml");

        @Test void shouldHaveSplitLayout() {then(app.hasSplitLayout()).isTrue();}

        @Test void shouldHaveModeSelectorInSplitSecondArea() {
            then(app.isModeSelectorInSplitSecond()).isTrue();
            then(app.isModeSelectorInDetailHeader()).isFalse();
        }

        @Test void shouldResizeOnDrag() {
            var widthBefore = app.treeWidth();
            app.dragSplitHandle(100);
            var widthAfter = app.treeWidth();

            then(widthAfter).isGreaterThan(widthBefore + MINIMUM_DRAG_DELTA);
        }

        @Test void shouldPersistTreeWidthAcrossReload() {
            app.dragSplitHandle(100);
            var widthAfterDrag = app.treeWidth();

            app.navigateHome();
            var widthAfterReload = app.treeWidth();

            then(Math.abs(widthAfterReload - widthAfterDrag)).isLessThan(10);
        }

        @Test void shouldShowMethodBadge() {then(app.hasMethodBadge("GET")).isTrue();}

        @Test void shouldRightAlignMethodAddons() {then(app.areMethodAddonsRightAligned()).isTrue();}

        @Test void shouldRenderTreeInBoxButNotDetail() {
            then(app.isTreeInBox()).isTrue();
            then(app.isDetailInBox()).isFalse();
        }

        @Test void shouldIncludeCustomStylesheet() {then(app.hasStylesheet("openapi-ui.css")).isTrue();}

        @Test void shouldLoadFragmentOnEnterKey() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");

            then(app.detailText()).contains("List pets");
            app.screenshot("fragment-loaded");
        }

        @Test void shouldNotShowResponseTypeSelectForSingleType() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");

            then(app.hasResponseTypeSelect()).isFalse();
        }

        @Test void shouldNotShowResponseBoxWithoutDocumentedContent() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");

            then(app.hasResponseBox()).isFalse();
        }

        @Test void shouldCaptureTreeFocusScreenshot() {
            app.focusTree();

            app.screenshot("focus-tree");
        }

        @Test void shouldMoveFocusWithTabAndEscape() {
            app.focusTree();

            app.pressKey("Tab");
            then(app.isTreeFocused()).isFalse();

            app.pressKey("Escape");
            then(app.isTreeFocused()).isTrue();
        }

        @Test void shouldLoadFragmentOnTreeNodeClick() {
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("List pets");

            then(app.detailText()).contains("List pets");
        }

        @Test void shouldFocusTreeOnTreeNodeClick() {
            app.focusTree();
            app.pressKey("Tab");
            then(app.isTreeFocused()).isFalse();

            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("List pets");

            then(app.isTreeFocused()).isTrue();
            then(app.selectedItemHasFocusRing()).isTrue();
        }

        @Test void shouldHaveRoundedCornersWhenGlobalHeadersCollapsed() {
            then(app.hasRoundedCornersWhenCollapsed("#global-headers")).isTrue();
        }

        @Test void shouldToggleGlobalHeadersPanel() {
            then(app.isGlobalHeadersCollapsed()).isTrue();
            app.clickGlobalHeadersToggle();
            then(app.isGlobalHeadersCollapsed()).isFalse();
            app.clickGlobalHeadersToggle();
            then(app.isGlobalHeadersCollapsed()).isTrue();
        }

        @Test void shouldRenderGlobalHeaderRowsAsPanelBlocks() {
            app.clickGlobalHeadersToggle();
            app.clickGlobalHeaderButton("+ Add global header");
            app.clickGlobalHeaderButton("+ Add global header");
            app.fillGlobalHeader(0, "Authorization", "Bearer token");
            app.fillGlobalHeader(1, "X-Custom", "value");

            then(app.isGlobalHeaderRowAPanelBlock(0)).isTrue();
            then(app.isGlobalHeaderRowAPanelBlock(1)).isTrue();
            app.screenshot("global-headers");
        }

        @Test void shouldShowGlobalHeaderPinAsIconRight() {
            app.clickGlobalHeadersToggle();
            app.clickGlobalHeaderButton("+ Add global header");

            then(app.isGlobalHeaderPinInsideControl(0)).isTrue();
        }

        @Test void shouldAutoResizeGlobalHeaderNameInput() {
            app.clickGlobalHeadersToggle();
            app.clickGlobalHeaderButton("+ Add global header");
            var emptyWidth = app.globalHeaderNameWidth(0);
            app.fillGlobalHeaderName(0, "X-Very-Long-Global-Header-Name");
            var filledWidth = app.globalHeaderNameWidth(0);
            then(filledWidth).isGreaterThan(emptyWidth);
        }

        @Test void shouldToggleGlobalHeaderPersistWithShortcut() {
            app.clickGlobalHeadersToggle();
            app.clickGlobalHeaderButton("+ Add global header");
            app.fillGlobalHeader(0, "Authorization", "Bearer secret");
            app.focusGlobalHeaderValue(0);

            app.pressKey(MOD + "+p");

            then(app.isGlobalHeaderPersisted(0)).isTrue();
        }

        @Test void shouldPersistGlobalHeader() {
            app.clickGlobalHeadersToggle();
            app.clickGlobalHeaderButton("+ Add global header");
            app.fillGlobalHeader(0, "Authorization", "Bearer secret");
            app.toggleGlobalHeaderPersist(0);
            app.navigateHome();
            app.clickGlobalHeadersToggle();
            then(app.globalHeaderName(0)).isEqualTo("Authorization");
            then(app.globalHeaderValue(0)).isEqualTo("Bearer secret");
        }

        @Test void shouldCleanUpOldKeyWhenRenamingPersistedGlobalHeader() {
            app.clickGlobalHeadersToggle();
            app.clickGlobalHeaderButton("+ Add global header");
            app.fillGlobalHeader(0, "Authorization", "Bearer secret");
            app.toggleGlobalHeaderPersist(0);
            app.fillGlobalHeaderName(0, "X-Auth");
            app.navigateHome();
            app.clickGlobalHeadersToggle();
            then(app.globalHeaderRowCount()).isEqualTo(1);
            then(app.globalHeaderName(0)).isEqualTo("X-Auth");
        }

        @Test void shouldCleanUpOldKeyWhenRenamingRestoredPersistedGlobalHeader() {
            app.clickGlobalHeadersToggle();
            app.clickGlobalHeaderButton("+ Add global header");
            app.fillGlobalHeader(0, "Authorization", "Bearer secret");
            app.toggleGlobalHeaderPersist(0);
            app.navigateHome();
            app.clickGlobalHeadersToggle();
            app.fillGlobalHeaderName(0, "X-Auth");
            app.navigateHome();
            app.clickGlobalHeadersToggle();
            then(app.globalHeaderRowCount()).isEqualTo(1);
            then(app.globalHeaderName(0)).isEqualTo("X-Auth");
        }

        @Test void shouldRemovePersistedGlobalHeaderFromStorageOnDelete() {
            app.clickGlobalHeadersToggle();
            app.clickGlobalHeaderButton("+ Add global header");
            app.fillGlobalHeader(0, "Authorization", "Bearer secret");
            app.toggleGlobalHeaderPersist(0);
            app.navigateHome();
            app.clickGlobalHeadersToggle();
            then(app.globalHeaderRowCount()).isEqualTo(1);

            app.removeGlobalHeader(0);
            app.navigateHome();
            app.clickGlobalHeadersToggle();
            then(app.globalHeaderRowCount()).isEqualTo(0);
        }

        @Test void shouldRemovePersistedHeaderWhenUnchecked() {
            app.clickGlobalHeadersToggle();
            app.clickGlobalHeaderButton("+ Add global header");
            app.fillGlobalHeader(0, "X-Temp", "val");
            app.toggleGlobalHeaderPersist(0);
            app.toggleGlobalHeaderPersist(0);
            app.navigateHome();
            app.clickGlobalHeadersToggle();
            then(app.globalHeaderRowCount()).isEqualTo(0);
        }

        @Test void shouldIncludeGlobalHeaderInCurlCommand() {
            app.clickGlobalHeadersToggle();
            app.clickGlobalHeaderButton("+ Add global header");
            app.fillGlobalHeader(0, "Authorization", "Bearer token123");
            app.clickModeButton("curl");
            app.clickSend();
            var clipboard = app.readClipboard();
            then(clipboard).contains("-H 'Authorization: Bearer token123'");
        }

        @Test void shouldRenderModeToggleAsSegmentedControl() {
            then(app.hasSegmentedControl()).isTrue();
            then(app.isSegmentActive("try")).isTrue();
        }

        @Test void shouldSwitchModeByClicking() {
            app.clickModeButton("curl");

            then(app.currentMode()).isEqualTo("curl");
        }

        @Test void shouldShowTooltipsOnModeButtons() {
            then(app.modeButtonTooltip("try")).contains(MOD_LABEL + "+1");
            then(app.modeButtonTooltip("httpie")).contains(MOD_LABEL + "+2");
            then(app.modeButtonTooltip("curl")).contains(MOD_LABEL + "+3");
        }

        @Test void shouldSwitchToNextModeOnArrowRight() {
            app.focusModeToggle();

            app.pressKey("ArrowRight");

            then(app.currentMode()).isEqualTo("httpie");
            then(app.isSegmentActive("httpie")).isTrue();
        }

        @Test void shouldSwitchToPreviousModeOnArrowLeft() {
            app.clickModeButton("curl");

            app.focusModeToggle();
            app.pressKey("ArrowLeft");

            then(app.currentMode()).isEqualTo("httpie");
            then(app.isSegmentActive("httpie")).isTrue();
        }

        @Test void shouldNotWrapRightWhenOnLastMode() {
            app.clickModeButton("curl");

            app.focusModeToggle();
            app.pressKey("ArrowRight");

            then(app.currentMode()).isEqualTo("curl");
            then(app.isSegmentActive("curl")).isTrue();
        }

        @Test void shouldNotWrapLeftWhenOnFirstMode() {
            app.focusModeToggle();

            app.pressKey("ArrowLeft");

            then(app.currentMode()).isEqualTo("try");
        }

        @Test void shouldSelectFirstModeOnHomeKey() {
            app.clickModeButton("curl");

            app.focusModeToggle();
            app.pressKey("Home");

            then(app.currentMode()).isEqualTo("try");
        }

        @Test void shouldSelectLastModeOnEndKey() {
            app.focusModeToggle();

            app.pressKey("End");

            then(app.currentMode()).isEqualTo("curl");
            then(app.isSegmentActive("curl")).isTrue();
        }

        @Test void shouldSwitchModeOnShortcutKey() {
            app.focusModeToggle();

            app.pressKey(MOD + "+2");
            then(app.currentMode()).isEqualTo("httpie");

            app.pressKey(MOD + "+3");
            then(app.currentMode()).isEqualTo("curl");

            app.pressKey(MOD + "+1");
            then(app.currentMode()).isEqualTo("try");
        }

        @Test void shouldSwitchModeOnShortcutKeyFromTree() {
            app.focusTree();

            app.pressKey(MOD + "+2");

            then(app.currentMode()).isEqualTo("httpie");
        }

        @Test void shouldRenderFourSegmentsInitially() {
            then(app.isModeButtonVisible("try")).isTrue();
            then(app.isModeButtonVisible("httpie")).isTrue();
            then(app.isModeButtonVisible("curl")).isTrue();
            then(app.isModeButtonVisible("overflow")).isTrue();
        }

        @Test void shouldOpenDropdownMenuOnOverflowClick() {
            then(app.isDropdownMenuVisible()).isFalse();

            app.clickModeButton("overflow");

            then(app.isDropdownMenuVisible()).isTrue();
        }

        @Test void shouldListAllGeneratorsInDropdown() {
            app.clickModeButton("overflow");

            then(app.dropdownContainsItem("httpie")).isTrue();
            then(app.dropdownContainsItem("curl")).isTrue();
        }

        @Test void shouldSelectFromDropdownAndCloseMenu() {
            app.clickModeButton("overflow");

            app.clickDropdownItem("httpie");

            then(app.isDropdownMenuVisible()).isFalse();
            then(app.currentMode()).isEqualTo("httpie");
        }

        @Test void shouldOpenDropdownOnCtrlPlus4() {
            app.pressKey(MOD + "+4");

            then(app.isDropdownMenuVisible()).isTrue();
        }

        @Test void shouldFocusViewToggleOnShiftTabFromTree() {
            app.focusTree();

            app.pressKey("Shift+Tab");

            then(app.isViewToggleFocused()).isTrue();
        }

        @Test void shouldFocusModeToggleOnShiftTabFromViewToggle() {
            app.focusViewToggle();

            app.pressKey("Shift+Tab");

            then(app.isModeToggleFocused()).isTrue();
        }

        @Test void shouldRenderSideBySideOnDesktop() {
            app.setViewportSize(1280, 720);
            app.navigateHome();

            then(app.treeBoundingBox()[0]).as("Tree should be left of detail pane on desktop")
                    .isLessThan(app.detailBoundingBox()[0]);
            app.screenshot("layout-desktop");
        }

        @Test void shouldRenderStackedOnMobile() {
            app.setViewportSize(375, 667);
            app.navigateHome();

            then(app.treeBoundingBox()[1]).as("Tree should be above detail pane on mobile")
                    .isLessThan(app.detailBoundingBox()[1]);
            app.screenshot("layout-mobile");
        }

        @ResourceLock("one-get-try") @Nested class InTryMode {
            @RegisterExtension static AppFixture app =
                    launch("one-get.yaml").withBaseUrlOverride();

            private void navigateToListPetsAndSend() {
                app.focusTree();
                app.pressKey("Enter");
                app.waitForDetailContent("List pets");
                app.clickSend();
                app.waitForResponse();
            }

            @Test void shouldSendRequestAndShowPrettifiedJsonInTryMode() {
                app.mockEndpoint("/pets", "application/json", "{\"id\":\"1\",\"name\":\"Fido\"}");

                navigateToListPetsAndSend();

                then(app.responseText())
                        .contains("\"name\"")
                        .contains("Fido")
                        .contains("\n");
                app.screenshot("try-mode-json-response");
            }

            @Test void shouldHighlightJsonResponse() {
                app.mockEndpoint("/pets", "application/json", "{\"id\":\"1\",\"name\":\"Fido\"}");

                navigateToListPetsAndSend();

                then(app.responseHasHighlighting()).isTrue();
            }

            @Test void shouldHighlightXmlResponse() {
                app.mockEndpoint("/pets", "application/xml", "<pets><pet><name>Fido</name></pet></pets>");

                navigateToListPetsAndSend();

                then(app.responseHasHighlighting()).isTrue();
            }

            @Test void shouldHighlightYamlResponse() {
                app.mockEndpoint("/pets", "application/yaml", "pets:\n  - name: Fido\n    id: 1");

                navigateToListPetsAndSend();

                then(app.responseHasHighlighting()).isTrue();
            }

            @Test void shouldHighlightVendorJsonResponse() {
                app.mockEndpoint("/pets", "application/vnd.api+json", "{\"id\":\"1\",\"name\":\"Fido\"}");

                navigateToListPetsAndSend();

                then(app.responseHasHighlighting()).isTrue();
            }

            @Test void shouldHighlightMarkdownResponse() {
                app.mockEndpoint("/pets", "text/markdown", "# Pets\n- Fido\n- Rex");

                navigateToListPetsAndSend();

                then(app.responseHasHighlighting()).isTrue();
            }

            @Test void shouldHighlightHtmlResponse() {
                app.mockEndpoint("/pets", "text/html", "<h1>Hello</h1><p>World</p>");

                navigateToListPetsAndSend();

                then(app.responseHasHighlighting()).isTrue();
            }

            @Test void shouldNotHighlightPlainTextResponse() {
                app.mockEndpoint("/pets", "text/plain", "just plain text");

                navigateToListPetsAndSend();

                then(app.responseHasHighlighting()).isFalse();
            }

            @Test void shouldShowHtmlResponseAsIsInTryMode() {
                app.mockEndpoint("/pets", "text/html", "<h1>Hello</h1><p>World</p>");

                navigateToListPetsAndSend();

                then(app.responseText()).contains("<h1>Hello</h1>");
            }

            @Test void shouldShowXmlResponseAsIsInTryMode() {
                app.mockEndpoint("/pets", "application/xml", "<pets><pet><name>Fido</name></pet></pets>");

                navigateToListPetsAndSend();

                then(app.responseText()).contains("<pets>");
            }

            @Test void shouldSubmitOnEnterFromModeToggle() {
                app.mockEndpoint("/pets", "application/json", "{\"id\":\"1\"}");
                app.clickTreeNode("pets/index.html");
                app.waitForDetailContent("List pets");
                app.focusModeToggle();

                app.pressKey("Enter");

                app.waitForResponse();
                then(app.statusBadgeText()).isEqualTo("200 OK");
            }

            @Test void shouldRecoverSendButtonAfterResponseInTryMode() {
                app.mockEndpoint("/pets", "application/json", "{\"id\":\"1\"}");

                navigateToListPetsAndSend();

                then(app.sendButtonText()).isEqualTo("Send");
                then(app.isSendButtonEnabled()).isTrue();
            }

            @Test void shouldShowStatusBadgeNextToSendButtonInTryMode() {
                app.mockEndpoint("/pets", "application/json", "{\"id\":\"1\"}");

                navigateToListPetsAndSend();

                then(app.statusBadgeText()).isEqualTo("200 OK");
                then(app.isStatusBadgeOnSameLineAsModeToggle())
                        .as("status badge should be on the same line as the mode toggle")
                        .isTrue();
            }

            @Test void shouldShowErrorStatusBadgeInTryMode() {
                app.mockEndpoint("/pets", "text/plain", "not found", 404);

                navigateToListPetsAndSend();

                then(app.statusBadgeText()).isEqualTo("404 Not Found");
            }

            @Test void shouldNotDuplicateErrorStatusInBodyInTryMode() {
                app.mockEndpoint("/pets", "text/plain", "not found", 404);

                navigateToListPetsAndSend();

                then(app.responseText()).doesNotContain("404");
                then(app.responseText()).contains("not found");
            }

            @Test void shouldShowNoBodyMessageForEmptyResponseInTryMode() {
                app.mockEndpoint("/pets", "application/json", "", 204);

                navigateToListPetsAndSend();

                then(app.statusBadgeText()).isEqualTo("204 No Content");
                then(app.noBodyMessageText()).isEqualTo("no body");
            }

            @Test void shouldShowNetworkErrorBadgeOnFetchFailure() {
                app.simulateNetworkError("/pets");

                navigateToListPetsAndSend();

                then(app.statusBadgeText()).isEqualTo("Network error");
            }

            @Test void shouldShowErrorMessageInBodyOnNetworkFailure() {
                app.simulateNetworkError("/pets");

                navigateToListPetsAndSend();

                then(app.responseText()).contains("Failed to fetch");
            }

            @Test void shouldNotAddBodyWhenCustomHeaderPresent() {
                app.mockEndpoint("/pets", "application/json", "{\"id\":\"1\"}");
                app.focusTree();
                app.pressKey("Enter");
                app.waitForDetailContent("List pets");
                app.clickButton("+ Add custom header");
                app.clickSend();
                app.waitForResponse();

                then(app.statusBadgeText()).isEqualTo("200 OK");
            }

            @Test void shouldShowHeadersToggleAfterSend() {
                app.mockEndpoint("/pets", "application/json", "{\"id\":\"1\"}");

                navigateToListPetsAndSend();

                then(app.hasResponseHeadersToggle()).isTrue();
                then(app.responseHeadersToggleText()).contains("Headers").endsWith("▶");
            }

            @Test void shouldHideHeadersByDefault() {
                app.mockEndpoint("/pets", "application/json", "{\"id\":\"1\"}");

                navigateToListPetsAndSend();

                then(app.responseHeadersVisible()).isFalse();
            }

            @Test void shouldExpandHeadersOnToggleClick() {
                app.mockEndpoint("/pets", "application/json", "{\"id\":\"1\"}");
                navigateToListPetsAndSend();

                app.toggleResponseHeaders();

                then(app.responseHeadersVisible()).isTrue();
                then(app.responseHeaderText("content-type")).contains("application/json");
            }

            @Test void shouldCollapseHeadersOnSecondClick() {
                app.mockEndpoint("/pets", "application/json", "{\"id\":\"1\"}");
                navigateToListPetsAndSend();
                app.toggleResponseHeaders();

                app.toggleResponseHeaders();

                then(app.responseHeadersVisible()).isFalse();
            }

            @Test void shouldKeepHeadersExpandedOnResend() {
                app.mockEndpoint("/pets", "application/json", "{\"id\":\"1\"}");
                navigateToListPetsAndSend();
                app.toggleResponseHeaders();
                then(app.responseHeadersVisible()).isTrue();

                app.resend();

                then(app.responseHeadersVisible()).isTrue();
                then(app.responseHeadersToggleText()).contains("Headers").endsWith("▼");
            }

            @Test void shouldKeepHeadersCollapsedOnResend() {
                app.mockEndpoint("/pets", "application/json", "{\"id\":\"1\"}");
                navigateToListPetsAndSend();
                then(app.responseHeadersVisible()).isFalse();

                app.resend();

                then(app.responseHeadersVisible()).isFalse();
                then(app.responseHeadersToggleText()).contains("Headers").endsWith("▶");
            }

            @Test void shouldNotShowShowAllWhenNoDocumentedHeaders() {
                app.mockEndpoint("/pets", "application/json", "{\"id\":\"1\"}");

                navigateToListPetsAndSend();
                app.toggleResponseHeaders();

                then(app.hasShowAllButton()).isFalse();
            }

            @Test void shouldShowYamlResponseAsIsInTryMode() {
                app.mockEndpoint("/pets", "application/yaml", "pets:\n  - name: Fido\n    id: 1");

                navigateToListPetsAndSend();

                then(app.responseText())
                        .contains("pets:")
                        .contains("Fido");
            }
        }
    }

    @ResourceLock("error-banner") @Nested class ErrorRetryBehavior {
        @RegisterExtension static AppFixture app = launch("one-get.yaml");

        @Test void shouldShowBannerWhenHtmxRequestFails() {
            app.waitForDetailContent("List pets");
            app.blockHtmxRequests();
            app.clickTreeNode("pets/index.html");
            app.waitForErrorBanner();

            then(app.isErrorBannerVisible()).isTrue();
        }

        @Test void shouldHideBannerAndShowContentWhenRetrySucceeds() {
            app.waitForDetailContent("List pets");
            app.blockHtmxRequests();
            app.clickTreeNode("pets/index.html");
            app.waitForErrorBanner();

            app.unblockHtmxRequests();
            app.waitForErrorBannerGone();

            then(app.isErrorBannerVisible()).isFalse();
            then(app.detailText()).contains("List pets");
        }
    }

    @ResourceLock("tagged-flat") @Nested class GivenFlatTaggedApp {
        @RegisterExtension static AppFixture app = launch("tagged-flat.yaml");

        @Test void shouldSwitchToPathViewOnClick() {
            then(app.isViewActive("tags")).isTrue();

            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");

            then(app.isViewActive("paths")).isTrue();
        }

        @Test void shouldSwitchBackToTagViewOnClick() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");

            app.clickViewButton("tags");
            app.waitForTreeContent("billing");

            then(app.isViewActive("tags")).isTrue();
        }

        @Test void shouldSwitchViewOnArrowKeys() {
            app.focusViewToggle();
            app.pressKey("ArrowLeft"); // tags is active (rightmost), ArrowLeft switches to paths
            app.waitForTreeContent("invoices");

            then(app.isViewActive("paths")).isTrue();
        }

        @Test void shouldFocusModeToggleOnArrowUpFromViewToggle() {
            app.focusViewToggle();

            app.pressKey("ArrowUp");
            app.waitForModeToggleFocused();

            then(app.isModeToggleFocused()).isTrue();
        }

        @Test void shouldNotJumpToViewToggleOnArrowDownFromModeToggle() {
            app.waitForDetailContent("List invoices");
            app.focusModeToggle();

            app.pressKey("ArrowDown");

            then(app.isViewToggleFocused()).isFalse();
        }

        @Test void shouldNotJumpToViewToggleOnArrowUpFromModeToggle() {
            app.waitForDetailContent("List invoices");
            app.focusModeToggle();

            app.pressKey("ArrowUp");

            then(app.isViewToggleFocused()).isFalse();
        }

        @Test void shouldNotWrapRightWhenOnLastView() {
            app.focusViewToggle();
            // tags is already active (rightmost), ArrowRight should not wrap to paths
            app.pressKey("ArrowRight");

            then(app.isViewActive("tags")).isTrue();
        }

        @Test void shouldNotWrapLeftWhenOnFirstView() {
            app.focusViewToggle();
            app.pressKey("ArrowLeft"); // switch from tags to paths (leftmost)
            app.waitForTreeContent("invoices");

            app.pressKey("ArrowLeft"); // already on paths (leftmost), should not wrap

            then(app.isViewActive("paths")).isTrue();
        }

        @Test void shouldSelectFirstViewOnHomeKey() {
            app.focusViewToggle();
            // tags is active (rightmost), Home should switch to paths (first)
            app.pressKey("Home");
            app.waitForTreeContent("invoices");

            then(app.isViewActive("paths")).isTrue();
        }

        @Test void shouldSelectLastViewOnEndKey() {
            app.focusViewToggle();
            app.pressKey("ArrowLeft"); // switch to paths first
            app.waitForTreeContent("invoices");

            app.pressKey("End");
            app.waitForTreeContent("billing");

            then(app.isViewActive("tags")).isTrue();
        }

        @Test void shouldKeepFocusOnViewToggleAfterSwitch() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");
            app.waitForViewToggleFocused();

            then(app.isViewToggleFocused()).isTrue();
        }

        @Test void shouldLoadDetailOnTagTreeOperationClick() {
            app.expandFirstNode();
            app.clickTreeNode("invoices/GET.html");
            app.waitForDetailContent("List invoices");

            then(app.detailText()).contains("List invoices");
        }

        @Test void shouldCaptureTagViewScreenshot() {
            app.expandFirstNode();
            app.screenshot("tag-view");
        }

        @Test void shouldUpdateHashWithTagPrefixWhenClickingTagTreeItem() {
            app.expandFirstNode();
            app.clickTreeNode("invoices/GET.html");
            app.waitForDetailContent("List invoices");

            then(app.locationHash()).isEqualTo("#[billing]invoices/GET");
        }

        @Test void shouldNavigateToTagTreeItemFromHash() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");

            app.navigateTo("#[billing]payments/GET");
            app.waitForDetailContent("List payments");

            then(app.detailText()).contains("List payments");
            then(app.isViewActive("tags")).isTrue();
            then(app.isTreeItemSelected("payments/GET.html")).isTrue();
        }

        @Test void shouldPersistViewChoiceAcrossReload() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");

            app.navigateHome();
            app.waitForTreeContent("invoices");

            then(app.isViewActive("paths")).isTrue();
        }

        // Tag filter tests
        @Test void shouldShowFilterIconInPathView() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");

            then(app.hasFilterIcon()).isTrue();
        }
        @Test void shouldHideFilterIconInTagView() {
            // filter icon should only be visible in path view, not tag view
            then(app.hasFilterIcon()).isFalse();
        }

        @Test void shouldTogglePillPanelOnFilterIconClick() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");

            then(app.isFilterPanelVisible()).isFalse();

            app.clickFilterIcon();

            then(app.isFilterPanelVisible()).isTrue();

            app.clickFilterIcon();

            then(app.isFilterPanelVisible()).isFalse();
        }
        @Test void shouldSelectPillOnClick() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");
            app.clickFilterIcon();

            then(app.isFilterActive("billing")).isFalse();

            app.clickPill("billing");

            then(app.isFilterActive("billing")).isTrue();
        }
        @Test void shouldDeselectActivePillOnClick() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");
            app.clickFilterIcon();
            app.clickPill("billing");

            then(app.isFilterActive("billing")).isTrue();

            app.clickPill("billing");

            then(app.isFilterActive("billing")).isFalse();
        }
        @Disabled("todo") @Test void shouldFilterTreeWhenPillSelected() {}
        @Disabled("todo") @Test void shouldShowOnlyMatchingTreeItems() {}
        @Disabled("todo") @Test void shouldHideNonMatchingMethodBadges() {}
        @Test void shouldShowFilterIconAsActiveWhenFilterActive() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");
            app.clickFilterIcon();

            then(app.isFilterIconActive()).isFalse();

            app.clickPill("billing");

            then(app.isFilterIconActive()).isTrue();

            app.clickPill("billing");

            then(app.isFilterIconActive()).isFalse();
        }
        @Test void shouldShowStatusLineWhenFilterActive() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");
            app.clickFilterIcon();

            then(app.getFilterStatusLine()).isNull();

            app.clickPill("billing");

            var statusLine = app.getFilterStatusLine();
            then(statusLine).isNotNull();
            then(statusLine).matches("Showing \\d+ of \\d+ operations");
        }
        @Test void shouldPersistPanelStateAcrossReload() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");
            app.clickFilterIcon();

            then(app.isFilterPanelVisible()).isTrue();

            app.navigateHome();
            app.waitForTreeContent("invoices");

            then(app.isFilterPanelVisible()).isTrue();
        }
        @Test void shouldPersistSelectedTagAcrossReload() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");
            app.clickFilterIcon();
            app.clickPill("billing");

            then(app.isFilterActive("billing")).isTrue();

            app.navigateHome();
            app.waitForTreeContent("invoices");

            then(app.isFilterActive("billing")).isTrue();
        }
        @Disabled("TODO: investigate why filter panel is still visible in tag view") @Test void shouldHideFilterPanelWhenSwitchingToTagView() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");
            app.clickFilterIcon();

            then(app.isFilterPanelVisible()).isTrue();

            app.clickViewButton("tags");
            app.waitForTreeContent("billing");

            // Tag view should not have filter UI at all
            then(app.isFilterPanelVisible()).isFalse();
        }
        @Test void shouldRestoreFilterPanelWhenSwitchingBackToPathView() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");
            app.clickFilterIcon();

            then(app.isFilterPanelVisible()).isTrue();

            app.clickViewButton("tags");
            app.waitForTreeContent("billing");
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");

            then(app.isFilterPanelVisible()).isTrue();
        }
        @Disabled("todo") @Test void shouldClearFilterWhenNavigatingToFilteredOutItem() {}
        @Disabled("todo") @Test void shouldClearDetailPanelWhenSelectedItemFilteredOut() {}
        
        // Keyboard navigation tests
        @Test void shouldFocusFilterIconWithTab() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");
            app.focusViewToggle();
            
            app.pressKey("Tab");
            
            then(app.isFilterIconFocused()).isTrue();
        }
        @Test void shouldOpenPanelWithEnterOnFilterIcon() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");
            
            then(app.isFilterPanelVisible()).isFalse();
            
            app.pressEnterOnFilterIcon();
            
            then(app.isFilterPanelVisible()).isTrue();
        }
        @Test void shouldFocusFirstPillWhenOpeningPanel() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");
            
            app.pressEnterOnFilterIcon();
            
            then(app.isPillFocused("billing")).isTrue();
        }
        @Test void shouldTogglePanelWithSpaceOnFilterIcon() {
            app.clickViewButton("paths");
            app.waitForTreeContent("invoices");
            
            then(app.isFilterPanelVisible()).isFalse();
            
            app.pressSpaceOnFilterIcon();
            
            then(app.isFilterPanelVisible()).isTrue();
        }
    }

    @ResourceLock("deep-paths") @Nested class GivenAppWithDeepPaths {
        @RegisterExtension static AppFixture app = launch("deep-paths.yaml");

        @Test void shouldRenderMethodAddonsInSingleRow() {
            app.expandAllNodes();

            then(app.areMethodAddonsInSingleRow()).isTrue();
        }
    }

    @ResourceLock("multi-method") @Nested class GivenMultiMethodApp {
        @RegisterExtension static AppFixture app = launch("multi-method.yaml");

        @Test void shouldCaptureHeroScreenshot() {
            app.waitForDetailContent("List pets");
            app.expandAllNodes();
            app.screenshot("hero");
        }

        @Test void shouldNavigateToTabWhenClickingMethodBadge() {
            app.waitForDetailContent("List pets");
            app.expandFirstNode();
            app.clickTreeNode("pets/{id}/index.html");
            app.waitForDetailContent("Get pet by ID");

            app.clickMethodBadge("pets/index.html", "POST");
            app.waitForDetailContent("Create a pet");

            then(app.isTabActive(2)).isTrue();
            then(app.detailText()).contains("Create a pet");
        }

        @Test void shouldSwitchMethodTabOnClick() {
            app.waitForDetailContent("List pets");
            then(app.detailText()).contains("List pets");

            app.clickMethodTab(2);
            app.waitForDetailContent("Create a pet");

            then(app.detailText()).contains("Create a pet");
        }

        @Test void shouldFocusTabOnClick() {
            app.waitForDetailContent("List pets");

            app.clickMethodTab(2);
            app.waitForDetailContent("Create a pet");

            then(app.isTabFocused()).isTrue();
            then(app.focusedTabHasFocusRing()).isTrue();
        }

        @Test void shouldEnterTabsOnArrowRight() {
            app.focusTree();
            app.pressKey("ArrowRight"); // expand collapsed node
            app.pressKey("ArrowRight"); // enter tabs

            then(app.isTabFocused()).isTrue();
            app.screenshot("focus-tab");
        }

        @Test void shouldNavigateFromChevronWithArrowDown() {
            app.waitForDetailContent("List pets");
            app.focusDescriptionToggle();
            then(app.activeElementSelector()).contains("desc-toggle");

            app.pressKey("ArrowDown");
            then(app.activeElementSelector()).contains("custom-header-add");
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
            app.pressKey("ArrowRight"); // expand collapsed node
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
            app.pressKey("ArrowRight"); // expand collapsed node
            app.pressKey("ArrowRight"); // enter tabs
            app.pressKey("ArrowLeft"); // first tab → back to tree

            then(app.isTreeFocused()).isTrue();
        }

        @Test void shouldEnterCurrentTabOnEnter() {
            app.focusTree();
            app.pressKey("ArrowRight"); // expand collapsed node
            app.pressKey("ArrowRight"); // enter tabs, first tab (GET)
            app.pressKey("ArrowRight"); // switch to POST
            app.pressKey("Escape"); // back to tree
            app.pressKey("Enter"); // should re-enter on POST (current tab)

            then(app.isTabFocused()).isTrue();
            then(app.isTabActive(2)).isTrue();
        }

        @Test void shouldJumpIntoContentOnTabKeyFromMethodTab() {
            app.waitForDetailContent("List pets");
            app.focusTab(1);

            app.pressKey("Tab");

            then(app.isTabFocused()).isFalse();
            then(app.isTabActive(1)).isTrue(); // tab should NOT switch
            then(app.activeElementSelector()).contains("button"); // focus landed in content
        }

        @Test void shouldJumpToTreeOnShiftTabFromMethodTab() {
            app.waitForDetailContent("List pets");
            app.focusTab(1);

            app.pressKey("Shift+Tab");

            then(app.isTreeFocused()).isTrue();
        }

        @Test void shouldNavigateFromTabsToFields() {
            app.focusTree();
            app.pressKey("ArrowRight"); // expand collapsed node
            app.pressKey("ArrowDown"); // select {id}
            app.waitForInput("id");
            app.pressKey("ArrowRight"); // expand {id} node
            app.pressKey("ArrowRight"); // enter tabs
            app.pressKey("ArrowRight"); // switch to DELETE tab
            app.waitForDetailContent("Delete a pet"); // wait for tab swap
            app.pressKey("ArrowDown"); // enter fields

            then(app.activeElementTag()).isEqualTo("INPUT");
            app.screenshot("focus-field");
        }

        @Test void shouldFocusFirstButtonOnArrowDownFromTabWithoutParams() {
            app.waitForDetailContent("List pets");
            app.focusTab(1);

            app.pressKey("ArrowDown");

            then(app.activeElementSelector()).contains("button");
        }

        @Test void shouldReturnToTabsOnArrowUpFromFirstField() {
            app.focusTree();
            app.pressKey("ArrowRight"); // expand collapsed node
            app.pressKey("ArrowDown"); // select {id}
            app.waitForInput("id");
            app.pressKey("ArrowRight"); // expand {id} node
            app.pressKey("ArrowRight"); // enter tabs
            app.pressKey("ArrowDown"); // enter fields
            app.pressKey("ArrowUp"); // back to tabs

            then(app.isTabFocused()).isTrue();
        }

        @Test void shouldReturnToActiveTabOnShiftTabFromFirstField() {
            app.waitForDetailContent("List pets"); // GET is first/active tab
            app.focusTab(1);
            app.pressKey("ArrowDown"); // enter fields

            app.pressKey("Shift+Tab"); // back to tabs — should land on GET (active), not DELETE (last)

            then(app.isTabFocused()).isTrue();
            then(app.isTabActive(1)).isTrue(); // GET tab should still be active
            then(app.detailText()).contains("List pets"); // content should not switch
        }

        @Test void shouldReturnToActiveTabOnArrowUpFromField() {
            app.focusTree();
            app.pressKey("ArrowRight"); // expand collapsed node
            app.pressKey("ArrowDown"); // select {id}
            app.waitForInput("id");
            app.pressKey("ArrowRight"); // expand {id} node
            app.pressKey("ArrowRight"); // enter tabs on GET (first)
            app.pressKey("ArrowRight"); // switch to DELETE tab
            app.waitForDetailContent("Delete a pet");
            app.pressKey("ArrowDown"); // enter fields
            app.pressKey("ArrowUp"); // back to tabs — should land on DELETE (active), not switch to GET

            then(app.isTabFocused()).isTrue();
            then(app.isTabActive(2)).isTrue(); // DELETE tab should still be active
            then(app.detailText()).contains("Delete a pet"); // content should not switch
        }

        @Test void shouldNavigateUpFromModeToggle() {
            app.waitForDetailContent("List pets");
            app.focusModeToggle();

            app.pressKey("ArrowUp");

            // should find the nearest focusable element above (not jump to view toggle)
            then(app.isModeToggleFocused()).isFalse();
            then(app.isViewToggleFocused()).isFalse();
        }

        @Test void shouldBumpOnArrowDownFromModeToggle() {
            app.waitForDetailContent("List pets");
            app.focusModeToggle();

            app.pressKey("ArrowDown");

            then(app.isModeToggleFocused()).isTrue();
        }

        @Test void shouldFocusViewToggleOnArrowUpAtFirstItem() {
            app.focusTree();
            app.pressKey("ArrowUp"); // already first item → view toggle

            then(app.isViewToggleFocused()).isTrue();
        }

        @Test void shouldEscapeFromFieldsToTree() {
            app.focusTree();
            app.pressKey("ArrowRight"); // expand collapsed node
            app.pressKey("ArrowDown"); // select {id}
            app.waitForDetailContent("Get pet by ID");
            app.pressKey("ArrowRight"); // expand {id} node
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

        @Test void shouldSharePersistedPathParamBetweenMethods() {
            app.expandFirstNode();
            app.clickTreeNode("pets/{id}/index.html");
            app.waitForInput("id");
            app.fillInput("id", "42");
            app.toggleParamPersist("id");

            app.clickMethodTab(2); // switch to DELETE tab
            app.waitForDetailContent("Delete a pet");

            then(app.inputValue("id")).isEqualTo("42");
        }

        @Test void shouldSharePersistedPathParamWithSubpath() {
            app.expandFirstNode();
            app.clickTreeNode("pets/{id}/index.html");
            app.waitForInput("id");
            app.fillInput("id", "42");
            app.toggleParamPersist("id");

            app.expandAllNodes();
            app.clickTreeNode("pets/{id}/visits/{visitId}/index.html");
            app.waitForDetailContent("Get a visit");

            then(app.inputValue("petId")).isEqualTo("42");
        }

        @Test void shouldLoadResponseSchemaForSubpathWithRenamedParam() {
            app.expandAllNodes();
            app.clickTreeNode("pets/{id}/visits/{visitId}/index.html");
            app.waitForDetailContent("Get a visit");
            app.toggleSchema("response");

            then(app.statusCodeTabs()).containsExactly("200");
        }
    }

    @ResourceLock("relative-base") @Nested class GivenAppWithRelativeBase {
        @RegisterExtension static AppFixture app = launch("relative-base.yaml");

        @Test void shouldResolveRelativeBaseUrlInTryMode() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List items");
            app.mockRootEndpoint("/items", "application/json", "{\"id\":\"1\"}");
            app.clickSend();
            app.waitForResponse();

            then(app.responseText()).contains("\"id\"");
        }

        @Test void shouldSendRequestWithPathParamInTryMode() {
            app.mockRootEndpoint("/items/42", "application/json", "{\"id\":\"42\",\"name\":\"Widget\"}");
            app.expandFirstNode();
            app.clickTreeNode("items/{itemId}/index.html");
            app.waitForInput("itemId");
            app.fillInput("itemId", "42");
            app.clickSend();
            app.waitForResponse();

            then(app.responseText()).contains("Widget");
        }
    }

    @ResourceLock("no-summary") @Nested class GivenAppWithNoSummary {
        @RegisterExtension static AppFixture app = launch("no-summary.yaml");

        @Test void shouldNotShowEmDashWithoutSummary() {then(app.selectedTreeItemText()).doesNotContain("—");}
    }

    @ResourceLock("params") @Nested class GivenAppWithParams {
        @RegisterExtension static AppFixture app = launch("params.yaml");

        @Test void shouldNavigateArrowDownFromChevronToField() {
            app.waitForDetailContent("Get a pet");
            app.focusDescriptionToggle();
            then(app.activeElementSelector()).contains("desc-toggle");

            app.pressKey("ArrowDown");

            then(app.activeElementSelector()).contains("input")
                    .doesNotContain("data-path");
        }

        @Test void shouldNotSwitchModeOnNumberKeyFromInput() {
            app.waitForInput("petId");
            app.focusInput("petId");

            app.pressKey("2");

            then(app.currentMode()).isEqualTo("try");
        }

        @Test void shouldNotJumpToTabOnShiftTabFromSecondField() {
            app.waitForInput("petId");
            app.focusInput("fields"); // focus second parameter field

            app.pressKey("Shift+Tab"); // should stay in content (normal tab order), not jump to tab

            then(app.activeElementSelector()).contains("input");
            then(app.isTabFocused()).isFalse();
        }

        @Test void shouldTogglePersistWithShortcut() {
            app.waitForInput("petId");
            app.focusInput("petId");

            app.pressKey(MOD + "+p");

            then(app.isParamPersisted("petId")).isTrue();
        }

        @Test void shouldShowPinUprightWhenPinned() {
            app.waitForInput("petId");
            app.focusInput("petId");
            app.pressKey(MOD + "+p");

            then(app.pinIconRotationAfterTransition("petId")).isEqualTo(0);
        }

        @Test void shouldShowPinTiltedWhenUnpinned() {
            app.waitForDetailContent("Get a pet");

            then(app.pinIconRotationAfterTransition("petId")).isEqualTo(45);
        }

        @Test void shouldPersistSelectQueryParam() {
            app.selectOption("status", "adopted");
            app.toggleParamPersist("status");
            app.navigateHome();
            then(app.selectValue("status")).isEqualTo("adopted");
        }

        @Test void shouldNotOverlapSelectCaretWithPinIcon() {
            app.waitForDetailContent("Get a pet");

            then(app.pinIconRight("status")).isGreaterThanOrEqualTo(20);
        }

        @Test void shouldOpenSelectOnEnterInsteadOfSend() {
            app.waitForDetailContent("Get a pet");
            app.focusSelect("status");

            app.pressKey("Enter");

            then(app.activeElementTag()).isEqualTo("SELECT");
            then(app.isModeToggleFocused()).isFalse();
        }

        @ResourceLock("params-try") @Nested class InTryMode {
            @RegisterExtension static AppFixture app =
                    launch("params.yaml").withBaseUrlOverride();

            @Test void shouldSendRequestWithPathParamInTryMode() {
                app.mockEndpoint("/pets/42", "application/json", "{\"id\":\"42\",\"name\":\"Fido\"}");
                app.expandFirstNode();
                app.clickTreeNode("pets/{petId}/index.html");
                app.waitForInput("petId");
                app.fillInput("petId", "42");
                app.clickSend();
                app.waitForResponse();

                then(app.responseText()).contains("Fido");
                app.screenshot("try-mode-path-param");
            }

            @Test void shouldPreventSendWhenRequiredParamIsEmpty() {
                app.mockEndpoint("/pets/", "application/json", "{\"id\":\"1\"}");
                app.expandFirstNode();
                app.clickTreeNode("pets/{petId}/index.html");
                app.waitForInput("petId");
                app.clickSend();


                then(app.hasResponseStatus()).isFalse();
            }

            @Test void shouldRestoreFocusToModeToggleAfterSend() {
                app.mockEndpoint("/pets/42", "application/json", "{\"id\":\"42\"}");
                app.expandFirstNode();
                app.clickTreeNode("pets/{petId}/index.html");
                app.waitForInput("petId");
                app.fillInput("petId", "42");
                app.clickSend();
                app.waitForResponse();

                then(app.isModeToggleFocused()).isTrue();
            }
        }

        @Test void shouldPreventCopyWhenRequiredParamIsEmpty() {
            app.clickModeButton("curl");
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForInput("petId");
            app.clearClipboard();
            app.clickSend();

            then(app.readClipboard()).isEmpty();
        }

        @Test void shouldCopyCommandInCurlMode() {
            app.clickModeButton("curl");
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForInput("petId");
            app.fillInput("petId", "42");
            app.screenshot("params-filled");
            app.clickSend();

            then(app.readClipboard())
                    .contains("curl -X GET")
                    .contains("https://api.example.com/pets/42");
        }

        @Test void shouldShowCopyButtonLabelInCurlMode() {
            app.clickModeButton("curl");
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForInput("petId");

            then(app.sendButtonText()).isEqualTo("Copy");
        }

        @Test void shouldShowCopiedFeedbackInCurlMode() {
            app.clickModeButton("curl");
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForInput("petId");
            app.fillInput("petId", "42");
            app.clickSend();

            then(app.sendButtonText()).isEqualTo("Copied!");
        }

        @Test void shouldFocusModeToggleAfterCopyViaEnter() {
            app.clickModeButton("curl");
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForInput("petId");
            app.fillInput("petId", "42");
            app.focusInput("petId");
            app.pressKey("Enter");

            then(app.isModeToggleFocused()).isTrue();
        }

        @Test void shouldIncludeMethodInCurlMode() {
            app.clickModeButton("curl");
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForInput("petId");
            app.fillInput("petId", "42");
            app.clickSend();

            then(app.readClipboard()).startsWith("curl -X GET");
        }

        @Test void shouldCopyCommandInHttpieMode() {
            app.clickModeButton("httpie");
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForInput("petId");
            app.fillInput("petId", "42");
            app.clickSend();

            then(app.readClipboard())
                    .startsWith("https ")
                    .doesNotContain("GET")
                    .contains("api.example.com/pets/42");
        }
    }

    @ResourceLock("post-endpoint") @Nested class GivenAppWithPostEndpoint {
        @RegisterExtension static AppFixture app =
                launch("post-endpoint.yaml").withBaseUrlOverride();

        @Test void shouldSendPostRequestInTryMode() {
            app.mockEndpoint("/pets", "POST", "application/json", "{\"id\":1}");
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.clickSend();
            app.waitForResponse();

            then(app.responseText()).contains("\"id\"");
        }

        @Test void shouldIncludeMethodInHttpieCommandForPOST() {
            app.clickModeButton("httpie");
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.clickSend();

            then(app.readClipboard())
                    .startsWith("http POST ")
                    .contains("/api/pets")
                    .doesNotContain("localhost");
        }
    }

    @ResourceLock("httpie-localhost") @Nested class GivenAppWithHttpLocalhostUrl {
        @RegisterExtension static AppFixture app = launch("http-localhost.yaml");

        @Test void shouldCopyHttpieCommandWithPortForLocalhostGET() {
            app.clickModeButton("httpie");
            app.expandFirstNode();
            app.clickTreeNode("pets/{id}/index.html");
            app.waitForInput("id");
            app.fillInput("id", "42");
            app.clickSend();

            then(app.readClipboard())
                    .isEqualTo("http :8080/pets/42")
                    .doesNotContain("GET")
                    .doesNotContain("localhost");
        }
    }

    @ResourceLock("httpie-localhost-default-port") @Nested class GivenAppWithHttpLocalhostDefaultPortUrl {
        @RegisterExtension static AppFixture app = launch("http-localhost-default-port.yaml");

        @Test void shouldCopyHttpieCommandWithHostnameForLocalhostDefaultPortGET() {
            app.clickModeButton("httpie");
            app.expandFirstNode();
            app.clickTreeNode("pets/{id}/index.html");
            app.waitForInput("id");
            app.fillInput("id", "42");
            app.clickSend();

            then(app.readClipboard())
                    .isEqualTo("http localhost/pets/42")
                    .doesNotContain("GET");
        }
    }

    @ResourceLock("httpie-http-api") @Nested class GivenAppWithHttpApiUrl {
        @RegisterExtension static AppFixture app = launch("http-api.yaml");

        @Test void shouldCopyHttpieCommandWithHttpCommandForNonLocalhostGET() {
            app.clickModeButton("httpie");
            app.expandFirstNode();
            app.clickTreeNode("pets/{id}/index.html");
            app.waitForInput("id");
            app.fillInput("id", "42");
            app.clickSend();

            then(app.readClipboard())
                    .startsWith("http ")
                    .doesNotContain("GET")
                    .contains("api.example.com/pets/42")
                    .doesNotContain("http://");
        }
    }

    @ResourceLock("request-body") @Nested class GivenAppWithRequestBody {
        @RegisterExtension static AppFixture app =
                launch("request-body.yaml").withBaseUrlOverride();

        @Test void shouldPreventSendWhenRequiredBodyIsEmpty() {
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            
            // Verify textarea has required attribute
            then(app.requestBodyHasRequiredAttribute()).as("textarea should have required attribute").isTrue();
            
            app.clearRequestBody();
            app.clickSend();

            // Wait briefly to ensure form validation blocks submission (no response should appear)
            app.waitForTimeout(500);
            then(app.hasResponseStatus()).isFalse();
        }

        @Test void shouldKeepFocusInTextareaOnArrowDownWhenNotAtLastLine() {
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.focusRequestBody();
            app.setCursorAtStart();

            app.pressKey("ArrowDown");

            then(app.activeElementTag()).isEqualTo("TEXTAREA");
        }

        @Test void shouldMoveOutOfTextareaOnArrowDownAtLastLine() {
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.focusRequestBody();
            app.setCursorAtEnd();

            app.pressKey("ArrowDown");

            then(app.isModeToggleFocused()).isTrue();
        }

        @Test void shouldMoveOutOfTextareaOnArrowUpAtFirstLine() {
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.focusRequestBody();
            app.setCursorAtStart();

            app.pressKey("ArrowUp");

            then(app.activeElementSelector()).contains("schema-toggle");
        }

        @Test void shouldSendRequestBodyInTryMode() {
            app.mockEndpointWithBodyEcho("/pets", "POST");
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.fillRequestBody("{\"name\": \"Fido\", \"age\": 3}");
            app.clickSend();
            app.waitForResponse();

            then(app.responseText()).contains("Fido");
        }

        @Test void shouldIncludeRequestBodyInCurlMode() {
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

        @Test void shouldIncludeRequestBodyInHttpieMode() {
            app.clickModeButton("httpie");
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.fillRequestBody("{\"name\": \"Fido\"}");
            app.clickSend();

            then(app.readClipboard())
                    .contains("http POST")
                    .contains("echo '{\"name\": \"Fido\"}'");
        }

        @Test void shouldIncludeRequestBodyInJsFetchMode() {
            app.clickModeButton("overflow");
            app.selectDropdownFormat("JS fetch");
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.fillRequestBody("{\"name\": \"Fido\"}");
            app.clickSend();

            then(app.readClipboard())
                    .contains("fetch(")
                    .contains("method: 'POST'")
                    .contains("JSON.stringify({\"name\": \"Fido\"})");
        }

        @Test void shouldIncludeRequestBodyInJavaHttpClientMode() {
            app.clickModeButton("overflow");
            app.selectDropdownFormat("Java HttpClient");
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.fillRequestBody("{\"name\": \"Fido\"}");
            app.clickSend();

            then(app.readClipboard())
                    .contains("HttpRequest.newBuilder()")
                    .contains(".POST(HttpRequest.BodyPublishers.ofString(\"{\\\"name\\\": \\\"Fido\\\"}\"))");
        }

        @Test void shouldIncludeRequestBodyInJaxRsMode() {
            app.clickModeButton("overflow");
            app.selectDropdownFormat("JAX-RS");
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.fillRequestBody("{\"name\": \"Fido\"}");
            app.clickSend();

            then(app.readClipboard())
                    .contains("ClientBuilder.newClient()")
                    .contains(".post(Entity.json(")
                    .contains("{\"name\": \"Fido\"}");
        }

        @Test void shouldIncludeRequestBodyInPythonMode() {
            app.clickModeButton("overflow");
            app.selectDropdownFormat("Python");
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.fillRequestBody("{\"name\": \"Fido\"}");
            app.clickSend();

            then(app.readClipboard())
                    .contains("import requests")
                    .contains("requests.post(")
                    .contains("json={\"name\": \"Fido\"}");
        }

        @Test void shouldIncludeRequestBodyInGoMode() {
            app.clickModeButton("overflow");
            app.selectDropdownFormat("Go");
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.fillRequestBody("{\"name\": \"Fido\"}");
            app.clickSend();

            then(app.readClipboard())
                    .contains("http.NewRequest(")
                    .contains("\"POST\"")
                    .contains("strings.NewReader(");
        }

        @Test void shouldIncludeRequestBodyInMpRestClientMode() {
            app.clickModeButton("overflow");
            app.selectDropdownFormat("MP Rest Client");
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.fillRequestBody("{\"name\": \"Fido\"}");
            app.clickSend();

            then(app.readClipboard())
                    .contains("@RegisterRestClient")
                    .contains("interface PetsClient")
                    .contains("@POST")
                    .contains("@Consumes(MediaType.APPLICATION_JSON)");
        }

        @Test void shouldIncludeRequestBodyInSpringWebClientMode() {
            app.clickModeButton("overflow");
            app.selectDropdownFormat("Spring WebClient");
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.fillRequestBody("{\"name\": \"Fido\"}");
            app.clickSend();

            then(app.readClipboard())
                    .contains("WebClient.create(")
                    .contains(".post()")
                    .contains(".bodyValue(");
        }

        @Test void shouldIncludeRequestBodyInSpringRestTemplateMode() {
            app.clickModeButton("overflow");
            app.selectDropdownFormat("Spring RestTemplate");
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            app.fillRequestBody("{\"name\": \"Fido\"}");
            app.clickSend();

            then(app.readClipboard())
                    .contains("HttpHeaders headers")
                    .contains("HttpEntity<String>")
                    .contains("new RestTemplate()");
        }

        @Test void shouldShowBodyBoxWithSchemaToggle() {
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");

            then(app.hasBodyBox()).isTrue();
            then(app.hasSchemaToggle("body")).isTrue();
            app.toggleSchema("body");
            app.screenshot("request-body-schema");
        }

        @Test void shouldShowSchemaTreeWhenToggled() {
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");

            app.toggleSchema("body");

            then(app.isSchemaExpanded("body")).as("schema should be expanded").isTrue();
            then(app.hasSchemaTree("body")).as("schema tree should be visible").isTrue();
        }

        @Test void shouldAutoGrowTextareaWhenContentIsAdded() {
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            var initialHeight = app.requestBodyHeight();

            app.fillRequestBody("{\n  \"name\": \"Fido\",\n  \"age\": 3,\n  \"extra1\": \"a\",\n  \"extra2\": \"b\",\n  \"extra3\": \"c\",\n  \"extra4\": \"d\",\n  \"extra5\": \"e\",\n  \"extra6\": \"f\",\n  \"extra7\": \"g\",\n  \"extra8\": \"h\"\n}");

            then(app.requestBodyHeight()).isGreaterThan(initialHeight);
        }

        @Test void shouldAutoGrowTextareaWhenWidthIsReduced() {
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("Add a pet");
            // Content with long lines: fits at full width but wraps significantly at narrow width
            app.fillRequestBody("""
                    {
                      "name": "Fido the Golden Retriever", "age": 3, "active": true, "tag": "dog",
                      "breed": "labrador retriever mix", "color": "golden brown", "weight": 30,
                      "owner": "Jonathan Doe", "address": "123 Main Street, Springfield, IL 62704",
                      "phone": "+1-555-0123-4567", "email": "jonathan.doe@example.com",
                      "notes": "Friendly dog, needs daily walks and regular grooming appointments",
                      "vaccinated": true, "microchip": "985141000123456", "registered": "2024-01-15"
                    }""");
            var initialHeight = app.requestBodyHeight();

            app.setViewportSize(500, 720);
            app.waitMs(200);

            then(app.requestBodyHeight()).isGreaterThan(initialHeight);
        }
    }

    @ResourceLock("form-encoded") @Nested class GivenAppWithFormEncodedRequestBody {
        @RegisterExtension static AppFixture app = launch("form-encoded.yaml").withBaseUrlOverride();

        @Test void shouldRenderFormFieldsInsteadOfTextarea() {
            app.clickTreeNode("login/index.html");
            app.waitForDetailContent("User login");

            then(app.hasFormField("username")).isTrue();
            then(app.hasFormField("password")).isTrue();
            then(app.hasFormField("remember")).isTrue();
            then(app.hasRequestBodyTextarea()).isFalse();
            app.screenshot("form-encoded-fields");
        }
    }

    @ResourceLock("nested-paths") @Nested class GivenAppWithNestedPaths {
        @RegisterExtension static AppFixture app = launch("nested-paths.yaml");

        @Test void shouldNavigateTreeWithArrowKeys() {
            app.focusTree();
            then(app.selectedTreeItemText()).contains("pets");

            app.pressKey("ArrowDown");
            then(app.selectedTreeItemText()).isNotNull();
        }

        @Test void shouldSelectTreeNodeOnClick() {
            then(app.isTreeItemSelected("pets/index.html")).isTrue();

            app.focusTree();
            app.pressKey("ArrowRight"); // expand collapsed node
            app.clickTreeNode("pets/{petId}/index.html");

            then(app.isTreeItemSelected("pets/{petId}/index.html")).isTrue();
        }

        @Test void shouldFocusTreeAfterContentSwapOnTreeNodeClick() {
            app.focusTree();
            app.pressKey("ArrowRight"); // expand collapsed node
            app.pressKey("ArrowRight"); // enter tabs
            then(app.isTreeFocused()).isFalse();

            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForDetailContent("Get a pet");

            then(app.isTreeFocused()).isTrue();
        }

        @Test void shouldStartNodesCollapsed() {
            then(app.isTextVisible("{petId}")).isFalse();
            app.screenshot("tree-collapsed");
        }

        @Test void shouldPointTriangleRightWhenCollapsed() {
            app.focusTree();
            then(app.treeToggleRotation()).as("collapsed: rotated -90deg").isEqualTo(-90.0);

            app.pressKey("ArrowRight");
            then(app.treeToggleRotation()).as("expanded: no rotation").isEqualTo(0.0);

            app.pressKey("ArrowLeft");
            then(app.treeToggleRotation()).as("re-collapsed: rotated -90deg").isEqualTo(-90.0);
            app.screenshot("tree-toggle-rotation");
        }

        @Test void shouldExpandOnArrowRightAndCollapseOnArrowLeft() {
            app.focusTree();
            then(app.isTextVisible("{petId}")).isFalse();

            app.pressKey("ArrowRight");
            then(app.isTextVisible("{petId}")).isTrue();

            app.pressKey("ArrowLeft");
            then(app.isTextVisible("{petId}")).isFalse();
            app.screenshot("tree-expanded");
        }
    }

    @ResourceLock("multi-response-type") @Nested class GivenAppWithMultiResponseType {
        @RegisterExtension static AppFixture app = launch("multi-response-type.yaml");

        @Test void shouldShowResponseTypeSelect() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");

            then(app.hasResponseTypeSelect()).isTrue();
            then(app.responseTypeOptions()).containsExactly("application/json", "application/xml");
        }

        @ResourceLock("multi-response-type-try") @Nested class InTryMode {
            @RegisterExtension static AppFixture app =
                    launch("multi-response-type.yaml").withBaseUrlOverride();

            @Test void shouldSendAcceptHeaderForSelectedXml() {
                app.mockEndpointWithContentNegotiation("/pets", Map.of(
                        "application/json", "{\"format\":\"json\"}",
                        "application/xml", "<format>xml</format>"));
                app.focusTree();
                app.pressKey("Enter");
                app.waitForDetailContent("List pets");

                app.selectResponseType("application/xml");
                app.clickSend();
                app.waitForResponse();

                then(app.responseText()).contains("<format>xml</format>");
            }

            @Test void shouldSendAcceptHeaderForSelectedJson() {
                app.mockEndpointWithContentNegotiation("/pets", Map.of(
                        "application/json", "{\"format\":\"json\"}",
                        "application/xml", "<format>xml</format>"));
                app.focusTree();
                app.pressKey("Enter");
                app.waitForDetailContent("List pets");

                app.selectResponseType("application/json");
                app.clickSend();
                app.waitForResponse();

                then(app.responseText()).contains("json");
            }
        }
    }

    @ResourceLock("schema-descriptions") @Nested class GivenAppWithSchemaDescriptions {
        @RegisterExtension static AppFixture app = launch("schema-descriptions.yaml");

        @Test void shouldShowStatusCodeTabForSingleResponse() {
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForDetailContent("Get a pet");
            app.toggleSchema("response");

            then(app.statusCodeTabs()).containsExactly("200");
        }
    }

    @ResourceLock("default-status-code") @Nested class GivenAppWithDefaultStatusCode {
        @RegisterExtension static AppFixture app = launch("default-status-code.yaml");

        @Test void shouldShowDefaultStatusCodeTab() {
            app.expandFirstNode();
            app.clickTreeNode("items/{id}/index.html");
            app.waitForDetailContent("Get an item");
            app.toggleSchema("response");

            then(app.statusCodeTabs()).containsExactly("200", "default");
        }
    }

    @ResourceLock("unsorted-status-codes") @Nested class GivenAppWithUnsortedStatusCodes {
        @RegisterExtension static AppFixture app = launch("unsorted-status-codes.yaml");

        @Test void shouldSortStatusCodeTabsNumerically() {
            app.expandFirstNode();
            app.clickTreeNode("items/{id}/index.html");
            app.waitForDetailContent("Get an item");
            app.toggleSchema("response");

            then(app.statusCodeTabs()).containsExactly("200", "404", "500");
        }
    }

    @ResourceLock("rich-response") @Nested class GivenAppWithRichResponse {
        @RegisterExtension static AppFixture app = launch("rich-response.yaml");

        private void navigateToPetDetail() {
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForDetailContent("Get a pet");
        }

        @Test void shouldShowArrayResponseSchemaProperties() {
            app.expandFirstNode();
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("List pets");
            app.toggleSchema("response");

            then(app.schemaTypeBadge("response")).isEqualTo("array");
            then(app.schemaPropertyNames("response")).contains("id", "name");
        }

        @Test void shouldShowResponseBoxWithSchemaToggle() {
            navigateToPetDetail();

            then(app.hasResponseBox()).isTrue();
            then(app.hasSchemaToggle("response")).isTrue();
        }

        @Test void shouldFocusSchemaToggleWithVisibleRingOnClick() {
            navigateToPetDetail();

            app.toggleSchema("response");

            then(app.isSchemaToggleFocused("response")).isTrue();
            then(app.schemaToggleHasFocusRing("response")).isTrue();
        }

        @Test void shouldShowPropertyTreeWithTypeBadges() {
            navigateToPetDetail();
            app.toggleSchema("response");

            then(app.schemaPropertyNames("response")).contains("id", "name", "status");
            then(app.schemaPropertyType("response", "id")).isEqualTo("integer");
            then(app.schemaPropertyExample("response", "name")).contains("Max");
        }

        @Test void shouldShowStatusCodeTabs() {
            navigateToPetDetail();
            app.toggleSchema("response");

            then(app.statusCodeTabs()).containsExactly("200", "404");
            app.screenshot("response-schema-tabs");
        }

        @Test void shouldSwitchSchemaOnStatusCodeTabClick() {
            navigateToPetDetail();
            app.toggleSchema("response");

            app.clickStatusCodeTab("404");

            then(app.schemaPropertyNames("response")).contains("message");
            then(app.schemaPropertyNames("response")).doesNotContain("id");
        }

        @Test void shouldShowAcceptSelectInResponseBox() {
            navigateToPetDetail();

            then(app.hasResponseTypeSelect()).isTrue();
            then(app.responseTypeOptions()).containsExactly("application/json", "application/xml");
        }

        @Test void shouldSwitchStatusCodeTabWithArrowRight() {
            navigateToPetDetail();
            app.toggleSchema("response");

            app.focusStatusCodeTab("200");
            app.pressKey("ArrowRight");

            then(app.activeStatusCodeTab()).isEqualTo("404");
        }

        @Test void shouldSwitchStatusCodeTabWithArrowLeft() {
            navigateToPetDetail();
            app.toggleSchema("response");

            app.focusStatusCodeTab("404");
            app.pressKey("ArrowLeft");

            then(app.activeStatusCodeTab()).isEqualTo("200");
        }

        @Test void shouldFocusNextElementOnTabFromStatusCodeTab() {
            navigateToPetDetail();
            app.toggleSchema("response");

            app.focusStatusCodeTab("200");
            app.pressKey("Tab");

            // Tab should move to next focusable element, not activate the next status tab
            then(app.activeStatusCodeTab()).isEqualTo("200"); // 200 should still be active, not 404
            then(app.activeElementSelector()).doesNotContain("schema-status-tab"); // should not be on a status tab anymore
        }

        @Test void shouldFocusActiveTabOnShiftTabIntoStatusCodeTabs() {
            navigateToPetDetail();
            app.toggleSchema("response");

            app.focusStatusCodeTab("200");
            app.pressKey("Tab"); // move past status tabs

            app.pressKey("Shift+Tab"); // back to status tabs — should land on 200 (active), not 404 (last)

            then(app.activeElementSelector()).contains("schema-status-tab");
            then(app.activeStatusCodeTab()).isEqualTo("200");
        }

        @Test void shouldNavigateDownFromAcceptSelectToModeToggle() {
            navigateToPetDetail();
            app.focusSelect("accept");

            app.pressKey("ArrowDown");

            then(app.isModeToggleFocused()).isTrue();
        }

        @Test void shouldNotJumpToViewToggleOnArrowDownFromModeToggle() {
            navigateToPetDetail();
            app.focusModeToggle();

            app.pressKey("ArrowDown");

            then(app.isViewToggleFocused()).isFalse();
        }

        @Test void shouldNavigateRightFromAcceptSelectToSchemaToggle() {
            navigateToPetDetail();
            app.focusSelect("accept");

            app.pressKey("ArrowRight");

            then(app.isSchemaToggleFocused("response")).isTrue();
        }

        @Test void shouldNavigateLeftFromSchemaToggleToAcceptSelect() {
            navigateToPetDetail();
            app.focusSchemaToggle("response");

            app.pressKey("ArrowLeft");

            then(app.activeElementSelector()).contains("select");
        }

        @Test void shouldNavigateDownFromSchemaToggleToModeToggle() {
            navigateToPetDetail();
            app.focusSchemaToggle("response");

            app.pressKey("ArrowDown");

            then(app.isModeToggleFocused()).isTrue();
        }


    }

    @ResourceLock("url-nav") @Nested class UrlHashNavigation {
        @RegisterExtension static AppFixture app = launch("multi-method.yaml");

        @Test void shouldUpdateHashWhenClickingTreeItem() {
            app.waitForDetailContent("List pets");

            app.expandFirstNode();
            app.clickTreeNode("pets/{id}/index.html");
            app.waitForDetailContent("Get pet by ID");

            then(app.locationHash()).isEqualTo("#pets/{id}");
        }

        @Test void shouldUpdateHashWhenSwitchingMethodTab() {
            app.waitForDetailContent("List pets");

            app.clickMethodTab(2);
            app.waitForDetailContent("Create a pet");

            then(app.locationHash()).isEqualTo("#pets/POST");
        }

        @Test void shouldNavigateToTreeItemFromHash() {
            app.navigateTo("#pets/{id}");
            app.waitForDetailContent("Get pet by ID");

            then(app.detailText()).contains("Get pet by ID");
            then(app.isTreeItemSelected("pets/{id}/index.html")).isTrue();
        }

        @Test void shouldNavigateToMethodTabFromHash() {
            app.navigateTo("#pets/POST");
            app.waitForDetailContent("Create a pet");

            then(app.detailText()).contains("Create a pet");
            then(app.isTabActive(2)).isTrue();
        }

        @Test void shouldIncludeMethodInHashWhenEnteringTabs() {
            app.waitForDetailContent("List pets");
            app.focusTree();
            app.pressKey("ArrowRight"); // expand
            app.pressKey("ArrowRight"); // enter tabs

            then(app.locationHash()).isEqualTo("#pets/GET");
        }

        @Test void shouldRemoveMethodFromHashWhenReturningToTree() {
            app.waitForDetailContent("List pets");
            app.focusTree();
            app.pressKey("ArrowRight"); // expand
            app.pressKey("ArrowRight"); // enter tabs
            then(app.locationHash()).isEqualTo("#pets/GET");

            app.pressKey("Escape"); // back to tree

            then(app.locationHash()).isEqualTo("#pets");
        }

        @Test void shouldNavigateBackWithBrowserHistory() {
            app.waitForDetailContent("List pets");
            app.expandFirstNode();
            app.clickTreeNode("pets/{id}/index.html");
            app.waitForDetailContent("Get pet by ID");

            app.goBack();
            app.waitForDetailContent("List pets");

            then(app.detailText()).contains("List pets");
        }
    }

    @ResourceLock("tagged-nested") @Nested class GivenTaggedNestedApp {
        @RegisterExtension static AppFixture app = launch("tagged-nested.yaml");

        @Test void shouldRestoreTagHashWhenDefaultViewIsPaths() {
            then(app.isViewActive("paths")).isTrue();

            app.navigateTo("#[pets]pets/GET");
            app.waitForDetailContent("List pets");

            then(app.detailText()).contains("List pets");
            then(app.isViewActive("tags")).isTrue();
            then(app.isTreeItemSelected("pets/GET.html")).isTrue();
        }
    }

    @ResourceLock("header-params") @Nested class GivenAppWithHeaderParams {
        @RegisterExtension static AppFixture app = launch("header-params.yaml");

        @Test void shouldIncludeHeaderParamInCurlCommand() {
            app.clickModeButton("curl");
            app.fillInput("X-Request-ID", "test-123");
            app.clickSend();

            var clipboard = app.readClipboard();
            then(clipboard).contains("-H 'X-Request-ID: test-123'");
        }

        @Test void shouldAddCustomHeaderRow() {
            app.clickButton("+ Add custom header");
            then(app.customHeaderRowCount()).isEqualTo(1);
        }

        @Test void shouldAddCustomHeaderRowOnEnter() {
            app.focusAddCustomHeaderButton();
            app.pressKey("Enter");

            then(app.customHeaderRowCount()).isEqualTo(1);
        }

        @Test void shouldShowHeaderBadgeOnCustomHeaderRow() {
            app.clickButton("+ Add custom header");
            then(app.customHeaderHasBadge(0, "custom")).isTrue();
            then(app.customHeaderHasBadge(0, "header")).isTrue();
        }

        @Test void shouldRemoveCustomHeaderViaTagDelete() {
            app.clickButton("+ Add custom header");
            then(app.customHeaderRowCount()).isEqualTo(1);
            app.clickCustomHeaderTagDelete(0);
            then(app.customHeaderRowCount()).isEqualTo(0);
        }

        @Test void customHeaderDeleteShouldBeFocusable() {
            app.clickButton("+ Add custom header");

            then(app.isCustomHeaderDeleteFocusable(0)).isTrue();
        }

        @Test void shouldRemoveCustomHeaderOnEnter() {
            app.clickButton("+ Add custom header");
            then(app.customHeaderRowCount()).isEqualTo(1);

            app.focusCustomHeaderDelete(0);
            app.pressKey("Enter");

            then(app.customHeaderRowCount()).isEqualTo(0);
        }

        @Test void shouldRemoveCustomHeaderOnSpace() {
            app.clickButton("+ Add custom header");
            then(app.customHeaderRowCount()).isEqualTo(1);

            app.focusCustomHeaderDelete(0);
            app.pressKey(" ");

            then(app.customHeaderRowCount()).isEqualTo(0);
        }

        @Test void shouldAutoResizeCustomHeaderNameInput() {
            app.clickButton("+ Add custom header");
            var emptyWidth = app.customHeaderNameWidth(0);
            app.fillCustomHeaderName(0, "X-Very-Long-Custom-Header-Name");
            var filledWidth = app.customHeaderNameWidth(0);
            then(filledWidth).isGreaterThan(emptyWidth);
        }

        @Test void shouldIncludeCustomHeaderInCurlCommand() {
            app.clickModeButton("curl");
            app.fillInput("X-Request-ID", "req-1");
            app.clickButton("+ Add custom header");
            app.fillCustomHeader(0, "X-Debug", "true");
            app.clickSend();
            var clipboard = app.readClipboard();
            then(clipboard).contains("-H 'X-Debug: true'");
        }

        @Test void shouldRemoveCustomHeaderRow() {
            app.clickButton("+ Add custom header");
            then(app.customHeaderRowCount()).isEqualTo(1);
            app.removeCustomHeader(0);
            then(app.customHeaderRowCount()).isEqualTo(0);
        }

        @Test void shouldOverrideGlobalHeaderWithPerOperationHeader() {
            app.clickGlobalHeadersToggle();
            app.clickGlobalHeaderButton("+ Add global header");
            app.fillGlobalHeader(0, "X-Debug", "global");
            app.clickModeButton("curl");
            app.fillInput("X-Request-ID", "req-1");
            app.clickButton("+ Add custom header");
            app.fillCustomHeader(0, "X-Debug", "per-op");
            app.clickSend();
            var clipboard = app.readClipboard();
            then(clipboard).contains("-H 'X-Debug: per-op'");
            then(clipboard).doesNotContain("global");
        }

        @Test void shouldPersistSpecDefinedHeaderValue() {
            app.fillInput("X-Request-ID", "persist-me");
            app.toggleParamPersist("X-Request-ID");
            app.navigateHome();
            then(app.inputValue("X-Request-ID")).isEqualTo("persist-me");
        }

        @Test void shouldPersistQueryParamValue() {
            app.fillInput("limit", "25");
            app.toggleParamPersist("limit");
            app.navigateHome();
            then(app.inputValue("limit")).isEqualTo("25");
        }

        @Test void shouldPersistBooleanQueryParam() {
            app.checkCheckbox("verbose");
            app.toggleParamPersist("verbose");
            app.navigateHome();
            then(app.isCheckboxChecked("verbose")).isTrue();
        }

        @Test void shouldCleanUpOldKeyWhenRenamingPersistedCustomHeader() {
            app.clickButton("+ Add custom header");
            app.fillCustomHeader(0, "X-Debug", "verbose");
            app.toggleCustomHeaderPersist(0);
            app.fillCustomHeaderName(0, "X-Test");
            app.navigateHome();
            then(app.customHeaderRowCount()).isEqualTo(1);
            then(app.customHeaderName(0)).isEqualTo("X-Test");
        }

        @Test void shouldCleanUpOldKeyWhenRenamingRestoredPersistedCustomHeader() {
            app.clickButton("+ Add custom header");
            app.fillCustomHeader(0, "X-Debug", "verbose");
            app.toggleCustomHeaderPersist(0);
            app.navigateHome();
            app.fillCustomHeaderName(0, "X-Test");
            app.navigateHome();
            then(app.customHeaderRowCount()).isEqualTo(1);
            then(app.customHeaderName(0)).isEqualTo("X-Test");
        }

        @Test void shouldPersistPerOperationCustomHeader() {
            app.clickButton("+ Add custom header");
            app.fillCustomHeader(0, "X-Debug", "verbose");
            app.toggleCustomHeaderPersist(0);
            app.navigateHome();
            then(app.customHeaderRowCount()).isEqualTo(1);
            then(app.customHeaderName(0)).isEqualTo("X-Debug");
            then(app.customHeaderValue(0)).isEqualTo("verbose");
        }

        @Test void shouldRemovePersistedCustomHeaderFromStorageOnDelete() {
            app.clickButton("+ Add custom header");
            app.fillCustomHeader(0, "X-Debug", "verbose");
            app.toggleCustomHeaderPersist(0);
            app.navigateHome();
            then(app.customHeaderRowCount()).isEqualTo(1);

            app.removeCustomHeader(0);
            app.navigateHome();
            then(app.customHeaderRowCount()).isEqualTo(0);
        }

        @Test void shouldIncludeHeaderParamInHttpieCommand() {
            app.clickModeButton("httpie");
            app.fillInput("X-Request-ID", "test-456");
            app.clickSend();

            var clipboard = app.readClipboard();
            then(clipboard).contains("X-Request-ID:test-456");
        }

        @Test void shouldShowGlobalHeaderValueAsPlaceholderOnDocumentedHeader() {
            app.clickGlobalHeadersToggle();
            app.clickGlobalHeaderButton("+ Add global header");
            app.fillGlobalHeader(0, "X-Request-ID", "abc");

            then(app.inputPlaceholder("X-Request-ID")).isEqualTo("abc \u00A0\u00A0\u00A0// from global headers");
        }

        @Test void shouldClearPlaceholderWhenGlobalHeaderRemoved() {
            app.clickGlobalHeadersToggle();
            app.clickGlobalHeaderButton("+ Add global header");
            app.fillGlobalHeader(0, "X-Request-ID", "abc");
            then(app.inputPlaceholder("X-Request-ID")).isEqualTo("abc \u00A0\u00A0\u00A0// from global headers");

            app.removeGlobalHeader(0);

            then(app.inputPlaceholder("X-Request-ID")).isNullOrEmpty();
        }

        @Test void shouldUpdatePlaceholderWhenGlobalHeaderValueChanges() {
            app.clickGlobalHeadersToggle();
            app.clickGlobalHeaderButton("+ Add global header");
            app.fillGlobalHeader(0, "X-Request-ID", "old-value");
            then(app.inputPlaceholder("X-Request-ID")).isEqualTo("old-value \u00A0\u00A0\u00A0// from global headers");

            app.fillGlobalHeader(0, "X-Request-ID", "new-value");

            then(app.inputPlaceholder("X-Request-ID")).isEqualTo("new-value \u00A0\u00A0\u00A0// from global headers");
        }

        @Test void shouldNotShowPlaceholderWhenGlobalHeaderValueIsEmpty() {
            app.clickGlobalHeadersToggle();
            app.clickGlobalHeaderButton("+ Add global header");
            app.fillGlobalHeader(0, "X-Request-ID", "");

            then(app.inputPlaceholder("X-Request-ID")).isNullOrEmpty();
        }
    }

    @ResourceLock("nested-schema") @Nested class GivenAppWithNestedSchema {
        @RegisterExtension static AppFixture app = launch("nested-schema.yaml");

        private void navigateToPetDetail() {
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForDetailContent("Get a pet");
        }

        @Test void shouldShowNestedPropertiesCollapsedByDefault() {
            navigateToPetDetail();
            app.toggleSchema("response");

            then(app.hasNestedToggle("response")).isTrue();
            then(app.isNestedExpanded("response")).isFalse();
        }

        @Test void shouldExpandNestedPropertiesOnClick() {
            navigateToPetDetail();
            app.toggleSchema("response");

            app.clickNestedToggle("response");

            then(app.isNestedExpanded("response")).isTrue();
            then(app.nestedPropertyNames("response")).contains("id", "name");
            app.screenshot("nested-schema-expanded");
        }

        @Test void shouldCollapseNestedPropertiesOnSecondClick() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.clickNestedToggle("response");

            app.clickNestedToggle("response");

            then(app.isNestedExpanded("response")).isFalse();
        }

        @Test void shouldShowArrayItemPropertiesWhenExpanded() {
            app.focusTree();
            app.pressKey("ArrowDown"); // move to owners/
            app.pressKey("ArrowRight"); // expand owners/
            app.clickTreeNode("owners/{ownerId}/index.html");
            app.waitForDetailContent("Get an owner");
            app.toggleSchema("response");

            app.clickNestedToggle("response");

            then(app.isNestedExpanded("response")).isTrue();
            then(app.nestedPropertyNames("response")).contains("id", "name", "status");
        }
    }

    @ResourceLock("multi-method") @Nested class NavigationStatePreservation {
        @RegisterExtension static AppFixture app = launch("multi-method.yaml");

        @Test void shouldPreserveFieldValueAcrossNavigation() {
            app.waitForDetailContent("List pets");
            app.expandFirstNode();
            app.clickTreeNode("pets/{id}/index.html");
            app.waitForDetailContent("Get pet by ID");
            app.fillInput("id", "42");

            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("List pets");
            app.clickTreeNode("pets/{id}/index.html");
            app.waitForDetailContent("Get pet by ID");

            then(app.inputValue("id")).isEqualTo("42");
        }

        @Test void shouldPreserveResponseAcrossNavigation() {
            app.waitForDetailContent("List pets");
            app.expandFirstNode();
            app.clickTreeNode("pets/{id}/index.html");
            app.waitForDetailContent("Get pet by ID");
            app.mockRootEndpoint("/pets/42", "application/json", "{\"id\":42}");
            app.fillInput("id", "42");
            app.clickSend();
            app.waitForResponse();

            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("List pets");
            app.clickTreeNode("pets/{id}/index.html");
            app.waitForDetailContent("Get pet by ID");

            then(app.hasResponseStatus()).isTrue();
            then(app.responseText()).contains("\"id\"");
        }

        @Test void shouldPreserveSchemaExpandedStateAcrossNavigation() {
            app.waitForDetailContent("List pets");
            app.expandAllNodes();
            app.clickTreeNode("pets/{id}/visits/{visitId}/index.html");
            app.waitForDetailContent("Get a visit");
            app.toggleSchema("response");
            then(app.isSchemaExpanded("response")).isTrue();

            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("List pets");
            app.clickTreeNode("pets/{id}/visits/{visitId}/index.html");
            app.waitForDetailContent("Get a visit");

            then(app.isSchemaExpanded("response")).isTrue();
        }

        @Test void shouldNotPreserveSchemaExpandedStateAcrossReload() {
            app.waitForDetailContent("List pets");
            app.expandAllNodes();
            app.clickTreeNode("pets/{id}/visits/{visitId}/index.html");
            app.waitForDetailContent("Get a visit");
            app.toggleSchema("response");
            then(app.isSchemaExpanded("response")).isTrue();

            app.navigateHome();
            app.waitForDetailContent("List pets");
            app.expandAllNodes();
            app.clickTreeNode("pets/{id}/visits/{visitId}/index.html");
            app.waitForDetailContent("Get a visit");

            then(app.isSchemaExpanded("response")).isFalse();
        }
    }

    @ResourceLock("response-headers") @Nested class GivenAppWithResponseHeaders {
        @RegisterExtension static AppFixture app =
                launch("response-headers.yaml").withBaseUrlOverride();

        private void navigateToListPetsAndSend() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");
            app.clickSend();
            app.waitForResponse();
        }

        @Test void shouldShowDescriptionForDocumentedHeader() {
            app.mockEndpointWithHeaders("/pets", "application/json", "{\"id\":\"1\"}",
                    Map.of("X-Request-Id", "abc-123"));

            navigateToListPetsAndSend();
            // headers auto-expand on first send when documented headers exist

            then(app.responseHeaderDescription("x-request-id")).isEqualTo("Unique request identifier");
        }

        @Test void shouldShowResponseDescriptionBelowStatus() {
            app.mockEndpointWithHeaders("/pets", "application/json", "{\"id\":\"1\"}",
                    Map.of("X-Request-Id", "abc-123"));

            navigateToListPetsAndSend();

            then(app.responseStatusDescription()).isEqualTo("A list of pets");
            then(app.responseStatusDescriptionIsBeforeHeaders()).isTrue();
            app.screenshot("response-headers-documented");
        }

        @Test void shouldShowDeprecatedIndicator() {
            app.mockEndpointWithHeaders("/pets", "application/json", "{\"id\":\"1\"}",
                    Map.of("X-Deprecated-Header", "old-value"));

            navigateToListPetsAndSend();

            then(app.isResponseHeaderDeprecated("x-deprecated-header")).isTrue();
        }

        @Test void shouldShowMissingWarningForRequiredHeader() {
            app.mockEndpoint("/pets", "application/json", "{\"id\":\"1\"}");

            navigateToListPetsAndSend();

            then(app.responseHeaderText("x-request-id")).isEqualTo("(missing)");
            then(app.isResponseHeaderMissing("x-request-id")).isTrue();
        }

        @Test void shouldHideUndocumentedHeadersWhenDocumentedExist() {
            app.mockEndpointWithHeaders("/pets", "application/json", "{\"id\":\"1\"}",
                    Map.of("X-Request-Id", "abc-123"));

            navigateToListPetsAndSend();

            then(app.responseHeaderText("x-request-id")).isEqualTo("abc-123");
            then(app.undocumentedHeadersVisible()).isFalse();
            then(app.hasShowAllButton()).isTrue();
        }

        @Test void shouldRevealUndocumentedHeadersOnShowAll() {
            app.mockEndpointWithHeaders("/pets", "application/json", "{\"id\":\"1\"}",
                    Map.of("X-Request-Id", "abc-123"));

            navigateToListPetsAndSend();
            // headers auto-expand on first send when documented headers exist
            app.clickShowAll();

            then(app.undocumentedHeadersVisible()).isTrue();
            then(app.responseHeaderText("content-type")).contains("application/json");
        }

        @Test void shouldAutoExpandOnFirstSendWhenDocumentedHeadersExist() {
            app.mockEndpointWithHeaders("/pets", "application/json", "{\"id\":\"1\"}",
                    Map.of("X-Request-Id", "abc-123"));

            navigateToListPetsAndSend();

            then(app.responseHeadersVisible()).isTrue();
        }

        @Test void shouldShowResponseSchemaBox() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");

            then(app.hasResponseSchemaBox()).isTrue();
        }

        @Test void shouldShowResponseDescriptionInSchema() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");
            app.toggleSchema("response");

            then(app.schemaResponseDescription("200")).isEqualTo("A list of pets");
        }

        @Test void shouldShowDocumentedHeadersInSchema() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");
            app.toggleSchema("response");

            then(app.schemaHeaderName("200", 0)).isEqualTo("X-Request-Id");
            then(app.schemaHeaderDescription("200", 0)).isEqualTo("Unique request identifier");
        }

        @Test void shouldShowRequiredBadgeOnSchemaHeader() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");
            app.toggleSchema("response");

            then(app.schemaHeaderHasBadge("200", 0, "required")).isTrue();
        }

        @Test void shouldShowDeprecatedBadgeOnSchemaHeader() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");
            app.toggleSchema("response");

            then(app.schemaHeaderHasBadge("200", 1, "deprecated")).isTrue();
        }

        @Test void shouldPreserveCollapsedStateOnResend() {
            app.mockEndpointWithHeaders("/pets", "application/json", "{\"id\":\"1\"}",
                    Map.of("X-Request-Id", "abc-123"));
            navigateToListPetsAndSend();
            then(app.responseHeadersVisible()).as("auto-expanded on first send").isTrue();
            app.toggleResponseHeaders();
            then(app.responseHeadersVisible()).as("collapsed after toggle").isFalse();

            app.resendAndWaitForHeaders();

            then(app.responseHeadersVisible()).as("stays collapsed on resend").isFalse();
        }
    }

    @ResourceLock("response-set-cookie") @Nested class GivenAppWithSetCookieHeader {
        @RegisterExtension static AppFixture app = launch("response-set-cookie.yaml").withBaseUrlOverride();

        @Test void shouldShowBrowserLimitationNoteForSetCookieHeader() {
            app.mockEndpoint("/login", "application/json", "{\"ok\":true}");
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("Login");
            app.clickSend();
            app.waitForResponse();

            then(app.responseHeaderText("set-cookie")).contains("not visible to JS");
        }
    }

    @ResourceLock("cookie-param") @Nested class GivenAppWithCookieParam {
        @RegisterExtension static AppFixture app = launch("cookie-param.yaml").withBaseUrlOverride();

        @Test void shouldShowCookieLimitationNoteAtField() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");

            then(app.cookieNoticeText()).contains("browser manages this automatically");
        }

        @Test void shouldDisableCookieParamInTryModeAndEnableInCurl() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");
            then(app.isInputDisabled("session")).isTrue();

            app.clickModeButton("curl");
            then(app.isInputDisabled("session")).isFalse();

            app.clickModeButton("try");
            then(app.isInputDisabled("session")).isTrue();
        }

        @Test void shouldSkipDisabledFieldOnArrowDown() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");
            app.focusTab(1);

            app.pressKey("ArrowDown"); // should skip disabled cookie input

            then(app.activeElementSelector()).contains("button");
        }

        @Test void shouldIncludeCookieParamInCurlCommand() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");
            app.clickModeButton("curl");
            app.fillInput("session", "abc123");
            app.clickSend();

            then(app.readClipboard()).contains("-b 'session=abc123'");
        }

        @Test void shouldIncludeCookieParamInHttpieCommand() {
            app.focusTree();
            app.pressKey("Enter");
            app.waitForDetailContent("List pets");
            app.clickModeButton("httpie");
            app.fillInput("session", "abc123");
            app.clickSend();

            then(app.readClipboard()).contains("Cookie:session=abc123");
        }
    }

    @ResourceLock("response-links") @Nested class GivenAppWithResponseLinks {
        @RegisterExtension static AppFixture app = launch("response-links.yaml");

        private void navigateToPetDetail() {
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForDetailContent("Get a pet");
        }

        @Test void shouldShowLinksInlineInSchema() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.expandNestedSchema("owner");
            then(app.inlineLinkNames("200")).contains("GetOwner");
        }

        @Test void shouldShowLinkDescription() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.expandNestedSchema("owner");
            then(app.inlineLinkDescription("200", "GetOwner")).isEqualTo("Get the owner of this pet");
        }

        @Test void shouldShowLinkParameters() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.expandNestedSchema("owner");
            then(app.inlineLinkParams("200", "GetOwner")).containsExactly("ownerId ← id");
        }

        @Test void shouldNotShowLinksWhenNoLinks() {
            app.expandFirstNode();
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("List pets");
            app.toggleSchema("response");
            then(app.inlineLinkNames("200")).isEmpty();
        }

        @Test void shouldAddOperationIdToForm() {
            navigateToPetDetail();

            then(app.operationFormAttribute("data-operation-id")).isEqualTo("getPet");
        }

        @Test void shouldHaveHrefOnInlineLink() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.expandNestedSchema("owner");
            then(app.inlineLinkHref("200", "GetOwner")).contains("#owners/{ownerId}/GET");
        }

        @Test void shouldNavigateToTargetOperationWhenClickingInlineLink() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.expandNestedSchema("owner");
            app.clickInlineLink("200", "GetOwner");
            app.waitForDetailContent("Get an owner");
        }

        @Test void shouldFocusFirstFieldAfterClickingInlineLink() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.expandNestedSchema("owner");
            app.clickInlineLink("200", "GetOwner");
            app.waitForDetailContent("Get an owner");
            app.waitForFocusedInput("ownerId");

            then(app.focusedInputName()).isEqualTo("ownerId");
        }

        @Disabled("not reliable")
        @Test void shouldRestoreFocusOnLinkWhenNavigatingBack() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.expandNestedSchema("owner");
            // Navigate away via schema link (pushes history entry)
            app.clickInlineLink("200", "GetOwner");
            app.waitForDetailContent("Get an owner");
            // Navigate back — focus should return to the GetOwner link
            app.goBack();
            app.waitForDetailContent("Get a pet");
            app.waitForInlineLinkFocused("200", "GetOwner");

            then(app.isInlineLinkFocused("200", "GetOwner")).isTrue();
        }

        @Test void shouldShowInlineLinkAsClickable() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.expandNestedSchema("owner");
            then(app.inlineLinkCursor("200", "GetOwner")).isEqualTo("pointer");
        }

        @Test void shouldShowBodyLinkAsSubRowUnderSourceProperty() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.expandNestedSchema("owner");
            then(app.schemaLinkSubRowExists("200", "GetOwner")).isTrue();
        }

        @Test void shouldShowBodyLinkOnlyUnderMatchingNestedProperty() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.expandNestedSchema("owner");
            then(app.schemaLinkSubRowCount("200", "GetOwner")).isEqualTo(1);
        }

        @Test void shouldMakeInlineLinkNameClickable() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.expandNestedSchema("owner");
            then(app.inlineLinkCursor("200", "GetOwner")).isEqualTo("pointer");
        }

        @Test void shouldShowHeaderLinkAsSubRowUnderSourceHeader() {
            navigateToPetDetail();
            app.toggleSchema("response");
            then(app.schemaLinkSubRowExists("200", "GetPetByRequestId")).isTrue();
        }

        @Test void shouldShowHeaderLinkDescription() {
            navigateToPetDetail();
            app.toggleSchema("response");
            then(app.inlineLinkDescription("200", "GetPetByRequestId")).isEqualTo("Look up by request ID");
        }

        @Test void shouldShowHeaderLinkParams() {
            navigateToPetDetail();
            app.toggleSchema("response");
            then(app.inlineLinkParams("200", "GetPetByRequestId")).containsExactly("requestId ← X-Request-Id");
        }

        @Test void shouldNavigateWhenClickingHeaderLink() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.clickInlineLink("200", "GetPetByRequestId");
            app.waitForDetailContent("Get pet by request ID");
        }

        @Test void shouldTakeScreenshotOfResponseLinks() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.screenshot("response-links");
        }

        @Test void shouldEmbedResponseLinksDataOnForm() {
            navigateToPetDetail();

            var linksData = app.operationFormAttribute("data-response-links");

            then(linksData).isNotNull();
            then(linksData).contains("getOwner");
            then(linksData).contains("$response.body#/owner/id");
        }

        @Test void shouldFillFieldFromHashQueryParameter() {
            app.navigateToHash("pets/{petId}/GET?petId=42");
            app.waitForDetailContent("Get a pet");
            app.waitForInputValue("petId", "42");

            then(app.inputValue("petId")).isEqualTo("42");
        }

        @Test void shouldIgnoreUnknownHashQueryParameters() {
            app.navigateToHash("pets/{petId}/GET?petId=42&unknownParam=ignored");
            app.waitForDetailContent("Get a pet");
            app.waitForInputValue("petId", "42");

            then(app.inputValue("petId")).isEqualTo("42");
        }

        @Test void shouldShowXLinksInSchemaView() {
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForDetailContent("Get a pet");
            app.toggleSchema("response");
            app.expandNestedSchema("visits");

            then(app.schemaLinkSubRowExists("200", "GetVisitDetail")).isTrue();
        }

        @Test void schemaLinkRowShouldBeFocusable() {
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForDetailContent("Get a pet");
            app.toggleSchema("response");

            then(app.schemaLinkHasTabindex("200", "owner")).isTrue();
        }

        @Test void shouldNavigateToTargetOperationWhenPressingEnterOnInlineLink() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.expandNestedSchema("owner");
            app.focusInlineLink("200", "GetOwner");
            app.pressKey("Enter");

            app.waitForDetailContent("Get an owner");
        }

        @Test void shouldNavigateToTargetOperationWhenPressingSpaceOnInlineLink() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.expandNestedSchema("owner");
            app.focusInlineLink("200", "GetOwner");
            app.pressKey(" ");

            app.waitForDetailContent("Get an owner");
        }

        @Test void shouldNavigateWhenPressingEnterOnSchemaLinkRow() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.expandNestedSchema("owner");
            app.focusSchemaLinkRow("200", "GetOwner");
            app.pressKey("Enter");

            app.waitForDetailContent("Get an owner");
        }

        @Test void shouldNavigateWhenPressingSpaceOnSchemaLinkRow() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.expandNestedSchema("owner");
            app.focusSchemaLinkRow("200", "GetOwner");
            app.pressKey(" ");

            app.waitForDetailContent("Get an owner");
        }

        @Test void shouldPreserveNestedSchemaToggleStateAfterNavigatingAwayAndBack() {
            navigateToPetDetail();
            app.toggleSchema("response");
            app.expandNestedSchema("owner");
            then(app.isNestedSchemaExpanded("owner")).isTrue();

            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("List pets");

            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForDetailContent("Get a pet");

            then(app.isNestedSchemaExpanded("owner")).isTrue();
        }
    }

    @ResourceLock("response-body-links") @Nested class GivenAppWithResponseBodyLinks {
        @RegisterExtension static AppFixture app = launch("response-links.yaml").withBaseUrlOverride();

        private void navigateToPetDetailAndSend() {
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForDetailContent("Get a pet");
            app.fillInput("petId", "42");
            app.mockEndpoint("/pets/42", "application/json", "{\"id\":42,\"name\":\"Buddy\",\"owner\":{\"id\":7,\"name\":\"Alice\"}}");
            app.clickSend();
            app.waitForResponse();
        }

        @Test void shouldShowLinkNameInBadge() {
            navigateToPetDetailAndSend();

            then(app.bodyLinkText(0)).isEqualTo("GetVisits");
        }

        @Test void shouldShowMultipleBadgesOnSameField() {
            navigateToPetDetailAndSend();

            var badgeTexts = IntStream.range(0, app.bodyLinkCount())
                    .mapToObj(app::bodyLinkText)
                    .toList();
            then(badgeTexts).contains("GetVisits", "GetPetAgain");
        }

        @Test void shouldWrapMatchingJsonValuesAsBodyLinks() {
            navigateToPetDetailAndSend();

            then(app.bodyLinkCount()).isEqualTo(3);
            then(app.bodyLinkText(0)).isEqualTo("GetVisits");
            then(app.bodyLinkHref(0)).contains("petId=42");
        }

        @Test void shouldNavigateToTargetOperationWhenClickingBodyLink() {
            navigateToPetDetailAndSend();
            app.clickBodyLink(2); // ownerId→getOwner

            app.waitForDetailContent("Get an owner");
        }

        @Test void shouldFillParameterFieldsWhenClickingBodyLink() {
            navigateToPetDetailAndSend();
            app.clickBodyLink(2); // ownerId=7 → getOwner
            app.waitForDetailContent("Get an owner");
            app.waitForInputValue("ownerId", "7");

            then(app.inputValue("ownerId")).isEqualTo("7");
        }

        @Test void shouldFocusLastFilledFieldAfterClickingBodyLink() {
            navigateToPetDetailAndSend();
            app.clickBodyLink(2); // ownerId=7 → getOwner
            app.waitForDetailContent("Get an owner");
            app.waitForInputValue("ownerId", "7");
            app.waitForFocusedInput("ownerId");

            then(app.focusedInputName()).isEqualTo("ownerId");
        }

        @Test void shouldLinkCorrectFieldWhenKeysAreDuplicated() {
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForDetailContent("Get a pet");
            app.fillInput("petId", "42");
            app.mockEndpoint("/pets/42", "application/json",
                    "{\"id\":42,\"name\":\"Buddy\",\"owner\":{\"id\":7,\"name\":\"Alice\"},\"extra\":{\"id\":99}}");
            app.clickSend();
            app.waitForResponse();

            // owner.id links to GetOwner; top-level id links to GetVisits and GetPetAgain; extra.id has no link
            then(app.bodyLinkCount()).isEqualTo(3);
            then(app.bodyLinkHref(2)).contains("ownerId=7");
        }

        @Test void shouldNotWrapNonMatchingJsonValues() {
            navigateToPetDetailAndSend();

            // "name":"Buddy" has no link; "id":42 links to GetVisits+GetPetAgain; "owner.id":7 links to GetOwner
            then(app.bodyLinkCount()).isEqualTo(3);
        }

        @Test void shouldShowBodyLinksAsClickable() {
            navigateToPetDetailAndSend();

            then(app.bodyLinkCursor(0)).isEqualTo("pointer");
        }

        @Test void shouldHandleNestedJsonWithMultipleIdFields() {
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForDetailContent("Get a pet");
            app.fillInput("petId", "1");
            app.mockEndpoint("/pets/1", "application/json",
                    "{\"id\":1,\"name\":\"Max\",\"owner\":{\"id\":3,\"name\":\"Alice\"}}");
            app.clickSend();
            app.waitForResponse();

            // owner.id links to GetOwner; top-level id links to GetVisits and GetPetAgain
            then(app.bodyLinkCount()).isEqualTo(3);
            then(app.bodyLinkHref(0)).contains("petId=1");
        }

        @Test void shouldTakeScreenshotOfResponseBodyLinks() {
            navigateToPetDetailAndSend();
            app.screenshot("response-body-links");
        }

        @Test void shouldStyleBadgeWithBorder() {
            navigateToPetDetailAndSend();

            then(app.bodyLinkBorderStyle(0)).isEqualTo("solid");
        }

        @Test void shouldNavigateToTargetOperationWhenPressingEnterOnBodyLink() {
            navigateToPetDetailAndSend();
            app.focusBodyLink(2); // ownerId→getOwner
            app.pressKey("Enter");

            app.waitForDetailContent("Get an owner");
        }

        @Test void shouldNavigateToTargetOperationWhenPressingSpaceOnBodyLink() {
            navigateToPetDetailAndSend();
            app.focusBodyLink(2); // ownerId→getOwner
            app.pressKey(" ");

            app.waitForDetailContent("Get an owner");
        }
    }

    @ResourceLock("x-links-body") @Nested class GivenAppWithXLinksBodyLinks {
        @RegisterExtension static AppFixture app = launch("response-links.yaml").withBaseUrlOverride();

        private void navigateToPetDetailAndSendWithVisits() {
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForDetailContent("Get a pet");
            app.fillInput("petId", "42");
            app.mockEndpoint("/pets/42", "application/json",
                    "{\"id\":42,\"name\":\"Buddy\",\"owner\":{\"id\":7,\"name\":\"Alice\"},"
                    + "\"visits\":[{\"id\":10,\"reason\":\"Checkup\"},{\"id\":11,\"reason\":\"Vaccination\"}]}");
            app.clickSend();
            app.waitForResponse();
        }

        @Test void shouldShowBodyLinkBadgesOnArrayItems() {
            navigateToPetDetailAndSendWithVisits();

            // Standard links: id→GetVisits, id→GetPetAgain, owner/id→GetOwner = 3
            // x-links: visits/0/id→GetVisitDetail, visits/1/id→GetVisitDetail = 2
            // Total = 5
            then(app.bodyLinkCount()).isEqualTo(5);
        }

        @Test void shouldShowXLinkBadgeWithCorrectName() {
            navigateToPetDetailAndSendWithVisits();

            var badgeTexts = IntStream.range(0, app.bodyLinkCount())
                    .mapToObj(app::bodyLinkText)
                    .toList();
            then(badgeTexts).contains("GetVisitDetail");
        }

        @Test void shouldFillParameterWhenClickingXLinkBadge() {
            navigateToPetDetailAndSendWithVisits();

            // Find the GetVisitDetail badge and click it
            var xLinkIndex = IntStream.range(0, app.bodyLinkCount())
                    .filter(i -> "GetVisitDetail".equals(app.bodyLinkText(i)))
                    .findFirst().orElseThrow();
            app.clickBodyLink(xLinkIndex);
            app.waitForDetailContent("Get a visit");
            app.waitForInputValue("visitId", "10");

            then(app.inputValue("visitId")).isEqualTo("10");
        }

        @Test void shouldExpandMultiLevelWildcards() {
            app.expandFirstNode();
            app.clickTreeNode("pets/{petId}/index.html");
            app.waitForDetailContent("Get a pet");
            app.fillInput("petId", "42");
            app.mockEndpoint("/pets/42", "application/json",
                    "{\"id\":42,\"name\":\"Buddy\",\"owner\":{\"id\":7,\"name\":\"Alice\"},"
                    + "\"visits\":[{\"id\":10,\"reason\":\"Checkup\","
                    + "\"treatments\":[{\"id\":100,\"name\":\"Antibiotics\"},{\"id\":101,\"name\":\"Painkillers\"}]}]}");
            app.clickSend();
            app.waitForResponse();

            var badgeTexts = IntStream.range(0, app.bodyLinkCount())
                    .mapToObj(app::bodyLinkText)
                    .toList();
            // Standard: GetVisits, GetPetAgain on id=42; GetOwner on owner/id=7 (3)
            // x-links: GetVisitDetail on visits/0/id=10 (1)
            // x-links: GetTreatment on visits/0/treatments/0/id=100, visits/0/treatments/1/id=101 (2)
            // Total = 6
            then(app.bodyLinkCount()).isEqualTo(6);
            then(badgeTexts).contains("GetTreatment");
        }
    }

    @ResourceLock("array-xlinks") @Nested class GivenAppWithArrayXLinks {
        @RegisterExtension static AppFixture app = launch("array-xlinks.yaml").withBaseUrlOverride();

        @Test void shouldShowBodyLinksForTopLevelArrayResponse() {
            app.expandFirstNode();
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("List pets");
            app.mockEndpoint("/pets", "application/json",
                    "[{\"id\":1,\"name\":\"Max\"},{\"id\":2,\"name\":\"Buddy\"}]");
            app.clickSend();
            app.waitForResponse();

            then(app.bodyLinkCount()).isEqualTo(2);
            then(app.bodyLinkText(0)).isEqualTo("pet");
        }

        @Test void shouldFillParameterWhenClickingBodyLinkToMultiMethodPath() {
            app.expandFirstNode();
            app.clickTreeNode("pets/index.html");
            app.waitForDetailContent("List pets");
            app.mockEndpoint("/pets", "application/json",
                    "[{\"id\":1,\"name\":\"Max\"},{\"id\":2,\"name\":\"Buddy\"}]");
            app.clickSend();
            app.waitForResponse();
            app.clickBodyLink(0);
            app.waitForDetailContent("Get a pet");
            app.waitForInputValue("id", "1");

            then(app.inputValue("id")).isEqualTo("1");
        }
    }

    @ResourceLock("multiple-servers") @Nested class GivenAppWithMultipleServers {
        @RegisterExtension static AppFixture app = launch("multiple-servers.yaml");

        @Test void shouldHaveServerDropdown() {
            then(app.hasServerPanel()).isTrue();
        }

        @Test void shouldShowServerDropdownInHeader() {
            then(app.isServerDropdownInHeader()).isTrue();
        }

        @Test void shouldShowServerItems() {
            app.clickServerToggle();
            then(app.serverItemCount()).isEqualTo(3);
        }

        @Test void shouldLabelFirstItemWithUrl() {
            app.clickServerToggle();
            then(app.serverItemUrl(0)).isEqualTo("https://api.example.com");
        }

        @Test void shouldShowServerDescriptions() {
            app.clickServerToggle();
            then(app.serverItemDescription(0)).isEqualTo("Production server");
        }

        @Test void shouldShowServerDescriptionBelowUrl() {
            app.clickServerToggle();
            then(app.isServerDescriptionBelowUrl(0)).isTrue();
        }

        @Test void shouldMarkFirstServerAsActive() {
            app.clickServerToggle();
            then(app.isServerItemActive(0)).isTrue();
        }

        @Test void shouldToggleServerDropdown() {
            app.clickServerToggle();
            then(app.isServerPanelExpanded()).isTrue();

            app.clickServerToggle();
            then(app.isServerPanelExpanded()).isFalse();
        }

        @Test void shouldKeepDropdownOpenAfterSelectingServer() {
            app.clickServerToggle();

            app.selectServer(1);

            then(app.isServerPanelExpanded()).isTrue();
        }

        @Test void shouldUseRadioButtonsForNonTemplateServers() {
            app.clickServerToggle();

            then(app.isServerItemActive(0)).isTrue();
        }

        @Test void shouldStartClosed() {
            then(app.isServerPanelExpanded()).isFalse();
        }

        @Test void shouldHaveDividersBetweenServerEntries() {
            app.clickServerToggle();
            then(app.serverDropdownDividerCount()).isEqualTo(3);
        }

        @Test void shouldHaveServerOverrideSlot() {
            then(app.hasElement("#server-override")).isTrue();
        }

        @Test void shouldShowKeyboardShortcutTooltipOnTrigger() {
            then(app.serverTriggerTooltip()).contains("+0");
        }

        @Test void shouldFocusInsideDropdownWhenOpened() {
            app.clickServerToggle();

            then(app.isFocusInsideServerDropdown()).isTrue();
        }

        @Test void shouldCloseDropdownOnEscape() {
            app.clickServerToggle();
            then(app.isServerPanelExpanded()).isTrue();

            app.pressKey("Escape");

            then(app.isServerPanelExpanded()).isFalse();
        }

        @Test void shouldToggleDropdownWithCtrl0() {
            app.focusTree(); // Ensure page has focus before keyboard shortcut

            app.pressKey(MOD + "+Digit0");

            then(app.isServerPanelExpanded()).isTrue();

            app.pressKey(MOD + "+Digit0");

            then(app.isServerPanelExpanded()).isFalse();
        }

        @Test void shouldCloseDropdownAndRestoreFocusOnEnter() {
            app.clickServerToggle();
            app.pressKey("ArrowDown");
            then(app.isServerItemActive(1)).isTrue();

            app.pressKey("Enter");

            then(app.isServerPanelExpanded()).isFalse();
        }

        @Test void shouldSelectNextServerOnArrowDown() {
            app.clickServerToggle();
            then(app.isServerItemActive(0)).isTrue();

            app.pressKey("ArrowDown");

            then(app.isServerItemActive(1)).isTrue();
        }

        @Test void shouldSelectPreviousServerOnArrowUp() {
            app.clickServerToggle();
            app.selectServer(2);

            app.pressKey("ArrowUp");

            then(app.isServerItemActive(1)).isTrue();
        }

        @Test void shouldBumpAtLastServerOnArrowDown() {
            app.clickServerToggle();
            app.selectServer(2);

            app.pressKey("ArrowDown");

            then(app.isServerItemActive(2)).isTrue();
        }

        @Test void shouldBumpAtFirstServerOnArrowUp() {
            app.clickServerToggle();
            then(app.isServerItemActive(0)).isTrue();

            app.pressKey("ArrowUp");

            then(app.isServerItemActive(0)).isTrue();
        }
    }

    @ResourceLock("custom-server-urls") @Nested class CustomServerUrls {
        @RegisterExtension static AppFixture app = launch("multiple-servers.yaml");

        @Test void shouldHaveAddCustomUrlButton() {
            app.clickServerToggle();

            then(app.hasCustomUrlButton()).isTrue();
        }

        @Test void shouldAddCustomUrlRow() {
            app.clickServerToggle();

            app.clickCustomUrlButton();

            then(app.customUrlRowCount()).isEqualTo(1);
        }

        @Test void shouldShowCustomUrlTextInput() {
            app.clickServerToggle();
            app.clickCustomUrlButton();

            then(app.hasCustomUrlInput()).isTrue();
        }

        @Test void shouldHaveDeleteButtonOnCustomUrl() {
            app.clickServerToggle();
            app.clickCustomUrlButton();

            then(app.hasCustomUrlDeleteButton()).isTrue();
        }

        @Test void shouldUsesBulmaDeleteForCustomUrlRemoveButton() {
            app.clickServerToggle();
            app.clickCustomUrlButton();

            then(app.customUrlDeleteButtonUsesBulmaDelete()).isTrue();
        }

        @Test void shouldDeleteCustomUrl() {
            app.clickServerToggle();
            app.clickCustomUrlButton();

            app.clickCustomUrlDelete(0);

            then(app.customUrlRowCount()).isEqualTo(0);
        }

        @Test void shouldPersistCustomUrlsInLocalStorage() {
            app.clickServerToggle();
            app.clickCustomUrlButton();
            app.setCustomUrlValue(0, "https://custom.example.com");

            var stored = app.getLocalStorageItem("openapi-ui-custom-urls");
            then(stored).isNotNull();
            then(stored).contains("https://custom.example.com");
        }

        @Test void shouldRestoreCustomUrlsOnReload() {
            app.setLocalStorageItem("openapi-ui-custom-urls", "[\"https://custom.example.com\"]");

            app.reload();
            app.clickServerToggle();

            then(app.customUrlRowCount()).isEqualTo(1);
            then(app.getCustomUrlValue(0)).isEqualTo("https://custom.example.com");
        }

        @Test void shouldSelectCustomUrl() {
            app.clickServerToggle();
            app.clickCustomUrlButton();
            app.setCustomUrlValue(0, "https://custom.example.com");

            app.selectCustomUrl(0);

            then(app.isCustomUrlSelected(0)).isTrue();
        }

        @Test void shouldCloseDropdownOnEnterInCustomUrlInput() {
            app.clickServerToggle();
            app.clickCustomUrlButton();
            app.setCustomUrlValue(0, "https://custom.example.com");
            app.focusCustomUrlInput(0);

            app.pressKey("Enter");

            then(app.isServerPanelExpanded()).isFalse();
        }

        @Test void shouldUpdateBaseUrlWhenCustomUrlSelected() {
            app.clickServerToggle();
            app.clickCustomUrlButton();
            app.setCustomUrlValue(0, "https://custom.example.com");

            app.selectCustomUrl(0);

            then(app.getBaseUrl()).isEqualTo("https://custom.example.com");
        }

        @Test void shouldSelectCustomUrlRadioOnAdd() {
            app.clickServerToggle();

            app.clickCustomUrlButton();

            then(app.isCustomUrlSelected(0)).isTrue();
        }

        @Test void shouldUpdateTriggerWhileTypingCustomUrl() {
            app.clickServerToggle();
            app.clickCustomUrlButton();

            app.setCustomUrlValue(0, "https://my-server.example.com");

            then(app.serverTriggerUrl()).isEqualTo("https://my-server.example.com");
        }

        @Test void shouldSelectUrlAboveAfterDeletingCustomUrl() {
            app.clickServerToggle();
            app.clickCustomUrlButton();
            app.setCustomUrlValue(0, "https://custom1.example.com");
            app.clickCustomUrlButton();
            app.setCustomUrlValue(1, "https://custom2.example.com");
            app.selectCustomUrl(1);

            app.clickCustomUrlDelete(1);

            then(app.getBaseUrl()).isEqualTo("https://custom1.example.com");
            then(app.customUrlRowCount()).isEqualTo(1);
        }
    }

    @ResourceLock("template-servers") @Nested class GivenAppWithTemplateServers {
        @RegisterExtension static AppFixture app = launch("template-servers.yaml");

        @Test void shouldShowTemplateUrlPattern() {
            then(app.templateServerUrlPattern(0)).isEqualTo("https://{environment}.example.com");
        }

        @Test void shouldShowTemplateDescriptionWithSameStyleAsNonTemplate() {
            app.clickServerToggle();
            then(app.templateServerDescription(0)).isNotEmpty();
            then(app.templateServerDescriptionUsesItemClass(0)).isTrue();
        }

        @Test void shouldShowDefaultPreset() {
            then(app.templateServerPresetCount(0)).isEqualTo(1);
        }

        @Test void shouldLabelDefaultPresetWithResolvedUrl() {
            then(app.templateServerPresetLabel(0, 0)).isEqualTo("https://api.example.com");
        }

        @Test void shouldShowPresetUrlWithMonospaceStyle() {
            app.clickServerToggle();
            then(app.templateServerPresetUrlUsesItemClass(0, 0)).isTrue();
        }

        @Test void shouldUseBulmaDeleteForPresetDeleteButton() {
            app.clickServerToggle();
            app.clickTemplateServerAddPreset(0);
            app.setTemplatePresetFormValue(0, "environment", "staging");
            app.clickTemplatePresetSave(0);

            then(app.presetDeleteButtonUsesBulmaDelete(0, 1)).isTrue();
        }

        @Test void shouldNotHaveBorderBetweenTemplateHeadingAndPresets() {
            then(app.templateGroupHeadingHasBottomBorder(0)).isFalse();
        }

        @Disabled("TODO") @Test void shouldShowNonTemplateServerAsRadio() {}

        @Test void shouldHaveAddPresetButton() {
            then(app.templateServerHasAddPresetButton(0)).isTrue();
        }

        @Test void shouldOpenFormOnAddPresetClick() {
            app.clickServerToggle();
            app.clickTemplateServerAddPreset(0);
            
            then(app.hasTemplateServerPresetForm(0)).isTrue();
        }

        @Test void shouldFocusFirstFieldOnAddPresetClick() {
            app.clickServerToggle();
            app.clickTemplateServerAddPreset(0);

            then(app.isPresetFormFirstFieldFocused(0)).isTrue();
        }

        @Test void shouldSavePresetOnEnterInFormField() {
            app.clickServerToggle();
            app.clickTemplateServerAddPreset(0);
            app.setTemplatePresetFormValue(0, "environment", "staging");

            app.pressKey("Enter");

            then(app.hasTemplateServerPresetForm(0)).isFalse();
            then(app.templateServerPresetCount(0)).isEqualTo(2);
        }

        @Test void shouldFocusSavedPresetRadio() {
            app.clickServerToggle();
            app.clickTemplateServerAddPreset(0);
            app.setTemplatePresetFormValue(0, "environment", "staging");
            app.clickTemplatePresetSave(0);

            then(app.isPresetRadioFocused(0, 1)).isTrue();
        }

        @Test void shouldCancelPresetFormOnEscape() {
            app.clickServerToggle();
            app.clickTemplateServerAddPreset(0);

            app.pressKey("Escape");

            then(app.hasTemplateServerPresetForm(0)).isFalse();
            then(app.templateServerPresetCount(0)).isEqualTo(1);
            then(app.isServerPanelExpanded()).isTrue();
        }

        @Test void shouldFocusPresetRadioAfterCancelClick() {
            app.clickServerToggle();
            app.clickTemplateServerAddPreset(0);

            app.clickTemplatePresetCancel(0);

            then(app.isPresetRadioFocused(0, 0)).isTrue();
        }

        @Test void shouldFocusPresetRadioAfterCancelEscape() {
            app.clickServerToggle();
            app.clickTemplateServerAddPreset(0);

            app.pressKey("Escape");

            then(app.isPresetRadioFocused(0, 0)).isTrue();
        }

        @Test void shouldCreateFormInputsFromVariableMetadata() {
            app.clickServerToggle();
            app.clickTemplateServerAddPreset(0);
            
            // First template server has one variable: "environment" with enum values
            then(app.templatePresetFormHasInput(0, "environment")).isTrue();
            then(app.templatePresetFormInputIsSelect(0, "environment")).isTrue();
        }

        @Test void shouldCreatePresetWithResolvedUrl() {
            app.clickServerToggle();
            app.clickTemplateServerAddPreset(0);
            
            // Keep default value "api" for environment variable, click Save
            app.clickTemplatePresetSave(0);
            
            // Should create a new preset with resolved URL
            then(app.templateServerPresetCount(0)).isEqualTo(2); // default + new one
            then(app.templateServerPresetLabel(0, 1)).isEqualTo("https://api.example.com");
        }

        @Test void shouldUpdateBaseUrlOnPresetSelection() {
            app.clickServerToggle();
            app.clickTemplateServerAddPreset(0);
            
            // Change environment to "staging" and save
            app.setTemplatePresetFormValue(0, "environment", "staging");
            app.clickTemplatePresetSave(0);
            
            // Select the new preset
            app.selectTemplatePreset(0, 1);
            
            // Base URL should be updated
            then(app.getBaseUrl()).isEqualTo("https://staging.example.com");
        }

        @Disabled("TODO") @Test void shouldDeleteUserCreatedPreset() {}

        @Disabled("TODO") @Test void shouldNotDeleteDefaultPreset() {}

        @Test void shouldPersistCreatedPresetInLocalStorage() {
            app.clickServerToggle();
            app.clickTemplateServerAddPreset(0);
            app.setTemplatePresetFormValue(0, "environment", "staging");
            app.clickTemplatePresetSave(0);

            app.reload();

            then(app.templateServerPresetCount(0)).isEqualTo(2);
            then(app.templateServerPresetLabel(0, 1)).isEqualTo("https://staging.example.com");
        }

        @Test void shouldNotRestoreDeletedPresetAfterReload() {
            app.clickServerToggle();
            app.clickTemplateServerAddPreset(0);
            app.setTemplatePresetFormValue(0, "environment", "staging");
            app.clickTemplatePresetSave(0);
            then(app.templateServerPresetCount(0)).isEqualTo(2);

            app.deleteTemplatePreset(0, 1);

            app.reload();

            then(app.templateServerPresetCount(0)).isEqualTo(1);
        }

        @Test void shouldSelectNewlyCreatedPreset() {
            app.clickServerToggle();
            app.clickTemplateServerAddPreset(0);
            app.setTemplatePresetFormValue(0, "environment", "staging");
            app.clickTemplatePresetSave(0);

            then(app.getBaseUrl()).isEqualTo("https://staging.example.com");
        }

        @Test void shouldSelectPresetAboveAfterDeletingPreset() {
            app.clickServerToggle();
            app.clickTemplateServerAddPreset(0);
            app.setTemplatePresetFormValue(0, "environment", "staging");
            app.clickTemplatePresetSave(0);
            app.clickTemplateServerAddPreset(0);
            app.setTemplatePresetFormValue(0, "environment", "dev");
            app.clickTemplatePresetSave(0);
            // dev (index 2) is selected

            app.deleteTemplatePreset(0, 2);

            then(app.getBaseUrl()).isEqualTo("https://staging.example.com");
        }
    }

    @ResourceLock("per-operation-servers") @Nested class PerOperationServers {
        @RegisterExtension static AppFixture app = launch("per-operation-servers.yaml");

        @Test void shouldShowOverrideWarningForOperationWithServers() {
            app.expandFirstNode();
            app.clickTreeNode("pets/index.html");
            app.clickMethodTab(2);
            app.waitForDetailContent("Create a pet");

            then(app.serverOverrideWarning()).isEqualTo("The operation selected below uses different servers");
            then(app.serverOverrideServerCount()).isEqualTo(2);
            then(app.serverOverrideServerUrl(0)).isEqualTo("https://write-api.example.com");
            then(app.serverOverrideServerUrl(1)).isEqualTo("https://write-staging.example.com");
        }

        @Test void shouldDisableGlobalServersWhenOverrideIsActive() {
            app.expandFirstNode();
            app.clickTreeNode("pets/index.html");
            app.clickMethodTab(2);
            app.waitForDetailContent("Create a pet");

            then(app.isGlobalServerDisabled(0)).isTrue();
            then(app.isGlobalServerDisabled(1)).isTrue();
        }

        @Test void shouldReEnableGlobalServersWhenSwitchingToNonOverrideOperation() {
            app.expandFirstNode();
            app.clickTreeNode("pets/index.html");
            app.clickMethodTab(2);
            app.waitForDetailContent("Create a pet");

            then(app.isGlobalServerDisabled(0)).isTrue();

            app.clickMethodTab(1);
            app.waitForDetailContent("List all pets");

            then(app.isGlobalServerDisabled(0)).isFalse();
            then(app.isGlobalServerDisabled(1)).isFalse();
        }

        @Test void shouldUseOverrideServerUrlForRequests() {
            app.expandFirstNode();
            app.clickTreeNode("pets/index.html");
            app.clickMethodTab(2);
            app.waitForDetailContent("Create a pet");

            then(app.getBaseUrl()).isEqualTo("https://write-api.example.com");
        }

        @Test void shouldUseGlobalServerUrlWhenNoOverride() {
            app.expandFirstNode();
            app.clickTreeNode("pets/index.html");
            app.clickMethodTab(1);
            app.waitForDetailContent("List all pets");

            then(app.getBaseUrl()).isEqualTo("https://api.example.com");
        }
    }

    @ResourceLock("cross-origin-security") @Nested class CrossOriginSecurityIntegration {
        @RegisterExtension static AppFixture app = launch("cross-origin-security.yaml");

        @Test void shouldHideBasicAuthFieldForSameOriginServer() {
            app.expandFirstNode();
            app.clickTreeNode("secure/index.html");
            app.waitForDetailContent("Secure endpoint");
            app.clickServerToggle();
            app.selectServer(0);

            then(app.isBrowserHandledAuthFieldVisible("basicAuth")).isFalse();
        }

        @Test void shouldHideCookieAuthFieldForSameOriginServer() {
            app.expandFirstNode();
            app.clickTreeNode("secure/index.html");
            app.waitForDetailContent("Secure endpoint");
            app.clickServerToggle();
            app.selectServer(0);

            then(app.isBrowserHandledAuthFieldVisible("cookieAuth")).isFalse();
        }

        @Test void shouldShowBasicAuthFieldForCrossOriginServer() {
            app.expandFirstNode();
            app.clickTreeNode("secure/index.html");
            app.waitForDetailContent("Secure endpoint");
            app.clickServerToggle();
            app.selectServer(1);

            then(app.isBrowserHandledAuthFieldVisible("basicAuth")).isTrue();
        }

        @Test void shouldShowCookieAuthFieldForCrossOriginServer() {
            app.expandFirstNode();
            app.clickTreeNode("secure/index.html");
            app.waitForDetailContent("Secure endpoint");
            app.clickServerToggle();
            app.selectServer(1);

            then(app.isBrowserHandledAuthFieldVisible("cookieAuth")).isTrue();
        }

        @Test void shouldAlwaysShowApiKeyFieldRegardlessOfOrigin() {
            app.expandFirstNode();
            app.clickTreeNode("secure/index.html");
            app.waitForDetailContent("Secure endpoint");
            app.clickServerToggle();
            app.selectServer(0);
            var sameOriginVisible = app.isTokenAuthFieldVisible("apiKeyAuth");

            app.selectServer(1);
            var crossOriginVisible = app.isTokenAuthFieldVisible("apiKeyAuth");

            then(sameOriginVisible).isTrue();
            then(crossOriginVisible).isTrue();
        }

        @Test void shouldAlwaysShowBearerFieldRegardlessOfOrigin() {
            app.expandFirstNode();
            app.clickTreeNode("secure/index.html");
            app.waitForDetailContent("Secure endpoint");
            app.clickServerToggle();
            app.selectServer(0);
            var sameOriginVisible = app.isTokenAuthFieldVisible("bearerAuth");

            app.selectServer(1);
            var crossOriginVisible = app.isTokenAuthFieldVisible("bearerAuth");

            then(sameOriginVisible).isTrue();
            then(crossOriginVisible).isTrue();
        }

        @Test void shouldShowBrowserHandledFieldsWhenSwitchingToCrossOrigin() {
            app.expandFirstNode();
            app.clickTreeNode("secure/index.html");
            app.waitForDetailContent("Secure endpoint");
            app.clickServerToggle();
            app.selectServer(0);
            var hiddenOnSameOrigin = app.isBrowserHandledAuthFieldVisible("basicAuth");

            app.selectServer(1);
            var shownOnCrossOrigin = app.isBrowserHandledAuthFieldVisible("basicAuth");

            then(hiddenOnSameOrigin).isFalse();
            then(shownOnCrossOrigin).isTrue();
        }

        @Test void shouldHideBrowserHandledFieldsWhenSwitchingToSameOrigin() {
            app.expandFirstNode();
            app.clickTreeNode("secure/index.html");
            app.waitForDetailContent("Secure endpoint");
            app.clickServerToggle();
            app.selectServer(1);
            var shownOnCrossOrigin = app.isBrowserHandledAuthFieldVisible("basicAuth");

            app.selectServer(0);
            var hiddenOnSameOrigin = app.isBrowserHandledAuthFieldVisible("basicAuth");

            then(shownOnCrossOrigin).isTrue();
            then(hiddenOnSameOrigin).isFalse();
        }
    }
}
