// SPDX-License-Identifier: Apache-2.0
import java.io.BufferedOutputStream
import net.swiftzer.semver.SemVer

plugins { id("lazy.zoo.gradle.git-data-plugin") }

tasks.register("githubVersionSummary") {
    group = "github"
    doLast {
        val ghStepSummaryPath: String =
            System.getenv("GITHUB_STEP_SUMMARY")
                ?: throw IllegalArgumentException(
                    "This task may only be run in a Github Actions CI environment! Unable to locate the GITHUB_STEP_SUMMARY environment variable."
                )

        val ghStepSummaryFile = File(ghStepSummaryPath)
        Utils.generateProjectVersionReport(
            rootProject,
            BufferedOutputStream(ghStepSummaryFile.outputStream())
        )
    }
}

tasks.register("showVersion") {
    group = "versioning"
    doLast { println(project.version) }
}

tasks.register("versionAsPrefixedCommit") {
    group = "versioning"
    doLast {
        gitData.lastCommitHash?.let {
            val prefix = findProperty("commitPrefix")?.toString() ?: "adhoc"
            val newPrerel = prefix + ".x" + it.take(8)
            val currVer = SemVer.parse(project.version.toString())
            try {
                val newVer = SemVer(currVer.major, currVer.minor, currVer.patch, newPrerel)
                Utils.updateVersion(project, newVer)
            } catch (e: java.lang.IllegalArgumentException) {
                throw IllegalArgumentException(String.format("%s: %s", e.message, newPrerel), e)
            }
        }
    }
}

tasks.register("versionAsSnapshot") {
    group = "versioning"
    doLast {
        val currVer = SemVer.parse(project.version.toString())
        val newVer = SemVer(currVer.major, currVer.minor, currVer.patch, "SNAPSHOT")

        Utils.updateVersion(project, newVer)
    }
}

tasks.register("versionAsSpecified") {
    group = "versioning"
    doLast {
        val verStr =
            providers.gradleProperty("newVersion").orNull
                ?: throw IllegalArgumentException(
                    "No newVersion property provided! Please add the parameter -PnewVersion=<version> when running this task."
                )

        val newVer = SemVer.parse(verStr)
        Utils.updateVersion(project, newVer)
    }
}
