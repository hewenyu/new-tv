# new-tv

新版本的电视桌面。基于 [cleanyourtv](https://github.com/Gathin23/cleanyourtv) 的 `launcher/`（commit f592b6d2，MIT，© 2026 Gathin），
按客厅这台小米 MiTV-ASTP0 重做了交互：控制中心取代 Android 原生设置侧栏，中文排版，统一的磁贴和桌面设置页。
设计依据和验收标准见 [docs/redesign-spec.md](docs/redesign-spec.md)。

包名仍是 `com.gathin.tvhome`，直接覆盖安装旧桌面，Home 键接管和 `stock_home_redirect` 等设置不变。

## 构建

需要 JDK 17、Android SDK platform 34、build-tools 34.0.0。

```sh
cd launcher
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

## 安装到电视

先在电视「设置 → 账号与安全」里打开 ADB 调试（这台电视每次重启都会关掉它）。

```sh
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt   # 只需一次
TV_HOST=<电视 IP> ./install.sh
```

装完关闭 ADB 调试、重启，再用 `tools/venv/bin/python tools/adb-closed.py <电视 IP>` 确认 5555 已关闭。

## 许可

MIT，见 [LICENSE](LICENSE)。字体 Outfit 为 OFL，见 `launcher/FONT-LICENSE-OFL.txt`。
