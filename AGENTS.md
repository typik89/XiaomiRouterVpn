# AGENTS.md

## Gradle execution rule

- Always run Gradle from the repository root (`D:\github\XiaomiRouterVpn`).
- Always use project-local homes to avoid sandbox/profile directory issues:
  - PowerShell:
    - `New-Item -ItemType Directory -Force .\.gradle-local,.\.android | Out-Null`
    - `$env:GRADLE_USER_HOME = (Resolve-Path .\.gradle-local).Path`
    - `$env:ANDROID_USER_HOME = (Resolve-Path .\.android).Path`
  - Then run: `.\gradlew.bat "-Dkotlin.compiler.execution.strategy=in-process" <tasks>`
- One-liner example:
  - `$env:GRADLE_USER_HOME=(Resolve-Path .\.gradle-local).Path; $env:ANDROID_USER_HOME=(Resolve-Path .\.android).Path; .\gradlew.bat "-Dkotlin.compiler.execution.strategy=in-process" :app:assembleRelease`
