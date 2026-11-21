# Keep rules for the release build. Keep them minimal and explain each one (docs/PERFORMANCE.md §2).

# Lottie's View, LottieAnimationView, extends AppCompatImageView. core:ui leaves out Lottie's AppCompat dependency
# (Pingogram has no Views and never uses that class, which R8 removes), so its superclass is missing on purpose
# (docs/adr/0008-lottie-for-animated-stickers.md).
-dontwarn androidx.appcompat.widget.AppCompatImageView

# TDLib, in place of its AAR's rule, which keeps every member of TdApi (app/build.gradle.kts ignores it; docs/TDLIB.md
# "Build strategy"). libtdjni finds each TdApi class by name, creates its objects without calling a constructor, reads
# and writes their fields by name and reads their type with getConstructor(). Each class keeps its no-argument
# constructor so that R8 still treats it as instantiated and keeps the app's checks of its type. The constructors
# with arguments and the CONSTRUCTOR constants stay only where the app uses them.
-keep class org.drinkless.tdlib.TdApi
-keep class org.drinkless.tdlib.TdApi$* {
    public <init>();
    !static <fields>;
    public int getConstructor();
}
# Registered by name from libtdjni (RegisterNatives), and the log callback it calls.
-keep class org.drinkless.tdlib.TdApi$Object { public native java.lang.String toString(); }
-keep class org.drinkless.tdlib.TdApi$Function { public native java.lang.String toString(); }
-keep class org.drinkless.tdlib.Client { native <methods>; }
-keep interface org.drinkless.tdlib.Client$LogMessageHandler { void onLogMessage(int, java.lang.String); }
