@echo OFF
cl launcher.c str_builder.c /link /SUBSYSTEM:WINDOWS /MACHINE:X64 /OUT:windows-launcher-x86_64.exe
del *.obj