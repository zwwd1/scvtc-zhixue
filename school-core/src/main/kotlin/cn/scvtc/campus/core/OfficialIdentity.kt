package cn.scvtc.campus.core

import kotlinx.serialization.json.*

/** Use only known student-number fields in a real querySelf response, never a DOM guess. */
object OfficialIdentity {
    private val fields=setOf("studentNumber","studentCode","studentNo","studentId","xh")
    fun account(body:String):String=runCatching {
        require(body.length<=1_000_000)
        val found=mutableSetOf<String>();var count=0
        fun visit(element:JsonElement,depth:Int){
            require(depth<=24 && ++count<=10000)
            when(element){
                is JsonObject->element.forEach{(key,value)->
                    if(key in fields && value is JsonPrimitive){value.contentOrNull?.takeIf{it.matches(Regex("[0-9]{6,20}"))}?.let(found::add)}
                    else if(value is JsonObject || value is JsonArray)visit(value,depth+1)
                }
                is JsonArray->element.forEach{visit(it,depth+1)}
                else->Unit
            }
        }
        val root=Json.parseToJsonElement(body)
        if(root is JsonObject){
            require(root["success"]?.jsonPrimitive?.booleanOrNull!=false)
            require(root["code"]==null || root["code"]?.jsonPrimitive?.contentOrNull in setOf("200","0"))
            require(listOf("code","status").none{root[it]?.jsonPrimitive?.intOrNull in setOf(401,403)})
        }
        visit(root,0);found.singleOrNull().orEmpty()
    }.getOrDefault("")
}
