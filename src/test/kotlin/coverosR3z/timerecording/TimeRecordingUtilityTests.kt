package coverosR3z.timerecording

import coverosR3z.authentication.exceptions.UnpermittedOperationException
import coverosR3z.authentication.types.CurrentUser
import coverosR3z.misc.*
import coverosR3z.persistence.utility.PureMemoryDatabase.Companion.createEmptyDatabase
import coverosR3z.timerecording.exceptions.ExceededDailyHoursAmountException
import coverosR3z.timerecording.persistence.TimeEntryPersistence
import coverosR3z.timerecording.types.*
import coverosR3z.timerecording.utility.TimeRecordingUtilities
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import java.lang.IllegalStateException

class TimeRecordingUtilityTests {

    private lateinit var tru : TimeRecordingUtilities
    private lateinit var tep : FakeTimeEntryPersistence
    private lateinit var cu : CurrentUser

    @Before
    fun init() {
        tep = FakeTimeEntryPersistence()
        cu = CurrentUser(DEFAULT_ADMIN_USER)
        tru = TimeRecordingUtilities(tep, cu, testLogger)
    }

    /**
     * Happy path - record time successfully
     */
    @Test
    fun `record time for someone`() {
        val fakeTimeEntryPersistence = FakeTimeEntryPersistence(minutesRecorded = Time(60))
        val utils = TimeRecordingUtilities(fakeTimeEntryPersistence, CurrentUser(DEFAULT_ADMIN_USER), testLogger)
        val entry = createTimeEntryPreDatabase()

        val actualResult = utils.createTimeEntry(entry)

        assertEquals("expect to see a success indicator", StatusEnum.SUCCESS, actualResult.status)
    }

    /**
     * Negative case - what happens if we receive a request to enter
     * time for an invalid project?
     */
    @IntegrationTest
    @Category(IntegrationTestCategory::class)
    @Test
    fun `Should fail to record time for non-existent project`() {
        // it's an invalid project because the project doesn't exist
        val pmd = createEmptyDatabase()
        val tep = TimeEntryPersistence(pmd, logger = testLogger)
        val utils = TimeRecordingUtilities(tep, CurrentUser(DEFAULT_ADMIN_USER), testLogger)
        val entry = createTimeEntryPreDatabase(project= Project(ProjectId(1), ProjectName("an invalid project")))
        val expectedResult = RecordTimeResult(StatusEnum.INVALID_PROJECT)

        val actualResult = utils.createTimeEntry(entry)

        assertEquals("Expect to see a message about invalid project", expectedResult, actualResult)
    }

    /**
     * Negative case - what happens if we receive a request to enter
     * time for an invalid employee?
     */
    @IntegrationTest
    @Category(IntegrationTestCategory::class)
    @Test
    fun `Should fail to record time for non-existent employee`() {
        // it's an invalid project because the project doesn't exist
        val pmd = createEmptyDatabase()
        val tep = TimeEntryPersistence(pmd, logger = testLogger)
        val utils = TimeRecordingUtilities(tep, CurrentUser(DEFAULT_ADMIN_USER), testLogger)
        val project = utils.createProject(DEFAULT_PROJECT_NAME)
        val entry = createTimeEntryPreDatabase(project = project)
        val expectedResult = RecordTimeResult(StatusEnum.INVALID_EMPLOYEE)

        val actualResult = utils.createTimeEntry(entry)

        assertEquals("Expect to see a message about invalid employee", expectedResult, actualResult)
    }

    /**
     * Negative case - what happens if we ask the system to record
     * any more time when we've already recorded the maximum for the day?
     * (It should throw an exception)
     */
    @IntegrationTest
    @Category(IntegrationTestCategory::class)
    @Test
    fun `Should throw ExceededDailyHoursException when asked to record more than 24 hours total in a day`() {
        val twentyFourHours = Time(24 * 60)
        val pmd = createEmptyDatabase()
        val tep = TimeEntryPersistence(pmd, logger = testLogger)
        val utils = TimeRecordingUtilities(tep, CurrentUser(DEFAULT_ADMIN_USER), testLogger)
        val project = utils.createProject(DEFAULT_PROJECT_NAME)
        val employee = utils.createEmployee(DEFAULT_EMPLOYEE_NAME)
        val entry1 = createTimeEntryPreDatabase(time= twentyFourHours, project= project, employee = employee)
        utils.createTimeEntry(entry1)
        val entry2 = createTimeEntryPreDatabase(time= Time(1), project= project, employee = employee)

        assertThrows(ExceededDailyHoursAmountException::class.java) { utils.createTimeEntry(entry2) }
    }

    /**
     * Negative case - what happens if we ask the system to record
     * any more time when we've already recorded the maximum for the day?
     * (It should throw an exception)
     */
    @Category(IntegrationTestCategory::class)
    @Test
    fun `Should throw ExceededDailyHoursException when asked to record more than 24 hours total with prior hours entered`() {
        val twentyThreeHours = Time(23 * 60)
        val pmd = createEmptyDatabase()
        val tep = TimeEntryPersistence(pmd, logger = testLogger)
        val utils = TimeRecordingUtilities(tep, CurrentUser(DEFAULT_ADMIN_USER), testLogger)
        val project = utils.createProject(DEFAULT_PROJECT_NAME)
        val employee = utils.createEmployee(DEFAULT_EMPLOYEE_NAME)
        val entry1 = createTimeEntryPreDatabase(time= twentyThreeHours, project= project, employee = employee)
        utils.createTimeEntry(entry1)
        val entry2 = createTimeEntryPreDatabase(time= Time(60 * 2), project= project, employee = employee)

        assertThrows(ExceededDailyHoursAmountException::class.java) { utils.createTimeEntry(entry2) }
    }


    @Test
    fun `just checking that two similar time entries are considered equal`() {
        assertEquals(createTimeEntryPreDatabase(), createTimeEntryPreDatabase())
    }

    /**
     * Now, this is something functional.  On the input, we want various
     * data, on the output we want a nice data object that has the relevant
     * interrelated data
     */
    @Test
    fun `creating a time entry should record its details`() {
        val timeEntry = createTimeEntryPreDatabase(date = A_RANDOM_DAY_IN_JUNE_2020)
        assertEquals(DEFAULT_EMPLOYEE, timeEntry.employee)
        assertEquals(DEFAULT_TIME, timeEntry.time)
        assertEquals(DEFAULT_PROJECT, timeEntry.project)
        assertEquals(Details(), timeEntry.details)
    }

    @Test
    fun `a employee should have a unique integer identifier`() {
        val employee = Employee(EmployeeId(1), EmployeeName("someone"))
        assertEquals(1, employee.id.value)
    }

    @Test
    fun `a employee should have a name`() {
        val name = "this is my name bro"
        val id = 1

        val employee = Employee(EmployeeId(id), EmployeeName(name))

        assertEquals(id, employee.id.value)
        assertEquals(name, employee.name.value)
    }

    @Test
    fun `a time should contain an integer number of minutes`() {
        val time = Time((60 * 3) + 15)
        assertEquals(THREE_HOURS_FIFTEEN, time)
    }

    @Test
    fun `a project should have a name and an id`() {
        val project = Project(ProjectId(1), ProjectName("some project name"))
        assertEquals(ProjectId(1), project.id)
        assertEquals(ProjectName("some project name"), project.name)
    }

    @Test
    fun `details should have a string representation` () {
        val actual = Details("Testing, testing")
        val expected = "Testing, testing"
        assertEquals(expected, actual.value)
    }

    /**
     * Both the database the type disallow a details message longer than some length of characters.
     * Testing that 1 more throws an exception
     */
    @Test fun `Details should throw an exception if one longer than max-length characters`() {
        val ex = assertThrows(IllegalArgumentException::class.java ) { Details("A".repeat(MAX_DETAILS_LENGTH + 1)) }
        assertTrue(ex.message.toString().contains("lord's prayer"))
    }

    /**
     * Both the database the type disallow a details message longer than 500 characters.
     * Testing that 499 doesn't throw an exception
     */
    @Test fun `Details should allow an input of max-length minus 1 characters`() {
        val value = "A".repeat(MAX_DETAILS_LENGTH - 1)
        val details = Details(value)
        assertEquals(value, details.value)
    }

    /**
     * Both the database the type disallow a details message longer than 500 characters.
     * Testing that max length doesn't throw an exception
     */
    @Test fun `Details should allow an input of max-length characters`() {
        val value = "A".repeat(MAX_DETAILS_LENGTH)
        val details = Details(value)
        assertEquals(value, details.value)
    }

    @Test fun `there should be no difference between details with no args and details with empty string`() {
        val actual = Details("")
        val expected = Details()
        assertEquals(expected, actual)
    }

    /**
     * a basic happy path
     */
    @Test fun `can create project`() {
        val fakeTimeEntryPersistence = FakeTimeEntryPersistence(
                persistNewProjectBehavior = { DEFAULT_PROJECT })
        val utils = TimeRecordingUtilities(fakeTimeEntryPersistence, CurrentUser(DEFAULT_ADMIN_USER), testLogger)
        val expected = utils.createProject(DEFAULT_PROJECT_NAME)
        assertEquals(expected, DEFAULT_PROJECT)
    }

    /**
     * Trying to create an already-existing project should throw exception
     */
    @Test fun testCannotCreateMultipleProjectsWithSameName() {
        val fakeTimeEntryPersistence = FakeTimeEntryPersistence(
                getProjectByNameBehavior = { DEFAULT_PROJECT })
        val utils = TimeRecordingUtilities(fakeTimeEntryPersistence, CurrentUser(DEFAULT_ADMIN_USER), testLogger)
        val ex  = assertThrows(java.lang.IllegalArgumentException::class.java) {utils.createProject(DEFAULT_PROJECT_NAME)}
        assertEquals("Cannot create a new project if one already exists by that same name", ex.message)
    }

    /**
     * List all of two projects
     */
    @Category(IntegrationTestCategory::class)
    @Test fun testCanListAllProjects() {
        val utils = makeTruWithAdminUser()
        val expectedList = mutableListOf<Project>()
        expectedList.add(utils.createProject(DEFAULT_PROJECT_NAME))
        expectedList.add(utils.createProject(ProjectName("second")))
        val allProjects : List<Project> = utils.listAllProjects()
        assertEquals(expectedList, allProjects)
    }

    /**
     * List all and no projects in database - [emptyList]
     */
    @Category(IntegrationTestCategory::class)
    @Test fun testCanListAllProjects_NoProjects() {
        val utils = makeTruWithAdminUser()
        val allProjects : List<Project> = utils.listAllProjects()
        assertEquals(emptyList<Project>(), allProjects)
    }

    /**
     * happy path
     */
    @Category(IntegrationTestCategory::class)
    @Test fun testCanGetProjectById() {
        val tru = makeTruWithAdminUser()
        val createdProject = tru.createProject(DEFAULT_PROJECT_NAME)
        val foundProject = tru.findProjectById(createdProject.id)
        assertEquals(createdProject, foundProject)
    }

    /**
     * what if the project doesn't exist? [NO_PROJECT]
     */
    @Category(IntegrationTestCategory::class)
    @Test fun testCanGetProjectById_NotFound() {
        val tru = makeTruWithAdminUser()
        val foundProject = tru.findProjectById(ProjectId(1))
        assertEquals(NO_PROJECT, foundProject)
    }

    /**
     * Given we have a time entry in the database, let's
     * edit its values
     */
    @Category(IntegrationTestCategory::class)
    @Test
    fun testCanEditTimeEntry() {
        // arrange
        testLogger.turnOnAllLogging()
        val tru = TimeRecordingUtilities(TimeEntryPersistence(createEmptyDatabase(), logger = testLogger), cu, testLogger)
        tru.createProject(DEFAULT_PROJECT_NAME)
        tru.createEmployee(DEFAULT_EMPLOYEE_NAME)
        val (_, newTimeEntry) = tru.createTimeEntry(createTimeEntryPreDatabase(time = Time(1)))
        val expected = RecordTimeResult(StatusEnum.SUCCESS,
            TimeEntry(TimeEntryId(1), DEFAULT_EMPLOYEE, DEFAULT_PROJECT, Time(2), A_RANDOM_DAY_IN_JUNE_2020))

        // act
        val actual: RecordTimeResult = tru.changeEntry(newTimeEntry!!.copy(time = Time(2)))

        // assert
        assertEquals(expected, actual)
        testLogger.resetLogSettingsToDefault()
    }

    /**
     * Nothing much different should take place if we're overwriting
     * with the exact same values, just want to make that explicit in a test
     */
    @Category(IntegrationTestCategory::class)
    @Test
    fun testCanEditTimeEntry_Unchanged() {
        // arrange
        val tru = makeTruWithAdminUser()
        tru.createProject(DEFAULT_PROJECT_NAME)
        tru.createEmployee(DEFAULT_EMPLOYEE_NAME)
        val result = tru.createTimeEntry(createTimeEntryPreDatabase(time = Time(1)))

        // act
        val actual: RecordTimeResult = tru.changeEntry(result.newTimeEntry!!)

        // assert
        assertEquals(result, actual)
    }

    /**
     * Someone who isn't this employee cannot change the time entry
     */
    @Category(IntegrationTestCategory::class)
    @Test
    fun testCanEditTimeEntry_DisallowDifferentEmployeeToChange() {
        // arrange
        val tru = makeTruWithAdminUser()
        tru.createProject(DEFAULT_PROJECT_NAME)
        tru.createEmployee(DEFAULT_EMPLOYEE_NAME)
        val result: RecordTimeResult = tru.createTimeEntry(createTimeEntryPreDatabase(time = Time(1)))
        val truOtherUser = TimeRecordingUtilities(
            TimeEntryPersistence(createEmptyDatabase(), logger = testLogger),
            CurrentUser(DEFAULT_USER_2),
            testLogger
        )
        val expected = RecordTimeResult(status= StatusEnum.USER_EMPLOYEE_MISMATCH, newTimeEntry=null)

        // act
        val actual: RecordTimeResult = truOtherUser.changeEntry(result.newTimeEntry!!)

        // assert
        assertEquals(expected, actual)
    }

    @Category(IntegrationTestCategory::class)
    @Test
    fun testCanEditTimeEntry_InvalidProject() {
        // arrange
        val tru = makeTruWithAdminUser()
        tru.createEmployee(DEFAULT_EMPLOYEE_NAME)
        tru.createProject(DEFAULT_PROJECT_NAME)
        val (_, newTimeEntry) = tru.createTimeEntry(createTimeEntryPreDatabase(time = Time(1)))
        val expected = RecordTimeResult(status= StatusEnum.INVALID_PROJECT, newTimeEntry=null)

        val result = tru.changeEntry(newTimeEntry!!.copy(project = Project(ProjectId(5), ProjectName("fake"))))

        assertEquals(expected, result)
    }

    /**
     * If we submit time on a period, and try to edit a time entry
     * that in in that period, it won't be allowed - it will be locked
     */
    @Test
    fun testSubmitTime_expectLockedTimeEntries_editingTimeEntry() {
        val ftep = FakeTimeEntryPersistence()
        val tru = TimeRecordingUtilities(ftep, CurrentUser(DEFAULT_USER), testLogger)
        ftep.setLockedEmployeeDateBehavior = {true}
        val expected = RecordTimeResult(StatusEnum.LOCKED_ALREADY_SUBMITTED)

        val result = tru.changeEntry(
            TimeEntry(
                TimeEntryId(1),
                DEFAULT_EMPLOYEE,
                DEFAULT_PROJECT,
                DEFAULT_TIME,
                DEFAULT_PERIOD_END_DATE))

        assertEquals("When a time period has been submitted, it's locked, cannot be changed",
            expected, result)
    }

    /**
     * If we submit time on a period, and try to create a time entry
     * that in in that period, it won't be allowed - it will be locked
     */
    @Test
    fun testSubmitTime_expectLockedTimeEntries_creatingTimeEntry() {
        val ftep = FakeTimeEntryPersistence()
        val tru = TimeRecordingUtilities(ftep, CurrentUser(DEFAULT_USER), testLogger)
        ftep.setLockedEmployeeDateBehavior = {true}
        val expected = RecordTimeResult(StatusEnum.LOCKED_ALREADY_SUBMITTED)

        val result = tru.createTimeEntry(createTimeEntryPreDatabase(date = DEFAULT_PERIOD_START_DATE))

        assertEquals("When a time period has been submitted, it's locked, cannot be changed",
            expected, result)
    }

    @IntegrationTest
    @Category(IntegrationTestCategory::class)
    @Test
    fun testSubmitTime() {
        val pmd = createEmptyDatabase()
        val tep = TimeEntryPersistence(pmd, logger = testLogger)
        val adminTru = TimeRecordingUtilities(tep, cu, testLogger)
        val project = adminTru.createProject(DEFAULT_PROJECT_NAME)
        val employee = adminTru.createEmployee(DEFAULT_EMPLOYEE_NAME)

        val tru = TimeRecordingUtilities(tep, CurrentUser(DEFAULT_REGULAR_USER), testLogger)
        val expected = RecordTimeResult(StatusEnum.LOCKED_ALREADY_SUBMITTED)

        // this locks the time entries for the period
        tru.submitTimePeriod(DEFAULT_TIME_PERIOD)

        val result = tru.createTimeEntry(TimeEntryPreDatabase(employee, project, Time(60), DEFAULT_PERIOD_START_DATE))

        assertEquals("When a time period has been submitted, it's locked, cannot be changed",
            expected, result)
    }

    @Test
    fun testGetSubmittedPeriod() {
        val ftep = FakeTimeEntryPersistence()
        val tru = TimeRecordingUtilities(ftep, CurrentUser(DEFAULT_USER), testLogger)

        val submittedTimePeriod = tru.getSubmittedTimePeriod(DEFAULT_TIME_PERIOD)

        assertEquals(DEFAULT_SUBMITTED_PERIOD, submittedTimePeriod)
    }

    /**
     * We shall have an easy way to obtain the list of
     * [TimeEntry] for a particular [TimePeriod]
     */
    @Test
    fun testGetTimeEntriesForPeriod() {
        val ftep = FakeTimeEntryPersistence()
        val tru = TimeRecordingUtilities(ftep, CurrentUser(DEFAULT_USER), testLogger)
        ftep.getTimeEntriesForTimePeriodBehavior = { setOf(DEFAULT_TIME_ENTRY) }

        val allEntriesForPeriod : Set<TimeEntry> = tru.getTimeEntriesForTimePeriod(DEFAULT_EMPLOYEE, DEFAULT_TIME_PERIOD)

        assertTrue(allEntriesForPeriod.any())
    }

    @Test
    fun testHandleRealCreateNewEmployee() {
        val expectedEmployee = Employee(EmployeeId(1), EmployeeName("ryan \"Kenney\" mcgee"))
        tep.persistNewEmployeeBehavior = {expectedEmployee }
        val newEmployee = tru.createEmployee(expectedEmployee.name)
        assertEquals(expectedEmployee, newEmployee)
    }

    @Test
    fun testCantHandleThePowerOfCreation() {
        val tru = TimeRecordingUtilities(FakeTimeEntryPersistence(), CurrentUser(DEFAULT_REGULAR_USER), testLogger)
        assertThrows(UnpermittedOperationException::class.java) {tru.createEmployee(EmployeeName("ryan \"Kenney\" mcgee"))}
    }

    @Test
    fun testCantHandleThePowerOfProjectCreation() {
        val tru = TimeRecordingUtilities(FakeTimeEntryPersistence(), CurrentUser(DEFAULT_REGULAR_USER), testLogger)
        assertThrows(UnpermittedOperationException::class.java) {tru.createProject(ProjectName("Chungus amongus"))}
    }

    /**
     * We succeed at deleting it, it's gone, return true
     */
    @Test
    fun testCanDeleteTimeEntry() {
        tep.deleteTimeEntryBehavior = { true }
        val result = tru.deleteTimeEntry(DEFAULT_TIME_ENTRY)
        assertTrue(result)
    }

    /**
     * If the time entry isn't there, we get an exception.
     * There isn't a reasonable situation where a user should
     * be able to get into the situation they are deleting a non-existent
     * time entry, that's an exceptional situation
     */
    @Test
    fun testCannotDeleteMissingTimeEntry() {
        tep.deleteTimeEntryBehavior = { false }
        assertThrows(IllegalStateException::class.java) {tru.deleteTimeEntry(DEFAULT_TIME_ENTRY)}
    }

    /**
     * Happy path - find a time entry
     */
    @Test
    fun testFindTimeEntryById() {
        tep.findTimeEntryByIdBehavior = { DEFAULT_TIME_ENTRY }
        val result = tru.findTimeEntryById(DEFAULT_TIME_ENTRY.id)
        assertEquals(DEFAULT_TIME_ENTRY, result)
    }

    /**
     * Find nothing
     */
    @Test
    fun testFindTimeEntryById_NothingFound() {
        tep.findTimeEntryByIdBehavior = { NO_TIMEENTRY }
        val result = tru.findTimeEntryById(DEFAULT_TIME_ENTRY.id)
        assertEquals(NO_TIMEENTRY, result)
    }

    private fun makeTruWithAdminUser(): TimeRecordingUtilities {
        return TimeRecordingUtilities(
            TimeEntryPersistence(createEmptyDatabase(), logger = testLogger),
            CurrentUser(DEFAULT_ADMIN_USER),
            testLogger
        )
    }
}
