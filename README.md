Google Cloud Storage Plugin for Fess
[![Java CI with Maven](https://github.com/codelibs/fess-storage-gcs/actions/workflows/maven.yml/badge.svg)](https://github.com/codelibs/fess-storage-gcs/actions/workflows/maven.yml)
====================================

Google Cloud Storage support for [Fess](https://github.com/codelibs/fess).

This plugin provides two things:

* the **storage client** behind `storage.type=gcs`, which backs the Storage page of the
  administration screen and its REST API -- the only callers of `StorageClientFactory` in Fess
* the **crawler client** behind `gcs:` URLs, so that a file crawling configuration can point at a
  bucket

Both were part of the Fess distribution until 15.9. They moved here with the Google Cloud Storage
SDK, which is about 15 MiB of jars that most installations never use.

## Installation

```
$ bin/fess-setup install plugin fess-storage-gcs
```

Or download the jar from [maven.codelibs.org](https://maven.codelibs.org/org/codelibs/fess/fess-storage-gcs/)
and put it in `app/WEB-INF/plugin`. Restart Fess afterwards: the components this plugin
contributes are read when the DI container is built.

## Configuration

### Object storage

Set these in the admin general page, or, with Fess 15.10 or later, as environment variables named
after the key: `FESS_SYSTEM_` followed by the key in upper case, with every character other than a
letter or a digit replaced by `_` (`storage.credentials.path` is
`FESS_SYSTEM_STORAGE_CREDENTIALS_PATH`). They can also be passed to the JVM as
`-Dfess.system.storage.type=...`. They are read through `FessProp#getSystemProperty`, which looks
in `system.properties`, where the admin general page saves them, then at a non-empty
`fess.system.<key>` option, then at the `FESS_SYSTEM_<KEY>` variable -- not in
`fess_config.properties`:

| Key | Value |
| --- | --- |
| `storage.type` | `gcs` |
| `storage.bucket` | the bucket name |
| `storage.project.id` | the GCP project (optional) |
| `storage.credentials.path` | path to a service account JSON file (optional) |
| `storage.endpoint` | a custom endpoint, for a GCS emulator (optional) |

Without `storage.credentials.path`, the client uses application default credentials, so
`GOOGLE_APPLICATION_CREDENTIALS` works as usual.

### Crawling a bucket

Create a file crawling configuration whose path is `gcs://<bucket>/<prefix>`. The plugin adds
`gcs` to the crawler's file protocol list on startup, so `crawler.file.protocols` needs no edit.

## Version

| Fess | Plugin |
| --- | --- |
| 15.9.x | 15.9.x |

## How it plugs in

Nothing here is wired by class name from Fess. The plugin ships two additive LastaDi files that
Fess merges from every jar on the class path:

* `fess_storage++.xml` registers `gcsStorageClient`. `StorageClientFactory` resolves a client as
  `<storage.type>StorageClient`, so the component name is what makes `storage.type=gcs` resolve.
* `crawler/client++.xml` registers `gcsClient` and calls `crawlerClientCreator.register()` for
  `gcs:.*`, the same way fess-crawler-playwright registers its client.

`GcsClient` and the `gcs:` URL handler themselves remain in fess-crawler; only the SDK they compile
against ships here. The SDK is shaded in without relocation for that reason — those classes resolve
`com.google.cloud.storage` by its real name.
