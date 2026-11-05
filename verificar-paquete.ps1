param([Parameter(Mandatory=$true)][string] $AppDirectory)
$ErrorActionPreference = 'Stop'
$app = (Resolve-Path -LiteralPath $AppDirectory).Path
foreach ($entry in @('MantenimientoServidores.exe','app','runtime','runtime\bin\javafx\prism_sw.dll')) {
    if (!(Test-Path -LiteralPath (Join-Path $app $entry))) { throw "Falta $entry" }
}
$version = & "$app\runtime\bin\java.exe" --version
if ($LASTEXITCODE -ne 0 -or $version[0] -notmatch '21\.') { throw 'Runtime incorrecto' }
$modules = & "$app\runtime\bin\java.exe" --list-modules
foreach ($module in @('javafx.base','javafx.graphics','javafx.controls','javafx.fxml')) {
    if (!($modules -match "^$([regex]::Escape($module))@21.0.8$")) { throw "Falta $module 21.0.8" }
}
$testDir = Join-Path $PSScriptRoot ('.packaging-tools\smoke-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force "$testDir\home" | Out-Null
# Un archivo ocupa el lugar del directorio: es imposible extraer DLL a esa cache.
Set-Content -LiteralPath "$testDir\home\.openjfx" -Value 'CACHE BLOCKED'
Set-Content -LiteralPath "$testDir\blocked-cache" -Value 'CACHE BLOCKED'
$saved = @{}
foreach ($key in @('PATH','JAVA_HOME','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS')) {
    $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}
$results = @()
try {
    $env:PATH = "$env:SystemRoot\System32;$env:SystemRoot"
    $env:JAVA_HOME = "$testDir\no-java-installed"
    $env:_JAVA_OPTIONS = $null
    $env:JDK_JAVA_OPTIONS = $null
    foreach ($mode in @('default','sw')) {
        $gui = $null
        $env:JAVA_TOOL_OPTIONS = '-Duser.home="' + "$testDir\home" + '" -Djavafx.cachedir="' + "$testDir\blocked-cache" + '" -Djavafx.verbose=true -Dprism.verbose=true'
        if ($mode -eq 'sw') { $env:JAVA_TOOL_OPTIONS += ' -Dprism.order=sw' }
        $process = Start-Process -FilePath "$app\MantenimientoServidores.exe" -WorkingDirectory $testDir -WindowStyle Hidden -PassThru -RedirectStandardOutput "$testDir\$mode-out.txt" -RedirectStandardError "$testDir\$mode-err.txt"
        try {
            $deadline = (Get-Date).AddSeconds(30)
            do {
                Start-Sleep -Milliseconds 300
                $process.Refresh()
                if ($process.HasExited) { throw "La app termino antes de abrir ($mode); revisar $testDir" }
                # jpackage puede crear un segundo proceso para alojar la JVM.
                $childIds = @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$($process.Id)" | ForEach-Object ProcessId)
                $candidates = @($process) + @($childIds | ForEach-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue })
                $gui = $candidates | Where-Object MainWindowHandle -ne 0 | Select-Object -First 1
            } while (!$gui -and (Get-Date) -lt $deadline)
            if (!$gui) { throw "No aparecio la ventana ($mode)" }
            Start-Sleep -Seconds 2
            $gui.Refresh()
            if ($gui.HasExited) { throw "La ventana se cerro durante el arranque ($mode)" }
            $loaded = @($gui.Modules | Where-Object ModuleName -match '^(jvm|prism_.*|glass|javafx_.*)\.dll$' | ForEach-Object FileName)
            foreach ($path in $loaded) {
                if (!$path.StartsWith("$app\runtime\", [StringComparison]::OrdinalIgnoreCase)) { throw "DLL externa: $path" }
            }
            if (!($loaded -match 'jvm\.dll$') -or !($loaded -match 'glass\.dll$')) { throw 'Faltan DLL cargadas' }
            if ($mode -eq 'sw' -and !($loaded -match 'prism_sw\.dll$')) { throw 'No se cargo prism_sw.dll' }
            if ((Get-Item -LiteralPath "$testDir\home\.openjfx").PSIsContainer) { throw 'Cache inesperada' }
            $results += [pscustomobject]@{ mode=$mode; title=$gui.MainWindowTitle; loadedLibraries=$loaded; cacheUnavailable=$true; passed=$true }
        } finally {
            if ($gui -and !$gui.HasExited) { Stop-Process -Id $gui.Id }
            if (!$process.HasExited) { Stop-Process -Id $process.Id }
        }
    }
} finally {
    foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process') }
}
[pscustomobject]@{ runtime=$version; tests=$results; logs=$testDir } | ConvertTo-Json -Depth 5 | Tee-Object -FilePath "$app\verificacion.json"
