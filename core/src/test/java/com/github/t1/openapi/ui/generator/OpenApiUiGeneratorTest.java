package com.github.t1.openapi.ui.generator;

import com.github.valfirst.slf4jtest.LoggingEvent;
import com.github.valfirst.slf4jtest.TestLogger;
import com.github.valfirst.slf4jtest.TestLoggerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.smallrye.openapi.runtime.io.OpenApiParser;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.BDDAssertions.then;

class OpenApiUiGeneratorTest {
    @TempDir Path outputDir;

    TestLogger generatorLog = TestLoggerFactory.getTestLogger(OpenApiUiGenerator.class);
    TestLogger fileGeneratorLog = TestLoggerFactory.getTestLogger(OpenApiUiFileGenerator.class);

    @BeforeEach void clearLogs() {
        generatorLog.clear();
        fileGeneratorLog.clear();
    }

    void generate(String path) throws URISyntaxException, IOException {
        var specPath = Path.of(requireNonNull(getClass().getResource(path)).toURI());
        new OpenApiUiFileGenerator(specPath, outputDir).generate();
    }

    String logMessages() {
        return Stream.concat(
                        fileGeneratorLog.getLoggingEvents().stream(),
                        generatorLog.getLoggingEvents().stream())
                .map(LoggingEvent::getFormattedMessage)
                .collect(joining("\n"));
    }

    @Test void shouldLogParsingStep() throws Exception {
        generate("/one-get.yaml");

        then(logMessages()).contains("Parsing");
    }

    @Test void shouldLogPathAndOperationCount() throws Exception {
        generate("/nested-paths.yaml");

        then(logMessages()).contains("2 paths").contains("2 operations");
    }

    @Test void shouldLogCompletion() throws Exception {
        generate("/one-get.yaml");

        then(logMessages()).contains("Done");
    }

    @Test void shouldGroupPathsBySegments() throws Exception {
        generate("/nested-paths.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        then(indexHtml).contains("pets");
        then(indexHtml).contains("{petId}");
        int petsListItem = indexHtml.indexOf("pets");
        int nestedUl = indexHtml.indexOf("<ul", petsListItem);
        int petIdItem = indexHtml.indexOf("{petId}", nestedUl);
        then(petIdItem).as("{petId} should be in a nested list under pets").isGreaterThan(nestedUl);
    }

    @Test void shouldGenerateFragmentFiles() throws Exception {
        generate("/nested-paths.yaml");

        then(outputDir.resolve("pets/GET.html")).exists();
        then(outputDir.resolve("pets/{petId}/GET.html")).exists();
        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("List pets");
        then(fragment).contains("GET");
    }

    @Test void shouldIncludeHtmxAttributes() throws Exception {
        generate("/one-get.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        then(indexHtml).contains("vendor/htmx.min.js");
        then(indexHtml).contains("hx-get=\"pets/index.html\"");
        then(indexHtml).contains("hx-target=");
    }

    @Test void shouldGenerateParameterInputs() throws Exception {
        generate("/params.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).contains("name=\"petId\"");
        then(fragment).contains("name=\"fields\"");
        then(fragment).contains("Comma-separated list of fields");
    }

    @Test void shouldRenderParameterTypeBadge() throws Exception {
        generate("/params.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).contains("has-addons");
        then(fragment).contains(">path<");
        then(fragment).contains(">query<");
    }

    @Test void shouldRenderRequiredBadgeForMandatoryParam() throws Exception {
        generate("/params.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).contains("required");
    }

    @Test void shouldRenderEnumParameterAsSelect() throws Exception {
        generate("/params.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).contains("<select");
        then(fragment).contains("name=\"status\"");
        then(fragment).contains("available");
        then(fragment).contains("adopted");
        then(fragment).contains("pending");
    }

    @Test void shouldRenderPluralExamples() throws Exception {
        generate("/schema-descriptions.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).contains("e.g. 1");
    }

    @Test void shouldRenderPropertyDescription() throws Exception {
        generate("/schema-descriptions.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).contains("schema-prop-desc");
        then(fragment).contains("Unique identifier");
        then(fragment).contains("The display name of the pet");
    }

    @Test void shouldNotRenderDescriptionWhenMissing() throws Exception {
        generate("/schema-descriptions.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        // status property has no description — find its details span (between this data-prop and the next)
        int statusStart = fragment.indexOf("data-prop=\"status\"");
        int nextProp = fragment.indexOf("data-prop=", statusStart + 1);
        var statusSection = nextProp > 0 ? fragment.substring(statusStart, nextProp) : fragment.substring(statusStart);
        then(statusSection).doesNotContain("schema-prop-desc");
    }

    @Test void shouldRenderSchemaTitle() throws Exception {
        generate("/schema-descriptions.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).contains("schema-title");
        then(fragment).contains("Pet");
    }

    @Test void shouldRenderSchemaDescription() throws Exception {
        generate("/schema-descriptions.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).contains("schema-title-desc");
        then(fragment).contains("Represents a pet in the store");
    }

    @Test void shouldRenderPropertyTypesFromOpenApi31() throws Exception {
        generate("/schema-descriptions.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).contains(">integer<"); // id type
        then(fragment).contains(">string<"); // name type
        then(fragment).doesNotContain(">object<"); // no property should fall back to "object"
    }

    @Test void shouldRenderBooleanParameterAsCheckbox() throws Exception {
        generate("/nested-schema.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).contains("type=\"checkbox\"");
        then(fragment).contains("name=\"showVisits\"");
    }

    @Test void shouldRenderNestedObjectProperties() throws Exception {
        generate("/nested-schema.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).contains("schema-nested");
        then(fragment).contains("data-prop=\"id\""); // nested owner.id
        then(fragment).contains("data-prop=\"name\""); // nested owner.name (appears twice: pet + owner)
    }

    @Test void shouldRenderArrayItemProperties() throws Exception {
        generate("/nested-schema.yaml");

        var fragment = Files.readString(outputDir.resolve("owners/{ownerId}/GET.html"));
        then(fragment).contains("schema-nested");
        then(fragment).contains("data-prop=\"status\""); // nested pet item status
    }

    @Test void shouldMarkNestedSectionAsCollapsed() throws Exception {
        generate("/nested-schema.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).contains("aria-expanded=\"false\"");
    }

    @Test void shouldNotRenderSchemaTitleWhenMissing() throws Exception {
        generate("/rich-response.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).doesNotContain("schema-title");
    }

    @Test void shouldRenderRequestBodyWithSplitPane() throws Exception {
        generate("/request-body.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/POST.html"));
        then(fragment).contains("split-layout");
        then(fragment).contains("split-first");
        then(fragment).contains("split-second");
    }

    @Test void shouldNotRenderSplitPaneWhenNoSchema() throws Exception {
        generate("/request-body-media-type-example.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{id}/PATCH.html"));
        then(fragment).doesNotContain("split-layout");
        then(fragment).contains("data-request-body");
    }

    @Test void shouldNotRenderBodyBoxWhenSchemaHasNoPropertiesAndNoExamples() throws Exception {
        generate("/request-body-no-properties.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{id}/PATCH.html"));
        then(fragment).doesNotContain("data-box=\"body\"");
        then(fragment).doesNotContain("data-request-body");
    }

    @Test void shouldNotRenderResponseBoxWithoutSchema() throws Exception {
        generate("/response-no-schema.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).doesNotContain("data-box=\"response\"");
    }

    @Test void shouldRenderResponseSchema() throws Exception {
        generate("/params.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
        then(fragment).contains("id");
        then(fragment).contains("name");
        then(fragment).contains("string");
    }

    @Test void shouldGroupPathsWithDifferentParamNames() throws Exception {
        generate("/different-param-names.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        then(indexHtml).as("should not have a separate {petId} branch")
                .doesNotContain("{petId}");
        then(indexHtml).contains("{id}");
        then(indexHtml).contains("visits");
    }

    @Test void shouldUseActualParamNameInFragment() throws Exception {
        generate("/different-param-names.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{id}/visits/GET.html"));
        then(fragment).contains("petId");
        then(fragment).contains("/pets/{petId}/visits");
    }

    @Test void shouldUseParamClassForPathParameters() throws Exception {
        generate("/nested-paths.yaml");

        var html = Files.readString(outputDir.resolve("index.html"));

        then(html).contains("class=\"tree-param\"");
    }

    @Test void shouldGenerateRequestBodyTextarea() throws Exception {
        generate("/request-body.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/POST.html"));
        then(fragment).contains("data-request-body");
        then(fragment).contains("&quot;name&quot;");
        then(fragment).contains("&quot;age&quot;: 0");
        then(fragment).contains("&quot;active&quot;: false");
        then(fragment).as("required request body should have required attribute").contains("required");
    }

    @Test void shouldResolveRefSchemaInRequestBody() throws Exception {
        generate("/request-body-ref.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/POST.html"));
        then(fragment).contains("&quot;name&quot;: &quot;&quot;");
        then(fragment).contains("&quot;age&quot;: 0");
    }

    @Test void shouldUseExampleValueInSkeleton() throws Exception {
        generate("/request-body-samples.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/POST.html"));
        then(fragment).contains("&quot;withExample&quot;: &quot;Fido&quot;");
    }

    @Test void shouldUseDefaultValueInSkeleton() throws Exception {
        generate("/request-body-samples.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/POST.html"));
        then(fragment).contains("&quot;withDefault&quot;: &quot;unknown&quot;");
    }

    @Test void shouldUseFirstEnumValueInSkeleton() throws Exception {
        generate("/request-body-samples.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/POST.html"));
        then(fragment).contains("&quot;withEnum&quot;: &quot;available&quot;");
    }

    @Test void shouldQuoteEnumRefValueInSkeleton() throws Exception {
        generate("/request-body-samples.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/POST.html"));
        then(fragment).contains("&quot;withEnumRef&quot;: &quot;available&quot;");
    }

    @Test void shouldUseFormatBasedValueInSkeleton() throws Exception {
        generate("/request-body-samples.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/POST.html"));
        then(fragment).contains("&quot;withDateFormat&quot;: &quot;2024-01-15&quot;");
        then(fragment).contains("&quot;withDateTimeFormat&quot;: &quot;2024-01-15T12:00:00Z&quot;");
        then(fragment).contains("&quot;withEmailFormat&quot;: &quot;user@example.com&quot;");
        then(fragment).contains("&quot;withUriFormat&quot;: &quot;https://example.com&quot;");
        then(fragment).contains("&quot;withUuidFormat&quot;: &quot;3fa85f64-5717-4562-b3fc-2c963f66afa6&quot;");
    }

    @Test void shouldColorPatchSameAsPut() throws Exception {
        generate("/request-body-media-type-example.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{id}/PATCH.html"));
        then(fragment).contains("tag is-warning is-medium\">PATCH");
    }

    @Test void shouldUseMediaTypeExampleWhenSchemaHasNoProperties() throws Exception {
        generate("/request-body-media-type-example.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{id}/PATCH.html"));
        then(fragment).contains("&quot;name&quot; : &quot;Rex&quot;");
    }

    @Test void shouldRenderExampleSelectWhenMultipleNamedExamples() throws Exception {
        generate("/request-body-media-type-example.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{id}/PATCH.html"));
        then(fragment).contains("<select");
        then(fragment).contains("Rename a pet");
        then(fragment).contains("Update multiple fields");
    }

    @Test void shouldNotRenderExampleSelectWhenSingleExample() throws Exception {
        generate("/request-body-single-example.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{id}/PATCH.html"));
        then(fragment).doesNotContain("<select");
        then(fragment).contains("&quot;name&quot; : &quot;Rex&quot;");
    }

    @Test void shouldNotRenderBodySchemaToggleWhenNoProperties() throws Exception {
        generate("/request-body-single-example.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{id}/PATCH.html"));
        then(fragment).doesNotContain("schema-toggle");
    }

    @Test void shouldIncludeRequestBodyStyles() throws Exception {
        generate("/request-body.yaml");

        var css = Files.readString(outputDir.resolve("openapi-ui.css"));
        then(css).contains("data-request-body");
    }

    @Test void shouldRenderMethodTagAddons() throws Exception {
        generate("/multi-method.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        // Method addons in tree, not operation labels
        then(indexHtml).contains("class=\"tag is-");
        then(indexHtml).doesNotContain("class=\"tree-op-summary\"");
        then(indexHtml).doesNotContain("class=\"tree-op-label\"");
    }

    @Test void shouldGeneratePathFragmentWithTabs() throws Exception {
        generate("/multi-method.yaml");

        var pathFragment = Files.readString(outputDir.resolve("pets/index.html"));
        then(pathFragment).contains("class=\"tabs\"");
        then(pathFragment).contains("hx-get=\"pets/GET.html\"");
        then(pathFragment).contains("hx-get=\"pets/POST.html\"");
        then(pathFragment).contains("id=\"method-content\"");
        then(pathFragment).contains("List pets");
    }

    @Test void shouldColorCodeMethodTabs() throws Exception {
        generate("/multi-method.yaml");

        var pathFragment = Files.readString(outputDir.resolve("pets/index.html"));
        then(pathFragment).contains("data-method=\"GET\"");
        then(pathFragment).contains("data-method=\"POST\"");
    }

    @Test void shouldColorCodeStatusCodeTabs() throws Exception {
        generate("/unsorted-status-codes.yaml");

        var operationFragment = Files.readString(outputDir.resolve("items/{id}/GET.html"));
        then(operationFragment).contains("class=\"schema-status-tab is-active\" tabindex=\"0\" data-status=\"200\"");
        then(operationFragment).contains("class=\"schema-status-tab\" tabindex=\"0\" data-status=\"404\"");
        then(operationFragment).contains("class=\"schema-status-tab\" tabindex=\"0\" data-status=\"500\"");
    }

    @Test void shouldStillGenerateOperationFragments() throws Exception {
        generate("/multi-method.yaml");

        then(outputDir.resolve("pets/GET.html")).exists();
        then(outputDir.resolve("pets/POST.html")).exists();
        then(outputDir.resolve("pets/{id}/GET.html")).exists();
        then(outputDir.resolve("pets/{id}/DELETE.html")).exists();
        then(outputDir.resolve("pets/index.html")).exists();
        then(outputDir.resolve("pets/{id}/index.html")).exists();
    }

    @Test void shouldCombineSummaryAndDescriptionWithDash() throws Exception {
        generate("/multi-method.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("<strong>List pets</strong>")
                .contains("— Returns all pets from the system.")
                .contains("Pagination is not yet supported");
    }

    @Test void shouldRenderSummaryAloneWhenNoDescription() throws Exception {
        generate("/one-get.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("List pets");
        then(fragment).doesNotContain("—");
    }

    @Test void shouldWrapDescriptionInMessageBody() throws Exception {
        generate("/one-get.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/index.html"));
        then(fragment).contains("class=\"message");
    }

    @Test void shouldApplyLineClampToDescription() throws Exception {
        generate("/one-get.yaml");

        var css = Files.readString(outputDir.resolve("openapi-ui.css"));
        then(css).contains("op-description");
        then(css).contains("-webkit-line-clamp");
    }

    @Test void shouldRenderExpandChevron() throws Exception {
        generate("/multi-method.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("desc-toggle");
    }

    @Test void shouldRenderDeprecatedBadgeInHeaderRow() throws Exception {
        generate("/multi-method.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{id}/GET.html"));
        var headerRow = fragment.substring(fragment.indexOf("is-flex"), fragment.indexOf("</div>"));
        then(headerRow).contains("deprecated-badge");
    }

    @Test void shouldRenderTags() throws Exception {
        generate("/multi-method.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("op-tag");
        then(fragment).contains("pets");
    }

    @Test void shouldRenderExternalDocs() throws Exception {
        generate("/multi-method.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/{id}/GET.html"));
        then(fragment).contains("external-docs");
        then(fragment).contains("https://example.com/docs/pets");
    }

    @Test void shouldGroupMethodAddons() throws Exception {
        generate("/nested-paths.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        then(indexHtml).contains("tags has-addons");
    }

    @Test void shouldGenerateIndexWithOnePath() throws Exception {
        generate("/one-get.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        then(indexHtml).contains("pets");
        then(indexHtml).contains("GET");
    }

    @Test void shouldGenerateTagTreeFragment() throws Exception {
        generate("/tagged-flat.yaml");

        then(outputDir.resolve("tag-tree.html")).exists();
    }

    @Test void shouldRenderTagGroupsInTagTree() throws Exception {
        generate("/tagged-flat.yaml");

        var tagTree = Files.readString(outputDir.resolve("tag-tree.html"));
        then(tagTree).contains("billing");
        then(tagTree).contains("users");
    }

    @Test void shouldRenderOperationsUnderTagGroups() throws Exception {
        generate("/tagged-flat.yaml");

        var tagTree = Files.readString(outputDir.resolve("tag-tree.html"));
        then(tagTree).contains("GET");
        then(tagTree).contains("/invoices");
        then(tagTree).contains("tree-segment");
    }

    @Test void shouldApplyMethodTagClassToHttpMethodBadges() throws Exception {
        generate("/tagged-flat.yaml");

        var tagTree = Files.readString(outputDir.resolve("tag-tree.html"));
        then(tagTree).contains("method-tag");
    }

    @Test void shouldDuplicateMultiTaggedOperations() throws Exception {
        generate("/tagged-flat.yaml");

        var tagTree = Files.readString(outputDir.resolve("tag-tree.html"));
        // POST /users is tagged [users, billing] — hx-get="users/POST.html" should appear under both
        var billingSection = tagTree.indexOf("billing");
        var usersSection = tagTree.indexOf(">users<"); // the tag group header, not the path
        var postUsersInBilling = tagTree.indexOf("users/POST.html", billingSection);
        var postUsersInUsers = tagTree.indexOf("users/POST.html", usersSection);
        then(postUsersInBilling).as("POST /users should appear under billing").isGreaterThan(billingSection);
        then(postUsersInUsers).as("POST /users should appear under users").isGreaterThan(usersSection);
    }

    @Test void shouldShowAlsoInHintForMultiTaggedOperations() throws Exception {
        generate("/tagged-flat.yaml");

        var tagTree = Files.readString(outputDir.resolve("tag-tree.html"));
        then(tagTree).contains("also in");
    }

    @Test void shouldLinkTagTreeOperationsToOperationFragments() throws Exception {
        generate("/tagged-flat.yaml");

        var tagTree = Files.readString(outputDir.resolve("tag-tree.html"));
        then(tagTree).contains("hx-get=\"invoices/GET.html\"");
        then(tagTree).contains("hx-get=\"invoices/POST.html\"");
        then(tagTree).contains("hx-get=\"users/GET.html\"");
        then(tagTree).doesNotContain("hx-get=\"invoices/index.html\"");
    }

    @Test void shouldOrderTagsPerSpecDeclaration() throws Exception {
        generate("/tagged-flat.yaml");

        var tagTree = Files.readString(outputDir.resolve("tag-tree.html"));
        int billingPos = tagTree.indexOf("billing");
        int usersPos = tagTree.indexOf("users");
        then(billingPos).as("billing should appear before users").isLessThan(usersPos);
    }

    @Test void shouldGroupUntaggedOperationsUnderOther() throws Exception {
        generate("/tagged-with-untagged.yaml");

        var tagTree = Files.readString(outputDir.resolve("tag-tree.html"));
        then(tagTree).contains("Other");
    }

    @Test void shouldDefaultToTagViewForFlatTaggedApi() throws Exception {
        generate("/tagged-flat.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        then(indexHtml).contains("billing");
        then(indexHtml).contains("users");
    }

    @Test void shouldDefaultToPathViewForNestedTaggedApi() throws Exception {
        generate("/tagged-nested.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        then(indexHtml).contains("class=\"tree-segment\"");
    }

    @Test void shouldDefaultToPathViewForNoTagApi() throws Exception {
        generate("/one-get.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        then(indexHtml).contains("class=\"tree-segment\"");
    }

    @Test void shouldRenderViewToggleAboveTree() throws Exception {
        generate("/tagged-flat.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        int togglePos = indexHtml.indexOf("data-toggle=\"view\"");
        int treeContainerPos = indexHtml.indexOf("id=\"tree-container\"");
        then(togglePos).as("view toggle should appear before tree container").isGreaterThan(-1);
        then(togglePos).isLessThan(treeContainerPos);
    }

    @Test void shouldRenderPathsAndTagsSegments() throws Exception {
        generate("/tagged-flat.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        then(indexHtml).contains("data-toggle-value=\"paths\"");
        then(indexHtml).contains("data-toggle-value=\"tags\"");
    }

    @Test void shouldHaveHtmxAttributesOnViewToggle() throws Exception {
        generate("/tagged-flat.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        then(indexHtml).contains("hx-get=\"path-tree.html\"");
        then(indexHtml).contains("hx-get=\"tag-tree.html\"");
    }

    @Test void shouldRenderViewToggleEvenWithoutTags() throws Exception {
        generate("/one-get.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        then(indexHtml).contains("data-toggle=\"view\"");
    }

    @Test void shouldShowNoTagsMessageInTagTree() throws Exception {
        generate("/one-get.yaml");

        var tagTree = Files.readString(outputDir.resolve("tag-tree.html"));
        then(tagTree).contains("define any tags");
    }

    @Test void shouldShowSingleTagMessageInTagTree() throws Exception {
        generate("/single-tag.yaml");

        var tagTree = Files.readString(outputDir.resolve("tag-tree.html"));
        then(tagTree).contains("only one tag");
    }

    @Test void shouldGeneratePathTreeFragment() throws Exception {
        generate("/tagged-flat.yaml");

        then(outputDir.resolve("path-tree.html")).exists();
        var pathTree = Files.readString(outputDir.resolve("path-tree.html"));
        then(pathTree).contains("role=\"tree\"");
    }

    @Test void shouldStyleAlsoInHint() throws Exception {
        generate("/tagged-flat.yaml");

        var css = Files.readString(outputDir.resolve("openapi-ui.css"));
        then(css).contains("also-in");
    }

    @Test void shouldRenderDataParamInAttribute() throws Exception {
        generate("/header-params.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("data-param-in=\"query\"");
        then(fragment).contains("data-param-in=\"header\"");
    }

    @Test void shouldRenderHeaderParameterBadge() throws Exception {
        generate("/header-params.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains(">header<");
    }

    @Test void shouldRenderHeaderEnumParameterAsSelect() throws Exception {
        generate("/header-params.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("name=\"X-Api-Version\"");
        then(fragment).contains("data-param-in=\"header\"");
        then(fragment).contains("2024-01");
        then(fragment).contains("2024-06");
    }

    @Test void shouldRenderCustomHeaderArea() throws Exception {
        generate("/header-params.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("custom-headers");
        then(fragment).contains("Add custom header");
    }

    @Test void shouldRenderGlobalHeadersPanel() throws Exception {
        generate("/one-get.yaml");

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        then(indexHtml).contains("global-headers");
        then(indexHtml).contains("Global Headers");
    }

    @Test void shouldNotIncludeDataMethodOnTagTreeOperations() throws Exception {
        generate("/tagged-flat.yaml");

        var tagTree = Files.readString(outputDir.resolve("tag-tree.html"));
        then(tagTree).doesNotContain("data-method");
    }

    @Test void shouldGenerateResponseFragmentForDocumentedStatusCode() throws Exception {
        generate("/response-headers.yaml");

        then(outputDir.resolve("pets/GET-response-200.html")).exists();
        var fragment = Files.readString(outputDir.resolve("pets/GET-response-200.html"));
        then(fragment).contains("200 OK");
        then(fragment).contains("A list of pets");
        then(fragment).contains("X-Request-Id");
        then(fragment).contains("Unique request identifier");
    }

    @Test void shouldGenerateResponseFallbackFragment() throws Exception {
        generate("/response-headers.yaml");

        then(outputDir.resolve("pets/GET-response-fallback.html")).exists();
        var fragment = Files.readString(outputDir.resolve("pets/GET-response-fallback.html"));
        then(fragment).contains("response-status");
        then(fragment).doesNotContain("X-Request-Id");
    }

    @Test void shouldWrapSendButtonInResponseArea() throws Exception {
        generate("/response-headers.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("response-area");
        then(fragment).contains("type=\"submit\"");
    }

    @Test void shouldAcceptOpenAPIModelDirectly() throws Exception {
        var specPath = Path.of(requireNonNull(getClass().getResource("/one-get.yaml")).toURI());
        var openApi = OpenApiParser.parse(specPath.toUri().toURL());

        new OpenApiUiGenerator(openApi, writeToOutputDir()).generate();

        then(outputDir.resolve("index.html")).exists();
        then(outputDir.resolve("pets/GET.html")).exists();
    }

    @Test void shouldGenerateViaConsumer() throws Exception {
        var specPath = Path.of(requireNonNull(getClass().getResource("/one-get.yaml")).toURI());
        var openApi = OpenApiParser.parse(specPath.toUri().toURL());
        var files = new LinkedHashMap<String, byte[]>();

        new OpenApiUiGenerator(openApi, files::put).generate();

        then(files).containsKey("index.html");
        then(files).containsKey("pets/GET.html");
        then(files).containsKey("openapi-ui.css");
        then(new String(files.get("index.html"))).contains("Test API");
    }

    @Test void shouldOrderMethodsAsGetPostPutPatchDelete() throws Exception {
        generate("/unordered-methods.yaml");

        var indexHtml = Files.readString(outputDir.resolve("pets/index.html"));
        var getPos = indexHtml.indexOf("data-method=\"GET\"");
        var postPos = indexHtml.indexOf("data-method=\"POST\"");
        var putPos = indexHtml.indexOf("data-method=\"PUT\"");
        var deletePos = indexHtml.indexOf("data-method=\"DELETE\"");
        then(getPos).as("GET should appear").isGreaterThan(-1);
        then(getPos).as("GET before POST").isLessThan(postPos);
        then(postPos).as("POST before PUT").isLessThan(putPos);
        then(putPos).as("PUT before DELETE").isLessThan(deletePos);
    }

    @Test void shouldNotCrashOnEmptyContentMediaTypes() throws Exception {
        generate("/empty-content.yaml");

        var fragment = Files.readString(outputDir.resolve("items/GET.html"));
        then(fragment).contains("listItems");
    }

    @Test void shouldRenderXLinksWithWildcardInSchema() throws Exception {
        generate("/array-xlinks.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("schema-link-row");
        then(fragment).contains("→ pet");
    }

    @Test void shouldRenderArrayItemPropertiesFromRef() throws Exception {
        generate("/array-ref-response.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("schema-prop-name");
        then(fragment).contains("name");
        then(fragment).contains("id");
    }

    @Test void shouldRenderAuthSectionWhenGlobalSecurityDefined() throws Exception {
        generate("/security-bearer.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("auth-section");
    }

    @Test void shouldNotRenderAuthSectionWhenSecurityExplicitlyEmpty() throws Exception {
        generate("/security-empty.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).doesNotContain("auth-section");
    }

    @Test void shouldRenderApiKeyHeaderWithLockBadge() throws Exception {
        generate("/security-apikey-header.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("🔒");
        then(fragment).contains("apiKey");
        then(fragment).contains("X-API-Key");
        then(fragment).contains("<input");
        then(fragment).contains("data-param-in=\"auth-header\"");
        then(fragment).contains("name=\"X-API-Key\"");
    }

    @Test void shouldRenderApiKeyQueryWithAuthQueryAttribute() throws Exception {
        generate("/security-apikey-query.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("🔒");
        then(fragment).contains("apiKey");
        then(fragment).contains("api_key");
        then(fragment).contains("<input");
        then(fragment).contains("data-param-in=\"auth-query\"");
        then(fragment).contains("name=\"api_key\"");
    }

    @Test void shouldRenderBearerAuthWithLockBadge() throws Exception {
        generate("/security-bearer.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("🔒");
        then(fragment).contains("bearer");
        then(fragment).contains("Authorization");
        then(fragment).contains("<input");
        then(fragment).contains("data-param-in=\"auth-header\"");
        then(fragment).contains("name=\"Authorization\"");
    }

    @Test void shouldRenderHttpBasicAsInfoOnly() throws Exception {
        generate("/security-http-basic.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("🔒");
        then(fragment).contains("basic");
        then(fragment).contains("Basic authentication (handled by browser)");
        then(fragment).doesNotContain("<input");
    }

    @Test void shouldRenderOAuth2AsInfoOnly() throws Exception {
        generate("/security-oauth2.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("🔒");
        then(fragment).contains("oauth2");
        then(fragment).contains("authorizationCode");
        then(fragment).contains("https://example.com/oauth/authorize");
        then(fragment).contains("https://example.com/oauth/token");
        then(fragment).doesNotContain("<input");
    }

    @Test void shouldRenderOpenIdConnectAsInfoOnly() throws Exception {
        generate("/security-openidconnect.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("🔒");
        then(fragment).contains("openIdConnect");
        then(fragment).contains("https://example.com/.well-known/openid-configuration");
        then(fragment).doesNotContain("<input");
    }

    @Test void shouldRenderMutualTlsAsInfoOnly() throws Exception {
        generate("/security-mutualtls.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("🔒");
        then(fragment).contains("mutualTLS");
        then(fragment).contains("Mutual TLS (client certificate required)");
        then(fragment).doesNotContain("<input");
    }

    @Test void shouldRenderApiKeyCookieAsInfoOnly() throws Exception {
        generate("/security-apikey-cookie.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("🔒");
        then(fragment).contains("apiKey");
        then(fragment).contains("sessionid");
        then(fragment).contains("handled by browser");
        then(fragment).doesNotContain("<input");
    }

    @Test void shouldIncludeServerOverrideOobForPerOperationServers() throws Exception {
        generate("/per-operation-servers.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/POST.html"));
        then(fragment).contains("<div hx-swap-oob=\"innerHTML:#server-override\">");
        then(fragment).contains("The operation selected below uses different servers");
        then(fragment).contains("https://write-api.example.com");
        then(fragment).contains("Write-only server");
    }

    @Test void shouldIncludeEmptyServerOverrideOobForOperationsWithoutOverrides() throws Exception {
        generate("/per-operation-servers.yaml");

        var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
        then(fragment).contains("<div hx-swap-oob=\"innerHTML:#server-override\"></div>");
        then(fragment).doesNotContain("The operation selected below uses different servers");
    }

    @Test void shouldIncludeServerOverrideOobForPerPathServers() throws Exception {
        generate("/per-operation-servers.yaml");

        var fragment = Files.readString(outputDir.resolve("owners/GET.html"));
        then(fragment).contains("<div hx-swap-oob=\"innerHTML:#server-override\">");
        then(fragment).contains("The operation selected below uses different servers");
        then(fragment).contains("https://owners-api.example.com");
        then(fragment).contains("Owners service");
    }

    private BiConsumer<String, byte[]> writeToOutputDir() {
        return (name, content) -> {
            try {
                var file = outputDir.resolve(name);
                Files.createDirectories(file.getParent());
                Files.write(file, content);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
