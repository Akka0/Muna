package me.akka

import org.luaj.vm2.*

object OpUtils {
    
    /**
     * op To Code
     */
    fun opToCode(op: Int): String {
        return when (op) {
            // binop
            Lua.OP_ADD -> "+"
            Lua.OP_SUB -> "-"
            Lua.OP_MUL -> "*"
            Lua.OP_DIV -> "/"
            Lua.OP_POW -> "^"
            Lua.OP_MOD -> "%"
            Lua.OP_CONCAT -> ".."
            Lua.OP_LT -> "<"
            Lua.OP_LE -> "<="
            Lua.OP_GT -> ">"
            Lua.OP_GE -> ">="
            Lua.OP_EQ -> "=="
            Lua.OP_NEQ -> "~="
            Lua.OP_AND -> "and"
            Lua.OP_OR -> "or"
            // unop
            Lua.OP_UNM -> "-"
            Lua.OP_NOT -> "not"
            Lua.OP_LEN -> "#"
            else -> throw RuntimeException("Unsupported OP")
        }
    }

    fun constantToCode(value : LuaValue) : String {
        return when (value) {
            // true
            is LuaBoolean -> value.v.toString()
            // 1
            is LuaInteger -> value.v.toString()
            // 1.1
            is LuaDouble -> value.todouble().toString()
            // "a"
            is LuaString -> "\"${escapeString(value.tojstring())}\""
            // nil
            is LuaNil -> "nil"
            else -> throw RuntimeException("Unsupported Constant Type")
        }
    }

    fun cleanString(exp: String): String = if (exp.first() == '\"') exp.substring(1, exp.length-1) else exp

    // escape if it has "
    private fun escapeString(str: String): String = str.replace("\"", "\\\"")

}