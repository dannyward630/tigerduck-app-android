// The app's localized strings, generated from the app-translation submodule's
// source/*.json on every build: nothing is checked in, here or in the
// submodule, so a pin bump is all a translation change takes. Applied by :app
// and :wear, which each add the task's output as a generated resource
// directory: a script applied like this one can't see the Android plugin's
// classes. Only :app also gets xml/locales_config.xml, for Android 13+'s
// per-app language picker.

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

@CacheableTask
abstract class GenerateAppTranslationStrings @Inject constructor(
    private val exec: ExecOperations,
) : DefaultTask() {
    /** app-translation/source: the strings themselves. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: DirectoryProperty

    /** The generator, its locale config, and the script that calls it: a change to any regenerates. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val tools: ConfigurableFileCollection

    @get:Input
    abstract val localesConfig: Property<Boolean>

    /** tools/localization/sync_localizations.py, which runs the generator (also among [tools]). */
    @get:Internal
    abstract val syncScript: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        // The generator never removes a locale's file; a locale dropped from source must go.
        out.deleteRecursively()
        val args = mutableListOf(python3(), syncScript.get().asFile.path, "--res-dir", out.path)
        if (localesConfig.get()) args += "--locales-config"
        exec.exec { commandLine(args) }
    }

    /** The first of the usual Python 3 launchers on Linux, macOS and Windows that is one. */
    private fun python3(): String = listOf("python3", "python", "py").firstOrNull { candidate ->
        runCatching {
            val process = ProcessBuilder(candidate, "--version").redirectErrorStream(true).start()
            val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
            process.waitFor() == 0 && output.contains("Python 3")
        }.getOrDefault(false)
    } ?: throw GradleException(
        "The app's strings are generated from app-translation with Python 3, which isn't on PATH " +
            "(tried python3, python, py). Install it from https://www.python.org/ and build again."
    )
}

tasks.register("generateAppTranslationStrings", GenerateAppTranslationStrings::class.java) {
    group = "localization"
    description = "Generates values*/strings.xml from the app-translation submodule's source/*.json."
    val root = rootProject.layout.projectDirectory
    sources.set(root.dir("app-translation/source"))
    tools.from(
        root.file("app-translation/config/locales.json"),
        root.file("app-translation/tools/localization/generate_localizations.py"),
        root.file("tools/localization/sync_localizations.py"),
    )
    syncScript.set(root.file("tools/localization/sync_localizations.py"))
    localesConfig.set(project.path == ":app")
    outputDir.set(layout.buildDirectory.dir("generated/appTranslation/res"))
}
