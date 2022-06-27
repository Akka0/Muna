# Muna

Convert lua to make it human-readable and remove obfuscation. Based on [luaj](https://github.com/luaj/luaj) parser and [lua formatter](https://github.com/Koihik/LuaFormatter).

Before

```lua
function L1_1(A0_2, A1_2)
  local L2_2, L3_2
  L2_2 = A1_2.type
  L3_2 = EventType
  L3_2 = L3_2.EVENT_1
  if L2_2 == L3_2 then
    L2_2 = A1_2.param3
    if not (50 < L2_2) then
      goto lbl_11
    end
  end
  L2_2 = false
  do return L2_2 end
  ::lbl_11::
  L2_2 = ScriptLib
  L2_2 = L2_2.call
  L3_2 = A0_2
  L2_2 = L2_2(L3_2)
  if 2 < L2_2 then
    L2_2 = false
    return L2_2
  end
  L2_2 = true
  return L2_2
end
condition_EVENT_1 = L1_1
```

after

```lua
function condition_EVENT_1(context, args)
    if args.type == EventType.EVENT_1 then
        if not (50 < args.param3) then goto lbl_11 end
    end
    do return false end
    ::lbl_11::
    local var1 = ScriptLib.call(context)
    if 2 < var1 then return false end
    return true
end
```

## Support

- assign, only one arg (don't support A,B = C)
- if-else
- goto/label/block [do ... end](https://blog.csdn.net/fightsyj/article/details/111289397)
- func call/define
- return

TODO: loop (for - both 2 type) statement

# How it works?

It assumed that the var in any assign statement is used only once, so that we can use a map to store the value and simplify almost all assign statement.
When it is referred in expression and function arg, we will output its real value.
Also, not all assign statement can be simplified due to grammar and idempotent.

The details below show how it handles with the code.

**And you may see it will meet some problems when it faces with loop, when the var will be used more than once.**

```lua
-- input
L2_2 = ScriptLib
L2_2 = L2_2.call
-- output
-- store
L2_2 : ScriptLib.call
```
We will set `L2_2` to `ScriptLib.call` and won't output both of them.

```lua
-- input
L5_2 = L1_1.challenge_id_no_record
-- output
-- store
L5_2 : L1_1.challenge_id_no_record
```
Since `L1_1` is a global var, we won't store its value and keep the reference.

```lua
-- input
L2_2 = L2_2(L3_2)
-- output
local var1 = ScriptLib.call(context)
-- store
L2_2 : var1
```
We use a temporal var to hold the result of a func call due to the idempotent.

```lua
-- input
L2_2 = {}
L2_2[1] = 1
L2_2[2] = nil
L2_2[3] = 2
...
L4_2 = ScriptLib
L4_2 = L4_2.call
L5_2 = A0_2
L6_2 = {}
L7_2 = L2_2[L3_2]
L6_2.config_id = L7_2
L4_2(L5_2, L6_2)
-- output
local var2 = {1, nil, 2}
ScriptLib.call(context, {config_id = var2[var1]})
-- store
L2_2 : var2
L6_2 : {config_id = var2[var1]}
```
To avoid grammar error like `{1, nil, 2}[var1]`, we use a temporal var to hold the table when it is indexed.

```lua
-- input
function L2_1(A0_2, A1_2)
  ...
end
condition_EVENT1 = L2_1
-- output
function condition_EVENT1(context, args)
    ...
end
-- store
func1 : Function ...
L2_1 : func1
condition_EVENT1 : func1
```
We use a temporal name `funcN` to hold the definition of function.
When the block's parser is done, we will start to output the functions & global definitions.

The first arg in func will be replaced with `context`, and the second one is `args`.

# Can I convert to another language?

Of course. I write it out as lua, but you can customize the output language by yourself.
