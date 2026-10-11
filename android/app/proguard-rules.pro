# Proguard rules for Signora Android

# Keep MediaPipe Tasks Vision classes
-keep class com.google.mediapipe.tasks.** { *; }
-keep interface com.google.mediapipe.tasks.** { *; }

# Keep CameraX
-keep class androidx.camera.** { *; }

# Keep Signora model data classes
-keep class ai.signora.models.** { *; }
-keep class ai.signora.ml.** { *; }
