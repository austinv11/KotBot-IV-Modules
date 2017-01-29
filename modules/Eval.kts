
import com.austinv11.kotbot.core.CLIENT
import com.austinv11.kotbot.core.api.commands.Command
import com.austinv11.kotbot.core.api.commands.Executor
import com.austinv11.kotbot.core.api.commands.Parameter
import com.austinv11.kotbot.core.api.commands.PermissionLevel
import com.austinv11.kotbot.core.util.context
import com.austinv11.kotbot.core.util.createEmbedBuilder
import com.austinv11.kotbot.core.util.scanForModuleDependentObjects
import org.apache.commons.io.input.ReaderInputStream
import org.apache.commons.io.output.WriterOutputStream
import org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmDaemonLocalEvalScriptEngineFactory
import sx.blah.discord.api.IDiscordClient
import sx.blah.discord.api.events.IListener
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent
import sx.blah.discord.modules.IModule
import sx.blah.discord.util.EmbedBuilder
import java.io.*
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate
import javax.script.ScriptEngine
import javax.script.ScriptException

object: IModule {
    
    override fun enable(client: IDiscordClient): Boolean {
        scanForModuleDependentObjects()
        return true
    }

    override fun getName(): String = "KotBot-Eval"

    override fun getVersion(): String = "1.0"

    override fun getMinimumDiscord4JVersion(): String = "2.7.1-SNAPSHOT"

    override fun getAuthor(): String = "Austin"

    override fun disable() {}
    
    inner class EvalCommand : Command(description = "This command will evaluate expressions written in Kotlin",
            aliases = arrayOf("evaluate", "script", "kotlin"), requiredLevel = PermissionLevel.ADMINISTRATOR) {
        
        val defaultImports = arrayOf("kotlin", "kotlin.annotation", "kotlin.collections", "kotlin.comparisons", 
                "kotlin.concurrent", "kotlin.coroutines", "kotlin.io", "kotlin.jvm", "kotlin.jvm.functions", 
                "kotlin.properties", "kotlin.ranges", "kotlin.reflect", "kotlin.reflect.jvm", "kotlin.system", "kotlin.text", 
                "java.io", "java.lang", "java.lang.reflect", "java.math", "java.nio", "java.nio.file", "java.time", 
                "java.time.format", "java.util", "java.util.concurrent", "java.util.concurrent.atomic", "java.util.function", 
                "java.util.regex", "java.util.stream", "sx.blah.discord", "sx.blah.discord.util", "sx.blah.discord.util.audio", 
                "sx.blah.discord.util.audio.events", "sx.blah.discord.util.audio.processors", "sx.blah.discord.util.audio.providers",
                "sx.blah.discord.modules", "sx.blah.discord.handle.obj", "sx.blah.discord.handle.impl.events.user", 
                "sx.blah.discord.handle.impl.events.shard", "sx.blah.discord.handle.impl.events.module", 
                "sx.blah.discord.handle.impl.events.guild", "sx.blah.discord.handle.impl.events.guild.voice", 
                "sx.blah.discord.handle.impl.events.guild.voice.user", "sx.blah.discord.handle.impl.events.guild.role", 
                "sx.blah.discord.handle.impl.events.guild.member", "sx.blah.discord.handle.impl.events.guild.channel", 
                "sx.blah.discord.handle.impl.events.guild.channel.message", 
                "sx.blah.discord.handle.impl.events.guild.channel.message.reaction", 
                "sx.blah.discord.handle.impl.events.guild.channel.webhook", "sx.blah.discord.api", "sx.blah.discord.api.events", 
                "com.austinv11.kotbot.core", "com.austinv11.kotbot.core.util", "com.austinv11.kotbot.core.scripting", 
                "com.austinv11.kotbot.core.db", "com.austinv11.kotbot.core.config", "com.austinv11.kotbot.core.api", 
                "com.austinv11.kotbot.core.api.commands")
        val scriptFactory = KotlinJsr223JvmDaemonLocalEvalScriptEngineFactory() //Skipping the indirect invocation from java because the shadow jar breaks the javax service
        val engine: ScriptEngine
            get() = scriptFactory.scriptEngine
        
        @Executor
        fun execute(@Parameter("This is the script snippet written in Kotlin to execute") script: String): EmbedBuilder {
            context.channel.typingStatus = true
            val builder = createEmbedBuilder().withTitle("Kotlin Evaluation Results").withThumbnail("https://avatars2.githubusercontent.com/u/1446536?v=3&s=400") //Kotlin's icon
            val writer = StringWriter()
            val engine = engine
            engine.context.errorWriter = writer
            engine.context.writer = writer
            val channelID = context.channel.id
            val reader = object: Reader(), IListener<MessageReceivedEvent> {
                val buffer = AtomicReference<StringBuilder>(StringBuilder())
                
                init {
                    CLIENT.dispatcher.registerListener(this)
                }
                
                override fun handle(event: MessageReceivedEvent) {
                    if (event.channel.id == channelID)
                        buffer.get().appendln(event.message.content)
                }

                override fun close() {
                    CLIENT.dispatcher.unregisterListener(this)
                }

                override fun read(cbuf: CharArray, off: Int, len: Int): Int {
                    if (cbuf.size+off < len)
                        throw IOException("Buffer length is less than read length!")
                    
                    var off = off
                    var counter = 0
                    val copy = buffer.getAndSet(StringBuilder()).toString()
                    for (i in 0..len) {
                        if (copy.length < i+1) {
                            if (buffer.get().isEmpty()) { //Builder needs more input, we should wait for it
                                CLIENT.dispatcher.waitFor(Predicate<MessageReceivedEvent> { it.message.channel.id == channelID })
                            }

                            val read = read(cbuf, off+i, len-(i+1))
                            counter += read
                            if (counter >= len)
                                break
                            else
                                off += read
                        } else {
                            cbuf[i+off] = copy[i]
                        }
                    }
                    
                    return counter
                }

                override fun ready(): Boolean {
                    return true
                }
            }
            engine.context.reader = reader
            val ris = ReaderInputStream(reader, Charset.defaultCharset())
            val wos = PrintStream(WriterOutputStream(writer, Charset.defaultCharset()))
            engine.put("_IN", ris)
            engine.put("_OUT", wos)
            engine.put("_CONTEXT", context)
            try {
                val result = engine.eval(buildString {
                    defaultImports.forEach { append("import $it.*;") }
                    appendln("\n")
                    append("System.setIn(bindings.get(\"_IN\") as InputStream);")
                    append("System.setOut(bindings.get(\"_OUT\") as PrintStream);")
                    append("System.setErr(bindings.get(\"_OUT\") as PrintStream)\n")
                    append("val context = bindings.get(\"_CONTEXT\") as CommandContext\n")
                    append(script.removeSurrounding("```").removePrefix("kotlin").removeSurrounding("`").trim())
                })
                builder.appendField("Output", "```\n$result```", false)
            } catch (e: ScriptException) {
                val stacktraceWriter = StringWriter()
                e.printStackTrace(PrintWriter(stacktraceWriter))
                val stacktraceLines = stacktraceWriter.toString().lines()
                val stacktrace = stacktraceLines.subList(0, Math.min(10, stacktraceLines.size)).joinToString("\n")
                stacktraceWriter.close()
                builder.appendField("Thrown Exception", "```\n$stacktrace```", false)
            } finally {
                wos.close()
                ris.close()
                val log = writer.toString()
                builder.appendField("Log", "```\n$log```", false)
                context.channel.typingStatus = false
            }
            return builder
        }
    }
}
