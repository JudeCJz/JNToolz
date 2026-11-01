package com.jntoolz

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.jntoolz.tools.Tools
import com.jntoolz.ui.theme.NothingCalendarTheme

class MainActivity : ComponentActivity() {
  // A tool asked for by the launch intent, e.g. "sot" from the tracker notification.
  private val openTool = mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Off the main thread: each tool's components follow its switch, whatever happened since (e.g. an update).
    Tools.reconcile(this)

    if (savedInstanceState == null) openTool.value = intent?.getStringExtra(EXTRA_OPEN_TOOL)
    enableEdgeToEdge()
    setContent {
      NothingCalendarTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(openTool = openTool.value, onToolOpened = { openTool.value = null })
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    intent.getStringExtra(EXTRA_OPEN_TOOL)?.let { openTool.value = it }
  }

  companion object {
    const val EXTRA_OPEN_TOOL = "open_tool"
  }
}
