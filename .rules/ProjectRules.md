# 项目说明
这是一个aiseo的项目，使用最新的springboot 3.5.3版本，同时是一个多模块项目。目前已经实现的模块有：siteclone
** 重点说明：** 最基本原则，在进行代码修改时需要对目标代码文件及关联代码进行分析，确保修改不会遗漏已有的业务逻辑。
## 项目结构
### 项目代码结构

项目采用标准Spring Boot多模块结构，完整文件结构如下：

```
aiseo/
├── .gitignore - Git版本控制忽略规则
├── pom.xml - 父项目POM文件
└── siteclone/ - 站点克隆模块
    ├── pom.xml - 模块POM文件
    ├── src/
    │   ├── main/
    │   │   ├── java/
    │   │   │   ├── com/jiwu/aiseo/siteclone/
    │   │   │   │   ├── SiteCloneApplication.java - Spring Boot主启动类
    │   │   │   │   ├── config/
    │   │   │   │   │   └── AsyncConfig.java - 异步任务配置
    │   │   │   │   ├── controller/
    │   │   │   │   │   ├── FileController.java - 文件操作控制器
    │   │   │   │   │   └── SiteCloneController.java - 站点克隆控制器
    │   │   │   │   ├── downloader/
    │   │   │   │   │   └── CustomHttpClientDownloader.java - 自定义HTTP下载器
    │   │   │   │   ├── dto/
    │   │   │   │   │   ├── CloneRequest.java - 克隆请求DTO
    │   │   │   │   │   └── CloneResponse.java - 克隆响应DTO
    │   │   │   │   ├── exception/
    │   │   │   │   │   └── GlobalExceptionHandler.java - 全局异常处理器
    │   │   │   │   ├── model/
    │   │   │   │   │   ├── CloneTask.java - 克隆任务模型
    │   │   │   │   │   └── CloneTaskStatus.java - 克隆任务状态枚举
    │   │   │   │   ├── processor/
    │   │   │   │   │   └── WebsiteMirrorProcessor.java - 网站镜像处理器
    │   │   │   │   └── service/
    │   │   │   │       ├── FileService.java - 文件服务
    │   │   │   │       └── SiteCloneService.java - 站点克隆服务
    │   │   │   └── org/apache/http/annotation/
    │   │   │       └── ThreadSafe.java - 线程安全注解
    │   │   └── resources/
    │   │       ├── application.yml - 应用配置文件
    │   │       └── static/
    │   │           ├── favicon.ico - 网站图标
    │   │           ├── index.html - 首页HTML
    │   │           └── js/
    │   │               └── app.js - 前端JavaScript
    │   └── test/
    │       └── java/ - 测试代码目录
    └── target/ - 构建输出目录
```
