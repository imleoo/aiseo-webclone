# AISEO WebClone

A multi-module Spring Boot application for web content processing.
https://webclone.leoobai.cn/

## Features

- Site cloning functionality
- Resource downloading and processing
- Multi-module architecture

## Technology Stack

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

## Quick Start

1. Clone the repository:
```bash
git clone https://github.com/your-repo/aiseo-webclone.git
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

## Building and Running

### Prerequisites
- Java 17+
- Maven 3.6+

### Build
```bash
mvn clean package
```

### Run
```bash
java -jar siteclone/target/siteclone-*.jar
```

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Please make sure to update tests as appropriate.
