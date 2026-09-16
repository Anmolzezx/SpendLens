# SQLCipher's classes are reached from native code, which R8 cannot see, so nothing here is "used".
-keep class net.zetetic.database.** { *; }

# kotlinx.serialization generates a `serializer()` on each @Serializable type and looks it up by name:
# navigation routes and the sync protocol's DTOs. Renaming them turns route encoding into a runtime
# crash that only a release build shows.
-keepclassmembers class com.spendlens.** {
    *** Companion;
}
-keepclasseswithmembers class com.spendlens.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.spendlens.**$$serializer { *; }

# Room reads @Entity field names through generated code; keeping the entities keeps the column mapping
# honest even when a future rule renames more aggressively.
-keep class com.spendlens.core.database.entity.** { *; }

# ML Kit finds its components by reflecting on no-argument constructors of registrar classes listed in
# the manifest, so R8 sees them as unused and removes the constructors. Without this, text recognition
# fails only in release — the debug build is fine, which is exactly how this reaches a user.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
-keep class com.google.mlkit.** { <init>(...); }
