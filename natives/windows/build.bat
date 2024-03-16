@echo OFF
rc.exe resources.rc
cl launcher.c str_builder.c resources.res /link /SUBSYSTEM:WINDOWS
del *.obj