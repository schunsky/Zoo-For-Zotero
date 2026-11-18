
# Zoo for Zotero [![Build Status](https://travis-ci.org/mickstar/Zoo-For-Zotero.svg?branch=master)](https://travis-ci.org/mickstar/Zoo-For-Zotero) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/80b55ec190444aab978050ad03a7b4b5)](https://www.codacy.com/manual/mickstar/Zoo-For-Zotero?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=mickstar/Zoo-For-Zotero&amp;utm_campaign=Badge_Grade)

  

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"

alt="Download from Google Play"

height="80">](https://play.google.com/store/apps/details?id=com.mickstarify.zooforzotero)

  

Zoo for Zotero is an Android application for viewing zotero libraries.

  

<img src="github/screenshot1.png" width="280"/> <img src="github/screenshot2.png" width="280"/> <img src="github/screenshot3.png" width="280" />

  

## Features

- Material Design

- Support for organization via Collections, including subcollections

- Supports two way syncing of attachments for (including WebDav)

- Support for viewing group libraries

- Supports the creating, editing and deleting Zotero's notes

- Flexible searching of your library

- Written in Kotlin using the MVP design pattern for a fast native experience

- Currently readonly, but have plans allow for editing in the future

- Support for the Zotero Account Sync service

  

## Libraries utilised

- Dagger2

- RxJava2

- Room

- Retrofit

- And more, the full list can be found in the app.gradle.

## Guides

 [Instructions for using Syncthing](https://github.com/arpsoid/zoo-syncthing/blob/main/README.md)

## How to Contribute

Any contributions, whether it be pull requests or even suggestions are welcome!

I am kind of busy but I will get to it!

  

## Building

### 1. Zotero API key (needed for any build)

The app logs in to zotero.org with OAuth, so each build needs its own Zotero app registration:

1. While logged in to zotero.org, register a new application at <https://www.zotero.org/oauth/apps>.
   Use this **callback URL** exactly: `zooforzotero://oauth_callback`
2. Create `apikeys.properties` in the repository's top folder (next to `settings.gradle`) with the
   client key and secret Zotero gives you. Keep the quotation marks:

   ```properties
   zotero_api_key="your_client_key"
   zotero_api_secret="your_client_secret"
   ```

`apikeys.properties` is listed in `.gitignore`, so it is never committed. Treat the secret like a password.

No `google-services.json` is needed: the app no longer uses Firebase.

### 2. Build with Docker (Linux, no Android Studio needed)

The `docker-build/` folder contains a build environment with JDK 17 and the Android SDK.
With Docker installed, run from the repository's top folder:

```bash
docker-build/zoo-build.sh assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

- The first run downloads a few GB and takes a while. Two Docker volumes keep things between runs:
  `zoo-gradle-cache` (downloaded libraries, so later builds are fast) and `zoo-android-home`
  (the debug signing key, so each new APK installs as an update of the previous one).
- `docker-build/zoo-build.sh` with no arguments runs the unit tests and then builds the APK. This
  currently fails, because one outdated test file (`ManualApiViewModelTest.kt`) doesn't compile, so
  use `assembleDebug` as above. Any other Gradle tasks can be passed too, e.g. `clean` or `tasks`.
- To build a different checkout, set `PROJECT`: `PROJECT=/path/to/checkout docker-build/zoo-build.sh assembleDebug`
- If building the image fails while downloading the Android command-line tools (HTTP 404), copy the
  current build number from <https://developer.android.com/studio#command-line-tools-only>
  into the `CMDLINE_TOOLS_VERSION` line of `docker-build/Dockerfile`.


## License

* The application is released fully under GPLv3

* This application is 100% Free with no ads!

* We utilise graphical resources directly from the desktop zotero client.

  

  

Credits:

- Michael Johnston

  

This is a third party zotero client and is not affiliated with Zotero.
