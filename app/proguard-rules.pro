## NewPipeExtractor (dari README resminya)
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

## Pustaka yang dipakai NewPipeExtractor: kelas opsional yang tidak ada di Android
-dontwarn org.mozilla.javascript.**
-dontwarn javax.script.**
-dontwarn javax.lang.model.**
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**
-dontwarn org.jsoup.**
-dontwarn com.google.re2j.**
-dontwarn javax.annotation.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.jsoup.** { *; }
-keep class com.grack.nanojson.** { *; }

## OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
