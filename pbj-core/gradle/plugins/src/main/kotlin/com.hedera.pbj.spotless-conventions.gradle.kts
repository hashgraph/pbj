// SPDX-License-Identifier: Apache-2.0
plugins { id("com.diffplug.spotless") }

spotless {
    // Disable strong enforcement during gradle check tasks
    isEnforceCheck = false

    // optional: limit format enforcement to just the files changed by this feature branch
    ratchetFrom("origin/main")

    format("misc") {
        // define the files to apply `misc` to
        target("*.gradle", "*.md", ".gitignore")

        // define the steps to apply to those files
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }

    format("actionYaml") {
        target(".github/workflows/*.yaml")
        /*
         * Prettier requires NodeJS and NPM installed; however, the NodeJS Gradle plugin and Spotless do not yet
         * integrate with each other. Currently there is an open issue report against spotless.
         *
         *   *** Please see for more information: https://github.com/diffplug/spotless/issues/728 ***
         *
         * The workaround provided in the above issue does not work in Gradle 7.5+ and therefore is not a viable solution.
         */
        // prettier()

        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()

        licenseHeader(
            """
            # SPDX-License-Identifier: Apache-2.0
            """
                .trimIndent(),
            "(name|on)"
        )
    }
}
