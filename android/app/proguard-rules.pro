# R8's rules for the release build, beside those the libraries ship (Room, WorkManager,
# kotlinx.serialization, OkHttp, ML Kit and Compose all bring their own).

# LiteRT-LM ships none, and its native library reaches into its Kotlin classes by name (JNI):
# kept whole, or the model fails to load in a release build only.
-keep class com.google.ai.edge.litertlm.** { *; }

# ML Kit finds its parts by the names in its manifest and makes each with its no-argument
# constructor (ComponentDiscovery). Its own rules don't keep those, and R8's full mode drops a
# constructor nothing calls: OCR then fails in a release build only, without a crash.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

# A stack trace from a phone keeps its line numbers; the release's mapping.txt turns the
# names back (see docs/workstreams/website.md, "Releasing").
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
