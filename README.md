# Reversion

Real-time cross-platform file versioning

![Reversion screenshot](https://i.imgur.com/kvQXz3a.png)

![Reversion screenshot](https://i.imgur.com/eEwbTtC.png)

## Description

Reversion is a file versioning system for end users. Unlike source code version control systems like Git, Reversion is
meant to be easy for anyone to use and designed to work with large binary files. Reversion can either be used for
manually managing file versions or as a set-and-forget automatic version control system that saves new versions in the
background and cleans up old ones according to user-defined rules.

Reversion works on Windows, macOS and Linux, and saves version information in a self-contained, platform-independent
format, meaning that tracked directories can be moved or synchronized between devices. Unlike cloud-based version
control offered by services like Google Drive and Microsoft OneDrive, versions made with Reversion are stored locally so
they can be kept private and accessed offline.

## Features

- File-level or block-level deduplication with content-defined chunking
- Efficient handling of large binary files
- Checks data integrity and provides options for repairing corrupt version history
- Supports tracking changes automatically in the background
- Automatically cleans up old versions based on user-defined rules, including support for staggered versioning
- Allows for defining complex rules about which files to track and which to ignore (file size, file extension, hidden
  files, cache files, application files, glob patterns, regex patterns, etc.)
- Browse through old versions of files using a FUSE file system
- Pin files to keep them forever
- Easily restore past versions of files
- Open past version in their default application without restoring them
- Organize versions by giving them optional names and descriptions
- Cross-platform

## Usage

Download and extract the `.zip` file for your platform. To start the program, run `Reversion.vbs` (Windows) or
`Reversion` (macOS/Linux).

Reversion needs to run in the background to track changes to your files. This background process is started the first
time the application is launched and keeps running after it is closed. To start the background process on boot, put a
link or shortcut to the Reversion executable in your operating system's application startup directory.

## Reversion is not...

- A source code VCS. While designed to be a more suitable alternate to Git for end users, Reversion does not have the
powerful merging, diffing, or branching features of a proper source code VCS.
- A backup solution. Version information is all stored locally, and storing version history remotely is currently not
supported. However, because version information is stored in the same directory as the files being tracked, tracked
directories can be backed up or synchronized to other devices.
- A frontend for LVM or copy-on-write file systems like ZFS or Btrfs. Reversion uses its own storage format which is
less efficient than native copy-on-write snapshots. However, versions made with Reversion can be backed up or shared
between devices.
- A place to store data which is not backed up elsewhere. While the storage backend is unit-tested, this software is
still immature. User testing is greatly appreciated, but use at your own risk!
