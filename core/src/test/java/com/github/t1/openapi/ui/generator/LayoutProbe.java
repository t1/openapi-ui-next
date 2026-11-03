package com.github.t1.openapi.ui.generator;

import com.github.t1.openapi.ui.generator.OpenApiUiFileGenerator;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

class LayoutProbe {
    @Test void probeServerRowLayout() throws Exception {
        var outputDir = Files.createTempDirectory("layout-probe");
        var specUrl = Optional.ofNullable(getClass().getResource("/multiple-servers.yaml"))
                .map(Object::toString).map(URI::create).map(Path::of).orElseThrow();
        new OpenApiUiFileGenerator(specUrl, outputDir).generate();

        var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", ex -> {
            var uriPath = ex.getRequestURI().getPath();
            if (uriPath.equals("/")) uriPath = "/index.html";
            var file = outputDir.resolve(uriPath.substring(1));
            if (Files.exists(file) && Files.isRegularFile(file)) {
                var bytes = Files.readAllBytes(file);
                ex.getResponseHeaders().set("Content-Type", uriPath.endsWith(".js") ? "application/javascript"
                        : uriPath.endsWith(".css") ? "text/css" : "text/html");
                ex.sendResponseHeaders(200, bytes.length);
                ex.getResponseBody().write(bytes);
            } else {
                ex.sendResponseHeaders(404, -1);
            }
            ex.close();
        });
        server.start();
        try (Playwright pw = Playwright.create()) {
            Browser b = pw.webkit().launch();
            Page p = b.newContext(new Browser.NewContextOptions().setViewportSize(1280, 800)).newPage();
            p.navigate("http://localhost:" + server.getAddress().getPort() + "/");
            p.locator("#server-selector .dropdown-trigger button").click();
            p.waitForTimeout(300);
            String js = "() => {"
                    + " const rows = [...document.querySelectorAll('#server-selector .server-row')];"
                    + " return rows.map(r => {"
                    + "  const radio = r.querySelector('input[type=radio]');"
                    + "  const url = r.querySelector('.server-item-url');"
                    + "  const rr = radio.getBoundingClientRect(), ur = url.getBoundingClientRect();"
                    + "  const cs = getComputedStyle(r), ucs = getComputedStyle(url);"
                    + "  return {radioRight: rr.right, urlLeft: ur.left, radioBottom: rr.bottom, urlTop: ur.top,"
                    + "   display: cs.display, flexWrap: cs.flexWrap, rowWidth: r.getBoundingClientRect().width,"
                    + "   urlWidth: ur.width, radioWidth: rr.width, urlDisplay: ucs.display, urlFlexBasis: ucs.flexBasis,"
                    + "   rowHTML: r.outerHTML.slice(0, 400)};"
                    + " });"
                    + "}";
            Object res = p.evaluate(js);
            System.out.println("PROBE=" + res);
            p.screenshot(new Page.ScreenshotOptions().setPath(Path.of("core/target/junit-jupiter/layout-probe.png")));
            b.close();
        }
        server.stop(0);
    }
}
