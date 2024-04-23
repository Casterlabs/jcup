#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "str_builder.h"

#pragma comment(lib, "SHELL32.LIB")

int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPTSTR lpCmdLine, int nCmdShow)
{
    boolean hasConsole = AttachConsole(ATTACH_PARENT_PROCESS);
    if (hasConsole)
    {
        freopen("CONIN$", "r", stdin);
        freopen("CONOUT$", "w", stdout);
        freopen("CONOUT$", "w", stderr);
    }

    // CWD to the executable path.
    {
        char path[MAX_PATH];
        DWORD length = GetModuleFileName(NULL, path, MAX_PATH);
        if (length == 0)
        {
            printf("Error getting the path of the executable.\n");
            return 1;
        }

        // Extract directory part of the path
        char *last_backslash = strrchr(path, '\\');
        if (last_backslash != NULL)
        {
            *last_backslash = '\0'; // Null-terminate to get the directory path
        }
        else
        {
            fprintf(stderr, "No directory part found in path, exiting.\n");
            return 1;
        }

        // Change the current working directory
        if (!SetCurrentDirectory(path))
        {
            fprintf(stderr, "Error changing the CWD, exiting.\n");
            return 1;
        }
    }

    str_builder_t *command = str_builder_create();

    str_builder_add_str(command, "runtime\\bin\\java.exe", 0);

    // Look for a vmargs.txt, if it exists then append it to the string builder.
    {
        FILE *fp = fopen("vmargs.txt", "r");

        if (fp == NULL)
        {
            fprintf(stderr, "No arguments file found (vmargs.txt) for the VM, exiting.\n");
            return -1;
        }

        str_builder_add_char(command, ' '); // Don't forget a leading space.
        char ch;
        while ((ch = fgetc(fp)) != EOF)
        {
            str_builder_add_char(command, ch);
        }
        fclose(fp);
    }

    str_builder_add_char(command, ' ');
    str_builder_add_str(command, lpCmdLine, 0);

    char *to_execute = str_builder_dump(command, NULL);
    str_builder_destroy(command); // Free that memory.

    STARTUPINFO si;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);

    // Redirect stdout to the current process' stdout
    si.dwFlags = STARTF_USESTDHANDLES;
    si.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
    si.hStdOutput = GetStdHandle(STD_OUTPUT_HANDLE);
    si.hStdError = GetStdHandle(STD_ERROR_HANDLE);

    // Start the child process in a new process group
    si.dwFlags |= STARTF_FORCEOFFFEEDBACK;

    // Create the child process in a new process group
    DWORD dwCreationFlags = CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW;

    PROCESS_INFORMATION pi;
    ZeroMemory(&pi, sizeof(pi));

    // Start the child process.
    if (!CreateProcess(
            NULL,            // No module name (use command line)
            to_execute,      // Command line
            NULL,            // Process handle not inheritable
            NULL,            // Thread handle not inheritable
            FALSE,           // Set handle inheritance to FALSE
            dwCreationFlags, // No creation flags
            NULL,            // Use parent's environment block
            NULL,            // Use parent's starting directory
            &si,             // Pointer to STARTUPINFO structure
            &pi              // Pointer to PROCESS_INFORMATION structure
            ))
    {
        fprintf(stderr, "CreateProcess failed (%lu).\n", GetLastError());
        return -1;
    }

    // Wait until child process exits.
    // WaitForSingleObject(pi.hProcess, INFINITE);

    // // Get the exit code.
    // DWORD exit_code = 0;
    // GetExitCodeProcess(pi.hProcess, &exit_code);

    // // Close process and thread handles.
    // CloseHandle(pi.hProcess);
    // CloseHandle(pi.hThread);

    // return exit_code;
    return 0;
}
