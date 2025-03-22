<div align="center">

![Animeko](https://socialify.git.ci/open-ani/animeko/image?description=1&descriptionEditable=%E9%9B%86%E6%89%BE%E7%95%AA%E3%80%81%E8%BF%BD%E7%95%AA%E3%80%81%E7%9C%8B%E7%95%AA%E7%9A%84%E4%B8%80%E7%AB%99%E5%BC%8F%E5%BC%B9%E5%B9%95%E8%BF%BD%E7%95%AA%E5%B9%B3%E5%8F%B0&font=Jost&logo=https%3A%2F%2Fraw.githubusercontent.com%2Fopen-ani%2Fanimeko%2Frefs%2Fheads%2Fmain%2F.github%2Fassets%2Flogo.png&name=1&owner=1&pattern=Plus&theme=Light)

| 下載量                                                                                                                                                                                                                | 正式版↓                                                                                                                                                                         | 测试版↓                                                                                                                                                                                    | 讨论群                                                                                                                                                                                                                                                                                                                                                                                                           |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [![GitHub downloads](https://img.shields.io/github/downloads/open-ani/ani/total?label=Downloads&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://github.com/open-ani/ani/releases) | [![Stable](https://img.shields.io/github/release/open-ani/ani.svg?maxAge=3600&label=Stable&labelColor=06599d&color=043b69)](https://github.com/open-ani/ani/releases/latest) | [![Beta](https://img.shields.io/github/v/release/open-ani/ani.svg?maxAge=3600&label=Beta&labelColor=2c2c47&color=1c1c39&include_prereleases)](https://github.com/open-ani/ani/releases) | [![Group](https://img.shields.io/badge/Telegram-2CA5E0?style=flat-squeare&logo=telegram&logoColor=white)](https://t.me/openani) [![QQ](https://img.shields.io/badge/927170241-EB1923?logo=tencent-qq&logoColor=white)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=2EbZ0Qxe-fI_AHJLCMnSIOnqw-nfrFH5&authKey=L31zTMwfbMG0FhIgt8xNHGOFPHc531mSw2YzUVupHLRJ4L2f8xerAd%2ByNl4OigRK&noverify=0&group_code=927170241) |

</div>

[dmhy]: http://www.dmhy.org/

[Bangumi]: http://bangumi.tv

[ddplay]: https://www.dandanplay.com/

[Compose Multiplatform]: https://www.jetbrains.com/compose-multiplatform/

[acg.rip]: https://acg.rip

[Mikan]: https://mikanani.me/

[Ikaros]: https://ikaros.run/

[Kotlin Multiplatform]: https://kotlinlang.org/docs/multiplatform.html

[ExoPlayer]: https://developer.android.com/media/media3/exoplayer

[VLC]: https://www.videolan.org/vlc/

[libtorrent]: https://libtorrent.org/

Animeko 支持云同步观看记录 ([Bangumi][Bangumi])、多视频数据源、缓存、弹幕、以及更多功能，提供尽可能简单且舒适的追番体验。

## 技术总览

以下几点可以给你一个技术上的大概了解，不感兴趣的可以直接看[截图](#功能截图)。

- [Kotlin 多平台][Kotlin Multiplatform]架构，支持 Windows、macOS、Android 和 iOS (计划)
- 基于 Kotlin 多平台架构，使用新一代响应式 UI 框架 [Compose Multiplatform][Compose Multiplatform] 构建
  UI
- 内置专为 Animeko 打造的基于 [libtorrent][libtorrent] 的 BitTorrent 引擎，优化边下边播的体验
- 高性能弹幕引擎，公益弹幕服务器 + 网络弹幕源
- 适配多平台的视频播放器，Android 底层为 [ExoPlayer][ExoPlayer]，PC 底层为 [VLC][VLC]
- 多类型数据源适配，内置 [动漫花园][dmhy]， [Mikan]，拥有强大的自定义数据源编辑器

### 参与开发

欢迎你提交 PR 参与开发，
有关项目技术细节请参考 [CONTRIBUTING](docs/contributing/README.md)。

## 下载

Animeko 支持 Android 和桌面端 (macOS、Windows)。

- 稳定版本: 每两周更新, 功能稳定  
  [下载稳定版本](https://github.com/Him188/ani/releases/latest)

通常建议使用稳定版本. 如果你愿意参与测试并拥有一定的对 bug 的处理能力, 也欢迎使用测试版本更快体验新功能.
具体版本类型可查看下方.

- 测试版本: 每两天更新, 体验最新功能  
  [下载测试版本](https://github.com/Him188/ani/releases)

<details>
<summary> <b>点击查看具体版本类型</b> </summary>

Animeko 采用语义化版本号, 简单来说就是 `4.x.y` 的格式. 有以下几种版本类型:

- 稳定版本:
    - **新特性发布**: 当 `x` 更新时, 会有新特性的发布. 通常为 2 周一次.
    - **Bug 修复**: 当 `y` 更新时, 只会有针对前个版本的重要的 bug 修复. 这些 Bug 修复版本穿插在新特性更新的间隔中,
      时间不固定.
- 在稳定版本的发布周期之间, 会发布测试版本:
    - **Alpha 测试版**: 所有重大新功能都会首先发布到 `alpha` 测试通道, 客户端内可使用 "每日构建"
      接收更新. 这些新功能非常不稳定, 适合热情的先锋测试员!
    - **Beta 测试版**: 在功能经过 alpha 测试修复重大问题后, 会进入 `beta` 测试通道,
      在客户端内名称为 "测试版". 此版本仍然不稳定, 是一个平衡新功能和稳定性的选择

</details>

## 功能截图

### 手机端

|            <img src=".readme/images/portrait/0_Onboarding_portrait.png" alt="Onboarding Screen Portrait" />             |  <img src=".readme/images/portrait/1_MainScreen_Exploration_portrait.png" alt="Main Screen Exploration Portrait" />  |      <img src=".readme/images/portrait/2_MainScreen_Collection_portrait.png" alt="Main Screen Collection Portrait"  />       | <img src=".readme/images/portrait/3_MainScreen_CacheManagement_portrait.png" alt="Main Screen Cache Management Portrait"  />  |            <img src=".readme/images/portrait/4_SubjectDetailsPage_portrait.png" alt="Subject Details Page Portrait"  />            | 
|:-----------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------------------------:|:-----------------------------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------------------------------:|
| <img src=".readme/images/portrait/5_EpisodePage_selected_web_portrait.png" alt="Episode Page Selected Web Portrait"  /> | <img src=".readme/images/portrait/6_EpisodePage_selected_bt_portrait.png" alt="Episode Page Selected BT Portrait" /> | <img src=".readme/images/portrait/7_EpisodePage_selector_simple_portrait.png" alt="Episode Page Selector Simple Portrait" /> | <img src=".readme/images/portrait/8_EpisodePage_selector_detail_portrait.png" alt="Episode Page Selector Detail Portrait"  /> | <img src=".readme/images/portrait/11_SettingsPage_media_preference_portrait.png" alt="Settings Page Media Preference Portrait"  /> |

| <img src=".readme/images/portrait/9_EpisodePage_fullscreen_android.png" alt="Episode Page Fullscreen Android Portrait"  /> | <img src=".readme/images/portrait/10_EpisodePage_fullscreen_danmaku_settings_android.png" alt="Episode Page Fullscreen Danmaku Settings Android Portrait"  /> | 
|:--------------------------------------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------------------------------------------------------------------------:|
|                                                                                                                            |                                                                                                                                                               |

### PC 端

|               <img src=".readme/images/landscape/0_Onboarding_landscape.png" alt="Onboarding Screen Landscape" />                |   <img src=".readme/images/landscape/1_MainScreen_Exploration_landscape.png" alt="Main Screen Exploration Landscape" />   |         <img src=".readme/images/landscape/2_MainScreen_Collection_landscape.png" alt="Main Screen Collection Landscape"  />          | <img src=".readme/images/landscape/3_MainScreen_CacheManagement_landscape.png" alt="Main Screen Cache Management Landscape"  />  |
|:--------------------------------------------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------:|
|         <img src=".readme/images/landscape/4_SubjectDetailsPage_landscape.png" alt="Subject Details Page Landscape"  />          | <img src=".readme/images/landscape/5_EpisodePage_selected_web_landscape.png" alt="Episode Page Selected Web Landscape" /> |        <img src=".readme/images/landscape/6_EpisodePage_selected_bt_landscape.png" alt="Episode Page Selected BT Landscape" />        | <img src=".readme/images/landscape/7_EpisodePage_selector_simple_landscape.png" alt="Episode Page Selector Simple Landscape"  /> |
| <img src=".readme/images/landscape/8_EpisodePage_selector_detail_landscape.png" alt="Episode Page Selector Detail Landscape"  /> |     <img src=".readme/images/landscape/9_EpisodePage_theater_landscape.png" alt="Episode Page Theater Landscape"  />      | <img src=".readme/images/landscape/12_SettingsPage_media_preference_landscape.png" alt="Settings Page Media Preference Landscape"  /> |               <img src=".readme/images/landscape/13_EpisodePage_macos.png" alt="Episode Page macOS Landscape"  />                |

| <img src=".readme/images/landscape/10_EpisodePage_fullscreen_windows.png" alt="Episode Page Fullscreen Windows Landscape" /> | <img src=".readme/images/landscape/11_EpisodePage_fullscreen_danmaku_settings_windows.jpg" alt="Episode Page Fullscreen Danmaku Settings Windows Landscape" /> |
|:----------------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------------------------------------:|
|                                                                                                                              |                                                                                                                                                                |

### 完全免费无广告且开放源代码

- 使用靠谱的 [Bangumi][Bangumi] 记录追番数据, 不怕网站跑路丢失数据
- 视频播放使用 P2P 资源, 无服务器维护成本, ~即使我跑路了 Animeko 也能用~
- 开放源代码, 公开自动构建, 无资料泄露风险
- 可 PR 添加自己喜欢的功能

## FAQ

### 资源来源是什么?

全部视频数据都来自网络, Animeko 本身不存储任何视频数据。
Animeko 支持两大数据源类型：BT 和在线。BT 源即为公共 BitTorrent P2P 网络，
每个在 BT
网络上的人都可分享自己拥有的资源供他人下载。在线源即为其他视频资源网站分享的内容。Animeko
本身并不提供任何视频资源。

本着互助精神，使用 BT 源时 Animeko 会自动做种 (分享数据)。
BT 指纹为 `-AL4123-`，其中 `4123` 为版本号 `4.12.3`；UA 为类似 `ani_libtorrent/4.12.3`。

### 弹幕来源是什么?

Animeko 拥有自己的公益弹幕服务器，在 Animeko 应用内发送的弹幕将会发送到弹幕服务器上。每条弹幕都会以
Bangumi
用户名绑定以防滥用（并考虑未来增加举报和屏蔽功能）。

Animeko 还会从[弹弹play][ddplay]获取关联弹幕，弹弹play还会从其他弹幕平台例如哔哩哔哩港澳台和巴哈姆特获取弹幕。
番剧每集可拥有几十到几千条不等的弹幕量。
