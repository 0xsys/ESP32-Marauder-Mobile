# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { @kotlinx.serialization.SerialName <fields>; }

# usb-serial-for-android reflectively probes driver classes.
-keep class com.hoho.android.usbserial.driver.** { *; }
