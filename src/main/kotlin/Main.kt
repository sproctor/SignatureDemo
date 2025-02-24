import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.seanproctor.signaturepad.SignaturePad
import com.seanproctor.signaturepad.rememberSignaturePadState

@Composable
@Preview
fun App() {
    val state = rememberSignaturePadState()
    var tabletState by remember { mutableStateOf<TabletState?>(null) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                if (tabletState == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = {
                                tabletState = TabletState(TabletType.WACOM)
                            },
                        ) {
                            Text("Wacom tablet")
                        }
                        Button(
                            onClick = {
                                tabletState = TabletState(TabletType.TOPAZ)
                            },
                        ) {
                            Text("Topaz tablet")
                        }
                    }
                } else {
                    LaunchedEffect(Unit) {
                        tabletState!!.connectTablet(
                            onCleared = {
                                state.clear()
                            },
                            onAccepted = {
                                println("Accepted")
                            },
                            onGestureMoved = state::gestureMoved,
                            onGestureStarted = state::gestureStarted,
                        )
                    }
                    val aspectRatio by tabletState!!.aspectRatio.collectAsState()
                    SignaturePad(
                        modifier = Modifier.fillMaxSize()
                            .aspectRatio(aspectRatio)
                            .onSizeChanged {
                                tabletState!!.setSize(it.width, it.height)
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
