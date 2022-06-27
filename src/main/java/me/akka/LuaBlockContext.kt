package me.akka

import me.akka.obj.LuaBlockCodeObj
import me.akka.obj.LuaObj
import me.akka.obj.LuaRef
import me.akka.obj.LuaTableObj
import java.util.HashSet
import java.util.LinkedHashMap

class LuaBlockContext(
    // reference the global scope context
    val globalVarMap : LinkedHashMap<String, LuaObj>,
    val globalVarSet : HashSet<String>,
    val isGlobal : Boolean
                      ){
    // local scope
    var localVarMap = LinkedHashMap<String, LuaObj>()
    // added if it is a local var
    var localVarSet = HashSet<String>()

    private var varNum = 0
    private var funcNum = 0

    fun addLocalVar(name: String) {
        localVarMap[name] = LuaRef()
        localVarSet.add(name)
    }
    /**
     * create a temp var
     */
    fun nextVar(): String {
        val name = "var" + ++varNum
        localVarSet.add(name)
        return name
    }

    /**
     * create a func and give it internal name
     */
    fun nextFunc(func: LuaBlockCodeObj, defName: String): String {
        val name = "func" + ++funcNum
        func.funcName = name
        this[name] = func
        this[defName] = LuaRef(name)
        return name
    }

    /**
     * get the global scope binding items
     */
    fun getGlobalBindings() = localVarMap.filter { item -> !localVarSet.contains(item.key) }

    /**
     * get table from both the local and global scope
     */
    fun getTable(key:String): Pair<LuaTableObj, Boolean> {
        if(localVarMap.containsKey(key)){
            return Pair(localVarMap[key] as LuaTableObj, false)
        }
        if(globalVarMap.containsKey(key)){
            globalVarSet.remove(key)
            return Pair(globalVarMap[key] as LuaTableObj, true)
        }
        throw RuntimeException("No table $key found, it seems that it has not defined yet")
    }

    /**
     * get obj from both the local and global scope
     */
    fun getObj(key:String): LuaObj{
        if(localVarMap.containsKey(key)){
            return localVarMap[key]!!
        }
        if(globalVarMap.containsKey(key)){
            globalVarSet.remove(key)
            // if it is a global item, we just refer it
            return LuaRef(key)
        }
        return LuaRef(key)
    }

    operator fun set(key: String, value: LuaObj) {
        localVarMap[key] = value
    }

    operator fun get(key: String): LuaObj? {
        return localVarMap[key]
    }

}