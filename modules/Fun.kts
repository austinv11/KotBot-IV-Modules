
import com.austinv11.kotbot.core.CLIENT
import com.austinv11.kotbot.core.api.commands.*
import com.austinv11.kotbot.core.db.DatabaseManager
import com.austinv11.kotbot.core.util.scanForModuleDependentObjects
import com.j256.ormlite.dao.Dao
import com.j256.ormlite.dao.DaoManager
import com.j256.ormlite.dao.ForeignCollection
import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.field.ForeignCollectionField
import com.j256.ormlite.table.DatabaseTable
import com.j256.ormlite.table.TableUtils
import sx.blah.discord.api.IDiscordClient
import sx.blah.discord.handle.obj.IUser
import sx.blah.discord.modules.IModule

enum class PseudoAdd {
    ADD, PUT, INSERT, EDIT, MODIFY
}

enum class PseudoRemove {
    REMOVE, DELETE
}

@DatabaseTable(tableName = "tags")
class TagsConfiguration {

    companion object {
        lateinit var TAGS_DAO: Dao<TagsConfiguration, Int>

        const val ID = "id"
        const val NAME = "name"
        const val TAG = "tag"
        const val AUTHOR = "author"
        const val MANAGER = "manager"
    }

    constructor()

    constructor(name: String, tag: String, author: String, manager: TagManagerConfiguration) {
        this.name = name
        this.tag = tag
        this.author = author
        this.manager = manager

        TAGS_DAO.create(this)
    }


    @DatabaseField(columnName = ID, generatedId = true)
    var id: Int = 0

    @DatabaseField(columnName = NAME, canBeNull = false)
    var name: String = ""

    @DatabaseField(columnName = TAG, canBeNull = false)
    var tag: String = ""

    @DatabaseField(columnName = AUTHOR, canBeNull = false)
    var author: String = ""

    @DatabaseField(columnName = MANAGER, canBeNull = true, foreign = true, foreignAutoRefresh = true)
    var manager: TagManagerConfiguration? = null
}

@DatabaseTable(tableName = "tag_manager")
class TagManagerConfiguration {

    companion object {
        lateinit var TAG_MANAGER_DAO: Dao<TagManagerConfiguration, Int>

        const val ID = "id"
        const val GUILD = "guild"
        const val TAGS = "tags"
    }

    constructor()

    constructor(guild: String) {
        this.guild = guild

        TAG_MANAGER_DAO.create(this)
    }


    @DatabaseField(columnName = ID, generatedId = true)
    var id: Int = 0

    @DatabaseField(columnName = GUILD, canBeNull = false)
    var guild: String = ""

    @ForeignCollectionField(columnName = TAGS, eager = true)
    var tags: ForeignCollection<TagsConfiguration>? = null
}

object: IModule {

    override fun enable(client: IDiscordClient): Boolean {
        TableUtils.createTableIfNotExists(DatabaseManager.CONNECTION_SOURCE, TagManagerConfiguration::class.java)
        TagManagerConfiguration.TAG_MANAGER_DAO = DaoManager.createDao(DatabaseManager.CONNECTION_SOURCE, TagManagerConfiguration::class.java)

        TableUtils.createTableIfNotExists(DatabaseManager.CONNECTION_SOURCE, TagsConfiguration::class.java)
        TagsConfiguration.TAGS_DAO = DaoManager.createDao(DatabaseManager.CONNECTION_SOURCE, TagsConfiguration::class.java)
        
        scanForModuleDependentObjects()
        return true
    }

    override fun getName() = "Fun"

    override fun getVersion() = "1.0"

    override fun getMinimumDiscord4JVersion() = "2.7.1-SNAPSHOT"

    override fun getAuthor() = "Austin"

    override fun disable() {}

    inner class TagCommand : Command("This manages tags on this server ", aliases = arrayOf("tags", "quote")) {

        private fun canEdit(config: TagsConfiguration, user: IUser): Boolean {
            val permission = user.retrievePermissionLevel(CLIENT.getGuildByID(config.manager!!.guild))

            return permission >= PermissionLevel.ADMINISTRATOR || user.id == config.author
        }

        @Executor
        fun execute() {

        }

        @Executor
        fun execute(@Parameter("The tag you wish to display") tag: String) {

        }

        @Executor
        fun execute(@Parameter("Makes KotBot create a new tag") option: PseudoAdd,
                    @Parameter("The name of the tag") name: String,
                    @Parameter("The content of the tag") content: String) {

        }

        @Executor
        fun execute(@Parameter("Makes KotBot delete a tag") option: PseudoRemove,
                    @Parameter("The name of the tag to delete") tag: String) {

        }
    }
}