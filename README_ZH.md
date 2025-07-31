# AISEO WebClone

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Build Status](https://github.com/your-repo/aiseo-webclone/actions/workflows/build.yml/badge.svg)](https://github.com/your-repo/aiseo-webclone/actions)

一个强大的多模块Spring Boot应用程序，用于网页内容处理和克隆，为**https://Aiseo.icu/**提供支持的工具。

🌐 在线演示: [https://webclone.leoobai.cn/](https://webclone.leoobai.cn/)

## ✨ 功能特点

- **网站克隆**: 克隆整个网站及其所有资源
- **内容处理**: 高效提取和处理网页内容
- **模块化架构**: 组织良好的多模块结构
- **可定制性**: 易于扩展以满足特定需求
- **高性能**: 针对大规模网页处理进行优化
- **安全性**: 内置安全措施防止不安全操作
- **资源管理**: 高效处理图片、CSS、JS和其他网页资源
- **异步处理**: 非阻塞任务执行与状态跟踪

## 🛠 技术栈

### 核心框架
- Spring Boot 3.5.3 (包含Spring Web MVC)
- Java 17

### 网页处理
- Jsoup 1.15.3 (HTML解析和操作)
- WebMagic 1.0.3 (网络爬虫框架)

### 构建与工具
- Maven (依赖管理)
- Git (版本控制)

### 其他库
- Apache HttpClient (HTTP通信)
- Selenium WebDriver (通过WebMagic进行浏览器自动化)
- Lombok (代码生成)

## 🚀 快速开始

### 前提条件
- Java 17+
- Maven 3.6+

1. 克隆仓库:
```bash
git clone https://github.com/your-repo/aiseo-webclone.git
cd aiseo-webclone
```

2. 构建项目:
```bash
mvn clean install
```

3. 运行应用:
```bash
cd siteclone
mvn spring-boot:run
```

应用将在`http://localhost:8080`上可用

## 📦 构建和部署

### 构建
```bash
mvn clean package
```

### 运行
```bash
java -jar siteclone/target/siteclone-*.jar
```

## 🔧 API使用

### 克隆网站
```
POST /api/clone
```

请求体:
```json
{
  "url": "https://example.com",
  "threadCount": 5,
  "retryTimes": 3,
  "sleepTime": 1000
}
```

响应:
```json
{
  "taskId": "uuid-task-id",
  "url": "https://example.com",
  "outputDir": "/path/to/output",
  "status": "RUNNING",
  "createdAt": "2023-01-01T12:00:00",
  "updatedAt": "2023-01-01T12:00:00",
  "pagesCrawled": 0,
  "filesDownloaded": 0
}
```

### 检查任务状态
```
GET /api/clone/{taskId}
```

响应:
```json
{
  "taskId": "uuid-task-id",
  "url": "https://example.com",
  "outputDir": "/path/to/output",
  "status": "COMPLETED",
  "createdAt": "2023-01-01T12:00:00",
  "updatedAt": "2023-01-01T12:05:00",
  "pagesCrawled": 25,
  "filesDownloaded": 87
}
```

## 🤝 贡献

我们欢迎社区的贡献！以下是您可以提供帮助的方式:

1. **报告问题**: 发现bug？请提交issue并详细描述复现步骤
2. **功能请求**: 建议新功能或改进
3. **提交PR**: 提交PR修复bug或添加新功能

### 贡献指南
- Fork仓库
- 创建功能分支 (`git checkout -b feature/your-feature`)
- 提交更改 (`git commit -am '添加某功能'`)
- 推送到分支 (`git push origin feature/your-feature`)
- 提交Pull Request

请确保您的代码:
- 遵循现有的风格和模式
- 包含适当的测试
- 有清晰的文档

## 📜 许可证

本项目采用MIT许可证 - 详情请参阅[LICENSE](LICENSE)文件。