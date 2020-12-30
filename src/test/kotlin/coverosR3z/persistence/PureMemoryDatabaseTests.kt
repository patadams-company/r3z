package coverosR3z.persistence

import coverosR3z.*
import coverosR3z.authentication.AuthenticationPersistence
import coverosR3z.domainobjects.*
import coverosR3z.exceptions.DatabaseCorruptedException
import coverosR3z.logging.logAudit
import coverosR3z.logging.loggerPrinter
import coverosR3z.misc.getTime
import coverosR3z.persistence.ConcurrentSet.Companion.concurrentSetOf
import coverosR3z.persistence.PureMemoryDatabase.Companion.databaseFileSuffix
import org.junit.*
import org.junit.Assert.*
import org.junit.runners.MethodSorters
import java.io.File
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PureMemoryDatabaseTests {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() {
            // wipe out the database
            File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        }

    }

    private lateinit var pmd: PureMemoryDatabase

    @Before
    fun init() {
        pmd = PureMemoryDatabase()
    }

    @After
    fun cleanup() {
        pmd.stop()
    }

    @Test
    fun `should be able to add a new project`() {
        pmd.addNewProject(DEFAULT_PROJECT_NAME)

        val project = pmd.getProjectById(DEFAULT_PROJECT.id)

        assertEquals(ProjectId(1), project.id)
    }

    @Test
    fun `should be able to add a new employee`() {
        pmd.addNewEmployee(DEFAULT_EMPLOYEE_NAME)

        val employee = pmd.getEmployeeById(DEFAULT_EMPLOYEE.id)

        assertEquals(1, employee.id.value)
    }

    @Test
    fun `should be able to add a new time entry`() {
        pmd.addTimeEntry(TimeEntryPreDatabase(DEFAULT_EMPLOYEE, DEFAULT_PROJECT, DEFAULT_TIME, A_RANDOM_DAY_IN_JUNE_2020))

        val timeEntries = pmd.getAllTimeEntriesForEmployeeOnDate(DEFAULT_EMPLOYEE, A_RANDOM_DAY_IN_JUNE_2020).first()

        assertEquals(1, timeEntries.id)
        assertEquals(DEFAULT_EMPLOYEE, timeEntries.employee)
        assertEquals(DEFAULT_PROJECT, timeEntries.project)
        assertEquals(DEFAULT_TIME, timeEntries.time)
        assertEquals(A_RANDOM_DAY_IN_JUNE_2020, timeEntries.date)
    }

    @Test
    fun `PERFORMANCE a firm should get responses from the database quite quickly`() {
        val numberOfEmployees = 30
        val numberOfProjects = 30
        val numberOfDays = 31

        val allEmployees = recordManyTimeEntries(numberOfEmployees, numberOfProjects, numberOfDays)

        val (totalTime) = getTime {
            readTimeEntriesForOneEmployee(allEmployees)
            accumulateMinutesPerEachEmployee(allEmployees)
        }

        logAudit("It took a total of $totalTime milliseconds for this code")
        assertTrue(totalTime < 100)
    }

    @Test
    fun `should be able to get the minutes on a certain date`() {
        pmd.addNewEmployee(DEFAULT_EMPLOYEE.name)
        pmd.addTimeEntry(TimeEntryPreDatabase(DEFAULT_EMPLOYEE, DEFAULT_PROJECT, DEFAULT_TIME, A_RANDOM_DAY_IN_JUNE_2020))

        val minutes = pmd.getMinutesRecordedOnDate(DEFAULT_EMPLOYEE, A_RANDOM_DAY_IN_JUNE_2020)

        assertEquals(DEFAULT_TIME, minutes)
    }

    /**
     * If I ask the database for all the time entries for a particular employee on
     * a date and there aren't any, I should get back an empty list, not a null.
     */
    @Test
    fun testShouldReturnEmptyListIfNoEntries() {
        val result = pmd.getAllTimeEntriesForEmployeeOnDate(DEFAULT_EMPLOYEE, A_RANDOM_DAY_IN_JUNE_2020)
        assertEquals(emptySet<TimeEntry>() , result)
    }

    /**
     * I wish to make an exact copy of the PMD in completely new memory locations
     */
    @Test
    fun testShouldBePossibleToCopy_different() {
        val originalPmd = pmd.copy()
        pmd.addNewEmployee(DEFAULT_EMPLOYEE_NAME)
        assertNotEquals("after adding a new employee, the databases should differ", originalPmd, pmd)
    }

    /**
     * I wish to make an exact copy of the PMD in completely new memory locations
     */
    @Test
    fun testShouldBePossibleToCopy_similar() {
        val originalPmd = pmd.copy()
        assertEquals(originalPmd, pmd)
    }

    /**
     * Test writing the whole database and reading the whole database
     */
    @Test
    fun testShouldWriteAndReadToDisk_PERFORMANCE() {
        val numberOfEmployees = 3
        val numberOfProjects = 5
        val numberOfDays = 2
        val maxMillisecondsAllowed = 200
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        pmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        File(DEFAULT_DB_DIRECTORY).mkdirs()

        val (totalTimeWriting, _) = getTime {
            recordManyTimeEntries(numberOfEmployees, numberOfProjects, numberOfDays)
            pmd.stop()
        }

        val (totalTimeReading, _) = getTime {
            val (timeToReadText, deserializedPmd) = getTime {
                PureMemoryDatabase.deserializeFromDisk(
                    DEFAULT_DB_DIRECTORY
                )
            }
            logAudit("it took $timeToReadText milliseconds to deserialize from disk")

            val (timeToAssert) = getTime { assertEquals(pmd, deserializedPmd) }
            logAudit("it took $timeToAssert milliseconds to assert the databases were equal")
        }

        val totalTime = totalTimeReading + totalTimeWriting
        logAudit("Total time taken for serialization / deserialzation was $totalTime milliseconds")

        assertTrue("totaltimeWriting was supposed to take $maxMillisecondsAllowed.  took $totalTimeWriting", totalTimeWriting < maxMillisecondsAllowed)
        assertTrue("totaltimeReading was supposed to take $maxMillisecondsAllowed.  took $totalTimeReading", totalTimeReading < maxMillisecondsAllowed)
    }

    /**
     * How long does it actually take to serialize and deserialize?
     * Not very long at all.
     */
    @Test
    fun testSerializingTimeEntries_PERFORMANCE() {
        val numTimeEntries = 1000
        val timeEntries: MutableSet<TimeEntry> = prepareSomeRandomTimeEntries(numTimeEntries, DEFAULT_PROJECT, DEFAULT_EMPLOYEE)

        val (timeToSerialize, serializedTimeEntries) = getTime{pmd.serializeTimeEntries(timeEntries)}
        val (timeToDeserialize, _) = getTime{
            PureMemoryDatabase.deserializeTimeEntries(
                serializedTimeEntries,
                DEFAULT_EMPLOYEE,
                concurrentSetOf(DEFAULT_PROJECT))
        }

        logAudit("Time to serialize $numTimeEntries time entries was $timeToSerialize milliseconds")
        logAudit("Time to deserialize $numTimeEntries time entries was $timeToDeserialize milliseconds")

        assertTrue(timeToSerialize < 150)
        assertTrue(timeToDeserialize < 150)
    }

    /**
     * This is to test that it is possible to corrupt the data when
     * multiple threads are changing it
     *
     * The idea behind this test is that if we aren't handling
     * the threading cleanly, we won't get one hundred employees
     * added, it will be some smaller number.
     *
     * When I added the lock, this caused us to have a result
     * of the proper number of new employees
     *
     * To see this fail, just remove the locking mechanism from the
     * method at [PureMemoryDatabase.addNewEmployee], but you
     * might need to run it a time or two to see it fail.
     */
    @Test
    fun testCorruptingEmployeeDataWithMultiThreading() {
        val listOfThreads = mutableListOf<Thread>()
        val numberNewEmployeesAdded = 20
        repeat(numberNewEmployeesAdded) { // each thread calls the add a single time
            listOfThreads.add(thread {
                pmd.addNewEmployee(DEFAULT_EMPLOYEE_NAME)
            })
        }
        // wait for all those threads
        listOfThreads.forEach{it.join()}
        assertEquals(numberNewEmployeesAdded, pmd.getAllEmployees().size)
    }

    /**
     * See [testCorruptingEmployeeDataWithMultiThreading]
     */
    @Test
    fun testCorruptingUserDataWithMultiThreading() {
        val listOfThreads = mutableListOf<Thread>()
        val numberNewUsersAdded = 20
        repeat(numberNewUsersAdded) { // each thread calls the add a single time
            listOfThreads.add(thread {
                pmd.addNewUser(DEFAULT_USER.name, DEFAULT_HASH, DEFAULT_SALT, DEFAULT_EMPLOYEE.id)
            })
        }
        // wait for all those threads
        listOfThreads.forEach{it.join()}
        assertEquals(numberNewUsersAdded, pmd.getAllUsers().size)
    }

    /**
     * See [testCorruptingEmployeeDataWithMultiThreading]
     */
    @Test
    fun testCorruptingProjectDataWithMultiThreading() {
        val listOfThreads = mutableListOf<Thread>()
        val numberNewProjectsAdded = 20
        repeat(numberNewProjectsAdded) { // each thread calls the add a single time
            listOfThreads.add(thread {
                pmd.addNewProject(DEFAULT_PROJECT_NAME)
            })
        }
        // wait for all those threads
        listOfThreads.forEach{it.join()}
        assertEquals(numberNewProjectsAdded, pmd.getAllProjects().size)
    }

    /**
     * Time entry recording is fairly involved, we have to lock
     * a lot.  See [testCorruptingEmployeeDataWithMultiThreading]
     */
    @Test
    fun testCorruptingTimeEntryDataWithMultiThreading() {
        val listOfThreads = mutableListOf<Thread>()
        val numberTimeEntriesAdded = 20
        repeat(numberTimeEntriesAdded) { // each thread calls the add a single time
            listOfThreads.add(thread {
                pmd.addTimeEntry(createTimeEntryPreDatabase())
            })
        }
        // wait for all those threads
        listOfThreads.forEach{it.join()}
        assertEquals(numberTimeEntriesAdded, pmd.getAllTimeEntriesForEmployee(DEFAULT_EMPLOYEE).size)
    }

    /**
     * These tests capture what happens when a file doesn't exist in the directory
     * because no entries have been, not because it's become corrupted.
     *
     * Here there are no users, so there cannot be any sessions either
     */
    @Test
    fun testPersistence_Read_MissingUsers() {
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        pmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        val newEmployee = pmd.addNewEmployee(DEFAULT_EMPLOYEE_NAME)
        val newProject = pmd.addNewProject(DEFAULT_PROJECT_NAME)
        pmd.addTimeEntry(createTimeEntryPreDatabase(employee = newEmployee, project = newProject))
        pmd.stop()

        val readPmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)

        assertEquals(pmd, readPmd)
        assertEquals(pmd.hashCode(), readPmd.hashCode())
    }

    /**
     * These tests capture what happens when a file doesn't exist in the directory
     * because no entries have been, not because it's become corrupted.
     *
     * Here there are no sessions, which might mean no one was logged in
     */
    @Test
    fun testPersistence_Read_MissingSessions() {
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        pmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        val newEmployee = pmd.addNewEmployee(DEFAULT_EMPLOYEE_NAME)
        pmd.addNewUser(DEFAULT_USER.name, DEFAULT_HASH, DEFAULT_SALT, newEmployee.id)
        val newProject = pmd.addNewProject(DEFAULT_PROJECT_NAME)
        pmd.addTimeEntry(createTimeEntryPreDatabase(employee = newEmployee, project = newProject))
        pmd.stop()

        val readPmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)

        assertEquals(pmd, readPmd)
        assertEquals(pmd.hashCode(), readPmd.hashCode())
    }

    /**
     * If we're starting and the directory for the database exists but no
     * files or subdirectories in it.  everything should 
     * still work as before
     */
    @Test
    fun testPersistence_Read_MissingAllFilesButDirectoryExists() {
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        File(DEFAULT_DB_DIRECTORY).mkdirs()
        PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        assertTrue("a new database will store its version", File(DEFAULT_DB_DIRECTORY + "version.txt").exists())
    }

    /**
     * If we are starting and try to read and there's no 
     * database directory.  Just make one.
     */
    @Test
    fun testPersistence_Read_MissingDbDirectory() {
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        assertTrue("a new database will store its version", File(DEFAULT_DB_DIRECTORY + "version.txt").exists())
    }



    /**
     * Assuming we have valid data stored on disk, do we read
     * it back properly?
     */
    @Test
    fun testPersistence_Read_HappyPath() {
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        pmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        val ap = AuthenticationPersistence(pmd)
        val newEmployee = pmd.addNewEmployee(DEFAULT_EMPLOYEE_NAME)
        val newUser = pmd.addNewUser(DEFAULT_USER.name, DEFAULT_HASH, DEFAULT_SALT, newEmployee.id)
        val newProject = pmd.addNewProject(DEFAULT_PROJECT_NAME)
        ap.addNewSession(DEFAULT_SESSION_TOKEN, newUser, DEFAULT_DATETIME)
        pmd.addTimeEntry(createTimeEntryPreDatabase(employee = newEmployee, project = newProject))
        pmd.stop()

        val readPmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)

        assertEquals(pmd, readPmd)
        assertEquals(pmd.hashCode(), readPmd.hashCode())
    }

    /**
     * These tests capture what happens when a file doesn't exist in the directory
     * because no entries have been, not because it's become corrupted.
     */
    @Test
    fun testPersistence_Read_MissingTimeEntries() {
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        pmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        val ap = AuthenticationPersistence(pmd)
        val newEmployee = pmd.addNewEmployee(DEFAULT_EMPLOYEE_NAME)
        val newUser = pmd.addNewUser(DEFAULT_USER.name, DEFAULT_HASH, DEFAULT_SALT, newEmployee.id)
        pmd.addNewProject(DEFAULT_PROJECT_NAME)
        ap.addNewSession(DEFAULT_SESSION_TOKEN, newUser, DEFAULT_DATETIME)
        pmd.stop()

        val readPmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)

        assertEquals(pmd, readPmd)
        assertEquals(pmd.hashCode(), readPmd.hashCode())
    }

    /**
     * These tests capture what happens when a file doesn't exist in the directory
     * because no entries have been, not because it's become corrupted.
     *
     * here there are no employees, so there cannot be any time entries
     */
    @Test
    fun testPersistence_Read_MissingEmployees() {
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        pmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        val ap = AuthenticationPersistence(pmd)
        val newUser = pmd.addNewUser(DEFAULT_USER.name, DEFAULT_HASH, DEFAULT_SALT, null)
        pmd.addNewProject(DEFAULT_PROJECT_NAME)
        ap.addNewSession(DEFAULT_SESSION_TOKEN, newUser, DEFAULT_DATETIME)
        pmd.stop()

        val readPmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)

        assertEquals(pmd, readPmd)
        assertEquals(pmd.hashCode(), readPmd.hashCode())
    }

    /**
     * These tests capture what happens when a file doesn't exist in the directory
     * because no entries have been, not because it's become corrupted.
     *
     * Here, there are no projects, which also means there cannot be time entries
     */
    @Test
    fun testPersistence_Read_MissingProjects() {
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        pmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        val ap = AuthenticationPersistence(pmd)
        val newEmployee = pmd.addNewEmployee(DEFAULT_EMPLOYEE_NAME)
        val newUser = pmd.addNewUser(DEFAULT_USER.name, DEFAULT_HASH, DEFAULT_SALT, newEmployee.id)
        ap.addNewSession(DEFAULT_SESSION_TOKEN, newUser, DEFAULT_DATETIME)
        pmd.stop()

        val readPmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)

        assertEquals(pmd, readPmd)
        assertEquals(pmd.hashCode(), readPmd.hashCode())
    }

    /**
     * What if some of the data in the time-entries file is
     * corrupted? I think the most appropriate
     * response is to halt and yell for help, because at that point all
     * bets are off, we won't have enough information to properly recover.
     */
    @Test
    fun testPersistence_Read_CorruptedData_TimeEntries_BadData() {
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        pmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        val ap = AuthenticationPersistence(pmd)
        val newEmployee = pmd.addNewEmployee(DEFAULT_EMPLOYEE_NAME)
        val newUser = pmd.addNewUser(DEFAULT_USER.name, DEFAULT_HASH, DEFAULT_SALT, newEmployee.id)
        val newProject = pmd.addNewProject(DEFAULT_PROJECT_NAME)
        ap.addNewSession(DEFAULT_SESSION_TOKEN, newUser, DEFAULT_DATETIME)
        pmd.addTimeEntry(createTimeEntryPreDatabase(employee = newEmployee, project = newProject))
        pmd.stop()

        // corrupt the time-entries data file
        File("$DEFAULT_DB_DIRECTORY/timeentries/2/2020_6$databaseFileSuffix").writeText("BAD DATA HERE")

        val ex = assertThrows(DatabaseCorruptedException::class.java) {PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)}
        assertEquals("Could not deserialize time entry file 2020_6$databaseFileSuffix.  Unable to deserialize this text as time entry data: BAD DATA HERE", ex.message)
    }

    /**
     * See [testPersistence_Read_CorruptedData_TimeEntries_BadData]
     */
    @Test
    fun testPersistence_Read_CorruptedData_Employees_BadData() {
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        pmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        val ap = AuthenticationPersistence(pmd)
        val newEmployee = pmd.addNewEmployee(DEFAULT_EMPLOYEE_NAME)
        val newUser = pmd.addNewUser(DEFAULT_USER.name, DEFAULT_HASH, DEFAULT_SALT, newEmployee.id)
        val newProject = pmd.addNewProject(DEFAULT_PROJECT_NAME)
        ap.addNewSession(DEFAULT_SESSION_TOKEN, newUser, DEFAULT_DATETIME)
        pmd.addTimeEntry(createTimeEntryPreDatabase(employee = newEmployee, project = newProject))

        // corrupt the employees data file
        File("$DEFAULT_DB_DIRECTORY/employees$databaseFileSuffix").writeText("BAD DATA HERE")
        
        val ex = assertThrows(DatabaseCorruptedException::class.java) {PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)}
        assertEquals("Unable to deserialize this text as employee data: BAD DATA HERE", ex.message)
    }

    /**
     * What if we see corruption in our database by dint of missing files
     * that definitely should not be missing?  I think the most appropriate
     * response is to halt and yell for help, because at that point all
     * bets are off, we won't have enough information to properly recover.
     */
    @Test
    fun testPersistence_Read_CorruptedData_Employees_MissingFile() {
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        pmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        val ap = AuthenticationPersistence(pmd)
        val newEmployee = pmd.addNewEmployee(DEFAULT_EMPLOYEE_NAME)
        val newUser = pmd.addNewUser(DEFAULT_USER.name, DEFAULT_HASH, DEFAULT_SALT, newEmployee.id)
        val newProject = pmd.addNewProject(DEFAULT_PROJECT_NAME)
        ap.addNewSession(DEFAULT_SESSION_TOKEN, newUser, DEFAULT_DATETIME)
        pmd.addTimeEntry(createTimeEntryPreDatabase(employee = newEmployee, project = newProject))
        pmd.stop()

        // delete a necessary file
        File("$DEFAULT_DB_DIRECTORY/employees$databaseFileSuffix").delete()

        val ex = assertThrows(DatabaseCorruptedException::class.java) {PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)}
        assertEquals("Unable to find an employee with the id of 2 based on entry in timeentries/", ex.message)
    }

    /**
     * In the time entries directory we store files by employee id.  If we look inside
     * a directory and find no files, that indicates a data corruption.
     */
    @Test
    fun testPersistence_Read_CorruptedData_TimeEntries_MissingFile() {
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        pmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        val ap = AuthenticationPersistence(pmd)
        val newEmployee = pmd.addNewEmployee(DEFAULT_EMPLOYEE_NAME)
        val newUser = pmd.addNewUser(DEFAULT_USER.name, DEFAULT_HASH, DEFAULT_SALT, newEmployee.id)
        val newProject = pmd.addNewProject(DEFAULT_PROJECT_NAME)
        ap.addNewSession(DEFAULT_SESSION_TOKEN, newUser, DEFAULT_DATETIME)
        pmd.addTimeEntry(createTimeEntryPreDatabase(employee = newEmployee, project = newProject))
        pmd.stop()

        // delete a necessary time entry file inside this employees' directory
        val file = File("$DEFAULT_DB_DIRECTORY/timeentries/${newEmployee.id.value}/")
                file.listFiles()?.forEach { it.delete() }

        val ex = assertThrows(DatabaseCorruptedException::class.java) {PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)}

        assertEquals("no time entry files found in employees directory at ${file.path}", ex.message)
    }
    
    /**
     * See [testPersistence_Read_CorruptedData_TimeEntries_BadData]
     */
    @Test
    fun testPersistence_Read_CorruptedData_Users_BadData() {
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        pmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        val ap = AuthenticationPersistence(pmd)
        val newEmployee = pmd.addNewEmployee(DEFAULT_EMPLOYEE_NAME)
        val newUser = pmd.addNewUser(DEFAULT_USER.name, DEFAULT_HASH, DEFAULT_SALT, newEmployee.id)
        val newProject = pmd.addNewProject(DEFAULT_PROJECT_NAME)
        ap.addNewSession(DEFAULT_SESSION_TOKEN, newUser, DEFAULT_DATETIME)
        pmd.addTimeEntry(createTimeEntryPreDatabase(employee = newEmployee, project = newProject))
        pmd.stop()

        // corrupt the users data file
        File("$DEFAULT_DB_DIRECTORY/users$databaseFileSuffix").writeText("BAD DATA HERE")
        
        assertThrows(DatabaseCorruptedException::class.java) {PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)}
    }
    
    /**
     * See [testPersistence_Read_CorruptedData_Employees_MissingFile]
     */
    @Test
    fun testPersistence_Read_CorruptedData_Users_MissingFile() {
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        pmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        val ap = AuthenticationPersistence(pmd)
        val newEmployee = pmd.addNewEmployee(DEFAULT_EMPLOYEE_NAME)
        val newUser = pmd.addNewUser(DEFAULT_USER.name, DEFAULT_HASH, DEFAULT_SALT, newEmployee.id)
        val newProject = pmd.addNewProject(DEFAULT_PROJECT_NAME)
        ap.addNewSession(DEFAULT_SESSION_TOKEN, newUser, DEFAULT_DATETIME)
        pmd.addTimeEntry(createTimeEntryPreDatabase(employee = newEmployee, project = newProject))
        pmd.stop()

        // delete a necessary file
        File("$DEFAULT_DB_DIRECTORY/users$databaseFileSuffix").delete()

        val ex = assertThrows(DatabaseCorruptedException::class.java) {PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)}
        assertEquals("Unable to find a user with the id of 1.  User set size: 0", ex.message)
    }
    
    /**
     * See [testPersistence_Read_CorruptedData_TimeEntries_BadData]
     */
    @Test
    fun testPersistence_Read_CorruptedData_Projects_BadData() {
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        pmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        val ap = AuthenticationPersistence(pmd)
        val newEmployee = pmd.addNewEmployee(DEFAULT_EMPLOYEE_NAME)
        val newUser = pmd.addNewUser(DEFAULT_USER.name, DEFAULT_HASH, DEFAULT_SALT, newEmployee.id)
        val newProject = pmd.addNewProject(DEFAULT_PROJECT_NAME)
        ap.addNewSession(DEFAULT_SESSION_TOKEN, newUser, DEFAULT_DATETIME)
        pmd.addTimeEntry(createTimeEntryPreDatabase(employee = newEmployee, project = newProject))
        pmd.stop()

        // corrupt the projects data file
        File("$DEFAULT_DB_DIRECTORY/projects$databaseFileSuffix").writeText("BAD DATA HERE")
        
        val ex = assertThrows(DatabaseCorruptedException::class.java) {PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)}
        assertEquals("Unable to deserialize this text as project data: BAD DATA HERE", ex.message)
    }
    
    /**
     * See [testPersistence_Read_CorruptedData_Employees_MissingFile]
     */
    @Test
    fun testPersistence_Read_CorruptedData_Projects_MissingFile() {
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        pmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        val ap = AuthenticationPersistence(pmd)
        val newEmployee = pmd.addNewEmployee(DEFAULT_EMPLOYEE_NAME)
        val newUser = pmd.addNewUser(DEFAULT_USER.name, DEFAULT_HASH, DEFAULT_SALT, newEmployee.id)
        val newProject = pmd.addNewProject(DEFAULT_PROJECT_NAME)
        ap.addNewSession(DEFAULT_SESSION_TOKEN, newUser, DEFAULT_DATETIME)
        pmd.addTimeEntry(createTimeEntryPreDatabase(employee = newEmployee, project = newProject))
        pmd.stop()

        // delete a necessary file
        File("$DEFAULT_DB_DIRECTORY/projects$databaseFileSuffix").delete()

        val ex = assertThrows(DatabaseCorruptedException::class.java) {PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)}
        assertEquals("Could not deserialize time entry file 2020_6$databaseFileSuffix.  Unable to find a project with the id of 1.  Project set size: 0", ex.message)
    }
    
    /**
     * See [testPersistence_Read_CorruptedData_TimeEntries_BadData]
     */
    @Test
    fun testPersistence_Read_CorruptedData_Sessions_BadData() {
        File(DEFAULT_DB_DIRECTORY).deleteRecursively()
        pmd = PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)
        val ap = AuthenticationPersistence(pmd)
        val newEmployee = pmd.addNewEmployee(DEFAULT_EMPLOYEE_NAME)
        val newUser = pmd.addNewUser(DEFAULT_USER.name, DEFAULT_HASH, DEFAULT_SALT, newEmployee.id)
        val newProject = pmd.addNewProject(DEFAULT_PROJECT_NAME)
        ap.addNewSession(DEFAULT_SESSION_TOKEN, newUser, DEFAULT_DATETIME)
        pmd.addTimeEntry(createTimeEntryPreDatabase(employee = newEmployee, project = newProject))
        pmd.stop()

        // corrupt the time-entries data file
        File("$DEFAULT_DB_DIRECTORY/sessions$databaseFileSuffix").writeText("BAD DATA HERE")
        
        assertThrows(DatabaseCorruptedException::class.java) {PureMemoryDatabase.startWithDiskPersistence(DEFAULT_DB_DIRECTORY)}
    }

    @Test
    fun testSerialization_User() {
        val user = PureMemoryDatabase.UserSurrogate(1, "myname", "myhash", "mysalt", 1)

        val result = user.serialize()

        assertEquals("""{ id: 1 , name: myname , hash: myhash , salt: mysalt , empId: 1 }""", result)

        val deserialized = PureMemoryDatabase.UserSurrogate.deserialize(result)

        assertEquals(user, deserialized)
    }

    @Test
    fun testSerialization_UserWithNullEmployee() {
        val user = PureMemoryDatabase.UserSurrogate(1, "myname", "myhash", "mysalt", null)

        val result = user.serialize()

        assertEquals("""{ id: 1 , name: myname , hash: myhash , salt: mysalt , empId: null }""", result)

        val deserialized = PureMemoryDatabase.UserSurrogate.deserialize(result)

        assertEquals(user, deserialized)
    }

    @Test
    fun testSerialization_UserWithMultilineText() {
        val user = PureMemoryDatabase.UserSurrogate(1, "myname", "myhash", """mysalt
            |thisisalsotext""".trimMargin(), 1)

        val result = user.serialize()

        assertEquals("""{ id: 1 , name: myname , hash: myhash , salt: mysalt%0Athisisalsotext , empId: 1 }""".trimMargin(), result)

        val deserialized = PureMemoryDatabase.UserSurrogate.deserialize(result)

        assertEquals(user, deserialized)
    }

    @Test
    fun testSerialization_UserWithUnicodeText() {
        val user = PureMemoryDatabase.UserSurrogate(1, "myname", "myhash", "L¡¢£¤¥¦§¨©ª«¬®¯°±²³´µ¶·¸¹º»¼½¾¿LÀÁÂÃÄÅÆÇÈÉÊË", 1)

        val result = user.serialize()

        assertEquals("""{ id: 1 , name: myname , hash: myhash , salt: L%C2%A1%C2%A2%C2%A3%C2%A4%C2%A5%C2%A6%C2%A7%C2%A8%C2%A9%C2%AA%C2%AB%C2%AC%C2%AE%C2%AF%C2%B0%C2%B1%C2%B2%C2%B3%C2%B4%C2%B5%C2%B6%C2%B7%C2%B8%C2%B9%C2%BA%C2%BB%C2%BC%C2%BD%C2%BE%C2%BFL%C3%80%C3%81%C3%82%C3%83%C3%84%C3%85%C3%86%C3%87%C3%88%C3%89%C3%8A%C3%8B , empId: 1 }""", result)

        val deserialized = PureMemoryDatabase.UserSurrogate.deserialize(result)

        assertEquals(user, deserialized)
    }


    @Test
    fun testSerialization_Employee() {
        val employee = PureMemoryDatabase.EmployeeSurrogate(1, "myname")

        val result = employee.serialize()

        assertEquals("""{ id: 1 , name: myname }""", result)

        val deserialized = PureMemoryDatabase.EmployeeSurrogate.deserialize(result)

        assertEquals(employee, deserialized)
    }

    @Test
    fun testSerialization_Employee_UnicodeAndMultiline() {
        val employee = PureMemoryDatabase.EmployeeSurrogate(1, "\n\r\tHelloµ¶·¸¹º»¼½¾¿LÀÁÂÃÄÅÆ")

        val result = employee.serialize()

        assertEquals("""{ id: 1 , name: %0A%0D%09Hello%C2%B5%C2%B6%C2%B7%C2%B8%C2%B9%C2%BA%C2%BB%C2%BC%C2%BD%C2%BE%C2%BFL%C3%80%C3%81%C3%82%C3%83%C3%84%C3%85%C3%86 }""", result)

        val deserialized = PureMemoryDatabase.EmployeeSurrogate.deserialize(result)

        assertEquals(employee, deserialized)
    }

    @Test
    fun testSerialization_Project() {
        val project = PureMemoryDatabase.ProjectSurrogate(1, "myname")

        val result = project.serialize()

        assertEquals("""{ id: 1 , name: myname }""", result)

        val deserialized = PureMemoryDatabase.ProjectSurrogate.deserialize(result)

        assertEquals(project, deserialized)
    }

    @Test
    fun testSerialization_Project_UnicodeAndMultiline() {
        val project = PureMemoryDatabase.ProjectSurrogate(1, "\n\r\tHelloµ¶·¸¹º»¼½¾¿LÀÁÂÃÄÅÆ")

        val result = project.serialize()

        assertEquals("""{ id: 1 , name: %0A%0D%09Hello%C2%B5%C2%B6%C2%B7%C2%B8%C2%B9%C2%BA%C2%BB%C2%BC%C2%BD%C2%BE%C2%BFL%C3%80%C3%81%C3%82%C3%83%C3%84%C3%85%C3%86 }""", result)

        val deserialized = PureMemoryDatabase.ProjectSurrogate.deserialize(result)

        assertEquals(project, deserialized)
    }

    @Test
    fun testSerialization_Session() {
        val session = PureMemoryDatabase.SessionSurrogate("abc123", 1, 1608662050608)

        val result = session.serialize()

        assertEquals("""{ s: abc123 , id: 1 , e: 1608662050608 }""", result)

        val deserialized = PureMemoryDatabase.SessionSurrogate.deserialize(result)

        assertEquals(session, deserialized)
    }

    @Test
    fun testSerialization_SessionUnicodeAndMultiline() {
        val session = PureMemoryDatabase.SessionSurrogate("\n\rabc123½¾¿LÀÁ", 1, 1608662050608)

        val result = session.serialize()

        assertEquals("""{ s: %0A%0Dabc123%C2%BD%C2%BE%C2%BFL%C3%80%C3%81 , id: 1 , e: 1608662050608 }""", result)

        val deserialized = PureMemoryDatabase.SessionSurrogate.deserialize(result)

        assertEquals(session, deserialized)
    }



    @Test
    fun testSerialization_TimeEntry() {
        val timeEntry = PureMemoryDatabase.TimeEntrySurrogate(123, 456, 789, 101, 234, "\n\rabc123½")

        val result = timeEntry.serialize()

        assertEquals("""{ i: 123 , e: 456 , p: 789 , t: 101 , d: 234 , dtl: %0A%0Dabc123%C2%BD }""", result)

        val deserialized = PureMemoryDatabase.TimeEntrySurrogate.deserialize(result)

        assertEquals(timeEntry, deserialized)
    }

    enum class Testing {
        NOTHING
    }

    /**
     * This is an experiment to try out concurrent hash maps as an alternative
     * to the sets and lists we have been using.  The benefits are that you
     * don't need to synchronize, which is pretty sweet when you consider
     * that in this application, the only thing we're synchronizing is the
     * data structures in the [PureMemoryDatabase].
     *
     * If we can somehow switch in these data structures and remove the sync'd
     * methods, I believe the performance gains will be through the roof... until
     * we have to consider the IO (writing to disk).
     *
     * Notice that when you run this you get a lot of different
     * numbers.  That's because this data structure is "weakly consistent",
     * that is, it's correct for the moment you read from it.  Since
     * in this experiment we are carrying out a lot of actions that
     * interfere, you would expect to see varying answers.
     */
    @Test
    fun testTryingOutConcurrentHashMap_PERFORMANCE() {
        val index = AtomicInteger(1)
        println("Running concurrentHashMap experiment at testTryingOutConcurrentHashMap_PERFORMANCE")
        for (i in 1..10) {
            val myMap = ConcurrentHashMap<Employee, Testing>()
            (1..10_000).forEach { myMap[Employee(EmployeeId(index.getAndIncrement()), EmployeeName(it.toString()))] = Testing.NOTHING }
            myMap[Employee(EmployeeId(index.getAndIncrement()), EmployeeName("alice"))] = Testing.NOTHING
            val (time, _) = getTime {
                val t1 = thread {
                    (10_001..20_000).forEach { myMap[Employee(EmployeeId(index.getAndIncrement()), EmployeeName(it.toString()))] = Testing.NOTHING }
                }
                val t2 = thread {
                    print(myMap.filter { it.key.hashCode() % 2 == 0 }.count())
                }
                t1.join()
                t2.join()
                print(". Totalsize: ${myMap.size}")
            }
            println(" time: $time")
        }
    }

    /*
     _ _       _                  __ __        _    _           _
    | | | ___ | | ___  ___  _ _  |  \  \ ___ _| |_ | |_  ___  _| | ___
    |   |/ ._>| || . \/ ._>| '_> |     |/ ._> | |  | . |/ . \/ . |<_-<
    |_|_|\___.|_||  _/\___.|_|   |_|_|_|\___. |_|  |_|_|\___/\___|/__/
                 |_|
     alt-text: Helper Methods
     */

    private fun prepareSomeRandomTimeEntries(numTimeEntries: Int, project : Project, employee : Employee): MutableSet<TimeEntry> {
        val timeEntries: MutableSet<TimeEntry> = mutableSetOf()
        for (i in 1..numTimeEntries) {
            timeEntries.add(
                TimeEntry(
                    i,
                    employee,
                    project,
                    Time(100),
                    A_RANDOM_DAY_IN_JUNE_2020,
                    Details("I was lazing on a sunday afternoon")
                )
            )
        }
        return timeEntries
    }

    private fun recordManyTimeEntries(numberOfEmployees: Int, numberOfProjects: Int, numberOfDays: Int) : List<Employee> {
        val lotsOfEmployees: List<String> = generateEmployeeNames()
        persistEmployeesToDatabase(numberOfEmployees, lotsOfEmployees)
        val allEmployees: List<Employee> = readEmployeesFromDatabase()
        persistProjectsToDatabase(numberOfProjects)
        val allProjects: List<Project> = readProjectsFromDatabase()
        enterTimeEntries(numberOfDays, allEmployees, allProjects, numberOfEmployees)
        return allEmployees
    }

    private fun accumulateMinutesPerEachEmployee(allEmployees: List<Employee>) {
        val (timeToAccumulate) = getTime {
            val minutesPerEmployeeTotal =
                    allEmployees.map { e -> pmd.getAllTimeEntriesForEmployee(e).sumBy { te -> te.time.numberOfMinutes } }
                            .toList()
            logAudit("the time ${allEmployees[0].name.value} spent was ${minutesPerEmployeeTotal[0]}")
            logAudit("the time ${allEmployees[1].name.value} spent was ${minutesPerEmployeeTotal[1]}")
        }

        logAudit("It took $timeToAccumulate milliseconds to accumulate the minutes per employee")
    }

    private fun readTimeEntriesForOneEmployee(allEmployees: List<Employee>) {
        val (timeToGetAllTimeEntries) = getTime { pmd.getAllTimeEntriesForEmployee(allEmployees[0]) }
        logAudit("It took $timeToGetAllTimeEntries milliseconds to get all the time entries for a employee")
    }

    private fun enterTimeEntries(numberOfDays: Int, allEmployees: List<Employee>, allProjects: List<Project>, numberOfEmployees: Int) {
        val (timeToEnterAllTimeEntries) = getTime {
            for (day in 1..numberOfDays) {
                for (employee in allEmployees) {
                    pmd.addTimeEntry(TimeEntryPreDatabase(employee, allProjects.random(), Time(2 * 60), Date(18438 + day), Details("AAAAAAAAAAAA")))
                    pmd.addTimeEntry(TimeEntryPreDatabase(employee, allProjects.random(), Time(2 * 60), Date(18438 + day), Details("AAAAAAAAAAAA")))
                    pmd.addTimeEntry(TimeEntryPreDatabase(employee, allProjects.random(), Time(2 * 60), Date(18438 + day), Details("AAAAAAAAAAAA")))
                    pmd.addTimeEntry(TimeEntryPreDatabase(employee, allProjects.random(), Time(2 * 60), Date(18438 + day), Details("AAAAAAAAAAAA")))
                }
            }
        }
        logAudit("It took $timeToEnterAllTimeEntries milliseconds total to enter ${numberOfDays * 4} time entries for each of $numberOfEmployees employees")
        logAudit("(That's a total of ${("%,d".format(numberOfDays * 4 * numberOfEmployees))} time entries)")
    }

    private fun readProjectsFromDatabase(): List<Project> {
        val (timeToReadAllProjects, allProjects) = getTime { pmd.getAllProjects()}
        logAudit("It took $timeToReadAllProjects milliseconds to read all the projects")
        return allProjects
    }

    private fun persistProjectsToDatabase(numberOfProjects: Int) {
        val (timeToCreateProjects) =
                getTime { (1..numberOfProjects).forEach { i -> pmd.addNewProject(ProjectName("project$i")) } }
        logAudit("It took $timeToCreateProjects milliseconds to create $numberOfProjects projects")
    }

    private fun readEmployeesFromDatabase(): List<Employee> {
        val (timeToReadAllEmployees, allEmployees) = getTime {
            pmd.getAllEmployees()
        }
        logAudit("It took $timeToReadAllEmployees milliseconds to read all the employees")
        return allEmployees
    }

    private fun persistEmployeesToDatabase(numberOfEmployees: Int, lotsOfEmployees: List<String>) {
        val (timeToEnterEmployees) = getTime {
            for (i in 1..numberOfEmployees) {
                pmd.addNewEmployee(EmployeeName(lotsOfEmployees[i]))
            }
        }
        logAudit("It took $timeToEnterEmployees milliseconds to enter $numberOfEmployees employees")
    }

    private fun generateEmployeeNames(): List<String> {
        val (timeToMakeEmployeenames, lotsOfEmployees) = getTime {
            listOf(
                    "Arlen", "Hedwig", "Allix", "Tandi", "Silvia", "Catherine", "Mavis", "Hally", "Renate", "Anastasia", "Christy", "Nora", "Molly", "Nelli", "Daphna", "Chloette", "TEirtza", "Nannie", "Melinda", "Tyne", "Belva", "Pam", "Rebekkah", "Elayne", "Dianne", "Christina", "Jeanne", "Norry", "Reina", "Erminia", "Eadie", "Valina", "Gayle", "Wylma", "Annette", "Annmaria", "Fayina", "Dita", "Sibella", "Alis", "Georgena", "Luciana", "Sidonnie", "Dina", "Ferdinande", "Coletta", "Esma", "Saidee", "Hannah", "Colette", "Anitra", "Grissel", "Caritta", "Ann", "Rhodia", "Meta", "Bride", "Dalenna", "Rozina", "Ottilie", "Eliza", "Gerda", "Anthia", "Kathryn", "Lilian", "Jeannie", "Nichole", "Marylinda", "Angelica", "Margie", "Ruthie", "Augustina", "Netta", "Fleur", "Mahala", "Cosette", "Zsa Zsa", "Karry", "Tabitha", "Andriana", "Fey", "Hedy", "Saudra", "Geneva", "Lacey", "Fawnia", "Ertha", "Bernie", "Natty", "Joyan", "Teddie", "Hephzibah", "Vonni", "Ambur", "Lyndsie", "Anna", "Minnaminnie", "Andy", "Brina", "Pamella", "Trista", "Antonetta", "Kerrin", "Crysta", "Kira", "Gypsy", "Candy", "Ree", "Sharai", "Mariana", "Eleni", "Yetty", "Maisie", "Deborah", "Doretta", "Juliette", "Esta", "Amandi", "Anallise", "Indira", "Aura", "Melodee", "Desiri", "Jacquenetta", "Joell", "Delcine", "Justine", "Theresita", "Belia", "Mallory", "Antonie", "Jobi", "Katalin", "Kelli", "Ester", "Katey", "Gianna", "Berry", "Sidonia", "Roseanne", "Cherida", "Beatriz", "Eartha", "Robina", "Florri", "Vitoria", "Debera", "Jeanette", "Almire", "Saree", "Liana", "Ruth", "Renell", "Katinka", "Anya", "Gwyn", "Kaycee", "Rori", "Rianon", "Joann", "Zorana", "Hermia", "Gwenni", "Poppy", "Dedie", "Cloe", "Kirsti", "Krysta", "Clarinda", "Enid", "Katina", "Ralina", "Meryl", "Andie", "Orella", "Alexia", "Clarey", "Iris", "Chris", "Devin", "Kally", "Vernice", "Noelyn", "Stephana", "Catina", "Faydra", "Fionna", "Nola", "Courtnay", "Vera", "Meriel", "Eleonora", "Clare", "Marsha", "Marita", "Concettina", "Kristien", "Celina", "Maryl", "Codee", "Lorraine", "Lauraine", "Sephira", "Kym", "Odette", "Ranee", "Margaux", "Debra", "Corenda", "Mariejeanne", "Georgeanne", "Laural", "Fredelia", "Dulcine", "Tess", "Tina", "Adaline", "Melisandra", "Lita", "Nettie", "Lesley", "Clea", "Marysa", "Arleyne", "Meade", "Ella", "Theodora", "Morgan", "Carena", "Camille", "Janene", "Anett", "Camellia", "Guglielma", "Evvy", "Shayna", "Karilynn", "Ingeberg", "Maudie", "Colene", "Kelcy", "Blythe", "Lacy", "Cesya", "Bobbe", "Maggi", "Darline", "Almira", "Constantia", "Helaina", "Merrili", "Maxine", "Linea", "Marley", "Timmie", "Devon", "Mair", "Thomasine", "Sherry", "Gilli", "Ursa", "Marlena", "Cloris", "Vale", "Alexandra", "Angel", "Alice", "Ulrica", "Britteny", "Annie", "Juliane", "Candida", "Jennie", "Susanne", "Robenia", "Benny", "Cassy", "Denyse", "Jackquelin", "Lorelle", "Lenore", "Sheryl", "Marice", "Clarissa", "Kippy", "Cristen", "Hanni", "Marne", "Melody", "Shane", "Kalli", "Deane", "Kaila", "Faye", "Noella", "Penni", "Sophia", "Marilin", "Cori", "Clair", "Morna", "Lynn", "Rozelle", "Berta", "Bamby", "Janifer", "Doro", "Beryle", "Pammy", "Paige", "Juanita", "Ellene", "Kora", "Kerrie", "Perrine", "Dorena", "Mady", "Dorian", "Lucine", "Jill", "Octavia", "Sande", "Talyah", "Rafaelia", "Doris", "Patti", "Mora", "Marja", "Rivi", "Drucill", "Marina", "Rennie", "Annabell", "Xylia", "Zorina", "Ashil", "Becka", "Blithe", "Lenora", "Kattie", "Analise", "Jasmin", "Minetta", "Deeanne", "Sharity", "Merci", "Julissa", "Nicoli", "Nevsa", "Friederike", "Caroljean", "Catlee", "Charil", "Dara", "Kristy", "Ag", "Andriette", "Kati", "Jackqueline", "Letti", "Allys", "Carlee", "Frannie", "Philis", "Aili", "Else", "Diane", "Tobey", "Tildie", "Merrilee", "Pearle", "Christan", "Dominique", "Rosemaria", "Bunnie", "Tedi", "Elinor", "Aeriell", "Karissa", "Darya", "Tonye", "Alina", "Nalani", "Marcela", "Anabelle", "Layne", "Dorice", "Aleda", "Anette", "Arliene", "Rosemarie", "Pru", "Tiffani", "Addi", "Roda", "Shandra", "Wendeline", "Karoline", "Ciel", "Ania"
            )
            // if you want to make a lot more names, uncomment below
            // lotsOfEmployees = (1..10).flatMap { n -> employeenames.map { u -> "$u$n" } }.toList()
        }
        logAudit("It took $timeToMakeEmployeenames milliseconds to create ${lotsOfEmployees.size} employeenames")
        return lotsOfEmployees
    }

}