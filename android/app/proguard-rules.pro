# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.hedwig.sms.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.hedwig.sms.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
