import net.swiftzer.semver.SemVer
import java.io.BufferedOutputStream

plugins {
    `java-platform`
    id("lazy.zoo.gradle.git-data-plugin")
}


tasks.create("githubVersionSummary") {
    group = "github"
    doLast {
        val ghStepSummaryPath: String = System.getenv("GITHUB_STEP_SUMMARY")
            ?: throw IllegalArgumentException("This task may only be run in a Github Actions CI environment! Unable to locate the GITHUB_STEP_SUMMARY environment variable.")

        val ghStepSummaryFile = File(ghStepSummaryPath)
        Utils.generateProjectVersionReport(rootProject, BufferedOutputStream(ghStepSummaryFile.outputStream()))
    }
}


tasks.create("showVersion") {
    group = "versioning"
    doLast {
        println(project.version)
    }
}

tasks.create("versionAsPrefixedCommit") {
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

tasks.create("versionAsSnapshot") {
    group = "versioning"
    doLast {
        val currVer = SemVer.parse(project.version.toString())
        val newVer = SemVer(currVer.major, currVer.minor, currVer.patch, "SNAPSHOT")

        Utils.updateVersion(project, newVer)
    }
}

tasks.create("versionAsSpecified") {
    group = "versioning"
    doLast {
        val verStr = findProperty("newVersion")?.toString()

        if (verStr == null) {
            throw IllegalArgumentException("No newVersion property provided! Please add the parameter -PnewVersion=<version> when running this task.")
        }

        val newVer = SemVer.parse(verStr)
        Utils.updateVersion(project, newVer)
    }
}
