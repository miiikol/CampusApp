# Campus App

This is an Android application built with Kotlin, MVVM architecture, and Jetpack components.

## Architecture

- **MVVM**: Model-View-ViewModel pattern.
- **Repository**: Single source of truth for data.
- **Clean Architecture**: Separation of concerns (Data, Domain/Common, UI).

## Tech Stack

- **Kotlin**: Programming language.
- **Jetpack Components**:
  - **ViewModel**: UI state holder.
  - **LiveData/StateFlow**: Reactive data stream.
  - **Room**: Local database (SQLite abstraction).
  - **Navigation**: Fragment navigation.
  - **Hilt**: Dependency Injection.
- **Retrofit**: Network client.
- **Glide**: Image loading.
- **Coroutines**: Asynchronous programming.

## Setup Instructions

1.  Open Android Studio.
2.  Select **Open** and navigate to the `CampusApp` directory.
3.  Wait for Gradle sync to complete.
4.  Run the app on an emulator or physical device.

## Modules Implemented

1.  **Course Schedule**: Displays a weekly course grid (Custom View).
2.  **News**: Displays news list (cached locally).
3.  **Market**: Second-hand market items.
4.  **Lost & Found**: Lost and found items.
5.  **Common**: Base classes, DI setup, Network/DB configuration.

## Test Accounts

- Student test account: `20260001` (password uses your current backend test data)
- Admin test account: `20269999` / `admin123456` (built-in fallback in `campus_api/index.php`)

## Moderation Workflow (MVP)

1. Student submits Market/LostFound content.
2. Backend marks it as `PENDING` first (not visible in public list yet).
3. Admin enters admin page and reviews pending items.
4. Approved items become visible in student-side list; rejected items remain hidden.
5. News detail page has comments; admin can ban harmful comments.
