buildscript {
    // Force patched versions of AGP's vulnerable transitive build-time libs (docs/AUDIT.md).
    configurations.classpath {
        resolutionStrategy.force(libs.bundles.build.tool.pins.get().map { "${it.module}:${it.version}" })
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}
