
import com.austinv11.kotbot.core.CLIENT
import com.austinv11.kotbot.core.LOGGER
import com.austinv11.kotbot.core.api.commands.*
import com.austinv11.kotbot.core.db.DatabaseManager
import com.austinv11.kotbot.core.update
import com.austinv11.kotbot.core.util.ModuleDependentObject
import com.austinv11.kotbot.core.util.buffer
import com.austinv11.kotbot.core.util.createEmbedBuilder
import com.austinv11.kotbot.core.util.scanForModuleDependentObjects
import com.darichey.github.Webhooks
import com.darichey.github.event.*
import com.github.kittinunf.fuel.httpDownload
import com.j256.ormlite.dao.Dao
import com.j256.ormlite.dao.DaoManager
import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable
import com.j256.ormlite.table.TableUtils
import org.wasabifx.wasabi.app.AppConfiguration
import sx.blah.discord.api.IDiscordClient
import sx.blah.discord.api.internal.json.objects.EmbedObject
import sx.blah.discord.handle.obj.IChannel
import sx.blah.discord.modules.IModule
import sx.blah.discord.util.EmbedBuilder
import java.awt.Color
import java.io.File

enum class GithubCommandActions {
    ADD, REMOVE
}

@DatabaseTable(tableName = "github")
class WebhookConfiguration {

    companion object {
        lateinit var WEBHOOK_DAO: Dao<WebhookConfiguration, Int>
        
        const val ID = "id"
        const val REPO = "repo"
        const val EVENTS = "events"
        const val CHANNEL = "channel"
    }
    
    constructor()

    constructor(repo: String, events: String, channel: String) {
        this.repo = repo
        this.events = events
        this.channel = channel

        WEBHOOK_DAO.create(this)
    }
    
    @DatabaseField(columnName = ID, generatedId = true)
    var id: Int = 0

    @DatabaseField(columnName = REPO, canBeNull = false)
    var repo: String = ""

    @DatabaseField(columnName = EVENTS, canBeNull = false)
    var events: String = ""

    @DatabaseField(columnName = CHANNEL, canBeNull = false)
    var channel: String = ""
}

object: IModule {
    
    override fun enable(client: IDiscordClient): Boolean {
        TableUtils.createTableIfNotExists(DatabaseManager.CONNECTION_SOURCE, WebhookConfiguration::class.java)
        WebhookConfiguration.WEBHOOK_DAO = DaoManager.createDao(DatabaseManager.CONNECTION_SOURCE, WebhookConfiguration::class.java)
        scanForModuleDependentObjects()
        return true
    }

    override fun getName(): String = "KotBot-Github"

    override fun getVersion(): String = "1.0"

    override fun getMinimumDiscord4JVersion(): String = "2.7.1-SNAPSHOT"

    override fun getAuthor(): String = "Austin"

    override fun disable() {}
    
    inner class GithubWebhookServer : ModuleDependentObject {
        
        lateinit var webhooks: Webhooks
        
        val KOTBOT_REPO = "https://github.com/austinv11/KotBot-IV"
        val MODULE_REPO = "https://github.com/austinv11/KotBot-IV-Modules"
        val MODULE_DOWNLOAD_REPO = "https://raw.githubusercontent.com/austinv11/KotBot-IV-Modules/master/"
        
        override fun clean() {
            webhooks.stop()
        }

        fun GithubEvent.createEmbed(): EmbedObject? {
            val builder = createEmbedBuilder()
                    .withAuthorName(this.sender.login)
                    .withAuthorIcon(this.sender.avatar_url)
                    .withAuthorUrl(this.sender.html_url)
            when (this) {
                is CommitCommentEvent -> {
                    when (this.action) {
                        "created" -> {
                            builder.withTitle("New Comment on `${this.comment.commit_id.substring(0, 7)}`")
                                    .withUrl(this.comment.html_url)
                                    .withDescription(this.comment.body)
                                    .withColor(Color.GREEN)
                        }
                        "edited" -> {
                            builder.withTitle("Modified Comment on `${this.comment.commit_id.substring(0, 7)}`")
                                    .withUrl(this.comment.html_url)
                                    .withDescription(this.comment.body)
                                    .withColor(Color.ORANGE)
                        }
                        "deleted" -> {
                            builder.withTitle("Deleted Comment on `${this.comment.commit_id.substring(0, 7)}`")
                                    .withUrl(this.comment.html_url)
                                    .withDescription(this.comment.body)
                                    .withColor(Color.RED)
                        }
                        else -> {
                            return null
                        }
                    }
                }
                is IssueCommentEvent -> {
                    when (this.action) {
                        "created" -> {
                            builder.withTitle("[New Comment on #${this.issue.id} \"${this.issue.title}\"")
                                    .withUrl(this.comment.html_url)
                                    .withDescription(this.comment.body)
                                    .withColor(Color.GREEN)
                        }
                        "edited" -> {
                            builder.withTitle("Modified Comment on #${this.issue.id} \"${this.issue.title}\"")
                                    .withUrl(this.comment.html_url)
                                    .withDescription(this.comment.body)
                                    .withColor(Color.ORANGE)
                        }
                        "deleted" -> {
                            builder.withTitle("Deleted Comment on #${this.issue.id} \"${this.issue.title}\"")
                                    .withUrl(this.comment.html_url)
                                    .withDescription(this.comment.body)
                                    .withColor(Color.RED)
                        }
                        else -> {
                            return null
                        }
                    }
                }
                is IssuesEvent -> {
                    when (this.action) {
                        "assigned" -> {return null}   
                        "unassigned" -> {return null}
                        "labeled" -> {return null}
                        "unlabeled" -> {return null}
                        "opened" -> {
                            builder.withTitle("Issue #${this.issue.id} \"${this.issue.title}\" Opened")
                                    .withUrl(this.issue.html_url)
                                    .withDescription(this.issue.body)
                                    .withColor(Color.GREEN)
                        }
                        "edited" -> {
                            builder.withTitle("Issue #${this.issue.id} \"${this.issue.title}\" Modified")
                                    .withUrl(this.issue.html_url)
                                    .withDescription(this.issue.body)
                                    .withColor(Color.ORANGE)
                        }
                        "milestoned" -> {return null}
                        "demilestoned" -> {return null}
                        "closed" -> {
                            builder.withTitle("Issue #${this.issue.id} \"${this.issue.title}\" Closed")
                                    .withUrl(this.issue.html_url)
                                    .withDescription(this.issue.body)
                                    .withColor(Color.RED)
                        }
                        "reopened" -> {
                            builder.withTitle("Issue #${this.issue.id} \"${this.issue.title}\" Reopened")
                                    .withUrl(this.issue.html_url)
                                    .withDescription(this.issue.body)
                                    .withColor(Color.GREEN)
                        }
                        else -> {
                            return null
                        }
                    } 
                }
                is PullRequestReviewEvent -> {
                    when (this.review.state) {
                        "approved" -> {
                            builder.withTitle("Pull Request #${this.pull_request.id} \"${this.pull_request.title}\" Approved")
                                    .withUrl(this.review.html_url)
                                    .withDescription(this.review.body)
                                    .withColor(Color.GREEN)
                        }
                        "changes_requested" -> {
                            builder.withTitle("Changes Requested on Pull Request #${this.pull_request.id} \"${this.pull_request.title}\"")
                                    .withUrl(this.review.html_url)
                                    .withDescription(this.review.body)
                                    .withColor(Color.RED)
                        }
                        "commented" -> {
                            builder.withTitle("Comment on Pull Request #${this.pull_request.id} \"${this.pull_request.title}\"")
                                    .withUrl(this.review.html_url)
                                    .withDescription(this.review.body)
                                    .withColor(Color.ORANGE)
                        }
                        else -> {
                            return null
                        }
                    }
                }
                is PullRequestEvent -> {
                    when (this.action) {
                        "opened" -> {
                            builder.withTitle("Pull Request #${this.number} \"${this.pull_request.title}\" Opened")
                                    .withUrl(this.pull_request.html_url)
                                    .withDescription("+${this.pull_request.additions}\t-${this.pull_request.deletions}\t${this.pull_request.changed_files} changed files\n"+this.pull_request.body)
                                    .withColor(Color.GREEN)
                        }
                        "edited" -> {
                            builder.withTitle("Pull Request #${this.number} \"${this.pull_request.title}\" Modified")
                                    .withUrl(this.pull_request.html_url)
                                    .withDescription("+${this.pull_request.additions}\t-${this.pull_request.deletions}\t${this.pull_request.changed_files} changed files\n"+this.pull_request.body)
                                    .withColor(Color.ORANGE)
                        }
                        "closed" -> {
                            if (this.pull_request.merged)
                                builder.withTitle("Pull Request #${this.number} \"${this.pull_request.title}\" Merged")
                                        .withUrl(this.pull_request.html_url)
                                        .withDescription("+${this.pull_request.additions}\t-${this.pull_request.deletions}\t${this.pull_request.changed_files} changed files\n"+this.pull_request.body)
                                        .withColor(Color.GREEN)
                            else
                                builder.withTitle("Pull Request #${this.number} \"${this.pull_request.title}\" Closed")
                                        .withUrl(this.pull_request.html_url)
                                        .withDescription("+${this.pull_request.additions}\t-${this.pull_request.deletions}\t${this.pull_request.changed_files} changed files\n"+this.pull_request.body)
                                        .withColor(Color.RED)
                        }
                        "reopened" -> {
                            builder.withTitle("Pull Request #${this.number} \"${this.pull_request.title}\" Reopened")
                                    .withUrl(this.pull_request.html_url)
                                    .withDescription("+${this.pull_request.additions}\t-${this.pull_request.deletions}\t${this.pull_request.changed_files} changed files\n"+this.pull_request.body)
                                    .withColor(Color.GREEN)
                        }
                        else -> {
                            return null
                        }
                    }
                }
                is PushEvent -> {
                    builder.withTitle("Commits to ${this.ref.removePrefix("refs/heads/")}")
                            .withDescription(buildString {
                                this@createEmbed.commits.forEach {
                                    append("[`${it.id.substring(0, 7)}` ") 
                                    append("${it.message.lines()[0]}](${it.url}) ")
                                    appendln("[[${it.author.name}](https://github.com/${it.author.username})]")
                                }
                            })
                            .withColor(Color.GREEN)
                }
                is ReleaseEvent -> {
                    builder.withTitle("Release ${this.release.name}")
                            .withUrl(this.release.html_url)
                            .withDescription(this.release.body)
                            .withColor(Color.GREEN)
                }
                else -> {
                    builder.withTitle(this::class.simpleName)
                            .withDescription("This event doesn't have built-in support yet")
                }
            }
            return builder.appendDescription("\n\n[${this.repository.full_name}](${this.repository.html_url})").build()
        }
        
        override fun inject() {
            webhooks = Webhooks("/github", AppConfiguration(port = 4000))
            webhooks.events.on<GithubEvent> { 
                if (this.repository.html_url.contains(KOTBOT_REPO, true) || this.repository.html_url.contains(MODULE_REPO, true)) {
                    if (this.repository.html_url.contains(KOTBOT_REPO, true) && this is PushEvent) { //Update bot
                        LOGGER.info("Updating...")
                        update()
                    } else if (this.repository.html_url.contains(MODULE_REPO, true) && this is PushEvent) { //Update modules
                        this.commits.forEach { 
                            val predicate: (String) -> Boolean = { it.startsWith("modules") }
                            it.added.filter(predicate).forEach { 
                                (MODULE_DOWNLOAD_REPO+it).httpDownload()
                                        .destination { response, url -> return@destination File("./$it") }
                                        .responseString { request, response, result ->  }
                            }
                            it.modified.filter(predicate).forEach {
                                File("./$it").delete()
                                (MODULE_DOWNLOAD_REPO+it).httpDownload()
                                        .destination { response, url -> return@destination File("./$it") }
                                        .responseString { request, response, result ->  }
                            }
                            it.removed.filter(predicate).forEach {
                                File("./$it").delete()
                            }
                        }
                    }
                } else { //Post repo info
                    val configurations = WebhookConfiguration.WEBHOOK_DAO.queryForFieldValuesArgs(mapOf(WebhookConfiguration.REPO to this.repository.full_name.toLowerCase()))
                    configurations.forEach { 
                        if (it.events.split(";").contains(this::class.simpleName!!.toLowerCase().removeSuffix("event"))) {
                            buffer { CLIENT.getChannelByID(it.channel)?.sendMessage(this.createEmbed()) }
                        }
                    }
                }
            }
            webhooks.start(false)
        }
    }
    
    inner class GithubCommand : Command("This command configures how this bot handles Github webhooks",
            aliases = arrayOf("git", "webhooks", "webhook"), requiredLevel = PermissionLevel.ADMINISTRATOR) {
        
        val validEvents = "commit_comment;issue_comment;issues;pull_request_review;pull_request;push;release"
        
        @Executor
        fun execute(): EmbedBuilder {
            val embed = createEmbedBuilder()
                    .withColor(Color.ORANGE)
                    .withTitle("Configured Webhooks")
            WebhookConfiguration.WEBHOOK_DAO.queryForAll().forEach { 
                embed.appendField(it.repo, buildString { 
                    appendln("In channel: ${CLIENT.getChannelByID(it.channel)!!.mention()}")
                    append("Received Events: ${it.events.split(";").joinToString(", ")}")
                }, true)
            }
            return embed
        }
        
        @Executor
        fun execute(@Parameter("This is the action to perform this bot's Github webhooks") action: GithubCommandActions,
                    @Parameter("This is the repo to configure") repo: String,
                    @Parameter("This is used to determine which channel a webhook config is added to") channel: IChannel,
                    @Parameter("This is the event(s) to configure (use `all` to listen to all events)") events: String? = null): EmbedBuilder {
            
            val embed = createEmbedBuilder()
            val repo = repo.removeSurrounding("/").toLowerCase()
            val modifiedEvents = (if (events == null || events.equals("all", true)) validEvents else events)
            val events = modifiedEvents.replace("_", "").toLowerCase()
            val configs = WebhookConfiguration.WEBHOOK_DAO.queryForFieldValuesArgs(mapOf(WebhookConfiguration.REPO to repo, 
                    WebhookConfiguration.CHANNEL to channel.id))
            if (action == GithubCommandActions.ADD) { // If statements because when breaks the compiler for some reason
                embed.withColor(Color.GREEN)
                        .withTitle("Successfully configured webhooks for ${channel.mention()}")
                        .withDescription("Added hooks for events: `${modifiedEvents.split(";").joinToString(", ")}`")
                if (configs.isEmpty()) {
                    WebhookConfiguration(repo, events, channel.id)
                } else {
                    val config = configs.first()
                    config.events = (config.events.split(";") + events.split(";")).distinct().joinToString(";")
                    WebhookConfiguration.WEBHOOK_DAO.update(config)
                }
            } else if (action == GithubCommandActions.REMOVE) {
                if (configs.isEmpty())
                    throw CommandException("No webhooks configured for the provided arguments!")

                embed.withColor(Color.RED)
                        .withTitle("Successfully configured webhooks for ${channel.mention()}")
                        .withDescription("Removed hooks for events: `${modifiedEvents.split(";").joinToString(", ")}`")

                val config = configs.first()
                val remaining = config.events.split(";") - events.split(";")
                if (remaining.isEmpty()) {
                    WebhookConfiguration.WEBHOOK_DAO.delete(config)
                } else {
                    config.events = remaining.joinToString(";")
                    WebhookConfiguration.WEBHOOK_DAO.update(config)
                }
            }
            return embed
        }
    }
}
