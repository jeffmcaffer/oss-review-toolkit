package com.here.provenanceanalyzer.managers

import com.here.provenanceanalyzer.model.Dependency
import com.here.provenanceanalyzer.PackageManager

import java.io.File

object Gradle : PackageManager(
        "https://gradle.org/",
        "Java",
        listOf("build.gradle")
) {
    override fun resolveDependencies(definitionFiles: List<File>): Map<File, Dependency> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}