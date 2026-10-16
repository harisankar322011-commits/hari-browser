# Hari Browser: Full Chromium Migration

## Goal

This branch is an isolated feasibility step toward making Hari Browser a browser built from the **official open-source Chromium Android source**, rather than an app that embeds Android System WebView.

It intentionally does not replace `main`. The existing app remains available while the Chromium-native path is evaluated.

## Important distinction

The current app uses Android System WebView. Chromium is the upstream project behind WebView, but using WebView is not the same as shipping Chromium's own browser application and engine in the app.

A full Chromium migration is not a Gradle dependency swap. The Chromium Android browser, UI, build rules, and native C++ engine are built together from Chromium's source tree. The current Hari Java/WebView implementation cannot simply be dropped into that browser unchanged.

## Stage 1: prove an Android Chromium build

The manual workflow in `.github/workflows/chromium-android-prototype.yml` checks out the official Chromium Android source, builds the open-source `chrome_public_apk` target for ARM64, and publishes the resulting APK as a workflow artifact.

**Stage 1 is an unmodified Chromium baseline, not the finished Hari Browser.** It is a feasibility checkpoint. The APK may use Chromium's default product label and UI until later branding and feature-porting work is completed.

## Build host requirements

Chromium's official Android build guide currently specifies:
- x86-64 Linux; Ubuntu is the most commonly used environment.
- At least 8 GB RAM, with more than 16 GB strongly recommended.
- At least 100 GB free disk space.
- Git and Python installed.

This repository's ordinary GitHub-hosted Android runner is not suitable for a full Chromium source checkout/build because it does not provide the large persistent disk needed. The prototype workflow therefore requires a **self-hosted Linux x86-64 runner** with the label `chromium-build`, at least 120 GiB free space on the runner temp/work volume, 8 GiB RAM minimum, and preferably 16–32 GiB or more. Initial checkout and compilation can take hours and significant bandwidth.

Do not run this workflow on an untrusted repository or untrusted branch. It executes build scripts from upstream source on a self-hosted machine. It runs only when manually dispatched; it is not configured for pull requests.

## How to run the prototype workflow

1. Configure a trusted self-hosted GitHub Actions runner using the labels `self-hosted`, `linux`, `x64`, and `chromium-build`.
2. Open **Actions → Experimental Chromium Android baseline (self-hosted)**.
3. Select the `chromium-native-prototype` branch and choose **Run workflow**.
4. After a successful run, download the artifact named `chromium-android-arm64-prototype`.

If no suitable Linux build host is available, the workflow will remain queued; a normal Android phone or tablet cannot perform this source build itself.

## Stage 2: turn Chromium into Hari Browser

Only after the baseline builds successfully should the project begin the Hari-specific port:
1. Replace default browser branding with original Hari Browser name, icons, colours, and start page. Do not copy Google Chrome logos or imply an official Google product.
2. Port Hari's search choices, curated shortcuts/tools, and home-dashboard functions into the Chromium browser architecture.
3. Define how bookmarks, browsing history, downloads, private tabs, settings, and site permissions are stored and migrated. Existing WebView storage will not automatically transfer.
4. Review Chromium security updates and update cadence; establish a repeatable update/build process.
5. Validate on a real ARM64 Android device, including install/update behaviour, websites, downloads/uploads, playback, privacy modes, accessibility, and memory/battery use.

A build from current upstream Chromium source will not automatically retain all existing Hari features. These need to be deliberately ported and tested.

## Licensing and services

Chromium has its own open-source licence and third-party notices. Keep those notices with any redistributed build and review every bundled dependency's terms. Building the open-source Chromium target does not automatically provide Google's proprietary Chrome services, Google API keys, sync, or Chrome-branded assets.

## Upstream reference

- [Official Chromium Android build instructions](https://chromium.googlesource.com/chromium/src/+/main/docs/android_build_instructions.md)
- [Chromium source repository](https://chromium.googlesource.com/chromium/src/)
