// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

/** The fast code generation settings in {@code pbj { }} reach the generate task, and stay unset unless configured. */
class PbjPerformanceSettingsTest {

    private static PbjCompilerTask generateTask(Project project) {
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(PbjCompilerPlugin.class);
        return project.getTasks()
                .named("generatePbjSource", PbjCompilerTask.class)
                .get();
    }

    @Test
    void settingsAreUnsetByDefault() {
        final PbjCompilerTask task = generateTask(ProjectBuilder.builder().build());
        assertFalse(task.getFastParse().isPresent());
        assertFalse(task.getFastWrite().isPresent());
        assertFalse(task.getGenerateViews().isPresent());
        assertFalse(task.getOneofStorage().isPresent());
        assertFalse(task.getPerformance().isPresent());
    }

    @Test
    void settingsReachTheTask() {
        final Project project = ProjectBuilder.builder().build();
        final PbjCompilerTask task = generateTask(project);
        final PbjExtension pbj = project.getExtensions().getByType(PbjExtension.class);
        pbj.getFastParse().set(true);
        pbj.getFastWrite().set(false);
        pbj.getGenerateViews().set(true);
        pbj.getOneofStorage().set("unboxed");
        pbj.getPerformance().set("max");
        assertEquals(true, task.getFastParse().get());
        assertEquals(false, task.getFastWrite().get());
        assertEquals(true, task.getGenerateViews().get());
        assertEquals("unboxed", task.getOneofStorage().get());
        assertEquals("max", task.getPerformance().get());
    }
}
