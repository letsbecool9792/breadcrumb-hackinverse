package com.lbc.breadcrumb.capture

/**
 * Where a memory came from, from the package that shared it. Pure, so JVM
 * tests cover it; [SourceHelper] feeds it the referrer and its name.
 */
internal object Sources {

    /**
     * System surfaces, not apps a person shared from: the clipboard arrives as
     * System UI, a share passes through the intent resolver, and the shell and
     * "android" are the system itself.
     */
    private val NOT_SOURCES = setOf(
        "com.android.systemui",
        "com.android.intentresolver",
        "android",
        "com.android.shell",
    )

    /**
     * (package, name), or (null, null) for a system surface or Breadcrumb
     * itself. A name that is just the package is no name.
     */
    fun of(packageName: String?, label: String?, ownPackage: String): Pair<String?, String?> {
        val source = packageName?.trim()
            ?.takeIf { it.isNotEmpty() && it !in NOT_SOURCES && it != ownPackage }
            ?: return null to null
        val name = label?.trim()?.takeIf { it.isNotEmpty() && it != source }
        return source to name
    }
}
