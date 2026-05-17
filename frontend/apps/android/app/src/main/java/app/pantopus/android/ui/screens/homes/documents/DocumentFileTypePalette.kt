@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.homes.documents

import androidx.compose.ui.graphics.Color
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * T6.4b — Per-MIME-type visual tokens for the DocumentsScreen row's
 * leading tile. Lifted from `docs-frames.jsx:54-62`. Documented
 * exception to the no-hex rule (palette file, per Android `CLAUDE.md`).
 *
 * Inferred client-side from `HomeDocument.mime_type` because the
 * backend stores the raw MIME string with no normalised file-type
 * column — mirrors the Bills `UtilityCategory.from(payee:)` pattern.
 */
enum class DocumentFileType(
    val id: String,
    val stamp: String,
    val icon: PantopusIcon,
    val background: Color,
    val foreground: Color,
) {
    Pdf(
        id = "pdf",
        stamp = "PDF",
        icon = PantopusIcon.FileText,
        background = Color(0xFFFEE2E2),
        foreground = Color(0xFFB91C1C),
    ),
    Image(
        id = "image",
        stamp = "JPG",
        icon = PantopusIcon.Image,
        background = Color(0xFFDBEAFE),
        foreground = Color(0xFF1D4ED8),
    ),
    Doc(
        id = "doc",
        stamp = "DOC",
        icon = PantopusIcon.FileType,
        background = Color(0xFFE0E7FF),
        foreground = Color(0xFF4338CA),
    ),
    Sheet(
        id = "sheet",
        stamp = "XLS",
        icon = PantopusIcon.FileSpreadsheet,
        background = Color(0xFFDCFCE7),
        foreground = Color(0xFF15803D),
    ),
    Archive(
        id = "archive",
        stamp = "ZIP",
        icon = PantopusIcon.Archive,
        background = Color(0xFFE2E8F0),
        foreground = Color(0xFF334155),
    ),
    Scan(
        id = "scan",
        stamp = "PDF",
        icon = PantopusIcon.ScanLine,
        background = Color(0xFFEDE9FE),
        foreground = Color(0xFF6D28D9),
    ),
    ;

    companion object {
        /**
         * Infer the file-type bucket from a backend `mime_type` string,
         * falling back to the filename extension when the MIME is empty
         * or generic (e.g. `application/octet-stream`). Defaults to
         * [Pdf] for unknown inputs because PDFs dominate the populated
         * design and red is the most legible fallback.
         */
        fun fromMime(
            mimeType: String?,
            filename: String? = null,
        ): DocumentFileType {
            val mime = mimeType?.lowercase() ?: ""
            if (mime.isNotEmpty() && mime != "application/octet-stream") {
                if (mime == "application/pdf") return Pdf
                if (mime.startsWith("image/")) return Image
                if (mime == "application/msword" ||
                    mime.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml") ||
                    mime == "application/vnd.oasis.opendocument.text"
                ) {
                    return Doc
                }
                if (mime == "application/vnd.ms-excel" ||
                    mime.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml") ||
                    mime == "text/csv" ||
                    mime == "application/vnd.oasis.opendocument.spreadsheet"
                ) {
                    return Sheet
                }
                if (mime == "application/zip" ||
                    mime == "application/x-zip-compressed" ||
                    mime == "application/x-tar" ||
                    mime == "application/x-gzip"
                ) {
                    return Archive
                }
            }
            // Fallback: inspect the filename extension if MIME was empty
            // or generic.
            val ext =
                (filename ?: "")
                    .lowercase()
                    .substringAfterLast('.', "")
            return when (ext) {
                "pdf" -> Pdf
                "jpg", "jpeg", "png", "gif", "heic", "webp", "tiff" -> Image
                "doc", "docx", "odt", "rtf", "txt" -> Doc
                "xls", "xlsx", "csv", "ods", "numbers" -> Sheet
                "zip", "tar", "gz", "rar", "7z" -> Archive
                else -> Pdf
            }
        }
    }
}
