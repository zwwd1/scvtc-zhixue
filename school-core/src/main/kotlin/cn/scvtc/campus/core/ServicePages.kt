package cn.scvtc.campus.core

/** A module replaces its last complete snapshot only after every page is acknowledged. */
class ServicePages {
    private var run=""
    private var generation=""
    private var page=0
    private var staged:Extraction?=null
    fun reset(){run="";generation="";page=0;staged=null}
    fun offer(next:Extraction,runId:String,epoch:String,index:Int,hasNext:Boolean):Extraction? {
        require(next.module!="schedule" && next.account.isNotBlank() && next.semester.isNotBlank())
        require(runId.isNotBlank() && epoch.isNotBlank() && index in 1..50)
        require(next.warnings.isEmpty() && (next.records.isNotEmpty() || (next.module=="calendar"&&next.links.isNotEmpty()))) {"服务页未识别，保留上次完整数据"}
        if(index==1){reset();run=runId;generation=epoch}
        require(run==runId && generation==epoch && index==page+1){"分页批次或顺序已改变"}
        val previous=staged
        require(previous==null || (previous.account==next.account && previous.semester==next.semester && previous.module==next.module)){"分页账号、学期或模块已改变"}
        staged=next.copy(records=(previous?.records.orEmpty()+next.records).distinct(),links=(previous?.links.orEmpty()+next.links).distinct(),full=true)
        page=index
        require(!(index==50 && hasNext)){"服务超过50页，请分段读取；原完整数据保留"}
        return if(hasNext)null else staged.also{reset()}
    }
}
