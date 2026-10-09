package cn.scvtc.campus.core

import org.junit.Assert.*
import org.junit.Test

class ServicePagesTest {
    private fun page(title:String)=Extraction("grades","student","term",records=listOf(NativeRecord(title)))
    @Test fun partialBatchDoesNotReplaceSnapshot(){val pages=ServicePages();assertNull(pages.offer(page("first"),"run","epoch",1,true));assertEquals(listOf("first","last"),pages.offer(page("last"),"run","epoch",2,false)!!.records.map{it.title})}
    @Test(expected=IllegalArgumentException::class) fun missingPageCannotCommit(){val pages=ServicePages();pages.offer(page("first"),"run","epoch",1,true);pages.offer(page("last"),"run","epoch",3,false)}
    @Test(expected=IllegalArgumentException::class) fun differentAccountCannotCommit(){val pages=ServicePages();pages.offer(page("first"),"run","epoch",1,true);pages.offer(page("last").copy(account="other"),"run","epoch",2,false)}
    @Test(expected=IllegalArgumentException::class) fun lateGenerationCannotCommit(){val pages=ServicePages();pages.offer(page("first"),"run","epoch",1,true);pages.offer(page("last"),"run","other",2,false)}
    @Test fun restartedBatchDoesNotKeepAbandonedPages(){val pages=ServicePages();pages.offer(page("abandoned"),"old","epoch",1,true);assertEquals(listOf("fresh"),pages.offer(page("fresh"),"new","epoch",1,false)!!.records.map{it.title})}
}
