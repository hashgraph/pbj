/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins { id("com.diffplug.spotless") }

spotless {
    java {
        targetExclude("build/generated/sources/**/*.java")
        // enable toggle comment support
        toggleOffOn()
        // don't need to set target, it is inferred from java
        // apply a specific flavor of google-java-format
        googleJavaFormat("1.17.0").aosp().reflowLongStrings()
        // make sure every file has the following copyright header.
        // optionally, Spotless can set copyright years by digging
        // through git history (see "license" section below).
        // The delimiter override below is required to support some
        // of our test classes which are in the default package.
        licenseHeader(
            """
           /*
            * Copyright (C) ${'$'}YEAR Hedera Hashgraph, LLC
            *
            * Licensed under the Apache License, Version 2.0 (the "License");
            * you may not use this file except in compliance with the License.
            * You may obtain a copy of the License at
            *
            *      http://www.apache.org/licenses/LICENSE-2.0
            *
            * Unless required by applicable law or agreed to in writing, software
            * distributed under the License is distributed on an "AS IS" BASIS,
            * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            * See the License for the specific language governing permissions and
            * limitations under the License.
            */${"\n\n"}
        """
                .trimIndent(),
            "(package|import)"
        )
    }
}
