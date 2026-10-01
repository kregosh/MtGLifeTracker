# Debug logging can contain session codes and IDs; keep it out of release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
