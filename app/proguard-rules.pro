# Keep rules for the release build. Keep them minimal and explain each one (docs/PERFORMANCE.md §2).

# Lottie's View, LottieAnimationView, extends AppCompatImageView. core:ui leaves out Lottie's AppCompat dependency
# (Pingogram has no Views and never uses that class, which R8 removes), so its superclass is missing on purpose
# (docs/adr/0008-lottie-for-animated-stickers.md).
-dontwarn androidx.appcompat.widget.AppCompatImageView
