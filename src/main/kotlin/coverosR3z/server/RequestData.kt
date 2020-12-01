package coverosR3z.server

import coverosR3z.domainobjects.NO_USER
import coverosR3z.domainobjects.User

/**
 * Encapsulates the proper action by the server, based on what
 * the client wants from us
 */
data class RequestData(
        val verb: Verb,
        val path: String = "(NOTHING REQUESTED)",
        val data: Map<String, String> = emptyMap(),
        val user: User = NO_USER,
        val sessionToken: String = "NO TOKEN",
        val headers: List<String> = emptyList()
)