// SPDX-License-Identifier: Apache-2.0
plugins { id("com.diffplug.spotless") }

spotless {
    kotlinGradle {
        ktfmt().kotlinlangStyle()

        licenseHeader(
            """
           // SPDX-License-Identifier: Apache-2.0
            """
                .trimIndent(),
            "(import|plugins|repositories)",
        )
    }
}
