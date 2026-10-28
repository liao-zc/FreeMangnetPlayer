package com.freemangnetplayer

import android.os.Bundle
import android.webkit.*
import android.content.Intent
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

class SearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        val url=intent.getStringExtra("url") ?: "https://clg63.top/"
        val query=intent.getStringExtra("query").orEmpty()
        var autoSearchStarted=false
        setContent { MaterialTheme { Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={finish()}) { Text("返回") }; Text("搜索页：点击磁力链接即可解析",Modifier.padding(top=14.dp))
            }
            AndroidView(factory={ctx -> WebView(ctx).apply {
                settings.javaScriptEnabled=true; settings.domStorageEnabled=true; settings.userAgentString="Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
                webViewClient=object: WebViewClient(){
                    override fun shouldOverrideUrlLoading(view:WebView?, request:WebResourceRequest):Boolean {
                        val value=request.url.toString()
                        if(value.startsWith("magnet:?")){startActivity(Intent(this@SearchActivity,PlayerActivity::class.java).putExtra("magnet",value)); return true}
                        return false
                    }
                    override fun onPageFinished(view:WebView?, pageUrl:String?) {
                        if(query.isBlank() || autoSearchStarted) return
                        autoSearchStarted=true
                        val quoted=JSONObject.quote(query)
                        val script = "(function(){var q=$quoted,n=0,t=setInterval(function(){var a=[...document.querySelectorAll('input[type=search],input[name=q],input[name=k],input[name=keyword],input[name=title],input[type=text]')].find(x=>x.offsetWidth>0);if(a){clearInterval(t);var setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(a,q);a.dispatchEvent(new Event('input',{bubbles:true}));a.dispatchEvent(new Event('change',{bubbles:true}));var f=a.form;if(f){f.requestSubmit?f.requestSubmit():f.submit();}else{a.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));}}if(++n>30)clearInterval(t);},500);})()"
                        view?.evaluateJavascript(script,null)
                    }
                    @Deprecated("legacy") override fun shouldOverrideUrlLoading(view:WebView?, url:String):Boolean {
                        if(url.startsWith("magnet:?")){startActivity(Intent(this@SearchActivity,PlayerActivity::class.java).putExtra("magnet",url)); return true}; return false
                    }
                }; loadUrl(url)
            }}, modifier=Modifier.fillMaxSize())
        } } }
    }
    override fun onBackPressed(){ finish() }
}
