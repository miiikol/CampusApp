# CampusApp 校园信息应用 · Campus Information App

一个面向校园场景的 Android 应用，覆盖课程表、校园资讯、二手市场、失物招领、地图定位等功能，并配套自研的 PHP 后端 API。采用 Kotlin + MVVM + Jetpack 技术栈，遵循分层架构与工程化实践。

*An Android application for campus scenarios, covering course schedules, campus news, second-hand market, lost & found, and map-based location, backed by a self-built PHP API. Built with Kotlin + MVVM + Jetpack, following a layered architecture and engineering best practices.*

---

## 功能特性 · Features

- **课程表 Course Schedule**：周视图网格展示（自定义 View），支持课程增删改、颜色自定义、周次管理。
- **校园资讯 News**：列表 + 详情，支持点赞、评论（含二级回复）、收藏。
- **二手市场 Market**：商品发布、管理员审核、收藏。
- **失物招领 Lost & Found**：信息发布、地图选点定位、管理员审核。
- **通知 Notification**：评论回复、点赞等站内通知。
- **管理员 Admin**：内容审核（通过/驳回）、违规评论封禁。

---

## 架构设计 · Architecture

采用 **MVVM + Repository** 分层架构，单向数据流，职责清晰：

```
┌─────────────┐    状态流(StateFlow)     ┌──────────────┐
│   View       │ ───────────────────────▶ │   ViewModel  │
│ (Fragment)   │ ◀─────────────────────── │  (UI 状态)    │
└─────────────┘                          └──────┬───────┘
                                                 │
                                                 ▼
                                        ┌──────────────┐
                                        │  Repository  │  (单一数据源)
                                        └──┬───────┬───┘
                                           │       │
                              ┌────────────▼─┐   ┌─▼──────────────┐
                              │  Remote API  │   │  Local Room DB │
                              │  (Retrofit)  │   │  (本地缓存)     │
                              └──────────────┘   └────────────────┘
```

- **View 层**：Fragment，只负责渲染和收集状态，不持有业务逻辑。
- **ViewModel 层**：通过 `StateFlow` 暴露 UI 状态，管理业务编排与生命周期。
- **Repository 层**：聚合远端与本地数据源，作为唯一数据来源（Single Source of Truth）。
- **数据层**：`Retrofit` 负责网络，`Room` 负责本地缓存。

---

## 技术栈 · Tech Stack

### Android 客户端 · Android Client

| 类别 Category | 技术 Technology |
|--------------|-----------------|
| 语言 Language | Kotlin |
| 架构 Architecture | MVVM + Repository |
| 依赖注入 DI | Hilt |
| 本地存储 Local DB | Room（含 Migration 迁移脚本） |
| 网络 Network | Retrofit + OkHttp（SSL Pinning、超时控制） |
| 异步 Asynchronous | Kotlin Coroutines + StateFlow |
| 导航 Navigation | Navigation Component |
| 图片加载 Image | Glide |
| 地图 Map | 高德地图 AMap SDK |
| 视图绑定 View Binding | ViewBinding / DataBinding |
| 测试 Testing | JUnit + MockK + kotlinx-coroutines-test |

### 后端 · Backend

| 类别 Category | 技术 Technology |
|--------------|-----------------|
| 语言 Language | PHP 7.3+ |
| 数据库 Database | MySQL（PDO） |
| 鉴权 Auth | JWT（HMAC-SHA256） |
| 限流 Rate Limit | APCu / 数据库降级 |
| 缓存 Cache | APCu |

---

## 项目结构 · Project Structure

```
CampusApp/
├── app/                              # Android 客户端
│   └── src/
│       ├── main/java/com/example/campus/
│       │   ├── core/                 # 基础层
│       │   │   ├── base/             # BaseViewModel（协程 + 统一异常处理）
│       │   │   ├── common/           # Resource / Constants / UserRole
│       │   │   ├── di/               # Hilt AppModule
│       │   │   └── ui/               # Snack 等通用 UI 工具
│       │   ├── data/                 # 数据层
│       │   │   ├── local/            # Room 数据库 + DAO + Entity
│       │   │   ├── remote/           # ApiService + DTO
│       │   │   └── repository/       # 各业务 Repository
│       │   └── ui/                   # UI 层
│       │       ├── auth/             # 登录、找回密码
│       │       ├── course/           # 课程表
│       │       ├── news/             # 资讯
│       │       ├── market/           # 二手市场
│       │       ├── lostfound/        # 失物招领
│       │       ├── map/              # 地图选点/查看
│       │       ├── admin/            # 管理员
│       │       ├── profile/          # 个人中心
│       │       └── MainActivity.kt
│       └── test/                     # 单元测试（MockK）
├── campus_api/                       # PHP 后端 API
│   ├── config/                       # 数据库、环境变量加载
│   ├── includes/                     # 鉴权、JWT、限流、缓存、存储
│   ├── modules/                      # 业务模块（auth/news/market/...）
│   ├── index.php                     # 路由入口
│   ├── migrate.php                   # 数据库迁移脚本
│   └── .env.example                  # 环境变量示例
├── gradle/                           # Gradle 版本目录
└── build.gradle.kts / settings.gradle.kts
```

---

## 环境要求 · Requirements

- **Android Studio**（推荐最新稳定版）
- **JDK 17**（AGP 8.x 要求）
- **Android SDK**：`compileSdk 34`、`minSdk 24`、`targetSdk 34`
- **后端环境**：PHP 7.3+、MySQL、Apache（推荐 WampServer）

---

## 构建与运行 · Build & Run

### 1. 配置后端 · Backend Setup

```bash
# 1. 将 campus_api 目录放到 Web 服务器可访问的位置（如 WampServer 的 www 目录）
# 2. 复制 .env.example 为 .env，并按需修改数据库配置
cp .env.example .env

# 3. 运行数据库迁移（建表 + 写入演示数据）
php migrate.php
```

`.env` 关键配置：

```ini
DB_HOST=127.0.0.1
DB_NAME=campus_api
DB_USER=root
DB_PASS=
JWT_SECRET=你的随机密钥
```

### 2. 配置客户端 · Android Setup

- **后端地址 BASE_URL**：在 [build.gradle.kts](app/build.gradle.kts) 中通过环境变量 `CAMPUS_BASE_URL` 注入，默认 `http://10.0.2.2/campus_api/`（模拟器访问宿主机）。
  - 模拟器：保持默认 `10.0.2.2`
  - 真机：改为电脑的局域网 IP，如 `http://192.168.x.x/campus_api/`
- **高德地图 Key**：在 `local.properties` 中配置 `AMAP_API_KEY=你的key`。

### 3. 运行 · Run

```bash
# 命令行构建
./gradlew assembleDebug

# 或在 Android Studio 中直接点击 Run
```

---

## 测试账号 · Test Accounts

| 角色 Role | 学号 Student ID | 密码 Password |
|-----------|----------------|---------------|
| 学生 Student | `20260001`（或 20260002~20260004） | `123456` |
| 管理员 Admin | `20269999` | `admin123456` |

---

## 审核工作流 · Moderation Workflow

1. 学生发布二手/失物招领内容。
2. 后端先标记为 `PENDING`（暂不进入公开列表）。
3. 管理员进入管理页，审核待处理内容。
4. 通过的内容对学生可见；驳回的内容保持隐藏。
5. 资讯详情页支持评论，管理员可封禁违规评论。

---

## 测试 · Testing

项目使用 **JUnit + MockK + kotlinx-coroutines-test**，覆盖 ViewModel 与 Repository：

- `LoginViewModelTest`：登录空输入校验、成功、网络错误。
- `NewsViewModelTest`：资讯列表加载状态。
- `MarketRepositoryTest`：刷新成功、IO 异常保留缓存、收藏同步、发布。
- `LostFoundRepositoryTest`：刷新成功、IO 异常保留缓存。

```bash
./gradlew testDebugUnitTest
```

---

## 安全与工程实践 · Security & Engineering Practices

- **依赖注入**：Hilt 全局单例管理网络与数据库依赖。
- **状态管理**：统一使用 StateFlow（无 LiveData 混用）。
- **数据库迁移**：Room 显式 Migration，不使用破坏性降级。
- **网络安全**：Release 构建启用 SSL Certificate Pinning、关闭明文流量。
- **混淆**：启用 ProGuard/R8，并配置了 Hilt/Retrofit/Room/Gson/Glide/AMap 的保留规则。
- **后端安全**：JWT 鉴权、接口限流、文件上传魔数校验、密码哈希存储。

---

## 许可证 · License

本项目为毕业设计/求职作品，仅供学习与展示使用。
