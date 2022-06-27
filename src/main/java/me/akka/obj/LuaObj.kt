package me.akka.obj

abstract class LuaObj : Cloneable {

    abstract fun toCode(): String

    public abstract override fun clone(): LuaObj

    override fun toString(): String {
        return toCode()
    }
}