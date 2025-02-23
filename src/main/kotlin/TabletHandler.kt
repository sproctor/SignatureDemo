import com.WacomGSS.STU.ITabletHandler
import com.WacomGSS.STU.Protocol.*
import com.WacomGSS.STU.STUException
import com.WacomGSS.STU.Tablet.IEncryptionHandler
import com.WacomGSS.STU.Tablet.IEncryptionHandler2
import kotlinx.coroutines.flow.MutableStateFlow
import java.math.BigInteger
import java.security.Key
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class TabletHandler(
    private val onData: (PenData) -> Unit,
) : ITabletHandler {

    internal class MyEncryptionHandler : IEncryptionHandler {
        private var p: BigInteger? = null
        private var g: BigInteger? = null
        private var privateKey: BigInteger? = null
        private var aesCipher: Cipher? = null
        override fun reset() {
            clearKeys()
            p = null
            g = null
        }

        override fun clearKeys() {
            privateKey = null
            aesCipher = null
        }

        override fun requireDH(): Boolean {
            return p == null || g == null
        }

        override fun setDH(dhPrime: DHprime, dhBase: DHbase) {
            p = BigInteger(1, dhPrime.value)
            g = BigInteger(1, dhBase.value)
        }

        override fun generateHostPublicKey(): PublicKey? {
            privateKey =
                BigInteger("0F965BC2C949B91938787D5973C94856C", 16) // should be randomly chosen according to DH rules.
            val publicKey_bi = g!!.modPow(privateKey, p)
            try {
                return PublicKey(publicKey_bi.toByteArray())
            } catch (e: Exception) {
            }
            return null
        }

        override fun computeSharedKey(devicePublicKey: PublicKey) {
            val devicePublicKey_bi = BigInteger(1, devicePublicKey.value)
            val sharedKey = devicePublicKey_bi.modPow(privateKey, p)
            var array = sharedKey.toByteArray()
            if (array[0].toInt() == 0) {
                val tmp = ByteArray(array.size - 1)
                System.arraycopy(array, 1, tmp, 0, tmp.size)
                array = tmp
            }
            try {
                val aesKey: Key = SecretKeySpec(array, "AES")
                aesCipher = Cipher.getInstance("AES/ECB/NoPadding")
                aesCipher!!.init(Cipher.DECRYPT_MODE, aesKey)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
            aesCipher = null
        }

        override fun decrypt(data: ByteArray): ByteArray? {
            try {
                return aesCipher!!.doFinal(data)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }
    }

    internal class MyEncryptionHandler2 : IEncryptionHandler2 {
        private var n: BigInteger? = null
        private var d: BigInteger? = null
        private val e: BigInteger
        private var aesCipher: Cipher? = null

        init {
            e = BigInteger.valueOf(65537)
        }

        override fun reset() {
            clearKeys()
            d = null
            n = null
        }

        override fun clearKeys() {
            aesCipher = null
        }

        override fun getSymmetricKeyType(): SymmetricKeyType {
            return SymmetricKeyType.AES128
            //return SymmetricKeyType.AES256; // requires "Java Crypotography Extension (JCE) Unlimited Strength Jurisdiction Policy Files"
        }

        override fun getAsymmetricPaddingType(): AsymmetricPaddingType {
            return AsymmetricPaddingType.None // not recommended
            //return AsymmetricPaddingType.OAEP;
        }

        override fun getAsymmetricKeyType(): AsymmetricKeyType {
            return AsymmetricKeyType.RSA2048
        }

        fun toHex(arr: ByteArray): String {
            val sb = StringBuilder(arr.size * 2)
            val formatter = Formatter(sb)
            for (b in arr) {
                formatter.format("%02x", b)
            }
            return sb.toString()
        }

        override fun getPublicExponent(): ByteArray {
            return e.toByteArray()
        }

        override fun generatePublicKey(): ByteArray {
            if (n != null) {
                return n!!.toByteArray()
            }

            // Generate your key pair here.
            // For speed and ease of demonstration, we use some precalulated values.
            // This is NOT recommended for production use!
            n = BigInteger(
                "93DDCD8BC9E478491C54413F0484FE79DDDA464A0F53AC043C6194FD473FB75B893C783F56701D2D30B021C4EE0401F058B98F035804CFBB0E67A8136A2F052A98037457460FAB7B3B148EC7C95604FF2192EA03FCC04285EC539DDF3375678E4C4D926163ABBC609C41EF5673C449DF5AC74FFA8150D33FC5436C5CC2621E642C42C10E71BF3895B07A52E7D86C84D3A9269462CF2E484E17D34DEDFF9090D6745A00EF40EE33C71C5688E856AF3C6C42AF3C4C8523711498F4508DC18BC5E24F38C2C7E971BA61BB24B19E3AE74D4D57023AF59BA9D979FCF48080E18D920E31A319C544DEA0E9DAF088E09B6098C07C20328DD0F62C5C99FCD2EB7C4F7CD3",
                16
            )
            d = BigInteger(
                "2B1DD41FDCE1180A098EAFEFD63B8990B3964044BC2F63CB6067FBEFD6E4C76C9399E45E63B01171E9EE920A40753EB37CCBAEDE04BE726C5308FAC39E84D376D618BBC5EF1206A8CA537646DF788BC07163CB851A205DC57B61EE78F52258EDEF65F7371ABF2B10E8BF7930B655184D5EC51B972A3A0D3F5D2009EB0A6B5DFCD8DDD29CA704CDFF2086A211CFE7E0C395E9B53D5B1FF370BFC90C3A8255A64A8674E8FB41002838ABFC430EA558DECFFE1B563D96D06DCAEA8A5793DCA68C3FB4265BCE38CBEFBBAEB3B8FC1689F7B8510BF20B9D72E490887FB36F4722FEB813E6252DDC3BB17DA645ACEE8292AB85FA1A3048B7BBB34F3B50489BE7913421",
                16
            )
            return n!!.toByteArray()
        }

        override fun computeSessionKey(data: ByteArray) {
            val c = BigInteger(1, data)
            val m = c.modPow(d, n)
            val keySizeBytes = 128 / 8
            var k = m.toByteArray()
            if (k.size != keySizeBytes) {
                val k2 = ByteArray(keySizeBytes)
                if (k.size > keySizeBytes) System.arraycopy(
                    k,
                    k.size - keySizeBytes,
                    k2,
                    0,
                    k2.size
                ) else System.arraycopy(k, 0, k2, 1, 15)
                k = k2
            }
            val aesKey: Key = SecretKeySpec(k, "AES")
            try {
                aesCipher = Cipher.getInstance("AES/ECB/NoPadding")
                aesCipher!!.init(Cipher.DECRYPT_MODE, aesKey)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
            aesCipher = null
        }

        override fun decrypt(data: ByteArray): ByteArray? {
            try {
                return aesCipher!!.doFinal(data)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }
    }

    val errorMessage = MutableStateFlow<String?>(null)

    override fun onGetReportException(e: STUException?) {
        errorMessage.value = e?.toString()
    }

    override fun onUnhandledReportData(data: ByteArray?) {
    }

    override fun onPenData(penData: PenData?) {
        // println("Got pen data: (${penData?.x},  ${penData?.y}, ${penData?.sw})")
        if (penData != null) {
            onData(penData)
        }
    }

    override fun onPenDataOption(penDataOption: PenDataOption?) {
        println("Got pen data option: ${penDataOption?.option}")
        onPenData(penDataOption)
    }

    override fun onPenDataEncrypted(penDataEncrypted: PenDataEncrypted?) {
        onPenData(penDataEncrypted?.penData1)
        onPenData(penDataEncrypted?.penData2)
    }

    override fun onPenDataEncryptedOption(penDataEncryptedOption: PenDataEncryptedOption?) {
        onPenData(penDataEncryptedOption?.penDataOption1)
        onPenData(penDataEncryptedOption?.penDataOption2)
    }

    override fun onPenDataTimeCountSequence(penDataTimeCountSequence: PenDataTimeCountSequence?) {
        onPenData(penDataTimeCountSequence)
    }

    override fun onPenDataTimeCountSequenceEncrypted(penDataTimeCountSequenceEncrypted: PenDataTimeCountSequenceEncrypted?) {
        onPenData(penDataTimeCountSequenceEncrypted)
    }

    override fun onEventDataPinPad(p0: EventDataPinPad?) {
    }

    override fun onEventDataKeyPad(p0: EventDataKeyPad?) {
    }

    override fun onEventDataSignature(eventData: EventDataSignature?) {
        onSignatureEvent(eventData!!.keyValue)
    }

    override fun onEventDataPinPadEncrypted(p0: EventDataPinPadEncrypted?) {
        TODO("Not yet implemented")
    }

    override fun onEventDataKeyPadEncrypted(p0: EventDataKeyPadEncrypted?) {
        TODO("Not yet implemented")
    }

    override fun onEventDataSignatureEncrypted(p0: EventDataSignatureEncrypted?) {
        TODO("Not yet implemented")
    }

    override fun onDevicePublicKey(p0: DevicePublicKey?) {
        TODO("Not yet implemented")
    }

    override fun onEncryptionStatus(p0: EncryptionStatus?) {
        TODO("Not yet implemented")
    }

    private fun onSignatureEvent(keyValue: Byte) {
        when (keyValue) {
            0.toByte() -> {} // cancel pressed
            1.toByte() -> {} // ok pressed
            2.toByte() -> {} // clear pressed
        }
    }
}