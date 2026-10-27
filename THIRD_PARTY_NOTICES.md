# Flashtool Community Edition: Third-party component inventory and attribution

**Status: PRE-RELEASE INVENTORY, NOT FINAL REDISTRIBUTION CLEARANCE.**

This document identifies components observed in **Flashtool 0.9.36.0 macOS ARM64 Community RC1**. It accompanies the RC2 preparation build so we can finish attribution against exactly the distributed components. Recording a component here **does not** prove that the actual bundled binary is licensed for redistribution, or that all licence conditions have been satisfied. Do not publish the DMG until the separate release checklist is closed.

## Main application

The Flashtool application was originally developed by Androxyde and contributors. Historical upstream revision `d4cba66` contained GPLv3-or-later licensing material, preserved as the separate `LICENSE` file in the app and `COPYING` in source. The licence status of all additions through upstream 0.9.36 has not been conclusively established; see `LICENSING.md`. Community modifications are documented in source history. This notice does not relicense third-party works.

Source and project history: https://github.com/Androxyde/Flashtool and https://github.com/settloe99/Flashtool

## Bundled Java runtime

- **Eclipse Adoptium Temurin 17.0.20.1+1**, macOS ARM64, custom `jlink` runtime.
- **Licence family:** OpenJDK GNU GPL version 2 with the Classpath Exception, with module-level third-party notices where applicable.
- The executable includes its own `Contents/runtime/Contents/Home/legal` directory; retain this directory intact.
- Upstream: https://adoptium.net/docs/faq
- **Publication check:** Confirm the exact distribution, accompanying source obligations and any additional notices that must accompany the custom `jlink` image.

## Bundled native utilities

| Component | Source/provenance | Reported licensing context | Outstanding release work |
|---|---|---|---|
| `adb`, `fastboot` | Identical to the developer's Homebrew-installed Android Platform Tools copies at RC1 preparation | AOSP is predominantly Apache-2.0, but the exact platform-tools binaries and their included code require checking | Obtain official package notices, record exact binary release, validate any third-party bundled licensing and redistribution basis |
| `libusbx-1.0.0.dylib` (and relative symlink) | Byte-identical to the Homebrew libusb development copy before relocation and signing | libusb project: LGPL-2.1-or-later | Include complete LGPL licence and applicable source / relinking information; document the changed install name applied to the packaged binary |
| `unyaffs` | Byte-identical to Homebrew's `unyaffs` installed copy before packaging | Homebrew formula lists GPL-2.0-only | Verify precise source version, include GPLv2 licence and corresponding source/offer as required, distinguish separately executed utility from main Java programme |

References: https://source.android.com/docs/setup/about/faqs ; https://android.googlesource.com/platform/packages/modules/adb/ ; https://github.com/libusb/libusb ; https://formulae.brew.sh/formula/unyaffs

## Java libraries shipped with the application

This is a complete list of the **51 third-party application JARs** observed in the RC1 installer, excluding `Flashtool.jar` and the Temurin runtime's `jrt-fs.jar`. Exact RC1 SHA-256 values are recorded in the accompanying `THIRD_PARTY_JARS_RC1.csv` build-audit file. A changed JAR requires rechecking its corresponding row.

The following are *licence identification leads*, not a blanket declaration for every JAR:

- **Eclipse Platform and SWT**: Eclipse Public License 2.0 appears relevant. GPL secondary licensing is conditional on the relevant initial contributor's notice; EPL-2.0 is not automatically GPL-compatible. Check each module's actual notices and how it is combined.
- **Eclipse JGit**: Eclipse Distribution License (EDL/BSD-family) is a relevant project licence; verify the included release and files.
- **Other Maven libraries**: Inspect the exact release's POM and embedded `META-INF` copyright / licence / NOTICE materials before assigning its final licence. Copy any required licence texts or notices into the eventual public package.

Sources: https://www.eclipse.org/legal/epl/epl-v20.html ; https://www.eclipse.org/org/documents/edl-v10/

### JAR inventory (exact RC1 filenames)

- `JavaEWAH-1.1.13.jar`
- `apk-parser-2.6.10.jar`
- `bcprov-jdk18on-1.71.jar`
- `bcutil-jdk18on-1.71.jar`
- `bigdoc-0.3.0.jar`
- `bsh-2.0b6.jar`
- `checker-qual-3.12.0.jar`
- `commons-compress-1.21.jar`
- `commons-io-2.11.0.jar`
- `delight-rhino-sandbox-0.0.15.jar`
- `error_prone_annotations-2.11.0.jar`
- `failureaccess-1.0.1.jar`
- `finbin-0.6.2.jar`
- `guava-31.1-jre.jar`
- `ini4j-0.5.4.jar`
- `j2objc-annotations-1.3.jar`
- `jarchivelib-1.2.0.jar`
- `jbbp-2.0.4.jar`
- `jdom2-2.0.6.1.jar`
- `jelf-0.7.0.jar`
- `jna-5.12.1.jar`
- `jna-platform-5.12.1.jar`
- `jsr305-3.0.2.jar`
- `listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar`
- `logback-classic-1.4.0.jar`
- `logback-core-1.4.0.jar`
- `lz4-java-1.8.0.jar`
- `org.eclipse.core.commands-3.10.200.jar`
- `org.eclipse.core.contenttype-3.9.900.jar`
- `org.eclipse.core.jobs-3.15.900.jar`
- `org.eclipse.core.runtime-3.25.0.jar`
- `org.eclipse.equinox.app-1.7.600.jar`
- `org.eclipse.equinox.common-3.21.0.jar`
- `org.eclipse.equinox.preferences-3.12.100.jar`
- `org.eclipse.equinox.registry-3.12.600.jar`
- `org.eclipse.jface-3.26.0.jar`
- `org.eclipse.jgit-6.3.0.202209071007-r.jar`
- `org.eclipse.jgit.archive-6.3.0.202209071007-r.jar`
- `org.eclipse.osgi-3.24.300.jar`
- `org.eclipse.swt-3.132.0.jar`
- `org.eclipse.swt.cocoa.macosx.aarch64-3.132.0.jar`
- `org.osgi.core-6.0.0.jar`
- `org.osgi.service.prefs-1.1.2.jar`
- `osgi.annotation-8.0.1.jar`
- `picocli-4.6.3.jar`
- `proxy-vole-1.0.17.jar`
- `rhino-runtime-1.7.13.jar`
- `simple-4.1.21.jar`
- `slf4j-api-2.0.0.jar`
- `slf4j-ext-2.0.0.jar`
- `ttorrent-core-1.5.jar`

## Other bundled resources

The RC1 catalogue also includes 27 scripts in `custom/shells`, including historically rooting-related shell scripts. Their presence and any runnable dependency paths must be reviewed; removing the `custom/root` directory alone is not proof all legacy functions are gone. Also assess icon/artwork and any firmware/device data included in the distributed source/resources.

## Supplying full notices and corresponding source

For public distribution, fill the outstanding verification items above and include the relevant complete licence texts, upstream copyright/NOTICE material, and corresponding source or source-offer arrangements required by each licence. The repository's `COPYING` addresses the historical principal-application GPL grant only; it does not satisfy every component-specific obligation.

This inventory is produced for review of the actual RC1 baseline. It is not legal advice and does not certify RC2 as publishable.
