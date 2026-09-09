package com.ptk.anatomypro.core.data

import androidx.room.Room
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

internal fun openTemporaryDatabase(): AnatomyDatabase =
    Room.databaseBuilder<AnatomyDatabase>(
        name = NSTemporaryDirectory() + "anatomy-test-${NSUUID().UUIDString}.db",
    ).openAnatomyDatabase()
