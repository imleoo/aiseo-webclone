# Project Description
This is an aiseo project using the latest Spring Boot 3.5.3 version, and it's a multi-module project. Currently implemented modules include: siteclone

**Important Note:** The most fundamental principle is that when modifying code, you must analyze the target code files and related code to ensure the changes won't miss any existing business logic.

## Project Structure
### Project Code Structure

The project adopts a standard Spring Boot multi-module structure. The complete file structure is as follows:

```
aiseo/
├── .gitignore - Git version control ignore rules
├── pom.xml - Parent project POM file
└── siteclone/ - Site clone module
    ├── pom.xml - Module POM file
    ├── src/
    │   ├── main/
    │   │   ├── java/
    │   │   │   ├── com/jiwu/aiseo/siteclone/
    │   │   │   │   ├── SiteCloneApplication.java - Spring Boot main application class
    │   │   │   │   ├── config/
    │   │   │   │   │   └── AsyncConfig.java - Async task configuration
    │   │   │   │   ├── controller/
    │   │   │   │   │   ├── FileController.java - File operation controller
    │   │   │   │   │   └── SiteCloneController.java - Site clone controller
    │   │   │   │   ├── downloader/
    │   │   │   │   │   └── CustomHttpClientDownloader.java - Custom HTTP downloader
    │   │   │   │   ├── dto/
    │   │   │   │   │   ├── CloneRequest.java - Clone request DTO
    │   │   │   │   │   └── CloneResponse.java - Clone response DTO
    │   │   │   │   ├── exception/
    │   │   │   │   │   └── GlobalExceptionHandler.java - Global exception handler
    │   │   │   │   ├── model/
    │   │   │   │   │   ├── CloneTask.java - Clone task model
    │   │   │   │   │   └── CloneTaskStatus.java - Clone task status enum
    │   │   │   │   ├── processor/
    │   │   │   │   │   └── WebsiteMirrorProcessor.java - Website mirror processor
    │   │   │   │   └── service/
    │   │   │   │       ├── FileService.java - File service
    │   │   │   │       └── SiteCloneService.java - Site clone service
    │   │   │   └── org/apache/http/annotation/
    │   │   │       └── ThreadSafe.java - Thread safety annotation
    │   │   └── resources/
    │   │       ├── application.yml - Application configuration file
    │   │       └── static/
    │   │           ├── favicon.ico - Website icon
    │   │           ├── index.html - Homepage HTML
    │   │           └── js/
    │   │               └── app.js - Frontend JavaScript
    │   └── test/
    │       └── java/ - Test code directory
    └── target/ - Build output directory
```
