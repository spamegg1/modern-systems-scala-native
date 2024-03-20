# How does `realloc` know the "old size" of the block of data that it needs to copy?

`realloc` needs a pointer that was returned by `malloc`,
otherwise it won't work correctly.

It seems that `malloc` returns a pointer, which is *preceded by some metadata* in memory.
For example, let's take this C code:

```c
int main() {
    void *array = malloc(sizeof(int) * 3);
    array[1] = 5;
    printf("%p\n", array); // Print, let's say: 0xAB
}
```

```bash
    Metadata    Pointer returned to the user by malloc
    V           V
... -+-----------+---+---+---+- ...
     | ... 3 ... | ? | 5 | ? |
... -+-----------+---+---+---+- ...
                 0xAB
           ^
           Information of the size of the chunk 0xAB is stored here
```

What happens when the size of the chunk is a *variable* provided by the user?

```c
int main(int size) {
    void *array = malloc(sizeof(int) * size);
    // do stuff...
}
```

I don't quite get it, but apparently the metadata stores `size` in it,
so when a user inputs `size`, it's also in the metadata?
Apparently `size` is known at runtime, somehow.
