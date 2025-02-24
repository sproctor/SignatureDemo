import androidx.compose.ui.geometry.Offset
import com.WacomGSS.STU.Protocol.EncodingMode
import com.WacomGSS.STU.Protocol.InkingMode
import com.WacomGSS.STU.Protocol.ProtocolHelper
import com.WacomGSS.STU.Tablet
import com.WacomGSS.STU.TlsDevice
import com.WacomGSS.STU.UsbDevice
import com.topaz.sigplus.SigPlus
import com.topaz.sigplus.SigPlusEvent0
import com.topaz.sigplus.SigPlusListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import util.getEncodingMode
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage

class TabletState {

    private val _aspectRatio = MutableStateFlow(1.5f)
    val aspectRatio = _aspectRatio.asStateFlow()

    var penDown = false
    var buttonDown = false

    var width = 0
    var height = 0

    private var button1: Position = Position(0, 0, 0, 0)
    private var button2: Position = Position(0, 0, 0, 0)

    private var tablet: Tablet? = null

    val scope = CoroutineScope(Dispatchers.IO)

    fun connectTablet(
        onCleared: () -> Unit,
        onAccepted: () -> Unit,
        onGestureStarted: (Offset) -> Unit,
        onGestureMoved: (Offset) -> Unit,
    ): Boolean {
        try {
            val sigObj = SigPlus()
            sigObj.tabletModel = "SignatureGem1X5"
            sigObj.tabletComPort = "HID1"
            sigObj.tabletState = 1
            val tabletWidth = sigObj.tabletLogicalXSize
            val tabletHeight = sigObj.tabletLogicalYSize
            _aspectRatio.value = tabletWidth.toFloat() / tabletHeight

            suspend fun drawPoints(stroke: Int, start: Int, end: Int) {
                withContext(Dispatchers.Main) {
                    for (i in start until end) {
                        val x = sigObj.getPointXValue(stroke, i).toFloat() * width / tabletWidth
                        val y = sigObj.getPointYValue(stroke, i).toFloat() * height / tabletHeight
                        val offset = Offset(x, y)
                        if (i == 0) {
                            onGestureStarted(offset)
                        } else {
                            onGestureMoved(offset)
                        }
                    }
                }
            }

            scope.launch {
                var currentStroke = 0
                var registeredPoints = 0
                sigObj.clearTablet()
                sigObj.addSigPlusListener(object : SigPlusListener {
                    override fun handleTabletTimerEvent(p0: SigPlusEvent0?) {
                        println("tabletTimerEvent")
                    }

                    override fun handleNewTabletData(p0: SigPlusEvent0?) {
                        println("newTabletData")
                    }

                    override fun handleKeyPadData(p0: SigPlusEvent0?) {
                        println("keyPadData")
                    }
                })
                while (true) {
                    val strokes = sigObj.numberOfStrokes
                    //println("Strokes: $strokes")
                    while (currentStroke + 1 < strokes) {
                        val points = sigObj.getNumPointsForStroke(currentStroke)
                        println("Stroke $currentStroke: $points")
                        drawPoints(currentStroke, registeredPoints, points)
                        currentStroke++
                        registeredPoints = 0
                    }
                    val points = sigObj.getNumPointsForStroke(currentStroke)
//                    println("Stroke $currentStroke: $points")
                    if (points > registeredPoints) {
                        println("Stroke $currentStroke: $points")
                        drawPoints(currentStroke, registeredPoints, points)
                        registeredPoints = points
                    }
                    delay(100)
                }
            }

            val usbDevices = UsbDevice.getUsbDevices()
            val tlsDevices = TlsDevice.getTlsDevices()

            val usbDevice = usbDevices.firstOrNull()
            val tlsDevice = tlsDevices.firstOrNull()

            if (usbDevice != null || tlsDevice != null) {
                val tablet = Tablet()
                this.tablet = tablet
                tablet.encryptionHandler = TabletHandler.MyEncryptionHandler()
                tablet.encryptionHandler2 = TabletHandler.MyEncryptionHandler2()
                val error = if (usbDevice != null) {
                    println("Connecting usb")
                    tablet.usbConnect(usbDevice, true)
                } else {
                    println("Connecting tls")
                    tablet.tlsConnect(tlsDevice)
                }
                if (error != 0) {
                    throw RuntimeException("Failed to connect to tablet, error: $error")
                }

                val capability = tablet.capability
                val information = tablet.information

                val tabletWidth = capability.tabletMaxX
                val tabletHeight = capability.tabletMaxY

                val screenWidth = capability.screenWidth
                val screenHeight = capability.screenHeight

                if (tablet.productId != UsbDevice.ProductId_300) {
                    // For most, place buttons across the bottom of the screen
                    val y = screenHeight * 6 / 7
                    val buttonHeight = screenHeight - y
                    button1 = Position(0, y, screenWidth / 2, buttonHeight)
                    button2 = Position(button1.width, y, capability.screenWidth - button1.width, buttonHeight)
                } else {
                    // The STU-300 is very shallow, so it is better to utilise
                    // the buttons to the side of the display instead.
                    val x = capability.screenWidth * 3 / 4
                    val buttonWidth = capability.screenWidth - x

                    button1 = Position(x, 0, buttonWidth, capability.screenHeight / 2)
                    button2 = Position(x, button1.height, buttonWidth, capability.screenHeight - button1.height)
                }

                _aspectRatio.value = tabletWidth.toFloat() / tabletHeight


                val tabletHandler = TabletHandler { data ->
                    if (data.sw == 0) {
                        penDown = false
                        buttonDown = false
                    } else {
                        if (!buttonDown) {
                            val offset = Offset(
                                x = data.x.toFloat() * width / tabletWidth,
                                y = data.y.toFloat() * height / tabletHeight,
                            )
                            if (!penDown) {
                                val screenPoint = Point(
                                    x = data.x * screenWidth / tabletWidth,
                                    y = data.y * screenHeight / tabletHeight,
                                )
                                if (button1.contains(screenPoint)) {
                                    buttonDown = true
                                    clearScreen()
                                    onCleared()
                                } else if (button2.contains(screenPoint)) {
                                    buttonDown = true
                                    onAccepted()
                                } else {
                                    onGestureStarted(offset)
                                    penDown = true
                                }
                            } else {
                                onGestureMoved(offset)
                            }
                        }
                    }
                }
                tablet.addTabletHandler(tabletHandler)

                clearScreen()

                tablet.setInkingMode(InkingMode.On)

                return true
            } else {
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun clearScreen() {
        val tablet = tablet ?: return
        val capability = tablet.capability
        val encodingMode = tablet.getEncodingMode()

        // Draw blank
        val image = BufferedImage(capability.screenWidth, capability.screenHeight, BufferedImage.TYPE_INT_RGB)
        val gfx = image.createGraphics()
        gfx.color = Color.WHITE
        gfx.fillRect(0, 0, image.width, image.height)

        // Draw buttons
        val fontSize = button1.height / 2
        gfx.font = Font("Arial", Font.PLAIN, fontSize)
        val buttonColor = if (encodingMode == EncodingMode.EncodingMode_1bit) Color.WHITE
        else Color.LIGHT_GRAY
        drawButton(gfx, button1, "Clear", buttonColor, Color.BLACK)
        drawButton(gfx, button2, "Accept", buttonColor, Color.BLACK)
        gfx.dispose()

        val data = ProtocolHelper.flatten(image, image.width, image.height, encodingMode)
        tablet.writeImage(encodingMode, data)
    }

    fun setSize(width: Int, height: Int) {
        this.width = width
        this.height = height
    }
}

private fun drawCenteredString(gfx: Graphics2D, text: String, position: Position) {
    with(position) {
        val fm = gfx.getFontMetrics(gfx.font)
        val textHeight = fm.height
        val textWidth = fm.stringWidth(text)

        val textX = x + (width - textWidth) / 2
        val textY = y + (height - textHeight) / 2 + fm.ascent

        gfx.drawString(text, textX, textY)
    }
}

private fun drawButton(gfx: Graphics2D, position: Position, text: String, backgroundColor: Color, textColor: Color) {
    with(position) {
        gfx.color = backgroundColor
        gfx.fillRect(x, y, width, height)
        gfx.color = textColor
        gfx.drawRect(x, y, width, height)
    }
    gfx.color = textColor
    drawCenteredString(gfx, text, position)
}

private data class Position(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun contains(point: Point): Boolean {
        return point.x >= x && point.x <= x + width && point.y >= y && point.y <= y + height
    }
}

private data class Point(val x: Int, val y: Int)
