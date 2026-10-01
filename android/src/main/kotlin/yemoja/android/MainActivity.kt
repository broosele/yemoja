package yemoja.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import yemoja.ui.gui.Yemoja

/*
 * The one activity, which hands the screen to the window the ui layer draws.
 *
 * See ../../../../../../../ui/gui/phone/android/doc.md.
 */

/** MainActivity is the app as Android launches it. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The logbook lives in the app's own storage until a folder the user picks is built,
        // which is where it belongs. `AND-5`.
        val logbook = filesDir.resolve("logbook").path
        setContent { Yemoja(logbook) }
    }
}
