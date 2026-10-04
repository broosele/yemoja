# Takes out of the installer at the path given the value that ticks the desktop shortcut's box, so
# the box starts unticked and a desktop shortcut is made only where the user ticks it. jpackage
# writes the value and offers no switch for it, so it is taken out after. See
# ../gui/desktop/windows/doc.md, WIN-5.
param([Parameter(Mandatory = $true)][string] $Msi)

$installer = New-Object -ComObject WindowsInstaller.Installer
# 1 opens the database to change it, the changes kept on Commit.
$database = $installer.GetType().InvokeMember('OpenDatabase', 'InvokeMethod', $null, $installer, @($Msi, 1))
$query = "DELETE FROM ``Property`` WHERE ``Property``='JP_INSTALL_DESKTOP_SHORTCUT'"
$view = $database.GetType().InvokeMember('OpenView', 'InvokeMethod', $null, $database, @($query))
$view.GetType().InvokeMember('Execute', 'InvokeMethod', $null, $view, $null) | Out-Null
$view.GetType().InvokeMember('Close', 'InvokeMethod', $null, $view, $null) | Out-Null
$database.GetType().InvokeMember('Commit', 'InvokeMethod', $null, $database, $null) | Out-Null
