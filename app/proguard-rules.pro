# Flatten all obfuscated classes into root package (e.g., "a" instead of "dev.omkartenkale.fliptopause.a")
-repackageclasses ''
-allowaccessmodification
-optimizationpasses 5

# Strip Kotlin @Metadata annotations (saves ~5-10 KB of reflection strings in classes.dex!)
-assumenosideeffects class kotlin.Metadata { *; }

# Strip all debug line numbers and source file names from classes.dex
-renamesourcefileattribute ''
# (Note: do NOT add -keepattributes SourceFile,LineNumberTable)

# Strip Kotlin Intrinsics
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(java.lang.Object);
    public static void checkNotNull(java.lang.Object, java.lang.String);
    public static void checkNotNullParameter(java.lang.Object, java.lang.String);
    public static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    public static void throwUninitializedPropertyAccessException(java.lang.String);
}