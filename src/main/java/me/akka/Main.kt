package me.akka

import org.luaj.vm2.parser.LuaParser
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import kotlin.io.path.isDirectory
import kotlin.io.path.name

val logger = Logger.getGlobal()
var success = AtomicInteger(0)
var failed = AtomicInteger(0)

fun String.execute() : Process = Runtime.getRuntime().exec(this)

fun main(){

    val time = System.currentTimeMillis()

    // deobfuscator
    //handleFolder(Path.of("input"), Path.of("output"))

    // test file can compile again and delete wrong script
    //handleFolder2(Path.of("???"), ::testFileCanCompile)

    // use lua format to format script
    //handleFolder2(Path.of("???"), ::luaFormat)

    println("Success $success")
    println("Failed $failed")
    val cost = System.currentTimeMillis() - time
    println("Cost $cost")
}

fun luaFormat(file : Path){
    val process = "lua-format -i $file".execute()
    process.waitFor()
    process.inputReader().lines().forEach { println(it) }
}

fun handleFolder(path : Path, output: Path){
    Files.list(path).parallel().forEach { f ->
            if(f.isDirectory()){
                val childOutput = Path.of(output.toString(), path.relativize(f).toString())
                Files.createDirectories(childOutput)
                handleFolder(f, childOutput)
                return@forEach
            }
            try {
                handleFile(f, output)
                success.incrementAndGet()
            }catch (e : Throwable){
                logger.warning("Parse Error " + f.fileName)
                e.printStackTrace()
                failed.incrementAndGet()
            }
        }
}

fun handleFile(file: Path, folder : Path){
    val text = Files.newInputStream(file)

    val parser = LuaParser(text)
    val chunk = parser.Chunk()

    val p = LuaBlockParser(LuaBlockContext(LinkedHashMap(), HashSet(), true))
    p.handleMain(chunk)

    val path = Path.of(folder.toString(), file.fileName.toString())
    Files.writeString(path, p.toScriptText())

    println("${file.fileName} Write to $path")
}

fun handleFolder2(path : Path, handler: (Path) -> Unit){
    Files.list(path).parallel().forEach { f ->
        if(f.isDirectory()){
            handleFolder2(f, handler)
            return@forEach
        }
        if(!f.name.contains(".lua")){
            return@forEach
        }
        handler(f)
    }
}


fun testFileCanCompile(file: Path){
    try {
        val text = Files.newInputStream(file)

        val parser = LuaParser(text)
        parser.Chunk()

    }catch (e : Throwable){
        logger.warning("Parse Error " + file.name)
        e.printStackTrace()

        Files.delete(file)

        println("${file.name} Delete.")
    }
}