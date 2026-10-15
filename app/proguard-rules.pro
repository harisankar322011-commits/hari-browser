# Keep Android component names referenced from AndroidManifest.xml.
-keep public class com.haricalcx.haribrowser.MainActivity { public <init>(); }
-keep public class com.haricalcx.haribrowser.PrivateActivity { public <init>(); }

# Avoid logging browser URLs or page content from release code.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
