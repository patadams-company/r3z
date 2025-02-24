package coverosR3z.server.utility

import coverosR3z.system.utility.FullSystem
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket

/**
 * Provides access to the reading and writing functions on a socket
 * in a standardized, tightly-controlled way
 */
class SocketWrapper(override val socket: Socket, override val name: String? = null, private val fullSystem: FullSystem? = null) : ISocketWrapper {
    private val writer: OutputStream = socket.getOutputStream()
    private val reader: BufferedReader = BufferedReader(InputStreamReader(socket.inputStream))

    init {
        // setting a timeout on a socket reading.  This is
        // necessary because without it, a client might make
        // a connection and keep us listening forever, without
        // continuing on.
        val seconds = 10
        socket.soTimeout = seconds * 1000
        fullSystem?.addRunningSocket(socket)
    }

    override fun write(input: String) {
        fullSystem?.logger?.logTrace{"${name ?: socket} is sending: ${input.replace("\r", "(CR)").replace("\n", "(LF)")}"}

        writer.write(input.toByteArray())
    }

    override fun writeBytes(input: ByteArray) {
        writer.write(input)
    }

    override fun readLine(): String? {
        val valueRead : String? = reader.readLine()
        val readResult: String = when (valueRead) {
            "" -> "(EMPTY STRING)"
            null -> "(EOF - client closed connection)"
            else -> valueRead
        }

        fullSystem?.logger?.logTrace{"${name ?: socket} read this line: $readResult"}
        return valueRead
    }

    override fun read(len : Int) : String {
        val buf = CharArray(len)
        val lengthRead = reader.read(buf, 0, len)
        val body = buf.slice(0 until lengthRead).joinToString("")
        fullSystem?.logger?.logTrace{"$name actually read $lengthRead bytes.  body: $body"}
        return body
    }

    override fun close() {
        socket.close()
        fullSystem?.removeRunningSocket(socket)
    }
}