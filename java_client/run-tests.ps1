param(
    [string]$JUnitVersion = '1.11.4'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

New-Item -ItemType Directory -Force -Path "$root\lib", "$root\target\classes", "$root\target\test-classes" | Out-Null

$json = Join-Path $root 'lib\json-20231013.jar'
if (-not (Test-Path $json)) {
    Write-Error "Missing $json — download json-20231013.jar first (Maven Central)."
}

$junit = Join-Path $root "lib\junit-platform-console-standalone-$JUnitVersion.jar"
if (-not (Test-Path $junit)) {
    $uri = "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$JUnitVersion/junit-platform-console-standalone-$JUnitVersion.jar"
    Invoke-WebRequest -Uri $uri -OutFile $junit -UseBasicParsing
}

$cpMain = "${json};${root}\target\classes"
javac -encoding UTF-8 -cp "$json" -d "$root\target\classes" "$root\src\main\java\SquareServiceClient.java"

$cpTest = "${json};${root}\target\classes;$junit"
javac -encoding UTF-8 -cp "$cpTest" -d "$root\target\test-classes" "$root\src\test\java\SquareServiceClientTest.java"

$runCp = "${json};${root}\target\classes;${root}\target\test-classes"
& java -jar $junit execute --class-path $runCp --scan-class-path
