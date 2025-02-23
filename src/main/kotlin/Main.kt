// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.seanproctor.signaturepad.SignaturePad
import com.seanproctor.signaturepad.rememberSignaturePadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
    val state = rememberSignaturePadState()
    val tabletState = remember { TabletState() }
    var capturing by remember { mutableStateOf(false) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            val scope = rememberCoroutineScope()
            Box(Modifier.fillMaxSize()) {
                if (!capturing) {
                    Button(
                        modifier = Modifier.align(Alignment.Center),
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                tabletState.connectTablet(
                                    onCleared = {
                                        state.clear()
                                    },
                                    onAccepted = {
                                        println("Accepted")
                                    },
                                    onGestureMoved = state::gestureMoved,
                                    onGestureStarted = state::gestureStarted,
                                )
                                capturing = true
                            }
                        },
                    ) {
                        Text("Get signature")
                    }
                } else {
                    val aspectRatio by tabletState.aspectRatio.collectAsState()
                    SignaturePad(
                        modifier = Modifier.fillMaxSize()
                            .aspectRatio(aspectRatio)
                            .onSizeChanged {
                                tabletState.setSize(it.width, it.height)
                            },
                        state = state,
                        penColor = MaterialTheme.colors.onSurface,
                        penWidth = 1.dp,
                    )
                }
            }
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
