// All this file does is immediately call a sister bat file with the same input arguments.

#include <windows.h>
#include <stdio.h>
#include <string.h>
#include "str_builder.h"

#pragma comment(lib, "SHELL32.LIB")

int RunCommand(char *module, char *to_execute, boolean discardConsole)
{
    // Create a job object
    HANDLE hJob = CreateJobObject(NULL, NULL);
    if (hJob == NULL)
    {
        fprintf(stderr, "Error creating job object\n");
        return 1;
    }

    // Assign the job to the current process
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION ji;
    ZeroMemory(&ji, sizeof(ji));
    ji.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    if (!SetInformationJobObject(hJob, JobObjectExtendedLimitInformation, &ji, sizeof(ji)))
    {
        fprintf(stderr, "Error setting job object information\n");
        CloseHandle(hJob);
        return 1;
    }

    STARTUPINFO si;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    // if (discardConsole)
    // {
    //     si.dwFlags = STARTF_USESHOWWINDOW;
    //     si.wShowWindow = SW_HIDE; // Hide the window of the child process
    // }
    // else
    // {
    si.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
    si.hStdOutput = GetStdHandle(STD_OUTPUT_HANDLE);
    si.hStdError = GetStdHandle(STD_ERROR_HANDLE);
    // }

    PROCESS_INFORMATION pi;
    ZeroMemory(&pi, sizeof(pi));

    // Start the child process.
    if (!CreateProcess(
            module,           // Can use CMD, otherwise nullptr.
            to_execute,       // Command line
            NULL,             // Process handle not inheritable
            NULL,             // Thread handle not inheritable
            FALSE,            // Set handle inheritance to FALSE
            CREATE_NO_WINDOW, //
            NULL,             // Use parent's environment block
            NULL,             // Use parent's starting directory
            &si,              // Pointer to STARTUPINFO structure
            &pi               // Pointer to PROCESS_INFORMATION structure
            ))
    {
        fprintf(stderr, "CreateProcess failed (%d).\n", GetLastError());
        return -1;
    }

    // Assign the child process to the job
    if (!AssignProcessToJobObject(hJob, pi.hProcess))
    {
        fprintf(stderr, "Error assigning process to job object\n");
        CloseHandle(pi.hProcess); // Go ahead and kill the process.
        CloseHandle(hJob);
        return -1;
    }

    // Wait for the process to exit normally.
    WaitForSingleObject(pi.hProcess, INFINITE);

    // Get the exit code.
    DWORD exit_code = 0;
    GetExitCodeProcess(pi.hProcess, &exit_code);

    // Cleanup.
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    CloseHandle(hJob);

    return exit_code;
}

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

    str_builder_add_str(command, "runtime/bin/java.exe", 0);

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

    long long exit_code = RunCommand(NULL, to_execute, !hasConsole);
    return exit_code;
}
