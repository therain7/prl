# Synchronization errors detectors

Simple open-source multithreaded application is taken and synchronization error detectors
([Helgrind](https://valgrind.org/docs/manual/hg-manual.html) & [ThreadSanitizer](https://clang.llvm.org/docs/ThreadSanitizer.html)) are run on it.<br>
Resulting reports of the tools are studied.


## Simple multithreaded application

[`MultithreadedDNSResolver`](https://github.com/stefanKnott/MultithreadedDNSResolver/tree/b15a22260739ed8d5a006f0df3965fd99d08a78e)
by _Stefan Knott_ was taken as a sample application to test for synchronization errors.<br>
It's a _multithreaded solution to DNS request handling, solves classic producer consumer problem_.

## Helgrind

First let's run Helgrind on the mentioned application.

For that the application was compiled using `clang 18.1.1`:
```bash
  $ clang -g -O0 -o lookup multi-lookup.c queue.c util.c
```

Then Helgrind (`valgrind-3.23.0`) was run on the application:
```bash
  $ valgrind --tool=helgrind ./lookup names3.txt results.txt
```

Helgrind points out the following possible data race:

```
Possible data race during read of size 4 at 0x440100 by thread #3
Locks held: none
   at 0x410558: outputUrls (multi-lookup.c:76)
   by 0x48CCD13: mythread_wrapper (hg_intercepts.c:406)
   by 0x49A6797: start_thread (in /usr/lib64/libc.so.6)
   by 0x4A1189B: thread_start (in /usr/lib64/libc.so.6)

This conflicts with a previous write of size 4 by thread #2
Locks held: 1, at address 0x440110
   at 0x410504: inputUrls (multi-lookup.c:63)
   by 0x48CCD13: mythread_wrapper (hg_intercepts.c:406)
   by 0x49A6797: start_thread (in /usr/lib64/libc.so.6)
   by 0x4A1189B: thread_start (in /usr/lib64/libc.so.6)
 Address 0x440100 is 0 bytes inside data symbol "open_requests"
```

## ThreadSanitizer

Let's also run ThreadSanitizer to see if it detects the same possible data race.

For that the application was compiled using `clang 18.1.1` and run as follows:
```bash
  $ clang -g -O0 -fsanitize=thread -o lookup multi-lookup.c queue.c util.c
  $ ./lookup names3.txt results.txt
```

ThreadSanitizer points at the same possible data race:
```
WARNING: ThreadSanitizer: data race (pid=24176)
  Write of size 4 at 0x0000019834a0 by thread T1 (mutexes: write M0):
    #0 inputUrls multi-lookup.c:63:15 (lookup+0x4fa028) (BuildId: 97b03d57e407a10e91d0ddf59d541e47d7a368c2)

  Previous read of size 4 at 0x0000019834a0 by thread T2:
    #0 outputUrls multi-lookup.c:76:24 (lookup+0x4fa0b0) (BuildId: 97b03d57e407a10e91d0ddf59d541e47d7a368c2)

  Location is global 'open_requests' of size 4 at 0x0000019834a0 (lookup+0x19834a0)
```

## Data race

Both tools correctly identified the data race when accessing a variable `open_requests`.<br>
Moreover, it was observed that due to the mentioned data race program execution sometimes hangs indefinetly.

The detected data race was eliminated by locking a mutex before accessing `open_requests`.<br>
Also some new conditinal variable signals were added to prevent the application from freezing.<br>
The fix is contained in [0001-data-race-fix.patch](0001-data-race-fix.patch) and can be applied by `git apply`.

Both Helgrind and ThreadSanitizer detected no errors when run on the fixed version.


