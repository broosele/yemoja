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
        // A read is kept alive in the background by a service of the app's own. `AND-3`.
        setContent { Yemoja { read -> ReadingService.tell(applicationContext, read) } }
    }
}
