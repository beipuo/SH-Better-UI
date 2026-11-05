# Author: dishanhai
& {
    $ErrorActionPreference = 'Stop'
    $jdkHome = Join-Path ${env:ProgramFiles} 'Java\jdk-21.0.11'
    if (-not (Test-Path -LiteralPath (Join-Path $jdkHome 'bin\java.exe'))) {
        throw "JDK 21 not found: $jdkHome"
    }
    $env:JAVA_HOME = $jdkHome
    $env:TEMP = 'C:\jtmp'
    $env:TMP = 'C:\jtmp'
    $gradle = Join-Path $PSScriptRoot 'gradle-install\gradle-8.8\bin\gradle.bat'
    Push-Location $PSScriptRoot
    try {
        & $gradle build --offline --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } finally { Pop-Location }
}
