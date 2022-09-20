# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The changes documented here do not include those from the original repository.

## [Unreleased]

### Fixes
- Changed toolbar color to black for iOS. (https://outsystemsrd.atlassian.net/browse/RMET-1839)

## [4.2.0-OS39]
### Fixes
- Update error codes and messages for iOS. (https://outsystemsrd.atlassian.net/browse/RMET-1744)
- Added permission requests for Android 13. (https://outsystemsrd.atlassian.net/browse/RMET-1831)

## [4.2.0-OS38]
### Fixes
- Removed hook that adds swift support and added the plugin as dependecy. (https://outsystemsrd.atlassian.net/browse/RMET-1680)

## [4.2.0-OS37]

### Fixes
- Fix: Fixed plugin's Kotlin version to use the default version set by MABS (https://outsystemsrd.atlassian.net/browse/RMET-1437)

## [4.2.0-OS36]

### Fixes
- Fix: Fixed crash when taking picture in Nokia 4.2 running Android 10 (https://outsystemsrd.atlassian.net/browse/RMET-1370)

## [4.2.0-OS35]

### Fixes
- Fix: Fixed error messages when cancelling to take or choose a photo, and when permissions for these are denied.
- Fix: Added dialog to be shown when permissions to the photo library are denied (https://outsystemsrd.atlassian.net/browse/RMET-1272).

## [4.2.0-OS34]
### Fixes
- Fix: Removed Swift class extensions so plugin compiles with projects with non-ascii names.

## [4.2.0-OS33]
### Fixes
- Fix: Updating plugin metadata to define its compatibility with MABS 7.2.0 upwards.

## [4.2.0-OS32]
### Fixes
- Fix: New plugin release to include metadata tag in Extensability Configurations in the OS wrapper

## [4.2.0-OS31]
### Fixes
- Fix: Fixed Camera not opening on Android 12 (targetSDK & compileSDK = 31) (https://outsystemsrd.atlassian.net/browse/RMET-812)

## [4.2.0-OS10 = 4.2.0-OS30]
### Feature
- Feature: Add Crop, Rotate and Flip features on Android and iOS (https://outsystemsrd.atlassian.net/browse/RMET-626)(https://outsystemsrd.atlassian.net/browse/RMET-627)

## [4.2.0-OS9]
### Fixes
- Fix: apply correct orientation to PNG too (https://outsystemsrd.atlassian.net/browse/RMET-739)

## [4.2.0-OS8]
### Fixes
- Fix: Fixed Android conflict with file providers (https://outsystemsrd.atlassian.net/browse/RMET-738)

## [4.2.0-OS7]
### Fixes
- Fix: Fixed iOS implementation to ask for photo library permissions when needed (https://outsystemsrd.atlassian.net/browse/RMET-733)
