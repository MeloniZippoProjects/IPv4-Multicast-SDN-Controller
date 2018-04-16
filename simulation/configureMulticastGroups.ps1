param(
    [Parameter(Mandatory=$true)][string]$restServerIP,
    [int]$restServerPort = 8080
)

$restAPI = "http://${restServerIP}:$restServerPort/";

Write-Host "Rest endpoint: $restAPI"

$createGroup = @{
    "ip" = "11.0.0.1";
    "name" = "group1";
} | ConvertTo-Json

$joinHost1 = @{
    "host" = "10.0.0.1";
} | ConvertTo-Json

$joinHost2 = @{
    "host" = "10.0.0.2";
} | ConvertTo-Json

function Call-Api($action, $method, $body)
{
    $response = Invoke-WebRequest -Uri ($restAPI + $action) -Method $method -Body $body -ContentType "application/json"
    $response
}

function Join-Host($lastByte)
{
    $joinHost = @{
        "host" = "10.0.0."+$lastByte;
    } | ConvertTo-Json

    $joinHost
}


Call-Api "multicastgroups/" 'Put' $createGroup
Call-Api "multicastgroups/1/hosts" 'Put' (Join-Host 2)
Call-Api "multicastgroups/1/hosts" 'Put' (Join-Host 4)
Call-Api "multicastgroups/1/hosts" 'Put' (Join-Host 7)
Call-Api "multicastgroups/1/hosts" 'Put' (Join-Host 8)
Call-Api "multicastgroups/" 'Get'


