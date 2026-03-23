# Add project specific ProGuard rules here.
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# Keep Gson models
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
