# Release signing

Release APKs are signed with a permanent keystore supplied only through GitHub Actions secrets.

Required repository Actions secrets:

- `RELEASE_KEYSTORE_BASE64`: Base64-encoded JKS keystore
- `RELEASE_KEYSTORE_PASSWORD`: keystore password
- `RELEASE_KEY_ALIAS`: key alias
- `RELEASE_KEY_PASSWORD`: private-key password

The workflow decodes the keystore into the ephemeral GitHub runner and passes its path and credentials to Gradle via environment variables. Do not commit the keystore or credentials.

The first release build is version `0.3.0`, version code `3`. Increase `versionCode` and `versionName` in `app/build.gradle.kts` for every subsequent release. Push a `v*` tag (for example `v0.3.0`) to publish the signed APK as a GitHub Release asset.

Keep multiple secure backups of the original JKS and its passwords. All future updates must use this exact signing key.


## In-app updater

The settings screen includes **Check for updates**. It checks the latest public GitHub
Release for an APK asset, downloads it into Android's PackageInstaller, and asks Android
to confirm installation. Android may require the user to allow installs from this app.
Updates must keep applicationId `com.christmasgsd.weather` and use the same permanent
release signing key. Publish each update as a GitHub Release with an APK asset and a
version tag (for example `v0.4.1`). The updater does not bypass Android's confirmation
screen or silently install updates.
