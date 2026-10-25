# Flashtool Community Edition

An unofficial, community-maintained continuation of **Flashtool for Sony Xperia devices**, originally developed by Androxyde and contributors.

## About this project

The aim of Flashtool Community Edition is to preserve and improve Flashtool for modern operating systems and hardware.

Our initial focus is native Apple Silicon support on macOS, with longer-term plans to address existing bugs, modernise dependencies, improve compatibility and maintain features that remain useful to Xperia device owners.

## Apple Silicon support

An ARM64-compatible version is under development, with:

- Native Apple Silicon launcher and SWT interface
- Bundled Java runtime
- Bundled ADB, Fastboot and native utilities
- Standalone macOS DMG installer
- Improvements to Fastboot handling, shutdown behaviour and boot-image operations

The intention is that users will not need to install Homebrew, Java or other dependencies separately.

## Release status

**Public release in preparation.**

The current preview builds are being validated for functionality, installation compatibility and redistribution compliance. No public release should be considered available until an installer is published through this repository's Releases page.

## Licensing and attribution

Flashtool was originally developed by Androxyde and other contributors.

Earlier upstream releases included GNU GPLv3-or-later licensing documentation. The licensing status of certain later additions and bundled third-party components is being reviewed before binary distribution.

All original copyright notices and applicable third-party licences will be respected.

This project is not affiliated with or endorsed by Androxyde or Sony.

## Community maintenance

Development and maintenance take place independently of the original upstream repository.

Bug reports, compatibility information, test results and contributions will be welcome as the project progresses.

## Safety

Flashing firmware, modifying bootloaders and working with device partitions can cause data loss or render devices unusable. Users should maintain appropriate backups and verify device and firmware compatibility before proceeding.
