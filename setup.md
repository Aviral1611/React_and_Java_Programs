# Setup Guide

This guide covers how to set up Phase 2 of the React + Java project, focusing on the backend running in Eclipse with no external build tools.

## Prerequisites

- **Eclipse IDE** (for Java Developers)
- **Java JDK** (version 11 or higher recommended)
- **MySQL/PostgreSQL** (or whichever DB your `db_setup.sql` is meant for)
- **Node.js** and **npm** (for the React frontend)

## 1. Database Setup

1. Open your database management tool.
2. Run these scripts from the repository root in this order:
   - `db_setup.sql`
   - `document_setup.sql`
   - `pdf_setup.sql` (only once, because it adds columns)
   - `annotation_setup.sql`

`annotation_setup.sql` is safe to run again and creates the table used to reload saved PDF annotations.

## 2. Backend Setup (Eclipse)

Since we are not using Maven or Gradle, we will configure the project manually in Eclipse.

1. **Create the Project:**
   - Open Eclipse and set your workspace to a convenient location.
   - Go to **File -> New -> Java Project**.
   - Set the **Project Name** to `backend`.
   - Ensure the default location is unchecked, and point it to the `backend`
     folder inside your local `React+Java` repository.
   - Click **Finish**.

2. **Add External JARs:**
   - Right-click on the `backend` project in the **Package Explorer** and select **Build Path -> Configure Build Path...**
   - Go to the **Libraries** tab.
   - Select **Classpath** (or just use the main view if you are on an older Java version).
   - Click **Add External JARs...**
   - Add the MySQL JDBC driver, Jackson Databind (plus its Core and Annotations JARs), and Apache PDFBox 3 (plus its required JARs).
   - Click **Apply and Close**.

3. **Running the Server:**
   - The project uses the built-in `com.sun.net.httpserver.HttpServer` (or HttpsServer).
   - In Package Explorer, locate `backend.Main` under `src/backend/Main.java`.
   - Right-click `Main.java` -> **Run As -> Java Application**.
   - If an older Eclipse run configuration still points to the previous default-package class,
     open **Run Configurations -> Java Application** and set the main class to `backend.Main`.
   - The server starts on port 8080 unless `server.port` in `src/config.properties` overrides it.

4. **Package Layout:**
   - `backend` - application startup and dependency wiring
   - `backend.config` - configuration loading
   - `backend.controller` - HTTP endpoints, request parsing, and responses
   - `backend.service` - application workflows and business rules
   - `backend.dao` - SQL and database persistence
   - `backend.model` - data passed between the layers and serialized as JSON
   - `backend.security` - JWT creation and validation
   - `backend.db` - database connection creation
   - `backend.util` - shared HTTP utilities
   - `backend.exception` - API errors and HTTP status codes

For more detail, see `backend/README.md`.

## 3. Frontend Setup

1. Open a terminal or command prompt.
2. Navigate to the `frontend` folder: `cd frontend`
3. Install dependencies: `npm install`
4. Start the development server: `npm run dev`

The React app will be available locally and is configured to make API calls to your Java backend.
