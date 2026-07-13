# Setup Guide

This guide covers how to set up Phase 2 of the React + Java project, focusing on the backend running in Eclipse with no external build tools.

## Prerequisites

- **Eclipse IDE** (for Java Developers)
- **Java JDK** (version 11 or higher recommended)
- **MySQL/PostgreSQL** (or whichever DB your `db_setup.sql` is meant for)
- **Node.js** and **npm** (for the React frontend)

## 1. Database Setup

1. Open your database management tool.
2. Run the `db_setup.sql` script located in the root directory to create the necessary tables and initial data.

## 2. Backend Setup (Eclipse)

Since we are not using Maven or Gradle, we will configure the project manually in Eclipse.

1. **Create the Project:**
   - Open Eclipse and set your workspace to a convenient location.
   - Go to **File -> New -> Java Project**.
   - Set the **Project Name** to `backend`.
   - Ensure the default location is unchecked, and point it to the `backend` folder inside your `React+Java` repository (`c:\Users\Aviral Bansal\Downloads\React+Java\backend`).
   - Click **Finish**.

2. **Add External JARs:**
   - Right-click on the `backend` project in the **Package Explorer** and select **Build Path -> Configure Build Path...**
   - Go to the **Libraries** tab.
   - Select **Classpath** (or just use the main view if you are on an older Java version).
   - Click **Add External JARs...**
   - Select any required JARs for your project (for example, the JDBC driver for your database like `mysql-connector-j-x.x.x.jar` or `postgresql-x.x.x.jar`).
   - Click **Apply and Close**.

3. **Running the Server:**
   - The project uses the built-in `com.sun.net.httpserver.HttpServer` (or HttpsServer).
   - Locate `Main.java` inside `src/`.
   - Right-click `Main.java` -> **Run As -> Java Application**.
   - The server will start on port 8080 (or the port specified in your code).

## 3. Frontend Setup

1. Open a terminal or command prompt.
2. Navigate to the `frontend` folder: `cd frontend`
3. Install dependencies: `npm install`
4. Start the development server: `npm run dev`

The React app will be available locally and is configured to make API calls to your Java backend.
