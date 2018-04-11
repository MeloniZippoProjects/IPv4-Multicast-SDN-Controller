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

$restAPI = "http://192.168.100.93:8080/";

function Call-Api($action, $method, $body)
{
    Write-Host $method
    $response = Invoke-WebRequest -Uri ($restAPI + $action) -Method $method -Body $body -ContentType "application/json"
    $response
}


Call-Api "multicastgroups/" 'Put' $createGroup
Call-Api "multicastgroups/1/hosts" 'Put' $joinHost1
Call-Api "multicastgroups/1/hosts" 'Put' $joinHost2
Call-Api "multicastgroups/" 'Get'


