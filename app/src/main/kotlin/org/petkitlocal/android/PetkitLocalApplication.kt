package org.petkitlocal.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** The Hilt root: `:app` is the only module that sees every implementation (ARCHITECTURE.md §2). */
@HiltAndroidApp
class PetkitLocalApplication : Application()
