# UK Fuel Prices

A Scala-based web application to track and visualize fuel prices across the United Kingdom. It fetches data from official government sources and provides a user-friendly interface to search for the best prices near you.

## Features

- **Search by Postcode**: Find the cheapest fuel prices within a specific radius of any UK postcode.
- **Fuel Type Filtering**: Search for E10, B7 (Diesel), Premium Diesel, and more.
- **Price History**: Visualize price trends for specific fuel stations with interactive charts.
- **GDS Design System**: Built using HMRC Frontend components for a consistent and accessible user experience.
- **Automatic Updates**: Scheduled jobs to fetch the latest price data from official sources.

## Tech Stack

- **Language**: [Scala 3](https://www.scala-lang.org/)
- **Framework**: [Play Framework 3.0](https://www.playframework.com/) (Pekko-based)
- **Build Tool**: [SBT](https://www.scala-sbt.org/)
- **Database**: MariaDB
- **Data Access**: [Anorm](https://playframework.github.io/anorm/)
- **Frontend**: [HMRC Frontend](https://github.com/hmrc/play-frontend-hmrc) (Gov.uk Design System)
- **Monitoring**: OpenTelemetry, Pyroscope

## Prerequisites

- **Java**: Version 25 or higher.
- **SBT**: Standard Scala Build Tool.
- **Docker**: For running MariaDB locally (optional if you have MariaDB installed).

## Local Setup

### 1. Database Setup

The application requires a MariaDB database named `fuel_price`. You can start one quickly using Docker:

```bash
docker run --name fuel-db \
  -e MARIADB_ROOT_PASSWORD=example \
  -e MARIADB_DATABASE=fuel_price \
  -p 3306:3306 \
  -d mariadb:latest
```

After starting the database, you need to initialize the schema and populate initial data:

1. **Initialize Schema**: Run the SQL script located at `doc/tables.sql`.
   ```bash
   docker exec -i fuel-db mariadb -u root -pexample fuel_price < doc/tables.sql
   ```

2. **Populate Fuel Types**: The application expects specific fuel types to be present in the `fuel_types` table. Run the following SQL:
   ```sql
   INSERT INTO fuel_types (name) VALUES 
   ('B7_PREMIUM'), ('B7_STANDARD'), ('B10'), ('HVO'), ('E10'), ('E5');
   ```

3. **Create Admin User**: To access the admin area, you'll need to create a user in the `fuel_admins` table. The password should be hashed (the app uses `password4j`).

The application is configured to connect to `localhost:3306` with username `root` and password `example` by default. These can be overridden using environment variables (see [Configuration](#configuration)).

### 2. Run the Application

The project supports SBT build tools.

#### Using SBT (Recommended)

Navigate to the project root and run:

```bash
sbt run
```

The application will be available at `http://localhost:9234`.

### 3. Initial Data Population

After the application is running, you may need to trigger the initial data fetch from the official sources. You can do this via the admin endpoints (require login):

- Update all stations: `http://localhost:9234/admin/update-fuel-stations`
- Update all prices: `http://localhost:9234/admin/update-fuel-prices`

### 4. Running Tests

To run the test suite:

```bash
sbt test
```

## Configuration

The application can be configured using environment variables. Key variables include:

| Variable | Description | Default |
|----------|-------------|---------|
| `APP_SECRET` | Play application secret | - |
| `DB_URL` | MariaDB connection URL | `jdbc:mariadb://localhost:3306/fuel_price?createDatabaseIfNotExist=true` |
| `DB_USER` | Database username | `root` |
| `DB_PASSWORD` | Database password | `example` |
| `FUEL_CLIENT_ID` | Client ID for the fuel finder API | `DUMMY_CLIENT_ID` |
| `FUEL_CLIENT_SECRET` | Client Secret for the fuel finder API | `DUMMY_SECRET_ID` |
| `PARTIAL_UPDATE_IS_ENABLED` | Enable scheduled price updates | `false` |

## Docker Deployment

A `docker-compose.yml` is provided for deploying the application. Note that it expects some environment variables to be set (e.g., in a `.env` file).

To build the Docker image locally:

```bash
sbt docker:publishLocal
```

## License

This project is licensed under the terms included in the [LICENSE](LICENSE) file.