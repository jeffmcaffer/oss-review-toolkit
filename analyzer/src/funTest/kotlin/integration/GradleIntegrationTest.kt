/*
 * Copyright (c) 2017 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.ort.analyzer.integration

import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.analyzer.managers.Gradle
import com.here.ort.analyzer.managers.Maven
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo

import java.io.File

class GradleIntegrationTest : AbstractIntegrationSpec() {

    override val pkg: Package = Package(
            packageManager = "Gradle",
            namespace = "org.gradle",
            name = "Gradle",
            version = "4.4.0",
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo(
                    provider = "Git",
                    url = "https://github.com/gradle/gradle.git",
                    revision = "v4.4.0",
                    path = ""
            )
    )

    override val expectedDefinitionFiles by lazy {
        // The Gradle project contains far too many definition files to list them all here. Use this tests to double
        // check that all of them are found, and that they are assigned to the correct package manager.
        val gradleFilenames = listOf("build.gradle", "settings.gradle")
        val gradleFiles = downloadDir.walkTopDown().filter { it.name in gradleFilenames }.toMutableList()

        // settings.gradle shall only be detected if there is no build.gradle file in the same directory.
        gradleFiles.removeAll {
            it.name == "settings.gradle" && gradleFiles.contains(File(it.parent, "build.gradle"))
        }

        mapOf(
                Gradle to gradleFiles,
                Maven to downloadDir.walkTopDown().filter { it.name == "pom.xml" }.toList()
        )
    }

    override val definitionFilesForTest by lazy {
        mapOf(Gradle as PackageManagerFactory<PackageManager> to listOf(File(downloadDir, "buildSrc/build.gradle")))
    }

}
