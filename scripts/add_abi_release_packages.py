from pathlib import Path
p=Path('app/build.gradle.kts');s=p.read_text(encoding='utf-8')
needle='versionCode = 15; versionName = "0.1.14" }'
assert needle in s;s=s.replace(needle,'''versionCode = 15; versionName = "0.1.14"
        providers.gradleProperty("targetAbi").orNull?.let { requested ->
            require(requested in setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"))
            ndk { abiFilters += requested }
        }
    }''',1);p.write_text(s,encoding='utf-8')
p=Path('.github/workflows/android.yml');s=p.read_text(encoding='utf-8')
needle='      - name: Package named source archive'
s=s.replace(needle,'''      - name: Build smaller ARM packages
        if: startsWith(github.ref, 'refs/tags/')
        run: |
          for abi in arm64-v8a armeabi-v7a; do
            gradle --no-daemon assembleDebug -PtargetAbi="$abi"
            cp app/build/outputs/apk/debug/app-debug.apk "app/build/outputs/apk/debug/FreeMangnetPlayer-${GITHUB_REF_NAME}-${abi}.apk"
          done
'''+needle,1)
s=s.replace('"FreeMangnetPlayer-${GITHUB_REF_NAME}-source.zip" --generate-notes', '"app/build/outputs/apk/debug/FreeMangnetPlayer-${GITHUB_REF_NAME}-arm64-v8a.apk" "app/build/outputs/apk/debug/FreeMangnetPlayer-${GITHUB_REF_NAME}-armeabi-v7a.apk" "FreeMangnetPlayer-${GITHUB_REF_NAME}-source.zip" --generate-notes',1)
p.write_text(s,encoding='utf-8')
p=Path('scripts/build_current_report.py');s=p.read_text(encoding='utf-8')
s=s.replace('v0.1.14/FreeMangnetPlayer-v0.1.14.apk)', 'v0.1.14/FreeMangnetPlayer-v0.1.14-arm64-v8a.apk)')
s=s.replace('## 6. 仓库清理与文档可读性','''## 安装包体积优化

v0.1.13 通用包包含四种 CPU 架构，约 250 MB，比参考 ARM 安装包大。本版另提供单 ABI 安装包，ARM64 手机、电视和平板优先下载 ARM64 版；旧 ARMv7 设备使用 ARMv7 版。保留通用包供其他架构使用，签名、应用名称和功能保持一致。

- [ARM64 版](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.14/FreeMangnetPlayer-v0.1.14-arm64-v8a.apk)
- [ARMv7 版](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.14/FreeMangnetPlayer-v0.1.14-armeabi-v7a.apk)
- [四 ABI 通用包](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.14/FreeMangnetPlayer-v0.1.14.apk)

## 6. 仓库清理与文档可读性''',1)
p.write_text(s,encoding='utf-8')
