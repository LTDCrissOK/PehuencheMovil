package ar.com.pehuenche.movil

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MicronautaLiveScreen(padding:PaddingValues,search:String,onSearch:(String)->Unit,favorites:MutableList<String>) {
    var conf by remember { mutableStateOf(MicronautaApi.ServiceConfig.all.first().conf) }
    var lines by remember { mutableStateOf(emptyList<MicronautaApi.Line>()) }
    var line by remember { mutableStateOf<MicronautaApi.Line?>(null) }
    var route by remember { mutableStateOf<MicronautaApi.Route?>(null) }
    var routeData by remember { mutableStateOf<MicronautaApi.RouteData?>(null) }
    var stop by remember { mutableStateOf<MicronautaApi.Stop?>(null) }
    var timetable by remember { mutableStateOf<MicronautaApi.Timetable?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(conf) {
        loading=true; error=null; line=null; route=null; routeData=null; stop=null; timetable=null
        try { lines=MicronautaApi.lines(conf) } catch(e:Exception) { lines=emptyList(); error=e.message ?: "No se pudieron cargar las líneas." }
        finally { loading=false }
    }
    LaunchedEffect(route?.id,line?.client,conf) {
        val r=route; val l=line
        if(r!=null && l!=null) {
            loading=true; error=null; routeData=null; stop=null; timetable=null
            try { routeData=MicronautaApi.route(conf,r.id,l.client) } catch(e:Exception) { error=e.message ?: "No se pudo consultar el recorrido." }
            finally { loading=false }
        }
    }
    LaunchedEffect(stop?.code,stop?.line,stop?.direction,conf) {
        val s=stop
        if(s!=null) {
            loading=true; error=null; timetable=null
            try { timetable=MicronautaApi.timetable(conf,s) } catch(e:Exception) { error=e.message ?: "No se pudieron consultar los horarios." }
            finally { loading=false }
        }
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        Header("Servicios", "Recorridos, paradas y horarios de Micronauta")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp, vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            MicronautaApi.ServiceConfig.all.forEach { c ->
                FilterChip(selected=conf==c.conf,onClick={conf=c.conf},label={Text(c.title,fontSize=11.sp)})
            }
        }
        if(route!=null) {
            TextButton(onClick={route=null;routeData=null;stop=null;timetable=null;line=null},modifier=Modifier.padding(start=8.dp)) {
                Icon(Icons.Default.ArrowBack,null); Spacer(Modifier.width(5.dp)); Text("Volver a las líneas")
            }
        }
        OutlinedTextField(value=search,onValueChange=onSearch,modifier=Modifier.fillMaxWidth().padding(horizontal=14.dp),singleLine=true,
            placeholder={Text(if(route==null) "Buscar línea o recorrido" else "Filtrá desde la búsqueda")})
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=7.dp),color=Blue)
        error?.let { Text(it,Modifier.fillMaxWidth().padding(14.dp).background(Color(0xFFFFEEE8),RoundedCornerShape(10.dp)).padding(12.dp),color=Color(0xFF8B3D20),fontSize=12.sp) }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            if(route==null) {
                val visible=lines.filter { (it.name+" "+it.routes.joinToString(" "){r->r.name}).contains(search,true) }
                if(!loading && visible.isEmpty() && error==null) Text("No hay líneas para mostrar en este servicio.",color=Muted)
                visible.forEach { item ->
                    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
                        Column(Modifier.padding(13.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name,color=Ink,fontWeight=FontWeight.Bold,fontSize=14.sp)
                                    Text("${item.routes.size} recorridos",color=Muted,fontSize=11.sp)
                                }
                                IconButton(onClick={if(item.name in favorites)favorites.remove(item.name) else favorites.add(item.name)}) {
                                    Icon(if(item.name in favorites)Icons.Default.Favorite else Icons.Default.FavoriteBorder,null,tint=Blue)
                                }
                            }
                            if(line?.id==item.id && line?.client==item.client) {
                                item.routes.forEach { r ->
                                    OutlinedButton(onClick={route=r},modifier=Modifier.fillMaxWidth().padding(top=4.dp)) { Text(r.name) }
                                }
                            } else {
                                TextButton(onClick={line=item}) { Text("Ver recorridos") }
                            }
                        }
                    }
                }
            } else {
                Text(route?.name.orEmpty(),color=Ink,fontWeight=FontWeight.Bold,fontSize=17.sp)
                Text("Paradas del recorrido",color=Muted,fontSize=12.sp)
                val stops=routeData?.stops.orEmpty()
                if(routeData!=null && stops.isEmpty()) Text("Micronauta no devolvió paradas para este recorrido.",color=Muted,fontSize=12.sp)
                stops.forEachIndexed { index,s ->
                    Card(Modifier.fillMaxWidth().clickable { stop=s },shape=RoundedCornerShape(12.dp),
                        colors=CardDefaults.cardColors(containerColor=if(stop==s)Color(0xFFE6F1FA) else Color.White)) {
                        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Default.Place,null,tint=Blue); Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${index+1}. ${s.name}",color=Ink,fontWeight=FontWeight.SemiBold,fontSize=13.sp)
                                Text("${"%.5f".format(s.lat)}, ${"%.5f".format(s.lon)}",color=Muted,fontSize=10.sp)
                            }
                            Icon(Icons.Default.Schedule,null,tint=Blue)
                        }
                    }
                }
                routeData?.let { Text("Datos del recorrido: ${it.trace.size} puntos de trazado y ${it.stops.size} paradas.",color=Muted,fontSize=10.sp) }
                timetable?.let { t ->
                    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.White)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("${stop?.name} · ${t.lineName}",color=Ink,fontWeight=FontWeight.Bold,fontSize=13.sp)
                            t.days.forEach { (day,times) ->
                                if(times.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp)); Text(day,color=Blue,fontWeight=FontWeight.Bold,fontSize=11.sp)
                                    Text(times.joinToString(" · "),color=Ink,fontSize=12.sp)
                                }
                            }
                            if(t.days.values.all{it.isEmpty()}) Text("No hay horarios publicados para esta parada.",color=Muted,fontSize=12.sp)
                        }
                    }
                }
            }
        }
    }
}
