package com.github.t1.openapi.ui.cli;

import com.github.t1.openapi.ui.OpenApiUiGenerator;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "Usage: openapi-ui <spec-file> <output-dir>");
        }
        new OpenApiUiGenerator(Path.of(args[0]), Path.of(args[1])).generate();
    }
}
