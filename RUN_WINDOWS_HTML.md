# CleanBengaluru — Complete Windows Setup (HTML/CSS/JS frontend)

## Install once

1. **JDK 21** — required by Spring Boot backend.
2. **Maven 3.9+** — required to build/run backend.
3. **MySQL 8.x + MySQL Workbench** — database.
4. **Node.js 20+** — only needed to serve the static HTML frontend using `npx`.
5. **VS Code** — recommended editor.

There is **no React, Vite, Axios, npm package installation, or Node project dependency installation** in this frontend.

## 1. Verify installations

```powershell
java -version
javac -version
mvn -version
node -v
npm -v
```

## 2. MySQL

Open MySQL Workbench and connect as `root` with the password you created during MySQL installation.

Run:

```sql
CREATE DATABASE cleanbengaluru CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Do not manually create tables unless you specifically want to use `database/schema.sql`; Hibernate is configured with `ddl-auto=update`.

## 3. Configure backend password

Open:

`backend/src/main/resources/application.properties`

Set:

```properties
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD
```

Or keep the existing `${DB_USERNAME:root}` / `${DB_PASSWORD:...}` form and set environment variables.

## 4. Start backend

Terminal 1:

```powershell
cd backend
mvn clean install
mvn spring-boot:run
```

Wait for the application to start. Test:

`http://localhost:8080/api/health`

## 5. Start HTML frontend

Terminal 2:

```powershell
cd frontend
npx --yes http-server . -p 5173
```

Open:

`http://localhost:5173`

You can also double-click `frontend/start-frontend.bat`.

## 6. Login demo accounts

| Role | Email | Password |
|---|---|---|
| Citizen | citizen@cleanbengaluru.com | citizen123 |
| Worker | worker1@cleanbengaluru.com | worker123 |
| Admin | admin@cleanbengaluru.com | admin123 |

## 7. Main features to test

### Citizen
- Register/login
- Report garbage
- Use browser location
- Upload before photo
- View possible duplicate warning
- View My Reports
- Open report details
- Verify cleanup or reopen
- View nearby bins
- Read notifications

### Worker
- Login
- View assigned tasks
- Accept task
- Reject task with reason
- Start cleaning
- Complete with after-cleaning photo
- View/update bin status where allowed

### Admin
- Dashboard analytics
- Review reports
- Assign a worker
- Reject reports
- View report map
- Add/delete/update bins
- Activate/deactivate staff
- Create worker/admin accounts

## 8. If backend reports the AVG/TIMESTAMPDIFF Hibernate error

This package already contains the compatibility fix in:

`backend/src/main/java/com/cleanbengaluru/repository/GarbageReportRepository.java`

The query uses a numeric cast around `TIMESTAMPDIFF` before `AVG`.


## Exact Windows run order
1. Start MySQL.
2. In `backend/src/main/resources/application.properties`, set `spring.datasource.password` to your MySQL root password.
3. Terminal 1: `cd backend` then `mvn clean install` then `mvn spring-boot:run`.
4. Verify `http://localhost:8080/api/health`.
5. Terminal 2: `cd frontend` then `npx --yes http-server . -p 5173`.
6. Open `http://localhost:5173`. The frontend automatically uses the same hostname for the backend (`hostname:8080`), so `localhost` and the LAN IP both work when the backend is reachable.
7. Do not run Maven from `frontend`; do not run `npm install`.
