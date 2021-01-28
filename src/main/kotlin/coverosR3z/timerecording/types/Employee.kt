package coverosR3z.timerecording.types

import coverosR3z.misc.utility.checkParseToInt
import coverosR3z.persistence.types.Deserializable
import coverosR3z.persistence.types.IndexableSerializable
import coverosR3z.persistence.types.SerializableCompanion
import coverosR3z.persistence.types.SerializationKeys
import coverosR3z.persistence.utility.DatabaseDiskPersistence.Companion.deserialize

private const val maxEmployeeCount = 100_000_000
private const val maxEmployeeNameSize = 30
private const val maxEmployeeNameSizeMsg = "Max size of employee name is $maxEmployeeNameSize"
private const val maxEmployeeMsg = "No way this company has more than 100 million employees"
const val minEmployeeIdMsg = "Valid identifier values are 1 or above"
private const val employeeNameCannotBeEmptyMsg = "All employees must have a non-empty name"
const val employeeIdNotNullMsg = "The employee id must not be null"
const val employeeNameNotNullMsg = "The employee name must not be null"
const val employeeIdCannotBeBlank = "The employee id must not be blank"

/**
 * This is used to represent no employee - just to avoid using null for an employee
 * It's a typed null, essentially
 */
val NO_EMPLOYEE = Employee(EmployeeId(maxEmployeeCount -1), EmployeeName("THIS REPRESENTS NO EMPLOYEE"))

/**
 * Holds a employee's name before we have a whole object, like [Employee]
 */
data class EmployeeName(val value: String) {
    init {
        require(value.isNotEmpty()) { employeeNameCannotBeEmptyMsg }
        require(value.length <= maxEmployeeNameSize) { maxEmployeeNameSizeMsg }
    }

    companion object {
        fun make(value: String?) : EmployeeName {
            val valueNotNull = checkNotNull(value) { employeeNameNotNullMsg }
            return EmployeeName(valueNotNull)
        }
    }
}

data class EmployeeId(val value: Int) {


    init {
        require(value < maxEmployeeCount) { maxEmployeeMsg }
        require(value > 0) { minEmployeeIdMsg }
    }

    companion object {

        /**
         * You can pass the id as a string and we'll try to parse it
         */
        fun make(value: String?) : EmployeeId {
            return EmployeeId(checkParseToInt(value, { employeeIdNotNullMsg }, { employeeIdCannotBeBlank }))
        }
    }
}

data class Employee(val id: EmployeeId, val name: EmployeeName) : IndexableSerializable() {

    override fun getIndex(): Int {
        return id.value
    }

    override val dataMappings: Map<SerializationKeys, String>
        get() = mapOf(
            Keys.ID to "${id.value}",
            Keys.NAME to name.value
        )

    class Deserializer : Deserializable<Employee> {

        override fun deserialize(str: String) : Employee {
            return deserialize(str, Employee::class.java, Companion) { entries ->
                val id = checkParseToInt(entries[Keys.ID])
                Employee(EmployeeId(id), EmployeeName.make((entries[Keys.NAME])))
            }
        }

    }

    companion object : SerializableCompanion {

        override val directoryName: String
            get() = "employees"

        override fun convertToKey(s: String): SerializationKeys {
            return Keys.values().single { it.getKey() == s }
        }

        enum class Keys(private val keyString: String) : SerializationKeys {
            ID("id"),
            NAME("name");

            /**
             * This needs to be a method and not just a value of the class
             * so that we can have it meet an interface specification, so
             * that we can use it in generic code
             */
            override fun getKey() : String {
                return keyString
            }
        }

    }
}


