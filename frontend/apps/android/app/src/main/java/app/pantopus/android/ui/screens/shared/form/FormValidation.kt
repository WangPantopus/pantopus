@file:Suppress("PackageNaming", "MatchingDeclarationName")

package app.pantopus.android.ui.screens.shared.form

/**
 * Single validation rule applied to a string field. `validate` returns
 * an error string when invalid, or null when acceptable. Mirrors the
 * iOS `FormValidator` API.
 */
fun interface FormValidator {
    fun validate(value: String): String?

    companion object
}

/** Trims whitespace; returns an error when the result is empty. */
fun FormValidator.Companion.required(label: String): FormValidator =
    FormValidator { value ->
        if (value.trim().isEmpty()) "$label is required." else null
    }

/** Ensures length does not exceed [max] characters. */
fun FormValidator.Companion.maxLength(max: Int): FormValidator =
    FormValidator { value ->
        if (value.length > max) "Must be $max characters or fewer." else null
    }

/**
 * E.164 phone-number format (`^\+[1-9]\d{1,14}$`). Empty is allowed.
 * Mirrors the iOS validator.
 */
fun FormValidator.Companion.e164Phone(): FormValidator =
    FormValidator { value ->
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            null
        } else if (E164_PATTERN.matches(trimmed)) {
            null
        } else {
            "Phone must be in E.164 format, e.g. +15555550123."
        }
    }

/**
 * RFC-5322-ish email check. Mirrors `Joi.string().email()` server-side
 * (`backend/routes/homeOwnership.js:67`).
 */
fun FormValidator.Companion.email(): FormValidator =
    FormValidator { value ->
        val trimmed = value.trim()
        when {
            trimmed.isEmpty() -> "Email is required."
            !EMAIL_PATTERN.matches(trimmed) -> "Enter a valid email address."
            else -> null
        }
    }

/** Reject the supplied email (case-insensitive). */
fun FormValidator.Companion.emailNotMatching(otherEmail: String): FormValidator {
    val normalised = otherEmail.lowercase()
    return FormValidator { value ->
        val trimmed = value.trim().lowercase()
        if (trimmed.isNotEmpty() && trimmed == normalised) {
            "You can't invite yourself."
        } else {
            null
        }
    }
}

/** Applies each rule in order; stops at the first failure. */
fun FormValidator.Companion.all(rules: List<FormValidator>): FormValidator =
    FormValidator { value ->
        rules.firstNotNullOfOrNull { it.validate(value) }
    }

private val E164_PATTERN = Regex("""^\+[1-9]\d{1,14}$""")
private val EMAIL_PATTERN = Regex("""^[A-Z0-9a-z._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$""")
