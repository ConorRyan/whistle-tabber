package io.github.conorryan.whistletabber.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import io.github.conorryan.whistletabber.MainActivity
import io.github.conorryan.whistletabber.data.tunes.SharedFiles

/** Opens the system share sheet for a file from [SharedFiles], leaving this app out of it. */
fun Context.openShareSheet(uri: Uri, subject: String) {
    startActivity(SharedFiles.chooser(uri, subject, ComponentName(this, MainActivity::class.java)))
}
