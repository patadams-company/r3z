package coverosR3z
import coverosR3z.authentication.AuthenticationPersistence
import coverosR3z.authentication.AuthenticationUtilities
import coverosR3z.authentication.CurrentUserAccessor
import coverosR3z.authentication.FakeCurrentUserAccessor
import coverosR3z.domainobjects.*
import coverosR3z.persistence.PureMemoryDatabase
import coverosR3z.timerecording.ITimeEntryPersistence
import coverosR3z.timerecording.TimeEntryPersistence
import coverosR3z.timerecording.TimeRecordingUtilities
import kotlinx.serialization.json.Json
import org.junit.Assert
import java.lang.IllegalArgumentException

/**
 * a test helper method to create a [TimeEntry]
 */

val A_RANDOM_DAY_IN_JUNE_2020 = Date(2020, Month.JUN, 25)
val A_RANDOM_DAY_IN_JUNE_2020_PLUS_ONE = Date(2020, Month.JUN, 26)
val THREE_HOURS_FIFTEEN = Time((3 * 60) + 15)
val DEFAULT_SALT = "12345"
val DEFAULT_PASSWORD = "password1234"
val DEFAULT_USER = User(1, "DefaultUser", Hash.createHash(DEFAULT_PASSWORD + DEFAULT_SALT), DEFAULT_SALT, 1)
val DEFAULT_EMPLOYEE = Employee(1, "DefaultEmployee")
val DEFAULT_EMPLOYEE_NAME = EmployeeName("DefaultEmployee")
val DEFAULT_TIME = Time(60)
val DEFAULT_PROJECT = Project(1, "Default_Project")
val DEFAULT_PROJECT_NAME = ProjectName("Default_Project")

fun createTimeEntryPreDatabase(
        employee: Employee = DEFAULT_EMPLOYEE,
        time: Time = DEFAULT_TIME,
        project: Project = DEFAULT_PROJECT,
        details: Details = Details(),
        date: Date = A_RANDOM_DAY_IN_JUNE_2020
) = TimeEntryPreDatabase ( employee, project, time, date, details)

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
 * Makes it easy to access data in the resource directory
 * This is to read text from a file in the resource directory
 */
fun getResourceAsText(path: String): String {
        return object {}.javaClass.getResource(path).readText()
}

/**
 * This is useful for tests that require serialization
 * using Kotlin's own serialization framework.
 *
 * See https://github.com/Kotlin/kotlinx.serialization
 */
val jsonSerialzation : Json = Json{allowStructuredMapKeys = true}
val jsonSerialzationWithPrettyPrint : Json = Json{prettyPrint = true; allowStructuredMapKeys = true}

/**
 * A test helper method to generate a [TimeRecordingUtilities]
 * with a real database connected
 */
fun createTimeRecordingUtility(): TimeRecordingUtilities {
        val timeEntryPersistence : ITimeEntryPersistence = TimeEntryPersistence(PureMemoryDatabase())
        return TimeRecordingUtilities(timeEntryPersistence, FakeCurrentUserAccessor())
}

/**
 * Create an employee, "Alice", register a user for her, create a project
 */
fun initializeAUserAndLogin(cua : CurrentUserAccessor) : Pair<TimeRecordingUtilities, Employee>{
        val pmd = PureMemoryDatabase()
        val authPersistence = AuthenticationPersistence(pmd)
        val au = AuthenticationUtilities(authPersistence, cua)

        val tru = TimeRecordingUtilities(TimeEntryPersistence(pmd), cua)
        val alice = tru.createEmployee(EmployeeName("Alice"))

        au.register("alice", DEFAULT_PASSWORD, alice.id)
        au.login("alice", DEFAULT_PASSWORD)

        // Perform some quick checks
        Assert.assertEquals("Auth persistence and user persistence must agree",
                authPersistence.getUser(UserName("alice")), cua.get())
        Assert.assertTrue("Registration must have succeeded", au.isUserRegistered("alice"))

        tru.createProject(DEFAULT_PROJECT_NAME)

        return Pair(tru, alice)
}

