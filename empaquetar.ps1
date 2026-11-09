param([switch] $VerificarHerramientas, [switch] $SinPruebas)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$project = $PSScriptRoot
$toolDir = Join-Path $project '.packaging-tools'
New-Item -ItemType Directory -Force $toolDir | Out-Null

# Visual puede heredar un PATH anterior a la instalacion de Maven.
$mavenCommand = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue
$maven = if ($mavenCommand) { $mavenCommand.Source } else { $null }
if (!$maven) {
    $candidatos = @()
    foreach ($base in @($env:MAVEN_HOME, $env:M2_HOME)) {
        if ($base) { $candidatos += Join-Path $base 'bin\mvn.cmd' }
    }
    $candidatos += @(Get-ChildItem -Path "$env:ProgramFiles\Apache\apache-maven-*\bin\mvn.cmd" -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | ForEach-Object FullName)
    $maven = $candidatos | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
}
if (!$maven) { throw 'No se encontro Maven. Configure MAVEN_HOME con la carpeta donde esta instalado.' }
Write-Host "Maven: $maven"

function Get-ToolArchive($Name, $Url, $Hash, $Folder) {
    $zip = Join-Path $toolDir $Name
    if (!(Test-Path -LiteralPath $zip)) {
        Invoke-WebRequest -Uri $Url -OutFile $zip
    }
    if ((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne $Hash) {
        throw "SHA256 incorrecto: $zip. Elimine ese archivo y vuelva a ejecutar."
    }
    if (!(Test-Path -LiteralPath (Join-Path $toolDir $Folder))) {
        Expand-Archive -LiteralPath $zip -DestinationPath $toolDir
    }
    return (Join-Path $toolDir $Folder)
}
function Invoke-Checked($Exe, [string[]] $Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Exe termino con codigo $LASTEXITCODE" }
}

$jdk = Get-ToolArchive 'jdk21.zip' 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_windows_hotspot_21.0.13_9.zip' 'a4dbaa05fca4bd89d84584e58c459bd732efebb1722af80ab13c6516832004f9' 'jdk-21.0.13+9'
$fx = Get-ToolArchive 'javafx-jmods.zip' 'https://download2.gluonhq.com/openjfx/21.0.8/openjfx-21.0.8_windows-x64_bin-jmods.zip' 'a19e964c14ad011d58726f00d8c810ba3d9da5dc369ea3e1b2f71e617302da0e' 'javafx-jmods-21.0.8'
if ((Get-Content (Join-Path $jdk 'release') -Raw) -notmatch 'JAVA_VERSION="21\.') { throw 'Se requiere JDK 21' }
if ($VerificarHerramientas) {
    $anterior = $env:JAVA_HOME
    try {
        $env:JAVA_HOME = $jdk
        Invoke-Checked $maven @('--version')
    } finally { $env:JAVA_HOME = $anterior }
    return
}
[xml] $pom = Get-Content (Join-Path $project 'pom.xml')
$fxDependencies = @($pom.project.dependencies.dependency | Where-Object groupId -eq 'org.openjfx')
if (@($fxDependencies | Where-Object version -ne '21.0.8').Count) { throw 'La version JavaFX del POM debe coincidir con los JMODS (21.0.8)' }
$oldJavaHome = $env:JAVA_HOME
$oldPath = $env:PATH
Push-Location $project
try {
    $env:JAVA_HOME = $jdk
    $env:PATH = "$jdk\bin;$oldPath"
    # Directorio nuevo en cada ejecucion: no borra distribuciones anteriores.
    $build = Join-Path $toolDir ('build-' + [guid]::NewGuid().ToString('N'))
    $classesDir = Join-Path $build 'maven'
    if ($SinPruebas) {
        Invoke-Checked $maven @('--batch-mode', "-Dpackaging.build.directory=$classesDir", '-Dmaven.test.skip=true', 'package')
    } else {
        Invoke-Checked $maven @('--batch-mode', "-Dpackaging.build.directory=$classesDir", 'verify')
    }
    $inputDir = Join-Path $build 'input'
    $runtime = Join-Path $build 'runtime'
    $dest = Join-Path $project ('dist\' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Force $inputDir, $dest | Out-Null
    $jarName = "$($pom.project.artifactId)-$($pom.project.version).jar"
    Copy-Item -LiteralPath (Join-Path $classesDir $jarName) -Destination $inputDir
    # Incluye dependencias futuras que no sean JavaFX; JavaFX vive solo en el runtime.
    Invoke-Checked $maven @('--batch-mode', 'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies', '-DincludeScope=runtime', '-DexcludeGroupIds=org.openjfx', "-DoutputDirectory=$inputDir")
    Invoke-Checked "$jdk\bin\jlink.exe" @('--module-path', "$jdk\jmods;$fx", '--add-modules', 'java.se,javafx.controls,javafx.fxml,jdk.crypto.ec,jdk.crypto.mscapi,jdk.localedata,jdk.charsets,jdk.unsupported,jdk.zipfs,jdk.naming.dns,jdk.security.auth', '--bind-services', '--strip-debug', '--no-header-files', '--no-man-pages', '--output', $runtime)
    foreach ($dll in @('prism_sw.dll', 'prism_d3d.dll', 'glass.dll', 'javafx_font.dll', 'javafx_iio.dll')) {
        if (!(Test-Path -LiteralPath "$runtime\bin\javafx\$dll")) { throw "Falta biblioteca nativa: $dll" }
    }
    Invoke-Checked "$jdk\bin\jpackage.exe" @('--type', 'app-image', '--name', 'MantenimientoServidores', '--app-version', '1.0', '--vendor', 'Mantenimiento Servidores', '--input', $inputDir, '--main-jar', $jarName, '--main-class', 'com.mantenimiento.servidores.Launcher', '--runtime-image', $runtime, '--java-options', '--add-modules=javafx.controls,javafx.fxml', '--dest', $dest)
    $app = Join-Path $dest 'MantenimientoServidores'
    Invoke-Checked "$app\runtime\bin\java.exe" @('-version')
    if (!$SinPruebas) { & (Join-Path $project 'verificar-paquete.ps1') -AppDirectory $app }
    $configDir = Join-Path $dest 'Configuracion'
    New-Item -ItemType Directory -Force $configDir | Out-Null
    Copy-Item -LiteralPath 'C:\MantenimientoServidores\portales.txt' -Destination (Join-Path $configDir 'portales.txt')
    Compress-Archive -Path @($app, $configDir) -DestinationPath (Join-Path $dest 'MantenimientoServidores-Windows-x64-Java21.zip')
    Write-Host "Aplicacion lista: $app"
} finally {
    $env:JAVA_HOME = $oldJavaHome
    $env:PATH = $oldPath
    Pop-Location
}

