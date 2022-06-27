package me.akka

import me.akka.OpUtils.cleanString
import me.akka.OpUtils.constantToCode
import me.akka.obj.LuaBlockCodeObj
import me.akka.obj.LuaObj
import me.akka.obj.LuaRef
import me.akka.obj.LuaTableObj
import org.luaj.vm2.ast.*
import org.luaj.vm2.ast.Stat.*
import java.util.*

class LuaBlockParser(private val context : LuaBlockContext) {
    var genCode = LuaBlockCodeObj()

    fun handleMain(chunk : Chunk){
        val level = 0

        for(stat in chunk.block.stats){
            when(stat){
                is FuncDef ->{
                    val func = LuaBlockParser(LuaBlockContext(context.localVarMap, context.localVarSet, false)).handleFuncDef(stat)
                    context.nextFunc(func, stat.name.name.name)
                }
                else -> handleBlock(stat, level)
            }
        }

        // create global definition
        context.getGlobalBindings()
            .filter { item -> item.value is LuaTableObj}
            .forEach{ item -> addFuncRefForTable(item.value as LuaTableObj, item.key, level)}
        // then functions
        context.getGlobalBindings()
            .filter { item -> item.value is LuaRef && context[item.value.toCode()] is LuaBlockCodeObj}
            .forEach{ item -> genCode.addFunc(context[item.value.toCode()] as LuaBlockCodeObj, item.key, level)}
    }

    private fun addFuncRefForTable(luaTableObj : LuaTableObj, tableName: String, level: Int){
        // add func ref for table
        luaTableObj.table
            .filter { item -> item.value is LuaRef }
            .filter { item -> (item.value as LuaRef).refName.startsWith("func") }
            .map { item -> context.localVarMap[(item.value as LuaRef).refName] }
            .filterNotNull()
            .forEach{ item -> genCode.addFunc(item as LuaBlockCodeObj, level = level)}

        genCode.addTable(luaTableObj, tableName, level)
    }

    private fun handleFuncDef(funcDef: FuncDef): LuaBlockCodeObj {
        for(argItem in funcDef.body.parlist.names){
            val name = argItem as Name
            val argVar = when(name.name){
                "A0_2" -> "context"
                "A1_2" -> "args"
                else -> name.name
            }
            context[name.name] = LuaRef(argVar)
            genCode.addArgs(argVar)
        }

        val level = 0
        funcDef.body.block.stats.forEach { stat -> handleBlock(stat, level) }
        return genCode
    }

   private fun handleBlock(stat: Any?, level: Int){
       when (stat) {
           is LocalAssign -> stat.names.forEach { n -> context.addLocalVar((n as Name).name) }
           is Assign -> handleAssign(stat, level)
           is FuncCallStat -> handleFuncCallStat(stat, level)
           is Return -> handleReturn(stat, level)
           is IfThenElse -> handleIf(level, stat)
           //is NumericFor -> handleFor(level, stat)
           is Block -> handleChildBlock(stat, level)
           is Goto -> genCode.addLine("goto ${stat.name}", level)
           is Label -> genCode.addLine("::${stat.name}::", level)
           else -> throw RuntimeException("Unsupported Statement found $stat")
       }
    }

    private fun handleFuncCallStat(stat: FuncCallStat, level: Int) {
        val lhs = stat.funccall.lhs
        val exp = expToCode(lhs as Exp)
        // clean ""
        genCode.addLine("${cleanString(exp)}(${handleArgs(stat.funccall.args)})", level)
    }

    private fun handleChildBlock(stat: Block, level: Int) {
        // https://blog.csdn.net/fightsyj/article/details/111289397
        genCode.addLine("do", level)
        stat.stats.forEach { child -> handleBlock(child, level + 1) }
        genCode.addLine("end", level)
    }

    private fun handleFor(level: Int, stat: NumericFor) {
        val name = stat.name.name
        // TODO
        val nameVar = context.nextVar()
        val initial = expToCode(stat.initial)
        val limit = expToCode(stat.limit)
        val step = expToCode(stat.step)
        // for head
        context[name] = LuaRef(nameVar)
        genCode.addLine("local $nameVar", level)
        genCode.addLine("for $nameVar = $initial, $limit, $step do", level)
        // child
        for (child in stat.block.stats) {
            handleBlock(child, level + 1)
        }
        // end
        genCode.addLine("end", level)
    }

    private fun handleIf(level: Int, ifThenElse: IfThenElse) {
        // if
        handleIfElseCond(level, ifThenElse.ifexp, false)
        ifThenElse.ifblock.stats.forEach { stat -> handleBlock(stat, level + 1) }
        // if else
        if (ifThenElse.elseifexps != null && ifThenElse.elseifexps.isNotEmpty()) {
            for (i in ifThenElse.elseifexps.indices) {
                handleIfElseCond(level, ifThenElse.elseifexps[i] as Exp, true)
                (ifThenElse.elseifblocks[i] as Block).stats.forEach { stat -> handleBlock(stat, level + 1) }
            }
        }
        // else
        if (ifThenElse.elseblock != null) {
            genCode.addLine("else", level)
            ifThenElse.elseblock.stats.forEach { stat -> handleBlock(stat, level + 1) }
        }
        // end
        genCode.addLine("end", level)
    }

    private fun handleIfElseCond(level: Int, cond: Exp, isIfElse: Boolean) {
        val code: String = expToCode(cond)
        val head = if (isIfElse) "elseif" else "if"

        genCode.addLine("$head $code then", level)
    }

    private fun handleReturn(stat: Return, level: Int) = when {
        stat.values == null -> genCode.addLine("return", level)
        stat.values.size == 1 -> {
            val exp = expToCode(stat.values[0] as Exp)
            genCode.addLine("return $exp", level)
        }
        else -> throw RuntimeException("Unsupported Return values count ${stat.values.size}")
    }

    private fun handleAssign(stat: Assign, level: Int) {
        if (stat.vars.size != 1 || stat.exps.size != 1) {
            throw RuntimeException("Unsupported Assign fields numbers")
        }
        val target = stat.vars[0]
        val expression = stat.exps[0]

        when (target) {
            // L1_1 = ...
            is Exp.NameExp -> context[target.name.name] = calculateExp(expression, level)
            // L1_1.xxx = ...
            is Exp.FieldExp -> {
                val lhs = target.lhs as Exp.NameExp
                val (table, isGlobal) = context.getTable(lhs.name.name)
                if(isGlobal){
                    // write out when it accesses global
                    genCode.addLine("${lhs.name.name}.${target.name.name} = ${calculateExp(expression, level).toCode()}", level)
                }else{
                    table[target.name.name] = calculateExp(expression, level)
                }
            }
            // L1_1[?] = ...
            is Exp.IndexExp -> {
                val lhs = target.lhs as Exp.NameExp
                when (val exp = target.exp) {
                    // L1_1[1]
                    is Exp.Constant -> {
                        val index = constantToCode(exp.value)
                        val (table, isGlobal) = context.getTable(lhs.name.name)
                        if(isGlobal){
                            // write out when it accesses global
                            genCode.addLine("${lhs.name.name}[$index] = ${calculateExp(expression, level).toCode()}", level)
                        }else{
                            table[index] = calculateExp(expression, level)
                        }
                    }

                    // L1_1[L1_2]
                    is Exp.NameExp -> {
                        if("_ENV" == lhs.name.name){
                            context[exp.name.name] = calculateExp(expression, level)
                        }else{
                            val (table, isGlobal) = context.getTable(lhs.name.name)
                            val name = context[exp.name.name]!!.toCode()
                            if(isGlobal){
                                // write out when it accesses global
                                genCode.addLine("${lhs.name.name}[$name] = ${calculateExp(expression, level).toCode()}", level)
                            }else{
                                table[name] = calculateExp(expression, level)
                            }
                        }
                    }
                    else -> throw RuntimeException("Unsupported IndexExp OP")
                }
            }
            else -> throw RuntimeException("Unsupported Assign OP")
        }
    }

    /**
     * calculate the exp and return its result
     */
    private fun calculateExp(e: Any?, level: Int) : LuaObj {
        when (e) {
            // = 1
            is Exp.Constant -> return LuaRef(constantToCode(e.value))
            // = L1_1
            is Exp.NameExp -> return context.getObj(e.name.name).clone()
            // = -A
            is Exp.UnopExp -> return LuaRef(expToCode(e))
            // = A+B
            is Exp.BinopExp -> return LuaRef("(${expToCode(e)})")
            // = {}
            is TableConstructor -> return LuaTableObj()
            // = A.B
            is Exp.FieldExp -> {
                val lhs = e.lhs as Exp.NameExp
                val name = lhs.name.name
                var luaObj = context[name]
                if(luaObj == null && context.globalVarMap[name] != null){
                    context.globalVarSet.remove(name)
                    luaObj = LuaRef(name)
                }else if(context.isGlobal && luaObj is LuaTableObj){
                    // handle global
                    return luaObj[e.name.name] ?: LuaRef()
                }
                return LuaRef(luaObj!!.toCode() + "." + e.name.name)

            }
            // = A(...)
            is Exp.FuncCall -> {
                val lhs = e.lhs as Exp.NameExp
                val name = context[lhs.name.name]
                val nv = context.nextVar()
                genCode.addLine("local $nv = ${name!!.toCode()}(${handleArgs(e.args)})", level)

                return LuaRef(nv)
            }
            // A[...]
            is Exp.IndexExp -> {
                val lhs = e.lhs
                val exp = e.exp
                return when {
                    lhs is Exp.NameExp && exp is Exp.NameExp && "_ENV" == lhs.name.name -> {
                        // L6_1 = _ENV[L6_1]
                        val content = context.getObj(exp.name.name)
                        content.clone()
                    }
                    lhs is Exp.NameExp && exp is Exp.NameExp && !context.isGlobal && context.globalVarMap.contains(lhs.name.name) -> {
                        // L7_1[var2](context)
                        context.globalVarSet.remove(lhs.name.name)
                        val index = context[exp.name.name]
                        LuaRef("${lhs.name.name}[$index]")
                    }
                    lhs is Exp.NameExp && exp is Exp.NameExp -> {
                        // L3_2 = L3_2[L2_2]
                        var lhsName = context.getObj(lhs.name.name).toCode()
                        val valueName = context.getObj(exp.name.name).toCode()
                        // if it indexes the obj which is a table, use a temp var to avoid syntax error (scene3_group133007241.lua)
                        if(lhsName.startsWith("{")){
                            val nextVar = context.nextVar()
                            genCode.addLine("local $nextVar = $lhsName", level)
                            lhsName = nextVar
                        }
                        // a["b"] is equal with a.b in Lua
                        LuaRef("${cleanString(lhsName)}[${valueName}]")
                    }
                    lhs is Exp.NameExp && exp is Exp.Constant -> {
                        // L5_2 = L2_2[1]
                        val lhsName = context.getObj(lhs.name.name).toCode()
                        LuaRef("${lhsName}[${constantToCode(exp.value)}]")
                    }
                    else -> throw RuntimeException("Unsupported IndexExp")
                }
            }
            else -> throw RuntimeException("Unsupported Assign")
        }
    }

    private fun handleArgs(args : FuncArgs) : String{
        val sj = StringJoiner(", ")
        args.exps.forEach { item -> sj.add(expToCode(item as Exp)) }
        return sj.toString()
    }

    /**
     * convert exp to code, readonly
     */
    private fun expToCode(exp: Exp) : String = when (exp) {
        // A + B
        is Exp.BinopExp ->  "${expToCode(exp.lhs)} ${OpUtils.opToCode(exp.op)} ${expToCode(exp.rhs)}"
        // -A
        is Exp.UnopExp -> "${OpUtils.opToCode(exp.op)}${expToCode(exp.rhs)}"
        // (A)
        is Exp.ParensExp -> "(${expToCode(exp.exp)})"
        // 1
        is Exp.Constant ->  constantToCode(exp.value)
        // A
        is Exp.NameExp ->  context.getObj(exp.name.name).toCode()
        else -> throw RuntimeException("Unsupported Exp")
    }

    fun toScriptText(): String{
        val outputJoiner = StringJoiner("\n")
        genCode.forEach { x -> outputJoiner.add(x) }
        return outputJoiner.toString()
    }
}

