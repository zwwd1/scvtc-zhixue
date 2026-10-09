package cn.scvtc.campus
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.popup.WindowDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
@Composable fun StableChoice(title:String,items:List<String>,selected:Int,modifier:Modifier=Modifier,onSelect:(Int)->Unit){
 var open by remember{mutableStateOf(false)}
 var hold by remember{mutableStateOf(false)}
 var pending by remember{mutableStateOf<Int?>(null)}
 val latest by rememberUpdatedState(onSelect)
 val entry=DropdownEntry(items.mapIndexed{i,label->DropdownItem(text=label,selected=i==selected,onClick={pending=i})})
 BasicComponent(title=title,modifier=modifier,onClick={open=!open;hold=open},holdDownState=hold,endActions={
  Text(items.getOrElse(selected){items.firstOrNull().orEmpty()},Modifier.padding(end=8.dp).align(Alignment.CenterVertically),color=MiuixTheme.colorScheme.onSurfaceVariantActions)
  DropdownArrowEndAction(MiuixTheme.colorScheme.onSurfaceVariantActions)
  WindowDropdownPopup(entry=entry,show=open,onDismiss={open=false},onDismissFinished={hold=false;val i=pending;pending=null;if(i!=null&&i!=selected)latest(i)},maxHeight=null,dropdownColors=DropdownDefaults.dropdownColors(),collapseOnSelection=true)
 })
}
