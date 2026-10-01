package yemoja.ui.gui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import yemoja.data.json.DiskFileStore
import yemoja.data.json.LogbookReader
import yemoja.logic.Universe
import yemoja.logic.today
import yemoja.logic.divecomputer.Devices
import yemoja.logic.divecomputer.FoundDevices

/*
 * The window the screens are shown in on Android, which is the one part per platform.
 *
 * See ../../../../../../gui/phone/android/doc.md.
 */

/**
 * The application on an Android screen, over the logbook in the folder the user picked.
 *
 * The folder is remembered, so the app opens on it again. One that holds no logbook is made into
 * one, which is how a logbook is started here: New and Open are one question on a phone, which
 * folder. `AND-5`. What this supplies is the platform: the manual and the map read from the app,
 * whatever the phone opens a link with, whether the screen is a phone's, its back button, and a
 * dive computer over Bluetooth, asked permission for when a download starts. `AND-6`. A read is
 * told to [onReading] while it runs, which is how the app keeps it alive in the background.
 * `AND-3`. Absent so far: import and export.
 */
@Composable
fun Yemoja(onReading: (Underway?) -> Unit = {}) {
    val context = LocalContext.current
    val remembered = remember { context.getSharedPreferences(KEPT, Context.MODE_PRIVATE) }
    // Dive computers over Bluetooth LE, through the library the app carries. `AND-6`.
    val devices = remember { FoundDevices() }
    var held by remember { mutableStateOf<Universe?>(null) }
    var refused by remember { mutableStateOf<String?>(null) }
    fun take(tree: Uri) {
        held = null
        when (val opened = openedIn(context, tree, devices)) {
            is Opening.Done -> {
                held = opened.universe
                remembered.edit().putString(FOLDER, tree.toString()).apply()
            }

            is Opening.Refused -> refused = opened.reason
        }
    }
    // The folder picked last time, opened again; a grant that lapsed leaves the welcome.
    remember {
        remembered.getString(FOLDER, null)?.let { kept ->
            (openedIn(context, Uri.parse(kept), devices) as? Opening.Done)?.let { held = it.universe }
        }
        true
    }
    DisposableEffect(Unit) {
        onDispose { devices.close() }
    }
    // What a download needs the user to allow, asked when one starts rather than when the app
    // does, so the question comes with its reason in front of the user. `AND-2`.
    var granting by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val asking = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { given ->
        // Bluetooth is what the read needs; notifications only show it, so refusing them refuses
        // nothing that matters.
        granting?.invoke(BLUETOOTH.all { given[it] == true || allowed(context, it) })
        granting = null
    }
    val picking = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        // Kept across restarts of the phone, or the folder is unreachable the next time.
        val both = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(tree, both)
        take(tree)
    }
    // A phone where the screen's shorter side is under 600, which is where Android itself draws
    // the line; a tablet is laid out as a desktop is. `PHONE-3`.
    val compact = LocalConfiguration.current.smallestScreenWidthDp < TABLET
    val platform = remember(compact) {
        val choose = { picking.launch(null) }
        Platform(
            manual = CHAPTERS.map { file -> chapterOf(file, bundled("manual/$file")) },
            atlas = { Atlas.read { scale, layer -> bundled("libraries/map/$scale/$layer.txt") } },
            open = { url ->
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            today = ::today,
            compact = compact,
            back = { enabled, onBack -> BackHandler(enabled, onBack) },
            deeds = mapOf(Deed.NEW to choose, Deed.OPEN to choose),
            permit = { granted ->
                val wanted = BLUETOOTH + NOTIFYING
                if (wanted.all { allowed(context, it) }) {
                    granted(true)
                } else {
                    granting = granted
                    asking.launch(wanted.toTypedArray())
                }
            },
            reading = onReading,
        )
    }
    // Light or dark as the system is set, in the application's own colours. `GUI-3`.
    val scheme = if (isSystemInDarkTheme()) MARINE_DARK else MARINE_LIGHT
    // Android draws an app under its status and navigation bars, so the window keeps clear of
    // them itself.
    MaterialTheme(colorScheme = scheme) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) { Application(held, platform) }
        refused?.let { said ->
            AlertDialog(
                onDismissRequest = { refused = null },
                confirmButton = { TextButton(onClick = { refused = null }) { Text("OK") } },
                text = { Text(said) },
            )
        }
    }
}

/** What opening a folder came to: the logbook, or why not. */
private sealed class Opening {
    class Done(val universe: Universe) : Opening()
    class Refused(val reason: String) : Opening()
}

/**
 * The logbook in the granted folder [tree], made where the folder holds none.
 *
 * Staged reviews and an agent's changes are kept in the app's own storage, there being no folder
 * beside a granted one that the grant reaches.
 */
private fun openedIn(context: Context, tree: Uri, devices: Devices): Opening {
    val store = GrantedFileStore(context.contentResolver, tree)
    val called = tree.lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: "this folder"
    val staging = DiskFileStore(context.filesDir.resolve("import").path)
    val proposing = DiskFileStore(context.filesDir.resolve("proposed").path)
    return try {
        val universe = if (store.isFile(LogbookReader.MANIFEST)) {
            Universe.open(store, staging, proposing, devices)
        } else {
            Universe.create(store, called, staging, proposing, devices)
        }
        Opening.Done(universe)
    } catch (refused: Exception) {
        Opening.Refused("$called could not be opened: ${refused.message}")
    }
}

/** What reading a dive computer needs: to look for one, and to talk to it. Android 12 on. */
private val BLUETOOTH = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

/** What showing a read's notification needs, which Android asks for from 13 on. */
private val NOTIFYING: List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyList()
    }

/** Whether the user has already allowed [permission]. */
private fun allowed(context: Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** Where the app keeps what it remembers between runs. */
private const val KEPT = "yemoja"

/** The folder picked last, as the grant's own link. */
private const val FOLDER = "folder"

/** The shorter side of a tablet's screen at its least, in density-independent pixels. */
private const val TABLET = 600

/** A text the build put inside the app, read whole. */
private fun bundled(path: String): String {
    val stream = Bundled::class.java.getResourceAsStream("/$path")
        ?: error("$path is not bundled, and the build should have done that")
    return stream.bufferedReader().use { it.readText() }
}

/** Something to look up resources from. A function has no class of its own to ask. */
private object Bundled
