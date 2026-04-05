package com.github.t1.openapi.ui.cli;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import com.github.t1.openapi.ui.generator.OpenApiUiGenerator;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        System.exit(run(args, System.err));
    }

    static int run(String[] args, PrintStream err) {
        var argList = new ArrayList<>(Arrays.asList(args));
        var verbose = argList.remove("--verbose");
        if (verbose) enableVerboseLogging();
        if (argList.size() < 2) {
            err.println("Usage: openapi-ui [--verbose] <spec-file> <output-dir>");
            return 1;
        }
        try {
            new OpenApiUiGenerator(Path.of(argList.get(0)), Path.of(argList.get(1))).generate();
            return 0;
        } catch (Exception e) {
            if (verbose) {
                e.printStackTrace(err);
            } else {
                err.println(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            return 2;
        }
    }

    private static void enableVerboseLogging() {
        var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger("ROOT").setLevel(Level.DEBUG);
        var appender = loggerContext.getLogger("ROOT").getAppender("STDERR");
        if (appender instanceof OutputStreamAppender<ILoggingEvent> streamAppender) {
            var encoder = new PatternLayoutEncoder();
            encoder.setContext(loggerContext);
            encoder.setPattern("%msg%n");
            encoder.start();
            streamAppender.setEncoder(encoder);
        }
    }
}
