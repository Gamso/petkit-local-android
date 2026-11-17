package org.petkitlocal.feature.settings.licences

import org.petkitlocal.core.domain.settings.OpenSourceComponent
import org.petkitlocal.core.domain.settings.OpenSourceLicence
import org.petkitlocal.core.domain.settings.OpenSourceProjects

/** A few components for previews and screenshot tests; the app reads the generated list. */
internal object LicencesPreviewData {
    val apache = OpenSourceLicence(
        name = "Apache License 2.0",
        text = """
            Apache License
            Version 2.0, January 2004
            http://www.apache.org/licenses/

            TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

            1. Definitions.

            "License" shall mean the terms and conditions for use, reproduction,
            and distribution as defined by Sections 1 through 9 of this document.
        """.trimIndent(),
    )

    val mit = OpenSourceLicence(
        name = "MIT License",
        text = """
            MIT License

            Copyright (c) 2026 Gamso

            Permission is hereby granted, free of charge, to any person obtaining a copy
            of this software and associated documentation files (the "Software"), to deal
            in the Software without restriction.
        """.trimIndent(),
    )

    private val ofl =
        OpenSourceLicence(name = "SIL Open Font License 1.1", text = "Copyright 2020 The Inter Project Authors")

    private const val ANDROID = "https://cs.android.com/androidx/platform/frameworks/support"

    val okhttp =
        OpenSourceComponent(
            "com.squareup.okhttp3:okhttp",
            "okhttp",
            "5.5.1",
            "https://github.com/square/okhttp",
            listOf(apache),
        )

    val components = listOf(
        OpenSourceComponent("androidx.core:core-ktx", "Core Kotlin Extensions", "1.19.1", ANDROID, listOf(apache)),
        OpenSourceComponent("androidx.room:room-runtime", "Room-Runtime", "2.8.5", ANDROID, listOf(apache)),
        OpenSourceComponent("androidx.compose.ui:ui", "Compose UI", "1.12.1", ANDROID, listOf(apache)),
        OpenSourceComponent(
            "io.coil-kt.coil3:coil-compose",
            "coil-compose",
            "3.6.3",
            "https://github.com/coil-kt/coil",
            listOf(apache),
        ),
        OpenSourceComponent(
            "io.coil-kt.coil3:coil-network-okhttp",
            "coil-network-okhttp",
            "3.6.3",
            "https://github.com/coil-kt/coil",
            listOf(apache),
        ),
        OpenSourceComponent(
            "petkitlocal.font:inter",
            "Inter (font)",
            null,
            "https://github.com/rsms/inter",
            listOf(ofl),
        ),
        okhttp,
    )

    val projects = OpenSourceProjects.group(components)

    /** The AndroidX line unfolded. */
    val unfolded = LicencesUiState(projects = projects, expanded = setOf(OpenSourceProjects.ANDROIDX_NAME))
}
