# Android 自动构建与更新发布

项目通过 `.github/workflows/android-release.yml` 完成一次构建、三路发布：

1. 使用正式 keystore 构建签名 Release APK。
2. 生成包含版本、下载地址和 SHA-256 的 `update.json`。
3. 创建或更新 GitHub Release。
4. 可选上传到阿里云 OSS。
5. 可选上传到腾讯云 COS。

阿里云、腾讯云均为可选渠道。某个渠道完全未配置时会自动跳过；某个
云上传失败时不会阻止 GitHub Release 发布，工作流会留下警告。

## 一、发布结果

默认对象路径：

| 文件 | 路径 |
|---|---|
| APK | `android/juying-版本号.apk` |
| 更新清单 | `api/android/update.json` |
| GitHub Release APK | `releases/download/v版本号/juying-版本号.apk` |

Android 构建时会把已配置的 OSS/COS 清单地址写入 APK。客户端按顺序
检查阿里云、腾讯云、`lanerc.app`，最后使用 GitHub Releases API 兜底。

## 二、正式签名

正式发布必须始终使用同一个 keystore。更换签名后，Android 不允许覆盖
安装旧版本。

首次创建 keystore：

```powershell
keytool -genkeypair -v `
  -keystore lanerc-release.jks `
  -alias lanerc `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000
```

将 keystore 转为 GitHub Secret 可保存的 Base64 文本（存储在粘贴板）：

```powershell
[Convert]::ToBase64String(
  [IO.File]::ReadAllBytes("C:\安全目录\lanerc-release.jks")
) | Set-Clipboard
```

在 GitHub 仓库的 `Settings → Secrets and variables → Actions → Secrets`
中添加：

| Secret | 内容 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | keystore 的完整 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 例如 `juying` |
| `ANDROID_KEY_PASSWORD` | alias 对应的密钥密码 |

不要把 `.jks`、密码或云 AccessKey 提交到仓库。

## 三、阿里云 OSS

建议创建只允许操作目标 Bucket 和 `android/`、`api/android/` 前缀的
RAM 用户。APK 和更新清单必须能通过 HTTPS 匿名下载，可以使用公开读
对象或绑定 CDN/自定义域名；不要给 RAM 用户不必要的全局权限。

GitHub Actions Secrets：

| Secret | 是否必需 |
|---|---|
| `ALIYUN_OSS_ACCESS_KEY_ID` | 是 |
| `ALIYUN_OSS_ACCESS_KEY_SECRET` | 是 |
| `ALIYUN_OSS_SECURITY_TOKEN` | 仅使用 STS 临时凭据时 |

GitHub Actions Variables：

| Variable | 示例 |
|---|---|
| `ALIYUN_OSS_ENDPOINT` | `https://oss-cn-hangzhou.aliyuncs.com` |
| `ALIYUN_OSS_BUCKET` | `lanerc-release` |
| `ALIYUN_OSS_PUBLIC_BASE_URL` | `https://download.example.com` |

`ALIYUN_OSS_PUBLIC_BASE_URL` 是用户真实下载文件的 HTTPS 域名，不是
OSS 控制台地址。如果绑定了 CDN，应填写 CDN 域名。

## 四、腾讯云 COS

建议创建权限仅覆盖目标 Bucket 和发布前缀的子账号或临时密钥。Bucket
名称必须包含 AppID。

GitHub Actions Secrets：

| Secret | 是否必需 |
|---|---|
| `TENCENT_COS_SECRET_ID` | 是 |
| `TENCENT_COS_SECRET_KEY` | 是 |
| `TENCENT_COS_TOKEN` | 仅使用临时密钥时 |

GitHub Actions Variables：

| Variable | 示例 |
|---|---|
| `TENCENT_COS_REGION` | `ap-guangzhou` |
| `TENCENT_COS_BUCKET` | `lanerc-release-1250000000` |
| `TENCENT_COS_PUBLIC_BASE_URL` | `https://download2.example.com` |

## 五、公共路径变量

以下 GitHub Actions Variables 可以不设置，工作流会使用默认值：

| Variable | 默认值 |
|---|---|
| `ANDROID_OBJECT_PREFIX` | `android` |
| `ANDROID_UPDATE_MANIFEST_KEY` | `api/android/update.json` |

如果希望继续使用：

```text
https://www.lanerc.app/api/android/update.json
```

需要把 `lanerc.app` 的 CDN/源站映射到同一个
`api/android/update.json` 对象。当前该地址返回 404，客户端代码无法
代替服务器创建文件。

## 六、触发发布

推荐使用版本标签自动发布：

```powershell
git tag v1.2.0
git push origin v1.2.0
```

也可以进入 GitHub `Actions → Android Release → Run workflow`，输入
版本号和更新说明手动发布。

未填写 `versionCode` 时按以下规则自动生成：

```text
major × 1,000,000 + minor × 1,000 + patch
```

例如 `1.2.3` 对应 `1002003`。发布版本必须始终大于用户设备上已安装
版本的 `versionCode`。

## 七、只使用 GitHub

不配置 OSS/COS 时，工作流仍会创建 GitHub Release，客户端通过 GitHub
Releases API 发现 APK。该模式可以工作，但国内网络下 GitHub API 和
Release 大文件下载可能不稳定，推荐至少配置一个国内对象存储/CDN。

## 八、本地验证

生成版本元数据：

```powershell
python scripts/release/android_release.py metadata `
  --version-name 1.2.0
```

生成清单：

```powershell
python scripts/release/android_release.py manifest `
  --apk android/app/build/outputs/apk/release/app-release.apk `
  --output android/release/update.json `
  --version-name 1.2.0 `
  --version-code 1002000 `
  --repository ssy20031224/juying `
  --tag v1.2.0
```

云上传脚本只从环境变量读取凭据，不接受命令行明文密码。
