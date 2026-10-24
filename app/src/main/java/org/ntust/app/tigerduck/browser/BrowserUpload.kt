package org.ntust.app.tigerduck.browser

/** Where a file for a page's upload field comes from. */
enum class UploadSource { PHOTOS, CAMERA, FILES }

/**
 * What the upload sheet offers for a file field, from the types the page
 * accepts (`FileChooserParams.acceptTypes`: MIME types and `.ext`
 * extensions, sometimes several in one comma-separated string).
 */
object BrowserUpload {
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp")

    /** The accepted types, one per item, lower case; empty when the page accepts anything. */
    fun acceptedTypes(acceptTypes: List<String?>): List<String> =
        acceptTypes.flatMap { it.orEmpty().split(',') }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it != "*/*" && it != "*" }
            .distinct()

    /** Whether the page asks for images and nothing else. */
    fun wantsImagesOnly(acceptTypes: List<String?>): Boolean {
        val types = acceptedTypes(acceptTypes)
        return types.isNotEmpty() && types.all(::isImageType)
    }

    /**
     * The sheet's choices: the photo picker and the camera wherever an image
     * may do, and files always; a page that takes only other types gets files
     * alone.
     */
    fun sources(acceptTypes: List<String?>): List<UploadSource> {
        val types = acceptedTypes(acceptTypes)
        val imagesMayDo = types.isEmpty() || types.any(::isImageType)
        return if (imagesMayDo) listOf(UploadSource.PHOTOS, UploadSource.CAMERA, UploadSource.FILES) else listOf(UploadSource.FILES)
    }

    /**
     * The MIME types to open the document picker with: each accepted type,
     * an extension by [mimeOfExtension] (MimeTypeMap's), and everything when
     * the page accepts anything or names nothing the phone knows.
     */
    fun documentTypes(acceptTypes: List<String?>, mimeOfExtension: (String) -> String?): Array<String> {
        val mimes = acceptedTypes(acceptTypes).mapNotNull { type ->
            if (type.startsWith('.')) mimeOfExtension(type.removePrefix(".")) else type.takeIf { '/' in it }
        }.distinct()
        return if (mimes.isEmpty()) arrayOf("*/*") else mimes.toTypedArray()
    }

    private fun isImageType(type: String): Boolean =
        type.startsWith("image/") || (type.startsWith('.') && type.removePrefix(".") in IMAGE_EXTENSIONS)
}
