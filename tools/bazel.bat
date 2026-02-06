@echo off
:: dummy wrapper script to populate the BAZEL_REAL environment variable

"%BAZEL_REAL%" %*

:: exit with the same error code as the command above
exit /b %ERRORLEVEL%
