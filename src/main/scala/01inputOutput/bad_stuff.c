#include <stdio.h>  // this has to be included in any file doing I/O
#include <stdlib.h> // needed for exit() and constants
// #include <string.h>

// This was an attempt to replicate segfault in badSscanfExample, but in C:
// int main(int argc, char *argv[])
// {
//     char *line_buffer = calloc(1024, sizeof(char));

//     while (fgets(line_buffer, 1023, stdin) != NULL)
//     {
//         char *string_pointer = calloc(1, sizeof(char *));
//         int scan_result = sscanf(line_buffer, "%s\n", string_pointer);

//         if (scan_result < 1)
//         {
//             printf("insufficient matches in sscanf: %d\n", scan_result);
//             exit(EXIT_FAILURE);
//         }

//         printf("scan results: %s\n", string_pointer);
//     }

// return EXIT_SUCCESS;
// }

// Stack overflow
// int main(void)
// {
//     return main(); // segfault! Clang compiles without errors.
// }

int main(int argc, char *argv[])
{
    // Writing to read-only memory
    // char *s = "hello world\n";
    // *s = 'H'; // compiles, but if you run, segfault!

    // Dereferencing a null pointer, attempting to read its value
    // int *ptr = NULL; // it also works with: int *ptr;
    // printf("%d", *ptr); // compiles, but if you run, segfault!

    // Dereferencing a null pointer, attempting to update its value
    // int *ptr = NULL;
    // *ptr = 1; // compiles, but if you run, segfault!

    // Dereferencing a null pointer without doing anything
    // int *ptr = NULL;
    // *ptr; // no segfault but unused warning (dead-code elimination by compiler)

    // Buffer overflow
    // array index 20 is past the end of the array (which contains 12 elements)
    // char s[] = "hello world";
    // char c = s[20]; // clang compiles with warnings, not always segfaults.

    // Accessing an address that has been freed
    int *p = malloc(8); // allocating memory to p
    *p = 100;
    free(p);  // deallocated the space allocated to p
    *p = 110; // no segfault on clang or gcc. Why?

    // Improper use of scanf
    int n = 2;
    scanf(" ", n); // no segfault! Damn.

    return EXIT_SUCCESS;
}
