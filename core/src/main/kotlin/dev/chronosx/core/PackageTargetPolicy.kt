package dev.chronosx.core

/** Keeps ChronosX intentionally out of system and malformed package scopes. */
object PackageTargetPolicy {
    private val packagePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private const val MODULE_PACKAGE = "dev.chronosx"

    fun assess(packageName: String): TargetAssessment = when {
        packageName.isBlank() -> TargetAssessment.Rejected("A package name is required.")
        !packagePattern.matches(packageName) -> TargetAssessment.Rejected("The package name is malformed.")
        packageName == MODULE_PACKAGE -> TargetAssessment.Rejected("ChronosX cannot target itself.")
        packageName == "android" || packageName == "system" ->
            TargetAssessment.Rejected("System processes are intentionally unsupported.")

        packageName.startsWith("com.android.") ->
            TargetAssessment.Rejected("System packages are intentionally unsupported.")

        else -> TargetAssessment.Allowed
    }

    fun isTargetable(packageName: String): Boolean = assess(packageName) is TargetAssessment.Allowed
}

sealed interface TargetAssessment {
    data object Allowed : TargetAssessment
    data class Rejected(val reason: String) : TargetAssessment
}
