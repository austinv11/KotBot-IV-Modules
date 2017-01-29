
import com.austinv11.kotbot.core.*
import com.austinv11.kotbot.core.api.commands.*
import com.austinv11.kotbot.core.config.Config
import com.austinv11.kotbot.core.util.context
import com.austinv11.kotbot.core.util.createEmbedBuilder
import com.austinv11.kotbot.core.util.scanForModuleDependentObjects
import sx.blah.discord.Discord4J
import sx.blah.discord.api.IDiscordClient
import sx.blah.discord.modules.IModule
import sx.blah.discord.util.BotInviteBuilder
import sx.blah.discord.util.EmbedBuilder
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.jvmErasure

enum class ModuleActions {
    ENABLE, DISABLE
}

fun String.removePrefix(prefix: String, predicate: String.() -> Boolean): String {
    return if (predicate.invoke(this)) this.removePrefix(prefix) else this
}

object: IModule {
    
    override fun enable(client: IDiscordClient): Boolean {
        scanForModuleDependentObjects()
        return true
    }

    override fun getName(): String = "KotBot-Core"

    override fun getVersion(): String = "1.0"

    override fun getMinimumDiscord4JVersion(): String = "2.7.1-SNAPSHOT"

    override fun getAuthor(): String = "Austin"

    override fun disable() {
       restart() //Uh, this should never happen, otherwise things are borked
    }
    
    inner class HelpCommand() : Command("This command helps users use KotBot", arrayOf("?", "h")) {
        
        @Executor
        fun execute(): EmbedBuilder {
            val builder = createEmbedBuilder().withTitle("__Commands List__")
            val commandMap = mutableMapOf<IModule, MutableSet<Command>>()
            CLIENT.moduleObjectCleaner.registry.forEach { module, objects -> 
                objects.filter { it is Command }.forEach { 
                    if (!commandMap.containsKey(module))
                        commandMap[module] = mutableSetOf()
                    
                    commandMap[module]!!.add(it as Command)
                }
            }
            commandMap.forEach { module, commands -> 
                builder.appendField(module.name, buildString { 
                    commands.forEach { appendln(it.name) }
                }.trim(), true)
            }
            return builder
        }
        
        @Executor
        fun execute(@Parameter("This represents a specific command or module") target: String): EmbedBuilder {
            val builder = createEmbedBuilder().withTitle("__Help Results for `$target`__")
            val commandResults = CommandRegistry.commands.filter { it.doesCommandMatch(target) }
            if (commandResults.isNotEmpty()) {
                commandResults.first().let { 
                    
                    builder.appendField("${it.name} Command Help", buildString { 
                        appendln("__Aliases:__ [${it.aliases.joinToString()}]")
                        appendln("__Description:__ ${it.description}")
                        appendln("__Required Permission Level:__ ${it.requiredLevel}")}, false)
                    it.javaClass.kotlin.functions
                        .filter {
                            try {
                                return@filter it.findAnnotation<Executor>() != null
                            } catch (e: Exception) { //Hack for KT-15540
                                return@filter false
                            }
                        }
                        .sortedWith(Comparator<KFunction<*>> { o1, o2 -> o1.valueParameters.size.compareTo(o2.valueParameters.size) })
                        .forEachIndexed { i, function ->
                            builder.appendField("Usage ${i+1}:", buildString {
                                appendln("${Config.command_prefix}${it.name}")
                                if (function.valueParameters.isEmpty()) {
                                    appendln("No arguments")
                                } else {
                                    function.valueParameters.forEachIndexed { i, param ->
                                        val annotation = param.findAnnotation<Parameter>()!!
                                        append("${i+1}. ")
                                        if (param.isOptional)
                                            append("Optional: ")
                                        append("*${if (!annotation.nameOverride.isNullOrEmpty()) annotation.nameOverride else param.name}*")
                                        val paramClass = param.type.jvmErasure
                                        append(": ${if (paramClass.java.isEnum) paramClass.java.enumConstants.map { it.toString().toLowerCase() }.joinToString() else paramClass.simpleName!!.toLowerCase().removePrefix("i", { this != "int"})}")
                                        appendln(" - ${annotation.description}")
                                    }
                                }
                            }.trim(), true)
                        }
                }
            } else {
                val moduleResults = CLIENT.moduleLoader.loadedModules.filter { it.name.equals(target, true) }
                if (moduleResults.isNotEmpty()) {
                    moduleResults.forEach { 
                        builder.appendField("$target Module Commands", buildString {
                            CLIENT.moduleObjectCleaner.registry.forEach { module, objects ->
                                objects.filter { it is Command }.forEach {
                                    appendln((it as Command).name)
                                }
                            } 
                        }.trim(), true)
                    }
                } else {
                    builder.withDescription("No results found for `$target`.\n[Perhaps Google has what you're looking for](https://www.google.com/search?q=$target)")
                }
            }
            return builder
        }
    }
    
    inner class InfoCommand() : Command(description = "This command displays various information about the bot", 
            aliases = arrayOf("about")) {
        
        private val dateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss")
        
        @Executor
        fun execute(): EmbedBuilder {
            return createEmbedBuilder()
                    .withTitle("__About KotBot__")
                    .withThumbnail("https://avatars2.githubusercontent.com/u/1446536?v=3&s=400") //Kotlin's icon
                    .withDescription(buildString { 
                        appendln("KotBot is completely modular a bot written in Kotlin and built on the Discord4J API")
                        appendln("[Invite Link](${BotInviteBuilder(CLIENT).build()})\t[Github Repo](https://github.com/austinv11/KotBot-IV)")
                    })
                    .appendField("Discord4J Version", Discord4J.VERSION, true)
                    .appendField("JVM Version", System.getProperty("java.version"), true)
                    .appendField("Kotlin Version", let { 
                        val properties = Properties()
                        properties.load(Thread.currentThread().contextClassLoader.getResourceAsStream("kotlinManifest.properties"))
                        return@let properties.getProperty("manifest.impl.value.kotlin.version")
                    }, true)
                    .appendField("Prefix", "${Config.command_prefix} or @${CLIENT.ourUser.getDisplayName(context.channel.guild)}#${CLIENT.ourUser.discriminator}", true)
                    .appendField("Server Count", "${CLIENT.guilds.size} servers", true)
                    .appendField("Instance Launch Time", Discord4J.getLaunchTime().format(dateFormat), true)
                    .appendField("Used Memory", "%.2f/%.2f MB".format((Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) / (Math.pow(1024.0, 2.0)), Runtime.getRuntime().totalMemory() / (Math.pow(1024.0, 2.0))), true)
        }
    }
    
    inner class ShutdownCommand() : Command(description = "This kills the bot", 
            aliases = arrayOf("kill"), requiredLevel = PermissionLevel.OWNER) {
        
        @Executor
        fun execute() {
            try {
                context.channel.sendMessage("Goodbye!") //We want to immediately shutdown so we won't bother to buffer this
            } catch (e: Exception) {}
            
            shutdown()
        }
    }
    
    inner class RestartCommand() : Command(description = "This restarts the bot",
            aliases = arrayOf("reboot"), requiredLevel = PermissionLevel.OWNER) {
        
        @Executor
        fun execute() {
            try {
                context.channel.sendMessage("Goodbye!") //We want to immediately shutdown so we won't bother to buffer this
            } catch (e: Exception) {}

            restart()
        }
    }

    inner class UpdateCommand() : Command(description = "This manually updates the bot",
            aliases = arrayOf("upgrade"), requiredLevel = PermissionLevel.OWNER) {

        @Executor
        fun execute() {
            try {
                context.channel.sendMessage("Goodbye!") //We want to immediately shutdown so we won't bother to buffer this
            } catch (e: Exception) {}

            update()
        }
    }
    
    inner class ModuleCommand() : Command(description = "This configures modules used by this bot",
            aliases = arrayOf("modules", "mod", "mods"), requiredLevel = PermissionLevel.OWNER) {

        @Executor
        fun execute(): EmbedBuilder {
            return createEmbedBuilder()
                    .withTitle("__Modules__")
                    .appendField("Loaded", buildString {
                        CLIENT.scriptManager.modules.values.forEach { appendln(it.name) }
                    }, true)
                    .appendField("File", buildString {
                        CLIENT.scriptManager.modules.keys.forEach { appendln(it.name) }
                    }, true)
        }

        @Executor
        fun execute(@Parameter("The module to get info about", nameOverride = "module") moduleName: String): EmbedBuilder {
            val module = CLIENT.scriptManager.modules.values.find { it.name.equals(moduleName, true) }
            if (module == null) {
                throw CommandException("Cannot find module `$moduleName`")
            } else {
                return createEmbedBuilder().withTitle("__$moduleName Information__")
                        .appendField("Name", module.name, true)
                        .appendField("Version", module.version, true)
                        .appendField("Author", module.author, true)
                        .appendField("File", CLIENT.scriptManager.modules.filter { it.value == module }.keys.first().name, true)
                        .appendField("Registered Objects", CLIENT.moduleObjectCleaner.registry[module]!!.size.toString(), true)
            }
        }

        @Executor
        fun execute(@Parameter("The action to do to a module") action: ModuleActions, 
                    @Parameter("The module to act upon") module: String): Boolean {
            val moduleObj = CLIENT.scriptManager.modules.values.find { it.name.equals(module, true) }
            
            if (action == ModuleActions.ENABLE) {
                if (moduleObj == null) {
                    try {
                        CLIENT.scriptManager.loadModule(File(CLIENT.scriptManager.modulesPath.toFile(), module))
                        return true
                    } catch (e: Throwable) {
                        return false
                    }
                } else { //Module already exists, so we should reload it
                    try {
                        val file = CLIENT.scriptManager.modules.filter { it.value == moduleObj }.keys.first()
                        CLIENT.scriptManager.disableModule(file)
                        CLIENT.scriptManager.loadModule(file)
                        return true
                    } catch (e: Throwable) {
                        return false
                    }
                }
            } else { //Disable
                if (moduleObj == null) {
                    throw CommandException("Module `$module` is already unloaded!")
                } else {
                    CLIENT.scriptManager.disableModule(CLIENT.scriptManager.modules.filter { it.value == moduleObj }.keys.first())
                    return true
                }
            }
        }
    }
}
