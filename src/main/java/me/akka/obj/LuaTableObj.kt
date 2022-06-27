package me.akka.obj

import java.util.*
import kotlin.collections.LinkedHashMap

/**
 * represent a table of Lua
 */
class LuaTableObj : LuaObj {
    var table = LinkedHashMap<String, LuaObj>()

    constructor()

    constructor(table: Map<String, LuaObj>) {
        table.forEach { (k,v) -> this.table[k] = v.clone() }
    }

    operator fun set(name: String, value: LuaObj) {
        table[name] = value
    }

    operator fun get(key: String?): LuaObj? {
        return table[key]
    }

    override fun clone(): LuaObj {
        return LuaTableObj(table)
    }

    override fun toCode(): String {
        val sj = StringJoiner(", ")
        // check if table
        if (table["1"] == null) {
            table.forEach { (k,v) -> sj.add("${handleKey(k)} = ${v.toCode()}") }
        } else {
            // handle array BY ORDER
            for(i in 1 .. table.size){
                sj.add(table[i.toString()]?.toCode())
            }
        }
        return "{ $sj }"
    }

    private fun handleKey(key: String): String{
        // number [1] = ...
        if(key.all { c -> Character.isDigit(c) }){
            return "[$key]"
        }
        // string ["1"] = ...
        if(key.first() == '\"'){
            return "[$key]"
        }
        // other
        return key
    }

}