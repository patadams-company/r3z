package coverosR3z.timerecording

import coverosR3z.authentication.persistence.AuthenticationPersistence
import coverosR3z.authentication.types.CurrentUser
import coverosR3z.authentication.types.UserName
import coverosR3z.authentication.utility.AuthenticationUtilities
import coverosR3z.bddframework.BDD
import coverosR3z.misc.*
import coverosR3z.persistence.utility.PureMemoryDatabase.Companion.createEmptyDatabase
import coverosR3z.timerecording.exceptions.ExceededDailyHoursAmountException
import coverosR3z.timerecording.persistence.TimeEntryPersistence
import coverosR3z.timerecording.types.*
import coverosR3z.timerecording.utility.TimeRecordingUtilities
import org.junit.Assert.*
import org.junit.Test

/**
 * See EnteringTimeBDD.md
 */
class EnteringTimeBDD {

    /**
     * Just another flavor of happy path
     */
    @BDD
    @Test
    fun `A employee enters six hours on a project with copious notes`() {
        val s = TimeEntryUserStory.getScenario("A employee enters six hours on a project with copious notes")

        val (tru, entry) = addingProjectHoursWithNotes()
        s.markDone("Given I have worked 6 hours on project A on Monday with a lot of notes,")

        val recordStatus = tru.createTimeEntry(entry)
        s.markDone("when I enter in that time,")

        assertEquals("the system indicates it has persisted the new information", StatusEnum.SUCCESS, recordStatus.status)
        s.markDone("then the system indicates it has persisted the new information.")
    }

    @BDD
    @Test
    fun `A employee has already entered 24 hours for the day, they cannot enter more time on a new entry`() {
        val s = TimeEntryUserStory.getScenario("A employee has already entered 24 hours for the day, they cannot enter more time on a new entry")

        val (tru, newProject: Project, newEmployee: Employee) = enterTwentyFourHoursPreviously()
        s.markDone("given the employee has already entered 24 hours of time entries before,")

        val entry = createTimeEntryPreDatabase(time = Time(30), project = newProject, employee = newEmployee)
        s.markDone("when they enter in a new time entry for one hour,")

        assertThrows(ExceededDailyHoursAmountException::class.java) { tru.createTimeEntry(entry) }
        s.markDone("then the system disallows it.")
    }

    /*
     _ _       _                  __ __        _    _           _
    | | | ___ | | ___  ___  _ _  |  \  \ ___ _| |_ | |_  ___  _| | ___
    |   |/ ._>| || . \/ ._>| '_> |     |/ ._> | |  | . |/ . \/ . |<_-<
    |_|_|\___.|_||  _/\___.|_|   |_|_|_|\___. |_|  |_|_|\___/\___|/__/
                 |_|
     alt-text: Helper Methods
     */

    private fun addingProjectHoursWithNotes(): Pair<TimeRecordingUtilities, TimeEntryPreDatabase> {
        val pmd = createEmptyDatabase()
        val authPersistence = AuthenticationPersistence(pmd, testLogger)
        val au = AuthenticationUtilities(authPersistence, testLogger)

        val adminTru = TimeRecordingUtilities(
            TimeEntryPersistence(pmd, logger = testLogger),
            CurrentUser(DEFAULT_ADMIN_USER),
            testLogger)
        val alice = adminTru.createEmployee(EmployeeName("Alice"))
        val userName = UserName("alice_1")

        au.registerWithEmployee(userName, DEFAULT_PASSWORD,alice)
        val (_, user) = au.login(userName, DEFAULT_PASSWORD)

        val tru = TimeRecordingUtilities(
            TimeEntryPersistence(pmd, logger = testLogger),
            CurrentUser(user),
            testLogger)

        assertTrue("Registration must have succeeded", au.isUserRegistered(userName))

        val newProject = adminTru.createProject(DEFAULT_PROJECT_NAME)

        val entry = createTimeEntryPreDatabase(
                employee = alice,
                project = newProject,
                time = Time(60 * 6),
                details = Details("Four score and seven years ago, blah blah blah".repeat(10))
        )
        return Pair(tru, entry)
    }

    private fun enterTwentyFourHoursPreviously(): Triple<TimeRecordingUtilities, Project, Employee> {
        val pmd = createEmptyDatabase()
        val timeEntryPersistence = TimeEntryPersistence(pmd, logger = testLogger)
        val adminTru = TimeRecordingUtilities(timeEntryPersistence, CurrentUser(DEFAULT_ADMIN_USER), testLogger)
        val newProject = adminTru.createProject(ProjectName("A"))
        val newEmployee = adminTru.createEmployee(DEFAULT_EMPLOYEE_NAME)
        val newUsername = UserName(newEmployee.name.value)
        val existingTimeForTheDay = createTimeEntryPreDatabase(employee = newEmployee, project = newProject, time = Time(60 * 24))

        val au = AuthenticationUtilities(
            AuthenticationPersistence(pmd, testLogger),
            testLogger
        )
        au.registerWithEmployee(newUsername, DEFAULT_PASSWORD, newEmployee)
        val (_, user) = au.login(newUsername, DEFAULT_PASSWORD)

        val tru = TimeRecordingUtilities(timeEntryPersistence, CurrentUser(user), testLogger)
        tru.createTimeEntry(existingTimeForTheDay)
        return Triple(tru, newProject, newEmployee)
    }

}