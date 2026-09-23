# CC10 优化管理器

面向天猫精灵 CC10（TG_Z04，Android 8.1 / ARMv7）的本地系统管理工具。

当前版本：

- CC10 优化管理器：`v3.4`
- 当贝桌面兼容入口：`v1.1`

## 主要功能

- 管理 CC10 上可安全停用或恢复的原厂组件。
- 跟随原厂儿童模式的开启/关闭状态。
- 读取原厂视频防沉迷参数：每日总时长、单次时长和可观看时间段。
- 只对用户选择的计时应用累计观看时长。
- 单次时长或每日总时长耗尽时显示休息锁定页。
- 支持儿童模式剩余时间悬浮卡片。
- 支持家长密码退出锁定页。
- 支持屏幕常亮、壁纸待机与系统黑屏策略。
- 提供当贝桌面兼容入口，避免系统应用在桌面中没有图标。

## 适用环境

- 设备：天猫精灵 CC10 / TG_Z04
- 系统：Android 8.1
- ABI：`armeabi-v7a`
- 分辨率：1280×800
- 需要 ADB root、可写 system 分区和与设备匹配的平台签名。

## 仓库结构

```text
.
├─ src/                         管理器 Java 源码
├─ res/                         管理器资源
├─ launcher-compat/             当贝桌面兼容入口源码
├─ release/                     已验证的最新 APK
├─ AndroidManifest.xml
├─ privapp-permissions-cc10manager.xml
├─ Build-CC10Manager.ps1
└─ .gitignore
```

## 安装

在操作 system 分区之前，请先完整备份设备。

### 1. 安装系统管理器

`CC10-Optimization-Manager-v3.4.apk` 使用目标设备的平台证书签名，安装到：

```text
/system/priv-app/CC10Manager/CC10Manager.apk
```

同时放置权限白名单：

```text
/system/etc/permissions/privapp-permissions-cc10manager.xml
```

两个文件权限均设置为 `0644 root:root`，然后重启设备。

### 2. 安装当贝桌面入口

```bash
adb install -r release/CC10-Optimization-Manager-Shortcut-v1.1.apk
```

入口包会启动：

```text
com.codex.cc10manager.system/com.codex.cc10manager.MainActivity
```

## 构建

构建环境变量：

```powershell
$env:ANDROID_SDK_ROOT = "C:\Android\Sdk"
$env:CC10_PLATFORM_KEY_DIR = "C:\keys\cc10-platform"
```

平台密钥目录应包含：

```text
platform.pk8
platform.x509.pem
```

随后执行：

```powershell
.\Build-CC10Manager.ps1
```

兼容入口还需要设置 `UBER_APK_SIGNER_JAR`，详见 `launcher-compat/Build-Launcher.ps1`。

## 安全说明

- 仓库不包含平台私钥、设备证书、账号信息或设备数据库。
- 原厂儿童模式配置通过系统 UID 只读访问，不修改原厂数据库权限。
- 不同 CC10 批次的系统签名可能不同，不能盲目互用签名后的系统 APK。
- 请勿将未知来源的 `preloader`、`nvram`、`nvdata` 或完整固件写入设备。

## 状态

当前版本已在 CC10 TG_Z04 上完成开机、系统权限、儿童模式参数同步、当贝桌面入口和应用启动验证。
