package com.ptk.anatomypro.core.data

import android.content.Context
import androidx.room.Room

/** Opens the atlas database in the app's private database directory. */
fun anatomyDatabase(context: Context, name: String = "anatomy.db"): AnatomyDatabase =
    Room.databaseBuilder<AnatomyDatabase>(
        context = context.applicationContext,
        name = context.getDatabasePath(name).absolutePath,
    ).openAnatomyDatabase()
