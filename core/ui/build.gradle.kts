// Shared screen pieces, formatting with resources (weights, durations, dates), and the keyed
// translation of the labels the server sends (SPEC.md §7).
plugins {
    alias(libs.plugins.petkit.android.compose)
}

dependencies {
    api(projects.core.designsystem)
}
