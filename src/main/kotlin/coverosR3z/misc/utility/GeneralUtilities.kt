package coverosR3z.misc.utility

import coverosR3z.misc.exceptions.InexactInputsException
import coverosR3z.server.types.Element
import java.lang.IllegalStateException
import java.net.URLDecoder
import java.net.URLEncoder

fun generateRandomString(length: Int) : String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}

/**
 * Returns the value parsed as an int.  If this fails, returns
 * an [IllegalStateException] with the message
 */
fun checkParseToInt(value: String?,
                    nullMsg: () -> String = {"Integer must not be a null value"},
                    blankMsg: () -> String = {"Integer must not be blank"},
                    parseMsg: () -> String = {"Must be able to parse ${value?.replace(" ", "(SPACE)")} as integer"}): Int {
    val notNullValue = requireNotNull(value){ nullMsg() }
    require(notNullValue.isNotBlank()) { blankMsg() }
    return try {
        notNullValue.toInt()
    } catch (ex: java.lang.NumberFormatException) {
        throw IllegalArgumentException(parseMsg())
    }
}

/**
 * Returns the value parsed as an int.  If this fails, returns
 * an [IllegalStateException] with the message
 */
fun checkParseToDouble(value: String?,
                    nullMsg: () -> String = {"Double must not be a null value"},
                    blankMsg: () -> String = {"Double must not be blank"},
                    parseMsg: () -> String = {"Must be able to parse ${value?.replace(" ", "(SPACE)")} as double"}): Double {
    val notNullValue = requireNotNull(value){ nullMsg() }
    require(notNullValue.isNotBlank()) { blankMsg() }
    return try {
        notNullValue.toDouble()
    } catch (ex: java.lang.NumberFormatException) {
        throw IllegalArgumentException(parseMsg())
    }
}

/**
 * Returns the value parsed as an int.  If this fails, returns
 * an [IllegalStateException] with the message
 */
fun checkParseToLong(value: String?,
                    nullMsg: () -> String = {"Must not be a null value"},
                    blankMsg: () -> String = {"Must not be blank"},
                    parseMsg: () -> String = {"Must be able to parse ${value?.replace(" ", "(SPACE)")} as long"}): Long {
    val notNullValue = requireNotNull(value){ nullMsg() }
    require(notNullValue.isNotBlank()) { blankMsg() }
    return try {
        notNullValue.toLong()
    } catch (ex: java.lang.NumberFormatException) {
        throw IllegalArgumentException(parseMsg())
    }
}

/**
 * Given a set of keys needed by an API (see files ending in "API") and the
 * actual data received, throw an error if it doesn't have the required inputs
 * (Note: there may be *more* inputs sent than this, but these are the required ones)
 * @param receivedKeys the data sent to the API
 * @param requiredKeys the exact keys required
 */
fun checkHasRequiredInputs(receivedKeys: Set<String>, requiredKeys: Set<Element>) {
    val requiredKeyStrings = requiredKeys.map { it.getElemName() }.toSet()
    if (!receivedKeys.containsAll(requiredKeyStrings)) {
        throw InexactInputsException("expected keys: ${requiredKeyStrings}. received keys: $receivedKeys")
    }
}

/**
 * returns the time spent on the items inside.
 * To use: simply wrap the code with getTime, like this:
 *
 *      val timeTaken = getTime {
 *           foo()
 *           bar()
 *      }
 */
fun <T>getTime(function: () -> T): Pair<Long, T> {
    val start = System.currentTimeMillis()
    val result : T = function()
    val finish = System.currentTimeMillis()
    return Pair(finish - start, result)
}

/**
 * Returns text that has three symbols replaced -
 * the less-than, greater-than, and ampersand.
 * See https://www.w3.org/International/questions/qa-escapes#use
 *
 * This will protect against something like <div>$USERNAME</div> allowing
 * a username of
 *      <script>alert(1)</script>
 * becoming
 *      <div><script>alert(1)</script</div>
 * and instead becomes
 *      <div>&lt;script&gt;alert(1)&lt;/script&gt;</div>
 *
 * If the text is going inside an attribute (e.g. <div class="TEXT_GOES_HERE"> )
 * Then you need to escape slightly differently. In that case see [safeAttr]
 */
fun safeHtml(input : String?) : String {
    return if (input == null) ""
     else {
        input.replace("&", "&amp;")
             .replace("<", "&lt;")
             .replace(">", "&gt;")
     }
}

/**
 * Replace dangerous text that would go inside an HTML attribute.
 * See [safeHtml]
 * If we get a null string, just return an empty string
 */
fun safeAttr(input : String?) : String {
    return if (input == null) ""
    else input
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

/**
 * Encodes UTF-8 text using URL-encoding
 */
fun encode(str : String) : String {
    return URLEncoder.encode(str, Charsets.UTF_8)
}

/**
 * Decodes URL-encoded UTF-8 text
 */
fun decode(str : String?) : String {
    requireNotNull(str)
    return URLDecoder.decode(str, Charsets.UTF_8)
}
