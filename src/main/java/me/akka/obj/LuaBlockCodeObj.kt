package me.akka.obj

import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.IntStream

/**
 * represent the lua block's code, such as a function
 */
class LuaBlockCodeObj : LuaObj, Iterable<String> {
    private var lines = LinkedList<String>()
    private var args = LinkedList<String>()

    var funcName = "unnamed"

    constructor()

    constructor(lines: List<String>, args: List<String>) {
        this.lines.addAll(lines)
        this.args.addAll(args)
    }

    override fun toCode(): String {
        return "<Error>"
    }

    override fun iterator(): MutableIterator<String> {
        return lines.iterator()
    }

    override fun spliterator(): Spliterator<String> {
        return lines.spliterator()
    }

    override fun clone(): LuaObj {
        return LuaBlockCodeObj(lines, args)
    }

    fun addArgs(arg: String) {
        args.add(arg)
    }

    fun addLine(code: String, level: Int) {
        val indentation = IntStream.range(0, level).mapToObj { "  " }.collect(Collectors.joining())
        lines.add(indentation + code)
    }

    fun addTable(luaTableObj: LuaTableObj, name: String, level: Int) {
        addLine(name + " = " + luaTableObj.toCode(), level)
    }

    fun addFunc(body: LuaBlockCodeObj, realName: String = body.funcName, level: Int) {
        val argJoiner = StringJoiner(", ")
        body.args.forEach(Consumer { newElement -> argJoiner.add(newElement) })

        addLine("function $realName($argJoiner)", level)
        for (line in body) {
            addLine(line, level + 1)
        }
        addLine("end", level)
    }

}