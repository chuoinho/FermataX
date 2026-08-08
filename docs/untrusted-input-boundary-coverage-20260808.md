# SMB, SFTP, and Whisper untrusted-input boundary coverage — 2026-08-08

## Scope and baseline

At the start of this task, `modules/smb`, `modules/sftp`, and `modules/whisper` had no unit-test source directories.
SMB and SFTP each contain only an addon `Provider`; their actual remote-response boundary is the shared `:utils` implementation used by those providers.
Whisper has a Java/JNI boundary and no prior test source. SMB/SFTP boundary tests therefore run in the existing `:fermata` Mobile suite, while Whisper tests run in its dynamic-feature Mobile task. CI now runs both in the Mobile unit gate.

## Boundary map and findings

| Functional module | Actual untrusted boundary | Previous behavior | Resolution and coverage |
| --- | --- | --- | --- |
| SMB | SMBJ parses packets; `SmbRoot.useShare()` receives failures and `SmbFolder.getChildren()` consumes `FileIdBothDirectoryInformation` listings | Null/truncated data became a generic NPE; server-controlled separators could enter a VFS path; EOF/timeouts exposed library-specific failures | `VfsNetworkSafety` rejects missing data/non-segment names and normalizes EOF, timeout, and protocol failures to `VfsException`. Tests cover all of those fixtures. |
| SFTP | JSch parses packets; `SftpRoot.useChannel()` receives failures and `SftpFolder.getChildren()` consumes `LsEntry`/`SftpATTRS` | Same malformed-listing and name-path risks; timeout/drop errors leaked from JSch | Uses the same validator and error normalization. Tests also preserve legal dot entries and Unicode names. |
| Whisper | `Whisper.read()` marshals PCM metadata/`ByteBuffer` into `Whisper_resample` | The direct-buffer requirement was only a release-disabled Java assert. Non-direct PCM could produce a null native address; invalid channels/rate could divide by zero/overflow; bad native counts could corrupt Java buffer position. | Java and C++ independently validate input. Tests cover heap buffers, partial frames, invalid metadata, and impossible positive/negative native consumed counts. Mobile/Auto native builds compile the guard. |

## Native crash boundary

There is **no recovery mechanism for a true native process crash**. A SIGSEGV, abort, or bug inside `whisper_jni`/whisper.cpp terminates the app process before Java can catch it. This task deliberately does not attempt out-of-process transcription isolation. The added Java and C++ guards remove the identified malformed-input routes into unsafe native operations; process isolation and native fuzzing remain a follow-up hardening item.

## Remaining coverage and follow-up risk

- These tests do not fuzz SMBJ or JSch raw wire decoders. A disposable fake SMB/SFTP server or fuzz harness is a future transport-level test.
- Whisper model loading and `fullTranscribe()` results are not model-fuzzed; those need an Android native/model fixture.
- Other modules without unit-test sources are Cast, GDrive, ML Kit, and OpusMT. GDrive and OpusMT are network-facing and merit a later boundary-focused pass; neither exposed a comparably direct unchecked native-buffer or server-listing boundary in this scope.

## Verification

Focused Mobile tests run `VfsNetworkSafetyTest`; the Whisper dynamic-feature Mobile task runs `WhisperAudioInputTest`. Mobile and Auto Whisper assemblies compile the changed JNI source. Full Mobile/Auto suites, architecture guards, lint, whitespace verification, and hosted CI are recorded with the delivery commit.
