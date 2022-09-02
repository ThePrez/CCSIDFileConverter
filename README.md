# CCSID File Converter
A tool for converting a file from one CCSID to another, but with options that provide
options for how to handle characters that cannot be converted to the target CCSID


```
Usage: java -jar ccsidconverter.jar  [options] <file>

    Valid options include:
        --in=<file>:             Input file.
        --out=<file>:            Output file.
        --opt=<replace/delete>:  How to handle unconvertible characters (default: delete)
        --in-ccsid=<ccsid>:      Input file CCSID.
        --out-ccsid=<ccsid>:     Output file CCSID.
        --replacement=<char>:    Replacement character to use if replacing.
        --smart-quotes:          Replace "smart quotes" with standard quotes.
        --line-end=<cr/crlf>:    Line endings to use for output file.
```

**Note:** If you're just trying to convert EBCDIC files to view them with an editor or something,
[this project](https://github.com/ThePrez/CcsidGuesser/) may also interest you. 
