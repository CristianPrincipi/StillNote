# Glance creates the widget's ActionCallbacks (CompleteTask) by reflection, through their
# no-argument constructor. Glance's own rule keeps the classes but not that constructor, so R8
# dropped it and ticking a task on the widget failed in release builds.
-keepclassmembers class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}
