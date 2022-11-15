package com.hedera.hashgraph.pbj.compiler;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

/**
 * Configuration extension to allow setting base package for generated code
 */
public abstract class PbjCompilerPluginExtension {
    @Input
    public abstract Property<String> getBasePackage();

    public PbjCompilerPluginExtension() {
        getBasePackage().convention("pbj");
    }
}
