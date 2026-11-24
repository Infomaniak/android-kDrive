/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.infomaniak.drive.data.cache

import java.io.IOException

internal object PublicShareCache {
    private var activeActivities = 0

    @Synchronized
    fun opened() { activeActivities++ }

    @Synchronized
    fun closed() { activeActivities-- }

    @Synchronized
    fun deleteIfInactive(action: () -> Unit) {
        if (activeActivities != 0) throw IOException("Public share is in use")
        action()
    }
}
