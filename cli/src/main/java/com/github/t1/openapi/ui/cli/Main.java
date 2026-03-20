package com.github.t1.openapi.ui.cli;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.github.t1.openapi.ui.OpenApiUiGenerator;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws Exception {
        var argList = new ArrayList<>(Arrays.asList(args));
        var verbose = argList.remove("--verbose");
        if (verbose) {
            var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            loggerContext.getLogger("ROOT").setLevel(Level.DEBUG);
        }
        if (argList.size() < 2) {
            throw new IllegalArgumentException(
                    "Usage: openapi-ui [--verbose] <spec-file> <output-dir>");
        }
        new OpenApiUiGenerator(Path.of(argList.get(0)), Path.of(argList.get(1))).generate();
    }
}
