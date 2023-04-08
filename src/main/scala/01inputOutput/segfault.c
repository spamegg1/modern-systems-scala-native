#include <stdio.h>  // this has to be included in any file doing I/O
#include <stdlib.h> // needed for exit() and constants
// #include <string.h>

int main(int argc, char *argv[])
{
    char *line_buffer = calloc(1024, sizeof(char));

    while (fgets(line_buffer, 1023, stdin) != NULL)
    {
        char *string_pointer = calloc(1, sizeof(char *));
        int scan_result = sscanf(line_buffer, "%s\n", string_pointer);

        if (scan_result < 1)
        {
            printf("insufficient matches in sscanf: %d\n", scan_result);
            exit(EXIT_FAILURE);
        }

        printf("scan results: %s\n", string_pointer);
    }

    return EXIT_SUCCESS;
}
