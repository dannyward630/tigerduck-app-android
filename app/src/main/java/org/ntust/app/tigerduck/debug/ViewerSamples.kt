package org.ntust.app.tigerduck.debug

/** One of the debug build's file viewer samples, as `assets/viewer-samples/index.tsv` lists it. */
data class ViewerSample(val group: String, val name: String, val mimeType: String, val bytes: Long)

/**
 * The debug build's file viewer samples (Settings › Developer › File viewer samples), which
 * tools/viewer-samples/make_samples.sh makes, and the files picked on the phone beside them. Both
 * are copied under [CACHE_DIR] and opened as a downloaded Moodle file opens.
 */
object ViewerSamples {
    const val ASSET_DIR = "viewer-samples"
    const val INDEX = "index.tsv"

    /** Under the cache, where the FileProvider's `viewer_samples` path hands them out. */
    const val CACHE_DIR = "viewer_samples"

    const val GENERIC_TYPE = "application/octet-stream"

    private const val MAX_NAME = 120
    private const val PICKED = "picked"

    /** The index's samples: group, name, MIME type and size, tab-separated, under a header. */
    fun parseIndex(text: String): List<ViewerSample> =
        text.lineSequence().drop(1).filter { it.isNotBlank() }.map { line ->
            val (group, name, mimeType, bytes) = line.split('\t')
            ViewerSample(group, name, mimeType, bytes.trim().toLong())
        }.toList()

    /**
     * Where the sample [name] is stored in the assets: a name ending in .gz has .sample after it,
     * since the Android Gradle plugin's asset merge takes a .gz off and unpacks the file.
     * make_samples.sh's write_index.py keeps the same rule.
     */
    fun assetName(name: String): String = if (name.endsWith(".gz")) "$name.sample" else name

    /**
     * The name a file picked on the phone is copied and opened under: its provider's
     * [displayName], down to its last path segment and with no control characters, at most
     * [MAX_NAME] long with its extension kept; or, with none, "picked" with the extension
     * [extensionOf] gives its [mimeType].
     */
    fun pickedName(displayName: String?, mimeType: String?, extensionOf: (String) -> String?): String {
        val own = displayName.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filterNot { it.isISOControl() }
            .trim()
        if (own.isEmpty() || own == "." || own == "..") {
            val extension = mimeType?.let(extensionOf)
            return if (extension.isNullOrEmpty()) PICKED else "$PICKED.$extension"
        }
        if (own.length <= MAX_NAME) return own
        val extension = own.substringAfterLast('.', missingDelimiterValue = "")
        return if (extension.isEmpty() || extension.length >= MAX_NAME / 2) {
            own.take(MAX_NAME)
        } else {
            own.substringBeforeLast('.').take(MAX_NAME - extension.length - 1) + ".$extension"
        }
    }

    /**
     * The type a picked file opens as: its provider's [providerType], as a Moodle file opens as
     * the type Moodle sends; with none, the one [typeOf] gives the extension of [name], or
     * [GENERIC_TYPE].
     */
    fun pickedType(providerType: String?, name: String, typeOf: (String) -> String?): String =
        providerType?.trim()?.takeIf { it.isNotEmpty() }
            ?: name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
                .takeIf { it.isNotEmpty() }?.let(typeOf)
            ?: GENERIC_TYPE
}
