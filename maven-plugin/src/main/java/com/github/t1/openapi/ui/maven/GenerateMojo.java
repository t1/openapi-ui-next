package com.github.t1.openapi.ui.maven;

import com.github.t1.openapi.ui.generator.OpenApiUiGenerator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class GenerateMojo extends AbstractMojo {
    @Parameter(property = "openapi.specFile", required = true)
    File specFile;

    @Parameter(property = "openapi.outputDirectory",
               defaultValue = "${project.build.directory}/openapi-ui")
    File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            new OpenApiUiGenerator(specFile.toPath(), outputDirectory.toPath())
                    .generate();
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to generate OpenAPI UI", e);
        }
    }
}
