
import com.austinv11.kotbot.core.api.ModuleDependentIListener
import com.austinv11.kotbot.core.db.DatabaseManager
import com.austinv11.kotbot.core.util.context
import com.austinv11.kotbot.core.CLIENT
import com.austinv11.kotbot.core.api.commands.*
import com.austinv11.kotbot.core.util.createEmbedBuilder
import com.austinv11.kotbot.core.util.scanForModuleDependentObjects
import com.austinv11.kotbot.core.config.Config
import com.austinv11.kotbot.core.util.buffer
import com.j256.ormlite.dao.Dao
import com.j256.ormlite.dao.DaoManager
import com.j256.ormlite.dao.ForeignCollection
import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.field.ForeignCollectionField
import com.j256.ormlite.table.DatabaseTable
import com.j256.ormlite.table.TableUtils
import sx.blah.discord.api.IDiscordClient
import sx.blah.discord.api.events.IListener
import sx.blah.discord.handle.impl.events.guild.member.UserJoinEvent
import sx.blah.discord.modules.IModule
import sx.blah.discord.handle.obj.*
import sx.blah.discord.util.EmbedBuilder
import java.util.concurrent.atomic.AtomicInteger

//These enums are hacks for tricking the parser
enum class PsuedoAdd {
    ADD, INVITE
}

enum class PsuedoRemove {
    REMOVE, DELETE
}

enum class PsuedoModify {
    EDIT, MODIFY
}

enum class PsuedoSetup {
    SETUP
}

enum class PsuedoAccept {
    ACCEPT
}

enum class PsuedoReject {
    REJECT
}

enum class PsuedoOverride {
    OVERRIDE
}

@DatabaseTable(tableName = "bots")
class BotsConfiguration {

    companion object {
        lateinit var BOTS_DAO: Dao<BotsConfiguration, Int>

        const val ID = "id"
        const val BOT_ID = "bot"
        const val OWNER_ID = "owner"
        const val PREFIX = "prefix"
        const val MANAGER = "manager"
    }

    constructor()

    constructor(bot: String, owner: String, prefix: String) {
        this.bot = bot
        this.owner = owner
        this.prefix = prefix

        BOTS_DAO.create(this)
    }


    @DatabaseField(columnName = ID, generatedId = true)
    var id: Int = 0

    @DatabaseField(columnName = BOT_ID, canBeNull = false)
    var bot: String = ""

    @DatabaseField(columnName = OWNER_ID, canBeNull = false)
    var owner: String = ""

    @DatabaseField(columnName = PREFIX, canBeNull = false)
    var prefix: String = ""

    @DatabaseField(columnName = MANAGER, canBeNull = true, foreign = true, foreignAutoRefresh = true)
    var manager: BotManagerConfiguration? = null
}

@DatabaseTable(tableName = "bot_manager")
class BotManagerConfiguration {

    companion object {
        lateinit var BOT_MANAGER_DAO: Dao<BotManagerConfiguration, Int>

        const val ID = "id"
        const val GUILD = "guild"
        const val REQUEST_CHANNEL = "channel"
        const val ROLE = "role"
        const val BOTS = "bots"
    }

    constructor()

    constructor(guild: String, channel: String, role: String?) {
        this.guild = guild
        this.channel = channel
        this.role = role

        BOT_MANAGER_DAO.create(this)
    }


    @DatabaseField(columnName = ID, generatedId = true)
    var id: Int = 0

    @DatabaseField(columnName = GUILD, canBeNull = false)
    var guild: String = ""

    @DatabaseField(columnName = REQUEST_CHANNEL, canBeNull = false)
    var channel: String = ""

    @DatabaseField(columnName = ROLE, canBeNull = true)
    var role: String? = null

    @ForeignCollectionField(columnName = BOTS, eager = true)
    var bots: ForeignCollection<BotsConfiguration>? = null
}

object: IModule { //TODO: Persist pending requests?

    val pendingRequests = mutableListOf<Request>()
    val PERMISSIONS_REGEX = Regex("((&|)permissions=)+(\\d*)")

    inner class Request(val id: Int, val guild: IGuild, val prefix: String, val invite: String,
                        val justification: String?, val owner: IUser, val requestingChannel: IChannel)

    override fun enable(client: IDiscordClient): Boolean {
        TableUtils.createTableIfNotExists(DatabaseManager.CONNECTION_SOURCE, BotManagerConfiguration::class.java)
        BotManagerConfiguration.BOT_MANAGER_DAO = DaoManager.createDao(DatabaseManager.CONNECTION_SOURCE, BotManagerConfiguration::class.java)

        TableUtils.createTableIfNotExists(DatabaseManager.CONNECTION_SOURCE, BotsConfiguration::class.java)
        BotsConfiguration.BOTS_DAO = DaoManager.createDao(DatabaseManager.CONNECTION_SOURCE, BotsConfiguration::class.java)

        scanForModuleDependentObjects()
        return true
    }

    override fun getName(): String = "KotBot-Bots"

    override fun getVersion(): String = "1.0"

    override fun getMinimumDiscord4JVersion(): String = "2.7.1-SNAPSHOT"

    override fun getAuthor(): String = "Austin"

    override fun disable() {}

    inner class BotJoinListener : ModuleDependentIListener<UserJoinEvent>(CLIENT) {

        override fun handle(event: UserJoinEvent) {
            if (event.user.isBot) {
                val guild = event.guild
                val config = BotManagerConfiguration.BOT_MANAGER_DAO
                        .queryForFieldValuesArgs(mapOf(BotManagerConfiguration.GUILD to guild.id))
                        .firstOrNull()
                if (config != null) {
                    if (config.role != null)
                        buffer { event.user.addRole(guild.getRoleByID(config.role)!!) }
                }
            }
        }
    }

    inner class BotsCommand : Command("This manages bots on this server ", aliases = arrayOf("bot", "invite")) {

        val isSetup: Boolean
            get() {
                return getConfig() != null
            }
        val nextId = AtomicInteger(1)

        private fun isAdmin(config: BotManagerConfiguration, user: IUser): Boolean {
            val permission = user.retrievePermissionLevel(CLIENT.getGuildByID(config.guild))

            return permission >= PermissionLevel.ADMINISTRATOR
        }

        private fun String.asMention(): String {
            return "<@!$this>" //Ensures NPEs don't occur if the bot's owner isn't in the guild
        }

        private fun getConfig() = BotManagerConfiguration.BOT_MANAGER_DAO
                .queryForFieldValuesArgs(mapOf(BotManagerConfiguration.GUILD to context.channel.guild.id))
                .firstOrNull()

        @Executor
        fun execute(): EmbedBuilder {
            if (!isSetup) throw CommandException("Bot management is not setup on this server!")

            val config = getConfig()!!

            val builder = createEmbedBuilder()
                    .withTitle("Bot Management")

            if (isAdmin(config, context.user)) {
                val requestsString = buildString {
                    pendingRequests.filter { it.guild == context.channel.guild }.forEach {
                        appendln("#${it.id} by ${it.owner.mention()}, [Invite](${it.invite})")
                    }
                }
                builder.appendField("Reporting Channel", context.channel.guild.getChannelByID(config.channel)?.mention() ?: "Not configured", true)
                        .appendField("Bot Role", context.channel.guild.getRoleByID(config.role)?.mention() ?: "Not configured", true)
                        .appendField("Pending Requests", if (requestsString.isNotEmpty()) requestsString else "None", true)
            }

            val prefixMap = mutableMapOf<String, MutableList<BotsConfiguration>>()
            config.bots!!.forEach {
                prefixMap.putIfAbsent(it.prefix, mutableListOf())
                prefixMap[it.prefix]!!.add(it)
            }

            val botStrings = mutableListOf<String>()
            prefixMap.toSortedMap().forEach { k, v ->
                v.forEach {
                    botStrings.add("${it.bot.asMention()} (${it.prefix}), owned by ${it.owner.asMention()}")
                }
            }

            val botString = buildString {
                botStrings.forEachIndexed { i, s ->
                    appendln("${i+1}. $s")
                }
            }
            builder.appendField("Bots", if (botString.isNotEmpty()) botString else "None", false)

            return builder
        }

        @Executor
        fun execute(@Parameter("The prefix the desired bots use") prefix: String): EmbedBuilder {
            if (!isSetup) throw CommandException("Bot management is not setup on this server!")

            val config = getConfig()!!

            val bots = mutableListOf<BotsConfiguration>()
            bots.addAll(config.bots!!.distinct().filter { it.prefix == prefix })

            if (bots.isEmpty()) throw CommandException("Cannot find any bots with the prefix `$prefix`")

            val builder = createEmbedBuilder().withTitle("Bots with the prefix `$prefix`")
                    .appendDescription(buildString {
                        bots.forEachIndexed { i, bot ->
                            appendln("${i+1}. ${bot.bot.asMention()}, owned by ${bot.owner.asMention()}")
                        }
                    })

            return builder
        }

        @Executor
        fun execute(@Parameter("Makes KotBot edit an existing entry") option: PsuedoModify,
                    @Parameter("The bot to edit") bot: IUser,
                    @Parameter("The new prefix for the bot to use") prefix: String): Boolean {
            if (!isSetup) throw CommandException("Bot management is not setup on this server!")

            val config = getConfig()!!

            val botConfig = config.bots!!.firstOrNull { it.bot == bot.id } ?: throw CommandException("${bot.mention()} has not been added to KotBot's internal database!")

            if (context.user.id != botConfig.owner && !isAdmin(config, context.user)) throw CommandException("Missing permissions to modify the bot properties of ${bot.mention()}")

            botConfig.prefix = prefix
            BotsConfiguration.BOTS_DAO.update(botConfig)

            return true
        }

        @Executor
        fun execute(@Parameter("Makes KotBot remove an entry") option: PsuedoRemove,
                    @Parameter("The bot to remove") bot: IUser?): Boolean {
            if (!isSetup) throw CommandException("Bot management is not setup on this server!")

            val config = getConfig()!!

            val botConfig = config.bots!!.firstOrNull { it.bot == (bot?.id ?: context.content.split(" ")[1]) } ?: throw CommandException("${bot?.mention() ?: context.content.split(" ")[1]} has not been added to KotBot's internal database!")

            if (context.user.id != botConfig.owner && !isAdmin(config, context.user)) throw CommandException("Missing permissions to modify the bot properties of ${bot?.mention() ?: context.content.split(" ")[1]}")

            BotsConfiguration.BOTS_DAO.delete(botConfig)

            return true
        }

        @Executor
        fun execute(@Parameter("Makes KotBot request your bot's inclusion to this server") option: PsuedoAdd,
                    @Parameter("The prefix your bot uses") prefix: String,
                    @Parameter("The invite link for your bot") invite: String,
                    @Parameter("The (optional) justification for your bot's inclusion") justification: String?): String {
            if (!isSetup) throw CommandException("Bot management is not setup on this server!")

            val config = getConfig()!!

            val request = Request(nextId.getAndIncrement(), context.channel.guild, prefix, invite.replace(PERMISSIONS_REGEX, ""), justification, context.user, context.channel)

            pendingRequests.add(request)

            val channel = context.channel.guild.getChannelByID(config.channel)
            val conflicts = config.bots!!.distinct().filter { it.prefix == request.prefix }
            val message = createEmbedBuilder()
                    .withTitle("Bot Addition Request #${request.id}")
                    .withAuthorName("${context.user.getDisplayName(context.channel.guild)}#${context.user.discriminator}")
                    .withAuthorIcon(context.user.avatarURL)
                    .withDesc(buildString {
                        appendln("User ${context.user.mention()} requested their bot be added!")
                        appendln("[Invite Link](${request.invite})")
                        append("Prefix: ${request.prefix}")
                        if (conflicts.isEmpty())
                            appendln()
                        else
                            appendln(" ${Config.command_error_format.format("Conflicts with: ${conflicts.map { it.bot.asMention() }.joinToString(", ")}")}")
                        appendln("Justification: ${request.justification ?: "none"}")
                        appendln()
                        appendln("*To respond to this request use `${Config.command_prefix}bots accept ${request.id}` and then add the bot or `${Config.command_prefix}bots reject ${request.id} optional:justification`*")
                    })

            buffer { channel.sendMessage(message.build()) }

            return ":ok_hand: Bot addition request #${request.id} has been created (please be patient!)"
        }

        @Executor
        fun execute(@Parameter("Makes KotBot configure bot management on this server") option: PsuedoSetup,
                    @Parameter("The channel to report bot requests") channel: IChannel,
                    @Parameter("The role to automatically give bots") role: IRole?): Boolean {
            val config = getConfig()

            if (config != null) {
                config.channel = channel?.id
                config.role = role?.id
                BotManagerConfiguration.BOT_MANAGER_DAO.update(config)
            } else {
                BotManagerConfiguration(context.channel.guild.id, channel?.id, role?.id)
            }

            return true
        }

        @Executor
        fun execute(@Parameter("Makes KotBot accept a bot to the server") option: PsuedoAccept,
                    @Parameter("The request id to accept") id: Int): Boolean {
            if (!isSetup) throw CommandException("Bot management is not setup on this server!")

            val config = getConfig()!!

            if (!isAdmin(config, context.user)) throw CommandException("Missing permissions to respond to request $id")

            val request = pendingRequests.find { it.id == id } ?: throw CommandException("Request $id not found!")

            if (request.guild != context.channel.guild) throw CommandException("Request $id is not in this guild!")

            CLIENT.dispatcher.registerListener(object: IListener<UserJoinEvent> {
                override fun handle(event: UserJoinEvent) {
                    if (event.guild == request.guild && event.user.isBot) {
                        val botConfig = BotsConfiguration(event.user.id, request.owner.id, request.prefix)
                        config.bots!!.add(botConfig)
                        buffer { request.requestingChannel.sendMessage("${request.owner.mention()}, bot accepted! :thumbsup:") }
                        pendingRequests.remove(request)
                        CLIENT.dispatcher.unregisterListener(this)
                    }
                }
            })

            return true
        }

        @Executor
        fun execute(@Parameter("Makes KotBot reject a bot from the server") option: PsuedoReject,
                    @Parameter("The request id to reject") id: Int,
                    @Parameter("The (optional) justification") justification: String?): Boolean {
            if (!isSetup) throw CommandException("Bot management is not setup on this server!")

            val config = getConfig()!!

            if (!isAdmin(config, context.user)) throw CommandException("Missing permissions to respond to request $id")

            val request = pendingRequests.find { it.id == id } ?: throw CommandException("Request $id not found!")

            if (request.guild != context.channel.guild) throw CommandException("Request $id is not in this guild!")

            buffer { request.requestingChannel.sendMessage("${request.owner.mention()}, bot rejected! :thumbsdown:"+(if (justification != null) "\nReason: `$justification`" else "")) }

            pendingRequests.remove(request)

            return true
        }

        @Executor
        fun execute(@Parameter("Makes KotBot override an entry") option: PsuedoOverride,
                    @Parameter("The bot user") bot: IUser,
                    @Parameter("The bot's new prefix") prefix: String,
                    @Parameter("The bot's new owner") owner: IUser?): Boolean {
            if (!isSetup) throw CommandException("Bot management is not setup on this server!")

            val config = getConfig()!!
            val botConfig = config.bots!!.firstOrNull { it.bot == bot.id }

            if (botConfig == null) {
                if (owner == null) throw CommandException("Must provide an owner when adding a bot manually!")

                config.bots!!.add(BotsConfiguration(bot.id, owner.id, prefix))
            } else {
                botConfig.prefix = prefix
                if (owner != null)
                    botConfig.owner = owner.id
                BotsConfiguration.BOTS_DAO.update(botConfig)
            }

            return true
        }
    }
}