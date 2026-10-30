<#
.SYNOPSIS
  Builds a self-contained Windows distribution (jlink runtime + both fat jars + launcher) as a zip.

.DESCRIPTION
  Run after `mvnw package -DskipTests`. Output: <OutDir>\ed-monitor-<Version>-windows-x64.zip
  Unpacked layout:
    ED-monitor.bat                       launcher (sets cwd to the folder, starts the UI with the bundled runtime)
    runtime\                             jlink-trimmed JRE shared by the UI and the backend
    app\ed-monitor-swing.jar             Swing client
    server\ed-monitor-server.jar         Spring Boot backend (started by the client, data in server\data)
    config\server-management.properties  overrides the classpath defaults with paths relative to the folder

.PARAMETER Version  Version label used in the zip/folder name (e.g. 0.3.1 or the tag name).
.PARAMETER JdkHome  JDK used for jlink (needs jmods). Defaults to JAVA_HOME.
.PARAMETER OutDir   Output directory. Defaults to <repo>\dist.
#>
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$OutDir
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) { $OutDir = Join-Path $repo 'dist' }
if (-not $JdkHome) { throw 'JdkHome not given and JAVA_HOME is not set.' }

$jlink = Join-Path $JdkHome 'bin\jlink.exe'
if (-not (Test-Path $jlink)) { throw "jlink not found at $jlink" }
if (-not (Test-Path (Join-Path $JdkHome 'jmods'))) { throw "No jmods directory in $JdkHome (need a full JDK, not a JRE)" }

function Find-Jar([string]$module, [string]$prefix) {
    $jar = Get-ChildItem (Join-Path $repo "$module\target") -Filter "$prefix-*.jar" |
        Where-Object { $_.Name -notlike 'original-*' -and $_.Name -notlike '*-sources.jar' } |
        Select-Object -First 1
    if (-not $jar) { throw "No $prefix jar in $module\target - run 'mvnw package -DskipTests' first." }
    return $jar
}
$swingJar = Find-Jar 'ed-monitor-swing' 'ed-monitor-swing'
$serverJar = Find-Jar 'ed-monitor-server' 'ed-monitor-server'

$name = "ed-monitor-$Version-windows-x64"
$stage = Join-Path $OutDir $name
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path $stage, "$stage\app", "$stage\server", "$stage\config" | Out-Null

# One runtime for both processes: Swing client (desktop, prefs) + Spring Boot/Hibernate/H2 backend.
$modules = 'java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,' +
    'java.net.http,java.prefs,java.rmi,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,' +
    'java.xml,jdk.crypto.ec,jdk.unsupported,jdk.zipfs,jdk.management'
& $jlink --add-modules $modules --output "$stage\runtime" --strip-debug --no-header-files --no-man-pages --compress=zip-6
if ($LASTEXITCODE -ne 0) { throw "jlink failed ($LASTEXITCODE)" }

Copy-Item $swingJar.FullName "$stage\app\ed-monitor-swing.jar"
Copy-Item $serverJar.FullName "$stage\server\ed-monitor-server.jar"

# Overrides merged on top of the classpath defaults (ServerManagementProperties). Relative paths resolve
# against the working directory, which ED-monitor.bat pins to this folder.
@'
# Paths relative to the unpacked folder (ED-monitor.bat sets it as working directory).
server.javaCommand=runtime\\bin\\java.exe
server.jarPath=server\\ed-monitor-server.jar
server.workingDir=server
server.args=--spring.profiles.active=prod
'@ | Set-Content "$stage\config\server-management.properties" -Encoding ASCII

@'
@echo off
rem Starts ed-monitor with the bundled runtime. No Java installation required.
cd /d "%~dp0"
start "" "runtime\bin\javaw.exe" -Ded.server.baseUrl=http://localhost:50080 "-Dserver.management.config=%~dp0config\server-management.properties" -jar "app\ed-monitor-swing.jar"
'@ | Set-Content "$stage\ED-monitor.bat" -Encoding ASCII

@"
ed-monitor $Version (Windows x64)

1. Unpack this folder anywhere you can write to (not Program Files).
2. Double-click ED-monitor.bat. No Java installation is required.

Start the backend from the "Server management" menu ("Start backend"); its database lives in server\data.
Logs are written to the logs folder. Settings: config\server-management.properties.
"@ | Set-Content "$stage\README.txt" -Encoding ASCII

$zip = Join-Path $OutDir "$name.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path $stage -DestinationPath $zip -CompressionLevel Optimal
Write-Host ("Created {0} ({1:N1} MB)" -f $zip, ((Get-Item $zip).Length / 1MB))
