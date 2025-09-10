// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler;

import static com.hedera.pbj.compiler.impl.LookupHelper.PROTO_EXTENSIION;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;

/**
 * A transform that transforms a Jar file to a folder that contains 'protobuf' files. If a given Jar file does not
 * contain any 'protobuf' file, the result of the transform is empty.
 */
@CacheableTransform
public abstract class PbjProtobufExtractTransform implements TransformAction<TransformParameters.None> {

    @InputArtifact
    @Classpath
    protected abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(@NonNull TransformOutputs outputs) {
        final var artifact = getInputArtifact().get().getAsFile();
        final var artifactName = artifact.getName();

        try {
            if (artifactName.toLowerCase().endsWith(".jar") && containsProtoFiles(artifact)) {
                final var destination = outputs.dir(artifactName.substring(0, artifactName.length() - 4));
                extractProtoFilesFromJar(artifact, destination);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean containsProtoFiles(File archive) throws IOException {
        try (var jarFile = new JarFile(archive)) {
            return jarFile.stream().anyMatch(entry -> entry.getName().endsWith(PROTO_EXTENSIION));
        }
    }

    private static void extractProtoFilesFromJar(File archive, File destination) throws IOException {
        try (var jis = new JarInputStream(Files.newInputStream(archive.toPath()))) {
            byte[] buffer = new byte[1024];

            var jarEntry = jis.getNextJarEntry();
            while (jarEntry != null) {
                File extractedFile = new File(destination, jarEntry.getName());
                if (!jarEntry.isDirectory() && jarEntry.getName().endsWith(PROTO_EXTENSIION)) {
                    Files.createDirectories(extractedFile.getParentFile().toPath());
                    final var fos = new FileOutputStream(extractedFile);
                    int length;
                    while ((length = jis.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                    fos.close();
                }
                jarEntry = jis.getNextJarEntry();
            }
            jis.closeEntry();
        }
    }
}
