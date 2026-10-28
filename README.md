# Flashtool Community Edition

An unofficial, community-maintained continuation of **Flashtool for Sony Xperia devices**, originally developed by Androxyde and contributors.

## About this project

Flashtool Community Edition aims to preserve and improve the original application for modern operating systems and hardware. It is maintained independently of the original upstream project.

Our initial focus is native Apple Silicon support on macOS. Future development will address bugs, compatibility, obsolete dependencies and functionality that remains useful to Xperia device owners.

## Download and release status

**Available pre-release: Flashtool Community Edition 0.9.36 RC2 (macOS Apple Silicon).**

**[Download RC2 from GitHub Releases](https://github.com/settloe99/Flashtool/releases/tag/v0.9.36-community.1-rc2)**

This is an initial community pre-release, not a fully validated stable release. It has been tested on an Apple Silicon Mac with a Sony Xperia E1; compatibility with other Xperia devices has not been comprehensively established.

## Apple Silicon support

The macOS ARM64 edition includes:

- Native Apple Silicon launcher and SWT interface
- Bundled Java 17 runtime
- Bundled ADB, Fastboot, libusb and other required native utilities
- Self-contained macOS DMG installer
- Improvements to Fastboot handling, macOS shutdown behaviour and Sony boot-image operations

**End users do not need to install Homebrew, Java or the bundled command-line dependencies separately.**

Legacy rooting and exploit payloads have been excluded from this release, and associated features are disabled. The Community Edition should not be assumed to provide every feature of the original Flashtool distribution.

### Installation

Download the DMG from the release page and copy `FlashTool.app` to your Applications folder.

The application is ad-hoc signed but **not notarised by Apple**. macOS may require you to approve opening it through **System Settings > Privacy & Security**. Do not disable Gatekeeper system-wide.

## Licensing and attribution

Flashtool was originally developed by Androxyde and other contributors. Earlier upstream releases included **GNU GPLv3-or-later** licensing documentation.

See [`COPYING`](COPYING) and [`LICENSING.md`](LICENSING.md) for the historical licensing basis and its limitations, and [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for bundled-component attribution. Some questions concerning later upstream additions and third-party redistribution rights remain unresolved and documented. The presence of licensing notices should not be interpreted as confirmation that all such questions have been settled.

This project is **not affiliated with or endorsed by Androxyde or Sony**.

## Community maintenance and feedback

Development and maintenance take place independently of the original upstream repository.

Please use [GitHub Issues](https://github.com/settloe99/Flashtool/issues) to report bugs or device compatibility problems. Include your macOS version, Xperia model, the steps to reproduce the issue and relevant logs where possible. Future changes and releases will be tracked in this repository.

## Safety

Flashing firmware, modifying bootloaders and working with device partitions can cause data loss or render devices unusable. Back up important data and verify device and firmware compatibility before proceeding.
