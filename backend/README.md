# Backend Architecture

This backend deliberately stays on plain Java and the JDK's built-in
`HttpServer`. It does not require Spring Boot, Maven, or Gradle. The source is
organized into packages so each class has one clear responsibility.

## Package structure

```text
src/
├── config.properties
└── backend/
    ├── Main.java
    ├── config/
    │   └── AppConfig.java
    ├── controller/
    │   ├── ApiHandler.java
    │   ├── AuthenticatedHandler.java
    │   ├── DocumentController.java
    │   ├── HelloController.java
    │   └── LoginController.java
    ├── dao/
    │   ├── DocumentDao.java
    │   ├── HistoryDao.java
    │   ├── PdfAnnotationDao.java
    │   └── UserDao.java
    ├── db/
    │   └── Database.java
    ├── exception/
    │   └── ApiException.java
    ├── model/
    ├── security/
    │   └── JwtService.java
    ├── service/
    │   ├── AuthService.java
    │   ├── DocumentService.java
    │   └── PdfAnnotationService.java
    └── util/
        └── HttpUtil.java
```

## Layer responsibilities

### Main

`backend.Main` is only the composition root. It loads configuration, creates
the database/DAO/service/controller objects, registers URL contexts, configures
the server thread pool, and starts the server. It contains no SQL or document
business logic.

### Controllers

Controllers are the HTTP boundary. They:

- match the request method and URL;
- authenticate requests where required;
- parse JSON or multipart request data;
- call a service;
- return an HTTP/JSON response.

`ApiHandler` applies CORS and converts exceptions into consistent error
responses. `AuthenticatedHandler` handles Bearer-token validation for protected
controllers.

### Services

Services implement complete use cases:

- `AuthService` checks login credentials.
- `DocumentService` creates, updates, lists, downloads, and versions documents.
- `PdfAnnotationService` validates annotations, writes the annotated PDF,
  creates its history record, stores annotation rows, and commits or rolls back
  the operation as one database transaction.

This is where business rules belong. Services do not write HTTP responses.

### DAOs

DAOs contain JDBC and SQL only:

- `UserDao` accesses users.
- `DocumentDao` accesses the current document record.
- `HistoryDao` accesses immutable historical versions.
- `PdfAnnotationDao` stores and retrieves annotation metadata.

Keeping SQL here prevents controllers and services from knowing table details.

### Models

Models are typed data objects shared between layers. Jackson annotations on
some fields preserve the JSON names expected by the React frontend, such as
`created_by` and `created_at`.

### Supporting packages

- `AppConfig` loads and validates `config.properties`.
- `Database` creates JDBC connections.
- `JwtService` creates and validates login tokens.
- `HttpUtil` contains reusable JSON, body, and response helpers.
- `ApiException` carries an intentional HTTP status and client-safe message.

## Request flow

```text
React request
    -> HttpServer URL context
    -> Controller
    -> Service
    -> DAO
    -> MySQL
```

The result travels back through the same layers. For an annotation save, the
service also uses PDFBox and the `uploads` directory while its database
transaction is open.

## Eclipse instructions

1. Refresh the backend project so Eclipse discovers all new package folders.
2. Confirm `src` is a source folder under **Build Path -> Configure Build
   Path -> Source**.
3. Keep the existing MySQL JDBC, Jackson, and PDFBox JARs on the project
   classpath.
4. Run `backend.Main` as a Java application.
5. If an old run configuration cannot find `Main`, change its main class from
   the former class to `backend.Main`.

`config.properties` remains directly inside `src`, so Eclipse copies it to the
classpath and `AppConfig` can load it.

## Adding future functionality

For a new feature, add the smallest necessary path through the layers:

1. add a model if new structured data is needed;
2. add DAO methods for new queries;
3. implement the workflow in a service;
4. expose it through a controller;
5. register a new top-level URL context in `Main` only if the existing contexts
   cannot route it.

This keeps `Main` small and prevents HTTP, business logic, and SQL from becoming
coupled again.
