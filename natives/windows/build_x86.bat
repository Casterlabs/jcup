@echo OFF
cl launcher.c str_builder.c /link /SUBSYSTEM:WINDOWS /MACHINE:X86 /OUT:windows-launcher-x86.exe
del *.obj