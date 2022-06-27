package me.akka.obj

/**
 * represent a symbol in Lua, like constant(1), name(abc) and their combination(abc.def)
 */
class LuaRef : LuaObj {
    var refName: String

    constructor(refName: String) {
        this.refName = refName
    }

    constructor() {
        refName = "nil"
    }

    override fun toCode(): String {
        return refName
    }

    override fun clone(): LuaObj {
        return LuaRef(refName)
    }
}