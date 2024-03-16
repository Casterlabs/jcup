@echo OFF
cl launcher.c str_builder.c /link /SUBSYSTEM:WINDOWS
del *.obj