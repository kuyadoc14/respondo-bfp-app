# ProGuard & R8 Optimization and Obfuscation Rules for Respondo BFP

# Firestore data model classes (needed for serialization/deserialization)
-keepclassmembers class com.bfp.alert.UserProfile { *; }
-keepclassmembers class com.bfp.alert.FirstAidItem { *; }

# Cloudinary SDK
-keep class com.cloudinary.** { *; }
-dontwarn com.cloudinary.**

# Preserve BuildConfig for ApiSecurityConfig reflection
-keep class com.bfp.alert.BuildConfig { *; }

# Preserve line numbers for stack traces while obfuscating source file names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
