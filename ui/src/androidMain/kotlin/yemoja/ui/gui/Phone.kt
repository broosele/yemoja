package yemoja.ui.gui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import yemoja.data.json.CachedFileStore
import yemoja.data.json.DiskFileStore
import yemoja.data.json.LogbookReader
import yemoja.logic.Universe
import yemoja.logic.today
import yemoja.logic.divecomputer.Devices
import yemoja.logic.divecomputer.FoundDevices
import yemoja.logic.tideCalculators
import java.io.IOException
import java.util.concurrent.CompletableFuture

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
 * dive computer over Bluetooth, asked permission for when a download starts. `AND-6`. A computer
 * that wants the code it is showing typed is given a dialog to type it in. `LOGIC-24`. A read is
 * told to [onReading] while it runs, which is how the app keeps it alive in the background.
 * `AND-3`. An import is a file picked and an export a file named, through Android's own pickers.
 * `AND-9`.
 */
@Composable
fun Yemoja(onReading: (Underway?) -> Unit = {}) {
    val context = LocalContext.current
    val remembered = remember { context.getSharedPreferences(KEPT, Context.MODE_PRIVATE) }
    // Dive computers over Bluetooth LE, through the library the app carries. `AND-6`.
    val devices = remember { FoundDevices() }
    var held by remember { mutableStateOf<Universe?>(null) }
    var refused by remember { mutableStateOf<String?>(null) }
    // Whether a logbook is being read, which a folder on a cloud drive can make take a while.
    var opening by remember { mutableStateOf(false) }
    // How many of the files a first opening fetches have come, and of how many, once that is known.
    var fetched by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val scope = rememberCoroutineScope()
    // Read off the screen's own thread, so the app draws at once and says what it is doing rather
    // than staying blank while the files come in. `AND-5`.
    fun take(tree: Uri) {
        held = null
        fetched = null
        opening = true
        scope.launch {
            val opened = withContext(Dispatchers.IO) {
                openedIn(context, tree, devices) { done, of -> fetched = done to of }
            }
            opening = false
            when (opened) {
                is Opening.Done -> {
                    held = opened.universe
                    remembered.edit().putString(FOLDER, tree.toString()).apply()
                }

                is Opening.Refused -> refused = opened.reason
            }
        }
    }
    // The folder picked last time, opened again. One that will not open says why and leaves the
    // welcome, from which another can be picked.
    LaunchedEffect(Unit) { remembered.getString(FOLDER, null)?.let { take(Uri.parse(it)) } }
    // What a download has asked and is waiting on, a code its computer is showing. `LOGIC-24`.
    var awaited by remember { mutableStateOf<Asking?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            // A download still waiting is let go with no answer, or its thread would wait for good.
            awaited?.answer?.complete(null)
            devices.close()
        }
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
    // What an import and an export hand their file to, kept until the picker answers, which is
    // after the deed that asked has returned. `AND-9`.
    var picked by remember { mutableStateOf<((Picked) -> Unit)?>(null) }
    val importing = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { document ->
        val hand = picked
        picked = null
        if (document == null || hand == null) return@rememberLauncherForActivityResult
        scope.launch {
            // Off the screen's thread, a file on a cloud drive taking a while to come.
            val copied = withContext(Dispatchers.IO) { runCatching { copiedIn(context, document) } }
            copied.fold(
                onSuccess = { hand(it) },
                onFailure = {
                    refused = "${calledOf(context, document) ?: "The file"} could not be read: ${it.message}"
                },
            )
        }
    }
    var named by remember { mutableStateOf<((Named) -> Unit)?>(null) }
    val exporting = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(UNTYPED)) { document ->
        val hand = named
        named = null
        if (document == null || hand == null) return@rememberLauncherForActivityResult
        hand(namedFor(context, document))
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
            tides = { tideCalculators(::bundled) },
            ask = { question ->
                // Waited for on the thread that asked, which is a download's own, while the
                // screen's thread shows the question. Asked from the screen's thread there would
                // be nothing left to show it, so that is answered with nothing.
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    null
                } else {
                    val answer = CompletableFuture<String?>()
                    Handler(Looper.getMainLooper()).post { awaited = Asking(question, answer) }
                    answer.get()?.ifBlank { null }
                }
            },
            pick = { _, hand ->
                picked = hand
                // Any file: neither a UDDF document nor a Diving Log database has a type a
                // phone knows, so a filter would hide them.
                importing.launch(arrayOf(ANY))
            },
            save = { _, hand ->
                named = hand
                exporting.launch(EXPORT)
            },
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
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            if (opening) BeingOpened(fetched) else Application(held, platform)
        }
        refused?.let { said ->
            AlertDialog(
                onDismissRequest = { refused = null },
                confirmButton = { TextButton(onClick = { refused = null }) { Text("OK") } },
                text = { Text(said) },
            )
        }
        awaited?.let { asked ->
            Asked(asked.question) { typed ->
                awaited = null
                asked.answer.complete(typed)
            }
        }
    }
}

/** Asking is a question a download put and is waiting on: what it asked, and where the answer goes. */
private class Asking(val question: String, val answer: CompletableFuture<String?>)

/**
 * A [question] with a box to answer it in, and [onAnswer] told what was typed, or nothing where
 * the reader gave none.
 *
 * Leaving the dialog is giving no answer, which is what the download is then told.
 */
@Composable
private fun Asked(question: String, onAnswer: (String?) -> Unit) {
    var typed by remember(question) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onAnswer(null) },
        confirmButton = { TextButton(onClick = { onAnswer(typed) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { onAnswer(null) }) { Text("Cancel") } },
        text = {
            Column {
                Text(question.replaceFirstChar { it.uppercase() })
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        },
    )
}

/**
 * What the screen shows while a logbook is read: that it is, and, where files are being fetched
 * for a first opening, how many of them have come as [fetched] counts them.
 */
@Composable
private fun BeingOpened(fetched: Pair<Int, Int>?) {
    // On the theme's own surface, which is what gives the words a colour that shows in the dark.
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            val said = when {
                fetched == null -> "Opening the logbook…"
                fetched.first < fetched.second -> "Reading the logbook: ${fetched.first} of ${fetched.second} files"
                else -> "Reading the logbook: all ${fetched.second} files are here"
            }
            Text(said, modifier = Modifier.padding(top = 16.dp))
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
private fun openedIn(context: Context, tree: Uri, devices: Devices, told: (done: Int, of: Int) -> Unit): Opening {
    // Read through a copy of the files kept in the app's own storage, so a folder on a cloud drive
    // gives up only what changed since the last opening. One copy per folder. `JSON-28`.
    val granted = GrantedFileStore(context.contentResolver, tree)
    val copies = context.filesDir.resolve("copies").resolve(tree.toString().hashCode().toUInt().toString(16))
    val store = CachedFileStore(granted, granted::stamps, DiskFileStore(copies.path))
    val called = nameOf(context, tree) ?: "this folder"
    val staging = DiskFileStore(context.filesDir.resolve("import").path)
    val proposing = DiskFileStore(context.filesDir.resolve("proposed").path)
    return try {
        // What the copy lacks is fetched several files at a time, before the reading asks for
        // each in turn. A logbook's own files are all JSON, and nothing else is worth fetching.
        granted.fetchAhead(store.wanting().filter { it.endsWith(".json") }, told)
        val universe = if (store.isFile(LogbookReader.MANIFEST)) {
            Universe.open(store, staging, proposing, devices)
        } else {
            Universe.create(store, called, staging, proposing, devices)
        }
        Opening.Done(universe)
    } catch (refused: Exception) {
        // The version is said with it, since a refusal is often sent back as a picture of itself.
        Opening.Refused("$called could not be opened: ${refused.message} (Yemoja ${versionOf(context)})")
    }
}

/**
 * What the granted folder [tree] is called, or absent where the provider will not say.
 *
 * Asked of the provider, since the tree's own id is the provider's: Google Drive's is a string of
 * letters that names nothing to a reader, where a folder on the phone's own storage ends in its path.
 */
private fun nameOf(context: Context, tree: Uri): String? =
    calledOf(context, DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)))

/** What [document] is called by its provider, or absent where the provider will not say. */
private fun calledOf(context: Context, document: Uri): String? {
    val asked = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
    return runCatching {
        context.contentResolver.query(document, asked, null, null, null)?.use { rows ->
            if (rows.moveToFirst() && !rows.isNull(0)) rows.getString(0) else null
        }
    }.getOrNull()?.ifBlank { null }
}

/**
 * The [document] the reader picked to import, copied to where the logbook can read it by path.
 *
 * A picked document is reached through its provider and has no path. The copy is in the app's
 * cache under the document's own name, and the one before it is removed: what an import staged is
 * kept elsewhere and does not read the copy again. Throws where the document cannot be read.
 * `AND-9`.
 */
private fun copiedIn(context: Context, document: Uri): Picked {
    val called = calledOf(context, document) ?: "import"
    val folder = context.cacheDir.resolve("import")
    folder.deleteRecursively()
    folder.mkdirs()
    val copy = folder.resolve(called.replace('/', '_'))
    val from = context.contentResolver.openInputStream(document) ?: throw IOException("its provider gave nothing")
    from.use { read -> copy.outputStream().use { read.copyTo(it) } }
    return Picked(copy.path, called)
}

/**
 * Where an export to the [document] the reader named is written, and how it gets there.
 *
 * Written in the app's cache, the logbook writing by path, and then copied into the document
 * through its provider. `AND-9`.
 */
private fun namedFor(context: Context, document: Uri): Named {
    val folder = context.cacheDir.resolve("export")
    folder.mkdirs()
    val written = folder.resolve(EXPORT)
    return Named(written.path, calledOf(context, document) ?: "the file") {
        val to = context.contentResolver.openOutputStream(document, "w")
            ?: throw IOException("its provider would not take it")
        to.use { write -> written.inputStream().use { it.copyTo(write) } }
        written.delete()
    }
}

/** Any type of file, which is what a picker is asked for where the type is one a phone has no name for. */
private const val ANY = "*/*"

/**
 * The type an export is made as. A UDDF document has no type of its own, and one made as XML
 * would be given `.xml` after its name by some providers.
 */
private const val UNTYPED = "application/octet-stream"

/** What an export is called until the reader names it otherwise. */
private const val EXPORT = "logbook.uddf"

/** The app's version as installed, or a question mark where the phone will not say. */
private fun versionOf(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

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
