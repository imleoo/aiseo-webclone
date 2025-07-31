# AISEO WebClone

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Build Status](https://github.com/your-repo/aiseo-webclone/actions/workflows/build.yml/badge.svg)](https://github.com/your-repo/aiseo-webclone/actions)
[![中文文档](https://img.shields.io/badge/文档-中文版-blue.svg)](README_ZH.md)

A powerful multi-module Spring Boot application for web content processing and cloning, A tool for **https://Aiseo.icu/**.

🌐 Live Demo: [https://webclone.leoobai.cn/](https://webclone.leoobai.cn/)

## ✨ Features

- **Website Cloning**: Clone entire websites with all resources
- **Content Processing**: Extract and process web content efficiently 
- **Modular Architecture**: Well-organized multi-module structure
- **Customizable**: Easily extendable for specific needs
- **High Performance**: Optimized for large-scale web processing
- **Security**: Built-in security measures to prevent unsafe operations
- **Resource Management**: Efficient handling of images, CSS, JS and other web resources
- **Asynchronous Processing**: Non-blocking task execution with status tracking

## 🛠 Technology Stack

### Core Frameworks
- Spring Boot 3.5.3 (with Spring Web MVC)
- Java 17

### Web Processing
- Jsoup 1.15.3 (HTML parsing and manipulation)
- WebMagic 1.0.3 (web crawler framework)

### Build & Tools
- Maven (dependency management)
- Git (version control)

### Other Libraries
- Apache HttpClient (HTTP communication)
- Selenium WebDriver (browser automation via WebMagic)
- Lombok (code generation)

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.6+

1. Clone the repository:
```bash
git clone https://github.com/your-repo/aiseo-webclone.git
cd aiseo-webclone
```

2. Build the project:
```bash
mvn clean install
```

3. Run the application:
```bash
cd siteclone
mvn spring-boot:run
```

The application will be available at `http://localhost:8080`

## 📦 Building and Deployment

### Build
```bash
mvn clean package
```

### Run
```bash
java -jar siteclone/target/siteclone-*.jar
```

## 🔧 API Usage

### Clone a Website
```
POST /api/clone
```

Request body:
```json
{
  "url": "https://example.com",
  "threadCount": 5,
  "retryTimes": 3,
  "sleepTime": 1000
}
```

Response:
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

### Check Task Status
```
GET /api/clone/{taskId}
```

Response:
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

## 🤝 Contributing

We welcome contributions from the community! Here's how you can help:

1. **Report Issues**: Found a bug? Open an issue with detailed steps to reproduce
2. **Feature Requests**: Suggest new features or improvements
3. **Pull Requests**: Submit PRs for bug fixes or new features

### Contribution Guidelines
- Fork the repository
- Create a feature branch (`git checkout -b feature/your-feature`)
- Commit your changes (`git commit -am 'Add some feature'`)
- Push to the branch (`git push origin feature/your-feature`)
- Open a Pull Request

Please ensure your code:
- Follows existing style and patterns
- Includes appropriate tests
- Has clear documentation

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

[中文文档](README_ZH.md)
