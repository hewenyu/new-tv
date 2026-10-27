# new-tv 桌面重设计：规范与计划

基于 cleanyourtv `launcher/`（上游 commit f592b6d2，MIT，© 2026 Gathin）。目标设备：客厅 Xiaomi MiTV-ASTP0，
Android 9 / SDK 28，1.5 GB 内存（空闲约 384 MB），1920×1080 @ 320dpi（960×540 dp），有线网络，小米遥控器。
包名保持 `com.gathin.tvhome`，可覆盖安装、可用旧的 restore 回滚。

## 1. 现状证据（截图 + 代码）

| # | 问题 | 证据 | 严重度 |
|---|---|---|---|
| P1 | 「设置」直达 Android TV 原生侧栏：英文式层级、灰底列表，还露出账号 | `MainActivity` btnSettings → `ACTION_SETTINGS`；shots/15 | 高 |
| P2 | ADB、画面、声音没有入口，用户只能回原厂桌面找 ADB | 上次会话；设置菜单需长按才出「原生设置」 | 高 |
| P3 | 桌面设置页是工程师式的：「程序坞下方行数」「程序坞透明度」、灰色方块、无分组无说明 | `activity_launcher_settings.xml`；模拟器截图 | 中 |
| P4 | 中文问候语被拉散（"下 午 好"）：`letterSpacing 0.22` + `textAllCaps` 是给拉丁大写字母的 | `activity_main.xml` greeting/date | 中 |
| P5 | 焦点应用名 48sp 与磁贴文字重复，占掉一行空间 | shots/11 | 中 |
| P6 | 磁贴风格混杂：整幅横幅、白底图标、暗底小图标并存；部分磁贴无名字 | shots/11、14 | 中 |
| P7 | 「有线网络」是状态，却占一个按钮位 | shots/11 | 低 |

## 2. 任务与按键预算（从主页 Dock 起，方向键 + OK 计次）

| 任务 | 现在 | 目标 |
|---|---|---|
| 打开 Dock 应用 | 1–4 | 不变 |
| 切到 HDMI1 | 上、OK、OK = 3 | 不变（下拉已够好），控制中心另有入口 |
| 打开 ADB 开关所在页 | 上、→、长按 OK、↓、OK、再在小米设置里找 ≥ 8 | 上、→、OK、↓×4、OK = 8，落点由 `control_center_targets` 的 `adb=` 定到「账号与安全」页 |

ADB 原定 ≤ 6，实测 8。没有为它调整布局：它前面是全家常用的四个磁贴，而 ADB 只有维护者偶尔用。
真正省按键的是让这一行直接打开小米设置里 ADB 所在的子页面，而不是设置首页。这要在真机上确认组件名后再写入。
| 看网络状态（有线/无线、IP） | 进系统设置 ≥ 5 | 上、→、OK 后直接可见 = 3 |
| 调音量（不靠遥控音量键） | 无 | 上、→、OK、←/→ = 4 |
| 改桌面外观 | 上、→、长按、OK ≥ 4 | 上、→、OK、↓…OK ≤ 6，且页面可读 |

## 3. 设计规范

颜色（深色，3 米外读）。对比度按 WCAG 公式计算：

| token | 值 | 用途 | 对 bg / surface / raised |
|---|---|---|---|
| tvh_bg | #0B0E14 | 页面底 | — |
| tvh_surface | #171C28 | 面板 | — |
| tvh_raised | #222A3A | 卡片、行 | — |
| tvh_text | #F2F5FA | 主文字 | 17.7 / 15.6 / 13.2 |
| tvh_text2 | #AEB8CA | 次文字 | 9.7 / 8.5 / 7.2 |
| tvh_text3 | #8691A6 | 说明 | 6.1 / 5.4 / 4.5 |
| tvh_accent | #7DB1FF | 选中、强调 | 8.8 / 7.8 / 6.6 |
| tvh_positive | #5FD49A | 已连接 | 10.5 / 9.2 / 7.8 |
| tvh_warning | #FFB65C | 警告 | 11.1 / 9.8 / 8.3 |
| 焦点 | 底 #F2F5FA，字 #0B0E14 | 当前焦点（与顶栏胶囊一致） | 17.7 |

规则：正文 ≥ 4.5:1；焦点态必须同时变底色和放大/描边，不只靠颜色。

字号（sp）：页面标题 30，分组标题 13（tvh_text3，中文不加字距），行标题 18，行说明 14，磁贴标题 16，
大数字/时钟沿用 Outfit。中文一律字距 0、不 `textAllCaps`；拉丁字距在 `values/`，中文覆盖在 `values-zh/`。
间距 8dp 网格；面板圆角 20，卡片 16，行 14。动效：面板进出 220ms decelerate，`reduced_motion` 时用 `Motion.REDUCED_MS`。

## 4. 范围

做：
- A. 中文排版与主页细节：P4、P5、P6、P7。
- B. 控制中心（取代 P1/P2）：主页「设置」胶囊改名「控制中心」，打开改版后的 `QuickSettingsActivity`（右侧全高面板）。
  遥控器 Settings 键（`settings_key_panel`）打开的也是这个面板。内容：
  1. 音量：焦点行左右调 `STREAM_MUSIC`，OK 静音/取消，显示格数。
  2. 状态磁贴（2 列）：信号源、网络（有线/无线/未连接 + IPv4）、画面、声音、蓝牙与遥控器、应用管理。
  3. 列表行：ADB 调试（打开厂商设置，注明「账号与安全」）、桌面设置（其中第一行就是「调整应用顺序」）、
     系统设置（优先厂商设置）、Android 设置（只在有厂商设置时单列）、重新启动…（`HomeKeyService.canOpenPowerMenu()` 时）。
  每个磁贴/行的目标可由 `control_center_targets`（`key=package/activity`）覆盖；不填时用标准 Settings action，解析不到再退厂商设置。
  解析不到任何目标的项隐藏，不放会提示「无法打开」的入口。
- C. 桌面设置页重排（P3）：分组卡片（布局 / 外观 / 启动与主页 / 系统），每行标题 + 一句人话说明 + 当前值，同一套 token。

不做：网络权限、模糊、视频、新依赖、改 `minSdk`、改 HomeKeyService / ServiceRepair / Home 判定逻辑、
亮度/睡眠定时（需要额外权限，且没法在这台机器上先验证）。

## 5. 验收

1. `./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease` 通过，lint 0 错误。
2. 新的纯逻辑（目标解析、音量格数、网络文字）有 JUnit 测试。
3. APK < 589,824 B，并给出与基线 535,689 B 的差值。
4. 1080p@320dpi 模拟器截图：主页、控制中心、桌面设置；方向键能走遍所有项，返回键关闭面板，焦点不丢。
5. 真机（ADB 打开后）：截图对比；`am start -W` 冷启动时间不比基线 734 ms 差 10% 以上；
   在小米设置里确认 ADB / 画面 / 声音的精确页面，写入 `control_center_targets`；
   ADB 关闭后重启，实体 Home 键仍回到新桌面。

真机结果（2026-10-27）：冷启动 460–475 ms（3 次，`am start -W`），PSS 约 30 MB；控制中心的信号源、
有线网络 IP、ADB 行都正确；`adb=` 指向 `entry.SecurityActivity`（ADB 开关所在页），`network=` 指向
`network.MitvEthernetActivity`。`entry.DisplaySoundActivity` 在清单里声明了，但类不存在，一打开就崩，所以画面和声音
仍用标准页面。音乐音量范围是 0–100，左右键每按一次调 1/15。

关机再开机后桌面回到原厂桌面：`enabled_accessibility_services` 被恢复成固件默认值，「主页键」助手没了，
而 ServiceRepair 只在新桌面启动时才运行。已加 `BootRepair`，开机时就运行同一套修复；关闭 ADB 后整机重启，
用户确认能回到新桌面。

待机后用小爱同学唤醒仍是原厂桌面。logcat 显示原因是待机时
`MitvActivityManagerService: PowerManagerService killAllThirdApp forcestop packageName:com.gathin.tvhome`：
force-stop 把「主页键」从无障碍列表里拿掉（`accessibility_enabled=0`），而唤醒是恢复运行，不是开机，
BootRepair 和开机探测都不会触发。修复方法：
- `install.sh` 打开 `GuardRevival` 通知监听（系统写入，`pkg:android`，关 ADB 后保留）和 `guard_off_in_standby`。
  被 force-stop 后，系统约 10 s 内重新绑定它，屏幕亮着时它把「主页键」写回去。
- `GuardKeep` 记下这次重新写入的时间，`HomeKeyService` 在 30 s 内绑定时再做一次 ProbeActivity 探测：前台是原厂桌面
  就回新桌面，HDMI 或其他应用不动。
- 在真机上模拟（`am force-stop` 后原厂桌面在前台）：约 12 s 后探测触发，前台回到 `com.gathin.tvhome/.MainActivity`。
  真实的待机加小爱唤醒，要等用户在真机上再试一次。

## 6. 计划（顺序执行，任务间不并行，每个任务后审查）

| 任务 | 文件 | 验证 |
|---|---|---|
| T1 排版与主页 | `values/colors.xml`（新）、`values/styles.xml`、`values-zh/styles.xml`（新）、`layout/activity_main.xml`、`values/config.xml` | 构建 + 模拟器主页截图 |
| T2 控制中心 | `QuickSettingsActivity.java`、`ControlCenter*.java`（新）、`layout/activity_quick_settings.xml`、新 drawable、`strings.xml`（en + zh-rCN）、`config.xml`、`MainActivity.java`（只改 btnSettings 的点击和文字）、测试 | 构建 + 单测 + 截图 + 按键走查 |
| T3 桌面设置页 | `layout/activity_launcher_settings.xml`、`LauncherSettingsActivity.java`（只改展示）、styles、strings | 构建 + 截图 + 按键走查 |
