# R8 rules for ShimmerCapture's release build (DEV-1426).
#
# Google Play flags apps whose code R8 does not optimise or obfuscate. R8 shrinks,
# optimises and obfuscates everything except what is kept here: the third-party
# libraries (AndroidX, Material, Guava, FastBle, ...) are most of the app, and
# they need no rules of their own.
#
# What is kept, and why. Each is code R8 cannot see being used, so it would
# remove or rename it and break the app only at runtime, not at build time.

# Shimmer code: the driver (shimmerdriver, shimmerbluetoothmanager), this repo's
# library, the app, and the closed-source ShimmerBiophysicalProcessingLibrary jar.
# - Java serialization: device objects are cloned through ObjectOutputStream
#   (e.g. configuring a device works on a clone), and many classes have private
#   readObject/writeObject, which only serialization calls.
# - Reflection: ExGConfigBytesDetails.getExGConfigOptionFields() and
#   UartPacketDetails.getUartPropertyParsed() collect static fields by
#   reflection, so R8 would drop those fields as unused.
# Keeping it whole means that is safe without auditing each class. Optimising it
# is left for later, with a test on hardware for each change.
-keep class com.shimmerresearch.** { *; }
-keep interface com.shimmerresearch.** { *; }

# androidplot: its Configurator sets plot properties from XML by reflecting on
# getter and setter names.
-keep class com.androidplot.** { *; }

# AndroidBluetoothLibrary: built around reflection.
-keep class it.gerdavax.** { *; }

# MainActivity aligns the toolbar by reaching into AppCompat by name: it reads
# Toolbar's private mTitleTextView by reflection, and finds the overflow menu by
# its class's simple name, "ActionMenuView". R8 would rename both, and the code
# fails silently (the title and the menu icon just lose their alignment).
-keepclassmembers class androidx.appcompat.widget.Toolbar {
    android.widget.TextView mTitleTextView;
}
-keepnames class androidx.appcompat.widget.ActionMenuView

# Serialization of the non-Shimmer classes a device object holds, e.g. Guava
# collections: keep what Java serialization uses but R8 cannot see.
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Classes the driver refers to that are not in the app, and were not before R8
# either: R8 only reports the references. They are reached only by driver code
# ShimmerCapture does not run (gRPC, Shimmer4, desktop UI), which is kept as-is
# above, so the app fails no differently than it did.
# - excluded on purpose in the root build.gradle (exclude group: ...)
-dontwarn com.google.protobuf.**
-dontwarn io.netty.**
-dontwarn com.sun.jna.**
# - desktop-only (the driver also runs on PCs)
-dontwarn java.awt.**
-dontwarn javax.swing.**
# - optional dependencies of gRPC, Netty and Guava
-dontwarn org.eclipse.jetty.alpn.**
-dontwarn org.eclipse.jetty.npn.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.impl.**
-dontwarn org.jetbrains.annotations.**
-dontwarn reactor.blockhound.**
-dontwarn sun.misc.**

# Line numbers in Play's crash reports. Play maps the rest back with the
# mapping.txt the release workflow uploads.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
