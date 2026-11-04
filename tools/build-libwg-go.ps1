#!/usr/bin/env pwsh
# Сборка libwg-go.so под android/arm без POSIX-утилит.
param(
    [string]$GoPath = "go",
    [Parameter(Mandatory=$true)][string]$Ndk,
    [string]$Source = "..\amneziawg\tunnel\tools\libwg-go",
    [string]$Out = ".\libwg-go.so"
)
$ErrorActionPreference = "Stop"
$tc = Join-Path $Ndk "toolchains\llvm\prebuilt\windows-x86_64"
$env:GOOS = "android"; $env:GOARCH = "arm"; $env:GOARM = "7"; $env:CGO_ENABLED = "1"
$env:CC = Join-Path $tc "bin\armv7a-linux-androideabi21-clang.cmd"
$env:CGO_CFLAGS = ""
$env:CGO_LDFLAGS = "-Wl,-soname=libwg-go.so"
Push-Location $Source
try {
    & $GoPath build -tags linux `
        -ldflags="-X github.com/amnezia-vpn/amneziawg-go/ipc.socketDirectory=/data/data/com.slimvpn.app/cache/amneziawg -buildid=" `
        -buildmode=c-shared -o $Out .
} finally { Pop-Location }
Write-Host "готово: $Out"