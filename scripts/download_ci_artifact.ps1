param(
    [Parameter(Mandatory=$true)][string]$ArtifactId,
    [Parameter(Mandatory=$true)][string]$EntryPath,
    [Parameter(Mandatory=$true)][string]$OutputFile
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$source = @'
using System;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Net.Http.Headers;
using System.IO.Compression;

using System.Threading.Tasks;
using System.Net;

public static class OmniArtifactEntryDownloader {
  static HttpClient ApiClient(string token) {
    var c = new HttpClient(new HttpClientHandler());
    c.DefaultRequestHeaders.UserAgent.ParseAdd("OmniPlayer-CI-Artifact-Downloader/1.0");
    c.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
    c.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/vnd.github+json"));
    return c;
  }
  static byte[] Range(Uri uri, long first, long last) {
    var req=(HttpWebRequest)WebRequest.Create(uri); req.UserAgent="OmniPlayer-CI";
    req.Timeout=1800000; req.ReadWriteTimeout=1800000; req.AddRange(first,last);
    using(var r=(HttpWebResponse)req.GetResponse()) {
      if((int)r.StatusCode!=206) throw new InvalidOperationException("Range returned HTTP "+(int)r.StatusCode);
      using(var s=r.GetResponseStream()) using(var m=new MemoryStream()) { s.CopyTo(m); return m.ToArray(); }
    }
  }
  static byte[] ParallelRange(Uri uri, long first, int size) {
    const int chunk = 4*1024*1024;
    int count=(size+chunk-1)/chunk;
    var tasks=Enumerable.Range(0,count).Select(i=>Task.Run(()=>{
      long a=first+(long)i*chunk; long b=Math.Min(first+size-1,a+chunk-1);
      return Range(uri,a,b);
    })).ToArray();
    Task.WaitAll(tasks);
    using(var result=new MemoryStream(size)) { foreach(var t in tasks) result.Write(t.Result,0,t.Result.Length); return result.ToArray(); }
  }
  static uint U32(byte[] b, int p) { return BitConverter.ToUInt32(b,p); }
  static ushort U16(byte[] b, int p) { return BitConverter.ToUInt16(b,p); }
  public static void Download(string url, string token, string entryName, string output) {
    ServicePointManager.DefaultConnectionLimit=16;
    Uri finalUri; long length;
    using (var api=ApiClient(token))
    using (var req=new HttpRequestMessage(HttpMethod.Head,url))
    using (var res=api.SendAsync(req).GetAwaiter().GetResult()) {
      res.EnsureSuccessStatusCode(); finalUri=res.RequestMessage.RequestUri; length=res.Content.Headers.ContentLength.Value;
      if (!res.Headers.AcceptRanges.Contains("bytes")) throw new InvalidOperationException("Artifact does not support byte ranges.");
    }
    long tailStart=Math.Max(0,length-65557);
    byte[] tail=Range(finalUri,tailStart,length-1);
    int eocd=-1;
    for(int p=tail.Length-22;p>=0;p--) if(U32(tail,p)==0x06054b50){eocd=p;break;}
    if(eocd<0) throw new InvalidDataException("ZIP end directory not found in final 4 MB.");
    long cdOffset=U32(tail,eocd+16); int cdSize=(int)U32(tail,eocd+12); byte[] cd;
    if(cdOffset>=tailStart) cd=tail.Skip((int)(cdOffset-tailStart)).Take(cdSize).ToArray();
    else cd=Range(finalUri,cdOffset,cdOffset+cdSize-1);
    int at=0, method=-1, compressed=0, expanded=0; long local=0;
    while(at+46<=cd.Length && U32(cd,at)==0x02014b50) {
      int m=U16(cd,at+10), c=(int)U32(cd,at+20), x=(int)U32(cd,at+24);
      int nameLen=U16(cd,at+28), extra=U16(cd,at+30), comment=U16(cd,at+32);
      string name=System.Text.Encoding.UTF8.GetString(cd,at+46,nameLen);
      if(String.Equals(name,entryName,StringComparison.OrdinalIgnoreCase)){method=m;compressed=c;expanded=x;local=U32(cd,at+42);break;}
      at+=46+nameLen+extra+comment;
    }
    if(method<0) throw new FileNotFoundException("Artifact entry not found: "+entryName);
    byte[] header=Range(finalUri,local,local+29);
    int dataAt=(int)(local+30+U16(header,26)+U16(header,28));
    byte[] payload=ParallelRange(finalUri,dataAt,compressed);
    Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(output)));
    using(var file=File.Create(output)) {
      if(method==0) file.Write(payload,0,payload.Length);
      else if(method==8) using(var input=new DeflateStream(new MemoryStream(payload),CompressionMode.Decompress)) input.CopyTo(file);
      else throw new InvalidDataException("Unsupported ZIP compression method: "+method);
    }
    if(new FileInfo(output).Length!=expanded) throw new InvalidDataException("Extracted entry size mismatch.");
  }
}
'@
$references = @('System.dll', 'System.Core.dll', [System.Net.Http.HttpClient].Assembly.Location)
Add-Type -TypeDefinition $source -Language CSharp -ReferencedAssemblies $references
$token = (gh auth token).Trim()
if (-not $token) { throw 'GitHub CLI authentication is required to download the CI artifact.' }
$uri = "https://api.github.com/repos/liao-zc/OmniPlayer/actions/artifacts/$ArtifactId/zip"
[OmniArtifactEntryDownloader]::Download($uri, $token, $EntryPath, $OutputFile)
Write-Host "Downloaded $EntryPath to $OutputFile"
