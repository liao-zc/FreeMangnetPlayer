package com.freemangnetplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment

private val sources = listOf(
    "clg63.top" to "https://clg63.top/",
    "cld123.com" to "https://www.cld123.com/",
    "juhebd.com" to "https://www.juhebd.com/",
    "seedog.cc" to "https://seedog.cc/",
    "btsj6.com" to "https://www.btsj6.com/",
    "ldysg.win" to "https://www.ldysg.win/"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        val inbound = intent?.data?.takeIf { it.scheme == "magnet" }?.toString()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF44D7B6), background = Color(0xFF101419), surface = Color(0xFF191F26))) {
                var query by remember { mutableStateOf("") }; var magnet by remember { mutableStateOf(inbound.orEmpty()) }
                var selected by remember { mutableStateOf(sources.first().first) }; var customBase by remember { mutableStateOf("") }
                Surface(Modifier.fillMaxSize()) { LazyColumn(Modifier.fillMaxSize().padding(horizontal=24.dp, vertical=20.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    item { Text("FreeMangnetPlayer", style=MaterialTheme.typography.headlineMedium, color=Color(0xFF44D7B6)); Text("纾佸姏鎼滅储銆佸湪绾挎挱鏀句笌涓嬭浇", color=Color.LightGray) }
                    item { Text("鎼滅储鏉ユ簮"); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { sources.take(3).forEach { (name,_) -> FilterChip(selected==name,{selected=name},label={Text(name)}) } } }
                    item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { sources.drop(3).forEach { (name,_) -> FilterChip(selected==name,{selected=name},label={Text(name)}) } } }
                    item { OutlinedTextField(customBase,{customBase=it},Modifier.fillMaxWidth(),label={Text("鑷畾涔夋悳绱㈢綉绔欙紙鍙€夛級")},placeholder={Text("https://example.org/")},singleLine=true) }
                    item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("鍏抽敭璇?)},singleLine=true) }
                    item { Button(onClick={ if(query.isNotBlank()){ val base=customBase.trim().ifBlank { sources.firstOrNull{it.first==selected}?.second ?: sources.first().second }; startActivity(Intent(this@MainActivity,SearchActivity::class.java).putExtra("url",base).putExtra("query",query)) } },Modifier.fillMaxWidth().height(54.dp)){Text("鎼滅储") } }
                    item { HorizontalDivider(); Text("纾佸姏閾炬帴", style=MaterialTheme.typography.titleMedium) }
                    item { OutlinedTextField(magnet,{magnet=it},Modifier.fillMaxWidth().heightIn(min=100.dp),label={Text("绮樿创 magnet 閾炬帴")},placeholder={Text("magnet:?xt=urn:btih:...")}) }
                    item { Button(onClick={ if(magnet.startsWith("magnet:?")) startActivity(Intent(this@MainActivity,PlayerActivity::class.java).putExtra("magnet",magnet)) },Modifier.fillMaxWidth().height(54.dp)){Text("瑙ｆ瀽骞舵挱鏀?/ 涓嬭浇") } }
                    item { Text("璇蜂粎涓嬭浇鍜屾挱鏀句綘鏈夋潈浣跨敤鐨勫唴瀹广€?, style=MaterialTheme.typography.bodySmall, color=Color.LightGray) }
                } }
            }
        }
    }
}
