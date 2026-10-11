package com.lbc.breadcrumb.imports

/**
 * Returns the line of text describing [progress] for the import bar and import panel (docs/design.md §14).
 *
 * Pure and JVM-testable:
 * - scanning -> "looking…"
 * - running -> "bringing in · {current} of {total}"
 * - done -> "brought in {imported}", plus " · {skipped} already here" when any were skipped;
 *   with nothing imported, "nothing new · {skipped} already here";
 *   with nothing found at all, "nothing to bring in"
 * - failed -> "import stopped · {reason}"
 */
fun importLine(progress: ImportProgress?): String? {
    return when (progress) {
        null -> null
        is ImportProgress.Scanning -> "looking…"
        is ImportProgress.Running -> "bringing in · ${progress.current} of ${progress.total}"
        is ImportProgress.Done -> {
            if (progress.total == 0 || (progress.imported == 0 && progress.skipped == 0)) {
                "nothing to bring in"
            } else if (progress.imported == 0) {
                "nothing new · ${progress.skipped} already here"
            } else if (progress.skipped > 0) {
                "brought in ${progress.imported} · ${progress.skipped} already here"
            } else {
                "brought in ${progress.imported}"
            }
        }
        is ImportProgress.Failed -> "import stopped · ${progress.reason}"
    }
}
