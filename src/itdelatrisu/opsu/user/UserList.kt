package itdelatrisu.opsu.user

import itdelatrisu.opsu.OpsuConstants
import itdelatrisu.opsu.db.ScoreDB

class UserList {

	private val users = mutableMapOf<String, User>()
	private lateinit var currentUser: User

	init {
		val list = ScoreDB.getUsers()
		for (user in list)
			users[user.getName().lowercase()] = user

		if (list.isEmpty()) {
			createNewUser(DEFAULT_USER_NAME, DEFAULT_ICON)
			changeUser(DEFAULT_USER_NAME)
		} else {
			if (!changeUser(ScoreDB.getCurrentUser())) {
				if (!userExists(DEFAULT_USER_NAME))
					createNewUser(DEFAULT_USER_NAME, DEFAULT_ICON)
				changeUser(DEFAULT_USER_NAME)
			}
		}
	}

	fun size() = users.size

	fun getUsers(): List<User> = users.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.getName() })

	fun getCurrentUser(): User = currentUser

	fun userExists(name: String?): Boolean = name != null && users.containsKey(name.lowercase())

	fun getUser(name: String): User? = users[name.lowercase()]

	fun changeUser(name: String?): Boolean {
		if (!userExists(name)) return false
		currentUser = getUser(name!!)!!
			ScoreDB.setCurrentUser(name)
		return true
	}

	fun createNewUser(name: String, icon: Int): User? {
		if (!isValidUserName(name)) return null
		val user = User(name, icon)
		ScoreDB.updateUser(user)
		users[name.lowercase()] = user
		return user
	}

	fun deleteUser(name: String): Boolean {
		if (!userExists(name) || name == currentUser.getName()) return false
		ScoreDB.deleteUser(name)
		users.remove(name.lowercase())
		return true
	}

	fun isValidUserName(name: String): Boolean =
		name.isNotEmpty() &&
	name.length <= MAX_USER_NAME_LENGTH &&
	name == name.trim() &&
		!userExists(name) &&
		!name.equals(AUTO_USER_NAME, ignoreCase = true)

	companion object {
        const val DEFAULT_USER_NAME = "Guest"
        const val DEFAULT_ICON      = 0
        const val AUTO_USER_NAME    = OpsuConstants.PROJECT_NAME
        const val MAX_USER_NAME_LENGTH = 16

		private var list: UserList? = null

		@JvmStatic fun create() { list = UserList() }
		@JvmStatic fun get(): UserList = list!!
	}
}
