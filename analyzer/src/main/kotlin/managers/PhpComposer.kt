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

package com.here.ort.analyzer.managers

import ch.frankel.slf4k.error
import com.fasterxml.jackson.databind.JsonNode
import com.here.ort.analyzer.Main

import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.jsonMapper
import com.here.ort.utils.log

import java.io.File

const val COMPOSER_SCRIPT_WINDOWS = "@php \"%~dp0composer.phar\" %*"
const val COMPOSER_SCRIPT_FILENAME_WINDOWS = "composer.bat"
const val COMPOSER_BINARY = "composer.phar"
const val COMPOSER_SCRIPT_FILENAME = "composer"

class PhpComposer : PackageManager() {
    companion object : PackageManagerFactory<PhpComposer>(
            "https://getcomposer.org/",
            "PHP",
            listOf("composer.json")
    ) {
        override fun create() = PhpComposer()
    }

    private val composerLocal: String
    private val composerGlobal: String

    init {
        if (OS.isWindows) {
            composerLocal = COMPOSER_SCRIPT_FILENAME_WINDOWS
            composerGlobal = COMPOSER_SCRIPT_FILENAME_WINDOWS
        } else {
            composerLocal = COMPOSER_BINARY
            composerGlobal = COMPOSER_SCRIPT_FILENAME
        }
    }

    override fun command(workingDir: File) =
            if (File(workingDir, COMPOSER_BINARY).isFile) {
                val localComposerFile = File(workingDir, composerLocal)
                if (OS.isWindows && localComposerFile.exists()) {
                    localComposerFile.writeText(COMPOSER_SCRIPT_WINDOWS)
                }
                composerLocal
            } else {
                composerGlobal
            }


    override fun resolveDependencies(projectDir: File, workingDir: File, definitionFile: File): AnalyzerResult? {
        installDependencies(workingDir)

        val scopes = mutableSetOf<Scope>()
        val packages = mutableSetOf<Package>()
        val errors = mutableListOf<String>()
        val vcsDir = VersionControlSystem.forDirectory(projectDir)
        val composerJson = jsonMapper.readTree(definitionFile)
        try {
            val (projectName, version, licenses, homepageUrl) = composerJson.let {
                parseDependencies(it, "require", scopes, packages, workingDir, errors)
                parseDependencies(it, "require-dev", scopes, packages, workingDir, errors)

//                TODO: do we need following scopes?
//                parseDependencies(it, "conflict", scopes, workingDir, errors)
//                parseDependencies(it, "replace", scopes, workingDir, errors)
//                parseDependencies(it, "provide", scopes, workingDir, errors)
//                parseDependencies(it, "suggest", scopes, workingDir, errors)
                val license = if (it["license"] != null && it["license"].isValueNode)
                    it["license"].asText()
                else if (it["license"] != null && it["license"].isArray) {
                    it["license"].joinToString(separator = ";") { it.asText() }
                } else {
                    ""
                }

                listOf(it["name"]?.asText() ?: "",
                       it["version"]?.asText() ?: "",
                       license,
                       it["homepage"]?.asText() ?: "")
            }



            val project = Project(
                    packageManager = javaClass.simpleName,
                    namespace = "",
                    name = projectName,
                    version = version,
                    declaredLicenses = licenses.split(";").toSortedSet(),
                    aliases = emptyList(),
                    vcs = vcsDir?.getInfo(projectDir) ?: VcsInfo.EMPTY,
                    homepageUrl = homepageUrl,
                    scopes = scopes.toSortedSet()
            )
            return AnalyzerResult(true, project, packages.toSortedSet(), errors)
        } catch (e: Exception) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            log.error { "Could not analyze '${definitionFile.absolutePath}': ${e.message}" }
            return null
        }
    }

    // Currently single 'composer install' is performed on top level of project, which results with sources of top level
    // dependencies. If we need deeper dependency analysis, recursive installing and parsing of dependencies should be
    // implemented (probably with controlled recursion depth)
    private fun parseDependencies(composerJson: JsonNode, scopeName: String, scopes: MutableSet<Scope>,
                                  packages: MutableSet<Package>,  workingDir: File, errors: MutableList<String>) {
        try {
            val scopeJson = composerJson[scopeName]
            val scopeDependencies = mutableListOf<PackageReference>()
            if (scopeJson != null) {
                for ((dependencyName, dependencyVersionConstraint) in scopeJson.fields()) {
                    val dependencyDependencies = mutableListOf<PackageReference>()
                    val dependencyFile = workingDir.resolve("vendor").resolve(dependencyName).resolve("composer.json")
                    val dependencyJson = jsonMapper.readTree(dependencyFile)

                    // Get dependency's dependencies from same scope only:
                    val scopeDependencyJson = dependencyJson[scopeName]
                    for ((dependantName, dependencyDependantVersionConstraint) in scopeDependencyJson.fields()) {
                        packages.add(parsePackage(workingDir,  dependantName))
                        dependencyDependencies.add(PackageReference(namespace = scopeName,
                                                                    name = dependantName,
                                                                    version = dependencyDependantVersionConstraint.asText(),
                                                                    dependencies = sortedSetOf()))
                    }

                    packages.add(parsePackage(workingDir,  dependencyName))
                    scopeDependencies.add(PackageReference(namespace = scopeName,
                                                           name = dependencyName,
                                                           version = dependencyVersionConstraint.asText(),
                                                           dependencies = dependencyDependencies.toSortedSet()))
                }

                scopes.add(Scope(name = scopeName, delivered = true, dependencies = scopeDependencies.toSortedSet()))
            }
        } catch (e: Exception) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }
            errors.add("Failed to parse scope $scopeName: ${e.message}")
        }
    }

    private fun parsePackage(workingDir: File, dependantName: String): Package {
        //FIXME: Fails for php dependency - skip on that one
        val packageShowTextResult = showPackage(workingDir, dependantName).stdout().trim().lineSequence()
        val pkgName = getLineValue(Regex("name\\s*:\\s+(?<name>[\\w\\/\\-_]+)"), packageShowTextResult, "name")
        val namespace = pkgName?.substringBefore("/")
        val version = getLineValue(Regex("versions\\s+:\\s\\*+\\s(?<version>[\\w.]+)"), packageShowTextResult, "version")
        val license = getLineValue(Regex("license\\s+:\\s+(?<license>[\\w. ():\\/]+)"), packageShowTextResult, "license")
        return  Package(
                packageManager = javaClass.simpleName,
                namespace = namespace,
                name =pkgName,
                version = version,
                declaredLicenses = sortedSetOf(license),
                description = "",
                homepageUrl = "",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = VcsInfo.EMPTY
        )
    }

    private fun installDependencies(workingDir: File): ProcessCapture =
            ProcessCapture(workingDir, command(workingDir), "install").requireSuccess()

    private fun showPackage(workingDir: File, pkgName: String): ProcessCapture =
            ProcessCapture(workingDir, command(workingDir), "show", pkgName).requireSuccess()

    private fun getLineValue(regex: Regex, lines: Sequence<String>, groupName: String) : String {
        val matched = lines.mapNotNull {
            regex.matchEntire(it.trim())?.groups?.get(groupName)?.value
        }

        return if (matched.count() > 0) matched.first() else ""
    }

}
