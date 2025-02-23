package util

import com.WacomGSS.STU.Protocol.EncodingFlag
import com.WacomGSS.STU.Protocol.EncodingMode
import com.WacomGSS.STU.Protocol.ProtocolHelper
import com.WacomGSS.STU.Tablet
import kotlin.experimental.and

fun Tablet.getEncodingMode(): EncodingMode {
    val encodingFlag = ProtocolHelper.simulateEncodingFlag(productId, capability.encodingFlag)

    return if ((encodingFlag and EncodingFlag.EncodingFlag_24bit) != 0.toByte()) {
        if (supportsWrite()) EncodingMode.EncodingMode_24bit_Bulk else EncodingMode.EncodingMode_24bit
    } else if ((encodingFlag and EncodingFlag.EncodingFlag_16bit) != 0.toByte()) {
        if (supportsWrite()) EncodingMode.EncodingMode_16bit_Bulk else EncodingMode.EncodingMode_16bit
    } else {
        EncodingMode.EncodingMode_1bit
    }
}