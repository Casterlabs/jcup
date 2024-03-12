// All this file does is immediately call a sister bat file with the same input arguments.

#include <windows.h>
#include <stdio.h>
#include <string.h>
#include "str_builder.h"

#pragma comment(lib, "SHELL32.LIB")

int RunCommand(char *module, char *to_execute)
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
    si.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
    si.hStdOutput = GetStdHandle(STD_OUTPUT_HANDLE);
    si.hStdError = GetStdHandle(STD_ERROR_HANDLE);
    // si.dwFlags |= STARTF_USESTDHANDLES;

    PROCESS_INFORMATION pi;
    ZeroMemory(&pi, sizeof(pi));

    // Start the child process.
    if (!CreateProcess(
            module,     // Can use CMD, otherwise nullptr.
            to_execute, // Command line
            NULL,       // Process handle not inheritable
            NULL,       // Thread handle not inheritable
            FALSE,      // Set handle inheritance to FALSE
            0,          // No creation flags
            NULL,       // Use parent's environment block
            NULL,       // Use parent's starting directory
            &si,        // Pointer to STARTUPINFO structure
            &pi         // Pointer to PROCESS_INFORMATION structure
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

int main(int argc, char **argv)
{
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

    // Grab the raw arguments, unparsed.
    {
        char *this_executable = argv[0];
        char *raw_command_line = GetCommandLine();
        char *this_command_line = calloc(strlen(raw_command_line), 1);
        memcpy( // Skip over the executable name.
            this_command_line,
            &raw_command_line[strlen(this_executable)],
            strlen(raw_command_line) - strlen(this_executable));

        str_builder_add_str(command, this_command_line, 0);
        free(raw_command_line);
        free(this_command_line);
    }

    char *to_execute = str_builder_dump(command, NULL);
    str_builder_destroy(command); // Free that memory.

    long long exit_code = RunCommand(NULL, to_execute);
    return exit_code;
}