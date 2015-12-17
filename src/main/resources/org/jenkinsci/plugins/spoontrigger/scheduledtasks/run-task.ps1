[CmdletBinding()]
param
(
	[Parameter(Mandatory=$True,ValueFromPipeline=$True,ValueFromPipelineByPropertyName=$True,HelpMessage="Name of the scheduled task")]
	[string] $TaskName,
    [Parameter(Mandatory=$True,ValueFromPipeline=$True,ValueFromPipelineByPropertyName=$True,HelpMessage="Path to the program to execute")]
    [string] $Path,
    [Parameter(ValueFromPipeline=$True,ValueFromPipelineByPropertyName=$True,HelpMessage="Program arguments")]
    [string] $Arguments,
    [Parameter(ValueFromPipeline=$True,ValueFromPipelineByPropertyName=$True,HelpMessage="Program to execute")]
	[string] $WorkingDirectory
)

function Log-Status ($log) {
	Write-Output "# PowerShell => $log"
}

function Is-ScheduledTaskRunning ($taskName) {
	return Get-ScheduledTask | Where-Object {$_.TaskName -like $taskName -and $_.State -like "Running"}
}

Try
{
    $taskExists = Get-ScheduledTask | Where-Object {$_.TaskName -like $TaskName}
    if($taskExists)
    {
	    Log-Status "Scheduled task '$TaskName' was left by previous job. Removing it now."
	    Unregister-ScheduledTask $TaskName -Confirm:$False
    }

    $workingDirectoryToUse = $WorkingDirectory
    if(-not $workingDirectoryToUse)
    {
        $workingDirectoryToUse = Get-Location
    }

    $logFileName = "log$(Get-Date -Format yyyymmddThhmmss).txt"
    $logFilePath = [System.IO.Path]::Combine($workingDirectoryToUse, $logFileName)
    $logStream = New-Object System.IO.FileStream $logFilePath, 'OpenOrCreate', 'Read', 'Write'
    $reader = New-Object System.IO.StreamReader $logStream

    function Forward-Logs()
    {
        $logs = $reader.ReadToEnd()
        if($logs)
        {
            Write-Output $logs
        }
    }

    try
    {
        $taskAction = New-ScheduledTaskAction -Execute $Path -Argument "$Arguments >> $logFilePath 2>&1" -WorkingDirectory $workingDirectoryToUse
        Log-Status "Registering scheduled task '$TaskName'"
        Register-ScheduledTask -Action $taskAction -TaskName $TaskName | Out-Null
        
        Log-Status "Starting scheduled task '$TaskName'"
        Start-ScheduledTask $TaskName

        $isTaskRunning = $True
        while ($isTaskRunning)
        {
            Forward-Logs

	        Start-Sleep -Seconds 5

            $isTaskRunning = Is-ScheduledTaskRunning $TaskName
        }

        Forward-Logs
    }
    finally
    {
        $reader.Close()
    }

    Log-Status "Scheduled task '$TaskName' finished"
    Log-Status "Removing scheduled task '$TaskName'"

    #Unregister-ScheduledTask $TaskName -Confirm:$False

    #Remove-Item $logFilePath

    Exit 0
}
Catch
{
    Write-Error $_.Exception.Message
    Exit -1
}