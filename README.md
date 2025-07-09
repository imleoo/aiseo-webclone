# AISEO WebClone

A multi-module Spring Boot application for web content processing.

## Features

- Site cloning functionality
- Resource downloading and processing
- Multi-module architecture

## Technology Stack

- Spring Boot 3.5.3
- Jsoup for HTML parsing
- Apache HttpClient

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
