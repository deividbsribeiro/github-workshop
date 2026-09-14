# CFTV Local

Minimal Android RTSP viewer for four local-network cameras.

- 2x2 grid
- tap camera to maximize; Back restores grid
- per-camera HD toggle (`subtype=0` / `subtype=1`)
- per-camera audio from main stream
- RTP over RTSP/TCP
- no cloud
- only attempts playback while Android reports an active Wi-Fi transport
- RTSP configuration stored locally encrypted with Android Keystore

The repository build contains no CFTV credentials. Configuration can be entered in-app or injected with ADB extras (`saveConfig=true`, `cam1b64` ... `cam4b64`).
