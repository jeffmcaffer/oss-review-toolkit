package com.here.provenanceanalyzer

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter

import java.io.File
import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

import kotlin.system.exitProcess

import mu.KotlinLogging

object Main {
    private val logger = KotlinLogging.logger {}

    class PackageManagerListConverter : IStringConverter<List<PackageManager>> {
        override fun convert(managers: String): List<PackageManager> {
            // Map lower-cased package manager class names to their instances.
            val packageManagerNames = packageManagers.associateBy { it.javaClass.simpleName.toLowerCase() }

            // Determine active package managers.
            val names = managers.toLowerCase().split(",")
            return names.mapNotNull { packageManagerNames[it] }
        }
    }

    @Parameter(names = arrayOf("--package-managers", "-m"), description = "A list of package managers to activate.", listConverter = PackageManagerListConverter::class, order = 0)
    var packageManagers: List<PackageManager> = PACKAGE_MANAGERS

    @Parameter(names = arrayOf("--help", "-h"), description = "Display the command line help.", help = true, order = 100)
    var help = false

    @Parameter(description = "project path(s)")
    var projectPaths: List<String>? = null

    /**
     * The entry point for the application.
     *
     * @param args The list of application arguments.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val jc = JCommander(this)
        jc.parse(*args)
        jc.programName = "pran"

        if (help) {
            jc.usage()
            exitProcess(1)
        }

        if (projectPaths == null) {
            logger.error("Please specify at least one project path.")
            exitProcess(2)
        }

        println("The following package managers are activated:")
        println("\t" + packageManagers.map { it.javaClass.simpleName }.joinToString(", "))

        // Map of paths managed by the respective package manager.
        val managedProjectPaths = HashMap<PackageManager, MutableList<File>>()

        projectPaths!!.forEach { projectPath ->
            val absolutePath = File(projectPath).absoluteFile
            println("Scanning project path '$absolutePath'.")

            if (packageManagers.size == 1 && absolutePath.isFile) {
                // If only one package manager is activated, treat given paths to files as definition files for that
                // package manager despite their name.
                managedProjectPaths.getOrPut(packageManagers.first()) { mutableListOf() }.add(absolutePath)
            } else {
                Files.walkFileTree(absolutePath.toPath(), object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attributes: BasicFileAttributes): FileVisitResult {
                        dir.toFile().listFiles().forEach { file ->
                            packageManagers.forEach { manager ->
                                if (manager.globForDefinitionFiles.matches(file.toPath())) {
                                    managedProjectPaths.getOrPut(manager) { mutableListOf() }.add(file)
                                }
                            }
                        }
                        return FileVisitResult.CONTINUE
                    }
                })
            }
        }

        managedProjectPaths.forEach { manager, paths ->
            println("$manager projects found in:")
            println(paths.map { "\t$it" }.joinToString("\n"))

            // Print the list of dependencies.
            val dependencies = manager.resolveDependencies(paths)
            dependencies.forEach(::println)
        }
    }
}