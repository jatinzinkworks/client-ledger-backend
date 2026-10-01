# Local Development Setup – Nexa

Follow these steps in order to run the Nexa stack on your local machine.

## Prerequisites

- [Google Cloud CLI](https://cloud.google.com/sdk/docs/install) (`gcloud`)
- [Docker](https://docs.docker.com/get-docker/) with Docker Compose
- [Node.js](https://nodejs.org/) and [pnpm](https://pnpm.io/installation)
- For mobile testing: an Android phone with **USB debugging** enabled, and `adb` installed

---

## 1. Set up GCP CLI config for the Nexa project

Point the `gcloud` CLI at the Nexa project.

```bash
# If you use a named configuration
gcloud config configurations activate nexa

# Or set the project directly
gcloud config set project <nexa-project-id>
```

Verify:

```bash
gcloud config list
```

## 2. `glogin` – authenticate the gcloud CLI

```bash
glogin
```

> `glogin` is an alias for `gcloud auth login`. It opens a browser to sign in with your Google account.

## 3. `galogin` – set up Application Default Credentials

```bash
galogin
```

> `galogin` is an alias for `gcloud auth application-default login`. This lets the backend application access GCP services locally using your credentials.

## 4. Start PostgreSQL with Docker Compose

From the directory containing the `docker-compose.yml` file:

```bash
docker compose up -d
```

Check the database container is running:

```bash
docker compose ps
```

## 5. Start the Backend application

In a new terminal, from the backend project folder:

```bash
<backend start command>
```

Make sure the backend connects to the local PostgreSQL database before moving on.

## 6. Start the Frontend application

In a new terminal, from the frontend project folder, run one of the following:

```bash
# Web
pnpm dev

# Mobile
pnpm dev:mobile
```

## 7. (Optional) Test on a mobile device

1. Connect your phone to your computer via **USB**.
2. Confirm the device is detected:
   ```bash
   adb devices
   ```
3. In the frontend terminal (running `pnpm dev:mobile`), press **`a`** to launch the app on the connected Android device.

---

## Shutting down

```bash
# Stop the frontend and backend with Ctrl + C in their terminals

# Stop the database
docker compose down
```
