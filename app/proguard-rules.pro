# JNToolz R8 rules. Manifest components (Activity, AppWidgetProvider, RemoteViewsService,
# FileProvider) are kept automatically by AAPT-generated rules.

# kotlinx.serialization (@Serializable navigation keys)
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.jntoolz.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class com.jntoolz.** {
    static ** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Readable crash stack traces
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
