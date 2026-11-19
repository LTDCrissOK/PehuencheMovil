package ar.com.pehuenche.movil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MicronautaApi {
    private const val ENDPOINT = "https://micronauta4.dnsalias.net/usuario/urbano2_cmd.php"
    data class ServiceConfig(val title:String,val conf:String) {
        companion object { val all=listOf(ServiceConfig("Provinciales","Provincial"),ServiceConfig("Centenario","centenario"),ServiceConfig("Interurbanos","nacional"),ServiceConfig("Cipolletti","urbcipol")) }
    }
    data class Route(val id:String,val name:String)
    data class Line(val id:String,val name:String,val client:String,val routes:List<Route>)
    data class Stop(val name:String,val code:String,val line:String,val client:String,val direction:String,val lineName:String,val lat:Double,val lon:Double)
    data class RouteData(val trace:List<Pair<Double,Double>>,val stops:List<Stop>)
    data class Timetable(val lineName:String,val days:Map<String,List<String>>)

    private suspend fun post(cmd:String,params:Map<String,String>)=withContext(Dispatchers.IO) {
        val body=params.entries.joinToString("&") { (k,v)->"${URLEncoder.encode(k,"UTF-8")}=${URLEncoder.encode(v,"UTF-8")}" }
        var lastError:Exception?=null

        // Micronauta can respond slowly at peak hours. Retry transient timeouts,
        // but keep every attempt bounded so the UI can report a useful error.
        repeat(3) { attempt ->
            try {
                val c=(URL("$ENDPOINT?cmd=$cmd").openConnection() as HttpURLConnection).apply {
                    requestMethod="POST"
                    connectTimeout=25000
                    readTimeout=45000
                    doOutput=true
                    setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 PehuencheMovil/1.2")
                    setRequestProperty("Accept","application/json, text/plain, */*")
                    setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8")
                    setRequestProperty("Origin","https://micronauta4.dnsalias.net")
                    setRequestProperty("Referer","https://micronauta4.dnsalias.net/usuario/urbano2.php?conf=${params["conf"].orEmpty()}")
                }
                try {
                    c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val status=c.responseCode
                    val stream=if(status in 200..299)c.inputStream else c.errorStream
                    val response=stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    if(status !in 200..299) {
                        val detail=response.replace(Regex("\\s+")," ").take(180)
                        throw java.io.IOException("Micronauta respondió HTTP $status${if(detail.isNotBlank()) ": $detail" else ""}")
                    }
                    return@withContext JSONObject(response)
                } finally {
                    c.disconnect()
                }
            } catch(e:kotlinx.coroutines.CancellationException) {
                throw e
            } catch(e:Exception) {
                lastError=e
                val retryable=e is java.net.SocketTimeoutException ||
                    e is java.net.ConnectException ||
                    (e.message?.contains("HTTP 408")==true) ||
                    (e.message?.contains("HTTP 502")==true) ||
                    (e.message?.contains("HTTP 503")==true) ||
                    (e.message?.contains("HTTP 504")==true)
                if(!retryable || attempt==2) {
                    val message=when {
                        e is java.net.SocketTimeoutException -> "Micronauta tardó demasiado en responder. Se agotó el tiempo de espera tras 3 intentos."
                        e.message?.contains("HTTP 408")==true -> "Micronauta devolvió HTTP 408 (tiempo de espera agotado) tras 3 intentos."
                        else -> e.message ?: "No se pudo conectar con Micronauta."
                    }
                    throw java.io.IOException(message,e)
                }
                kotlinx.coroutines.delay(1200L * (attempt+1))
            }
        }
        throw java.io.IOException(lastError?.message ?: "No se pudo conectar con Micronauta.",lastError)
    }

    suspend fun lines(conf:String):List<Line> {
        val a=post("lineasyrutas",mapOf("conf" to conf)).optJSONArray("lineas")?:JSONArray()
        return buildList { for(i in 0 until a.length()) {
            val o=a.optJSONObject(i)?:continue; val r=o.optJSONArray("rutas")?:JSONArray()
            val routes=buildList { for(j in 0 until r.length()) { val x=r.optJSONObject(j)?:continue; add(Route(x.optString("ruta_id"),x.optString("ruta_nombre","Recorrido"))) } }
            add(Line(o.optString("linea_id"),o.optString("linea_nombre","Línea"),o.optString("cliente"),routes))
        } }
    }

    suspend fun route(conf:String,route:String,client:String):RouteData {
        val o=post("seleccionatraza",mapOf("ruta" to route,"cliente_id" to client,"conf" to conf))
        if(o.optString("ok")=="error") error(o.optString("errorMsg","No se pudo cargar el recorrido"))
        val a=o.optJSONArray("traza")?:JSONArray()
        val trace=buildList { for(i in 0 until a.length()) { val p=a.optJSONArray(i)?:continue; if(p.length()>1)add(p.optDouble(1) to p.optDouble(0)) } }
        val b=o.optJSONArray("paradas")?:JSONArray()
        val stops=buildList { for(i in 0 until b.length()) {
            val p=b.optJSONObject(i)?:continue; val lat=p.optDouble("lat",Double.NaN); val lon=p.optDouble("lon",Double.NaN)
            if(!lat.isNaN()&&!lon.isNaN()) add(Stop(p.optString("parada_nombre","Parada"),p.optString("codigo"),p.optString("linea"),p.optString("cliente",client),p.optString("sentido","I"),p.optString("nombre"),lat,lon))
        } }
        return RouteData(trace,stops)
    }

    suspend fun timetable(conf:String,s:Stop):Timetable {
        val o=post("ver_horarios",mapOf("linea" to s.line,"codigo" to s.code,"cliente" to s.client,"sentido" to s.direction.ifBlank{"I"},"conf" to conf))
        val days=linkedMapOf<String,List<String>>()
        listOf("lu" to "Lunes","ma" to "Martes","mi" to "Miércoles","ju" to "Jueves","vi" to "Viernes","sa" to "Sábado","do" to "Domingos y feriados").forEach { (k,label)->
            val v=o.opt(k); days[label]=when(v) {
                is JSONArray -> (0 until v.length()).mapNotNull { v.optString(it).takeIf { x->x.isNotBlank() } }
                is String -> v.split(',', ';','\n').map{it.trim()}.filter{it.isNotBlank()}
                else -> emptyList()
            }
        }
        return Timetable(o.optString("linea_nombre",s.lineName),days)
    }
}