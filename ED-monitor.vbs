Set objShell = CreateObject("WScript.Shell")
' Route optimizer logging: INFO by default. Change to DEBUG when you need the
' per-candidate ROUTE_STEP/ROUTE_DECISION trace in logs\com.zergatstage.monitor.routes.service.log
routeLogLevel = "INFO"
objShell.Run "C:\Users\Father\.jdks\temurin-25.0.1\bin\javaw.exe" & " " & _
    "-Ded.server.baseUrl=http://localhost:50080" & " " & _
    "-Ded.route.logLevel=" & routeLogLevel & " " & _
    "-jar C:\App\ed-monitor\ed-monitor-swing\target\ed-monitor-swing-0.3.1.jar", 0, False
