package coverosR3z.server.utility

import coverosR3z.authentication.api.LoginAPI
import coverosR3z.authentication.api.RegisterAPI
import coverosR3z.authentication.api.generateLogoutPage
import coverosR3z.logging.LoggingAPI
import coverosR3z.server.api.HomepageAPI
import coverosR3z.server.api.handleBadRequest
import coverosR3z.server.api.handleNotFound
import coverosR3z.server.types.NamedPaths
import coverosR3z.server.types.PreparedResponseData
import coverosR3z.server.types.ServerData
import coverosR3z.server.types.Verb
import coverosR3z.timerecording.api.EmployeeAPI
import coverosR3z.timerecording.api.EnterTimeAPI
import coverosR3z.timerecording.api.ProjectAPI
import coverosR3z.timerecording.api.ViewTimeAPI


/**
 * Examine the request and headers, direct the request to a proper
 * point in the system that will take the proper action, returning a
 * proper response with headers.
 *
 * If we cannot find a dynamic processor, it means the user wants a static
 * file, which we handle at the end.
 *
 */
fun directToProcessor(sd : ServerData): PreparedResponseData {
    val verb = sd.rd.verb
    val path = sd.rd.path
    val au = sd.au
    val tru = sd.tru
    val rd = sd.rd

    /**
     * The user currently logged in
     */
    val user = rd.user

    /**
     * The data sent in the POST body
     */
    val data = rd.data

    if (verb == Verb.INVALID) {
        return handleBadRequest()
    }

    val authStatus = sd.authStatus

    return when (Pair(verb, path)){
        Pair(Verb.GET, ""),
        Pair(Verb.GET, NamedPaths.HOMEPAGE.path)  -> HomepageAPI.handleGet(sd)
        Pair(Verb.GET, NamedPaths.ENTER_TIME.path) -> doGETRequireAuth(authStatus) { EnterTimeAPI.generateEnterTimePage(tru, user.name) }
        Pair(Verb.GET, NamedPaths.TIMEENTRIES.path) -> ViewTimeAPI.handleGet(sd)
        Pair(Verb.GET, NamedPaths.CREATE_EMPLOYEE.path) -> doGETRequireAuth(authStatus) { EmployeeAPI.generateCreateEmployeePage(user.name) }
        Pair(Verb.GET, NamedPaths.EMPLOYEES.path) -> doGETRequireAuth(authStatus) { EmployeeAPI.generateExistingEmployeesPage(user.name, tru) }
        Pair(Verb.GET, NamedPaths.LOGIN.path) -> doGETRequireUnauthenticated(authStatus) { LoginAPI.generateLoginPage() }
        Pair(Verb.GET, NamedPaths.REGISTER.path) -> doGETRequireUnauthenticated(authStatus) { RegisterAPI.generateRegisterUserPage(tru) }
        Pair(Verb.GET, NamedPaths.CREATE_PROJECT.path) -> doGETRequireAuth(authStatus) { ProjectAPI.generateCreateProjectPage(user.name) }
        Pair(Verb.GET, NamedPaths.LOGOUT.path) -> doGETRequireAuth(authStatus) { generateLogoutPage(au, user) }
        Pair(Verb.GET, NamedPaths.LOGGING.path) -> doGETRequireAuth(authStatus) { LoggingAPI.generateLoggingConfigPage() }

        // posts
        Pair(Verb.POST, NamedPaths.ENTER_TIME.path) -> doPOSTAuthenticated(authStatus, EnterTimeAPI.requiredInputs, data) { EnterTimeAPI.handlePOST(tru, user.employeeId, data) }
        Pair(Verb.POST, NamedPaths.CREATE_EMPLOYEE.path) -> doPOSTAuthenticated(authStatus, EmployeeAPI.requiredInputs, data) { EmployeeAPI.handlePOST(tru, data) }
        Pair(Verb.POST, NamedPaths.LOGIN.path) -> doPOSTRequireUnauthenticated(authStatus, LoginAPI.requiredInputs, data) { LoginAPI.handlePOST(au, data) }
        Pair(Verb.POST, NamedPaths.REGISTER.path) -> doPOSTRequireUnauthenticated(authStatus, RegisterAPI.requiredInputs, data) { RegisterAPI.handlePOST(au, data) }
        Pair(Verb.POST, NamedPaths.CREATE_PROJECT.path) -> doPOSTAuthenticated(authStatus, ProjectAPI.requiredInputs, data) { ProjectAPI.handlePOST(tru, data) }
        Pair(Verb.POST, NamedPaths.LOGGING.path) -> doPOSTAuthenticated(authStatus, LoggingAPI.requiredInputs, data) { LoggingAPI.handlePOST(data) }

        else -> {
            handleNotFound()
        }
    }
}